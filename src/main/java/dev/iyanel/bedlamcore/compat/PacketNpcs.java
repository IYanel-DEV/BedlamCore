package dev.iyanel.bedlamcore.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Citizens-free packet player-model NPCs. Renders a real player model (skin included) for a lobby NPC body
 * without spawning a real player entity: tab-list add + player spawn packets, then an immediate tab-list
 * remove. The real body (invisible armor stand / villager) stays the click hitbox, so every existing
 * metadata-driven interaction path keeps working unchanged.
 *
 * Version paths, resolved once per JVM:
 * - Versioned Spigot (1.8–1.19.2): EntityPlayer(MinecraftServer, WorldServer, GameProfile, PlayerInteractManager)
 *   + PacketPlayOutPlayerInfo(ADD_PLAYER, ep) + PacketPlayOutNamedEntitySpawn(ep) + PacketPlayOutPlayerInfo(REMOVE_PLAYER, ep).
 * - 1.19.3–1.20.4 layouts: same EntityPlayer construction (ClientInformation factory on 1.20.4),
 *   ClientboundPlayerInfoUpdatePacket(EnumSet, Collection) + PacketPlayOutSpawnEntity(handle)
 *   + ClientboundPlayerInfoRemovePacket(List).
 * - Mojang-mapped (26.2): tab Entry record + ClientboundAddEntityPacket(id, UUID, pos, angles, EntityType.PLAYER, ...).
 * Rotation streams through the look-only entity packet + head-rotation packet (same yaw on both, no position
 * delta), and each viewer gets an arm-swing animation when the model loads — the Citizens behaviour, Citizens-free.
 * Any missing piece → {@link #available()} false → callers keep their existing (armor-stand) NPC look.
 */
public final class PacketNpcs {
    private PacketNpcs() { }

    private static volatile boolean resolved;
    private static boolean ok;

    // send plumbing (getHandle → playerConnection|connection → sendPacket|send)
    private static Field connectionField;
    private static Method sendMethod;

    // authlib (reflective — not on the compile classpath)
    private static Constructor<?> profileCtor;
    private static Constructor<?> propertyCtor;
    private static Constructor<?> propertyCtor3;   // (name, value, signature) — SIGNED textures
    private static Method propertiesPut;
    private static Method getPropertiesMethod;     // GameProfile getProperties() (legacy) or properties() (record, authlib 9.x)
    // authlib 9.x (Paper 1.21+/26.x): properties() is an IMMUTABLE multimap and PropertyMap has no no-arg ctor
    // (only PropertyMap(Multimap)). Build a mutable Guava multimap, wrap it in a PropertyMap, and pass that to
    // the GameProfile(UUID,String,PropertyMap) record constructor.
    private static Method multimapCreate;                // LinkedHashMultimap.create()
    private static Method multimapPut;                   // Multimap.put(Object,Object)
    private static Constructor<?> propertyMapWrapCtor;   // PropertyMap(Multimap)
    private static Constructor<?> gameProfilePropMapCtor; // GameProfile(UUID,String,PropertyMap)

    // EntityPlayer construction path
    private static Method serverGetter;
    private static Constructor<?> entityPlayerCtor;
    private static Constructor<?> pimCtor;            // PlayerInteractManager(World) — versioned only
    private static Method clientInfoDefault;          // ClientInformation.a() — 1.20.4 only
    private static Constructor<?> infoPacketCtor;     // (EnumPlayerInfoAction, EntityPlayer...) — versioned
    private static Object infoAddAction;
    private static Object infoRemoveAction;
    private static Constructor<?> namedSpawnCtor;     // (EntityHuman) — versioned ≤1.20.1
    private static Constructor<?> spawnEntityCtor;    // PacketPlayOutSpawnEntity(Entity) — 1.20.4
    private static Constructor<?> infoUpdateCollectionCtor; // (EnumSet, Collection<EntityPlayer>) — 1.20.4
    private static Object updateAddAction;            // 1.20.4 action enum constant (ordinal 0 = ADD_PLAYER)
    private static Constructor<?> infoRemoveCtor;     // (List<UUID>) — 1.19.3+ layouts

    // 26.2 Entry path
    private static Constructor<?> entryCtor;          // (UUID, GameProfile, boolean, int, GameType, Component, ...)
    private static boolean entryNineArg;
    private static Constructor<?> infoUpdateEntryListCtor; // (EnumSet, List<Entry>)
    private static Constructor<?> addEntityCtor;      // (int, UUID, DDD, FF, EntityTypes, int, Vec3, double)
    private static Object playerEntityType;
    private static Object gameTypeConst;
    private static Constructor<?> vecCtor;

    // shared packets
    private static Constructor<?> destroyCtor;        // (int[])
    private static Constructor<?> headRotCtor;        // () or (Entity, byte)
    private static boolean headRotNeedsEntity;
    private static Constructor<?> lookCtor;           // (int, byte yaw, byte pitch, boolean) — look-only
    private static Constructor<?> swingCtor;          // () | (id|Entity, action) — arm swing (optional)
    private static boolean swingNeedsEntity;
    private static boolean swingActionIsEnum;
    private static Object swingAction;

    private static final Map<String, Object> profileCache = new ConcurrentHashMap<String, Object>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0x40000000);

    /** Callback fired (on the main thread) when a viewer right/left-clicks a fake-player model. The model has no
     *  server entity, so the click arrives as an inbound interact packet intercepted on the viewer's channel. */
    public interface ClickHandler {
        void click(Player viewer);
    }

    /** One packet NPC: prebuilt tab/spawn packets plus the id/uuid the look packets need. */
    public static final class Model {
        int entityId;
        final UUID uuid;
        final String name;
        final Location location;
        Object handle;                                 // versioned/1.20.4 EntityPlayer (nullable on 26.2)
        Object tabAdd;                                 // prebuilt tab-add packet
        Object spawn;                                  // prebuilt spawn packet
        Object tabRemove;                              // prebuilt tab-remove packet
        Object swing;                                  // cached arm-swing packet (nullable)
        Plugin plugin;                                 // for the delayed tab-remove
        volatile ClickHandler onClick;                 // dispatched when a viewer clicks the model (nullable)
        final Map<UUID, Boolean> shown = new ConcurrentHashMap<UUID, Boolean>();

        Model(int entityId, UUID uuid, String name, Location location) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.name = name;
            this.location = location.clone();
        }

        /** Fire this model's action when a viewer clicks it — replaces the vanilla interact event a real body
         *  would have fired (a fake player has no server entity). */
        public void onClick(ClickHandler handler) {
            this.onClick = handler;
        }

        /** Drop one viewer from the shown set — a rejoining client never received the old spawn. */
        public void forget(UUID viewer) {
            shown.remove(viewer);
        }
    }

    // ------------------------------------------------------------------ inbound click interception
    // A fake-player model is client-side only, so a right/left-click on it never reaches the server as a Bukkit
    // interact event — the client sends an inbound interact packet naming the model's (server-unknown) entity id.
    // We add a reflective Netty inbound handler to each viewer's channel that spots those packets and routes them
    // to the model's ClickHandler. Best-effort: any failure just leaves the (less reliable) real-body click path.
    private static final Map<Integer, Model> MODELS_BY_ID = new ConcurrentHashMap<Integer, Model>();
    private static final java.util.Set<UUID> INTERCEPTED =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<UUID, Long>();
    private static volatile Plugin interceptPlugin;

    public static boolean available() {
        resolve();
        return ok;
    }

    /** Cache key folds in the cape flag: the SAME skin with/without a cape are two distinct profiles. */
    private static String cacheKey(String skinKey, boolean cape) {
        return skinKey.toLowerCase() + (cape ? "" : "#nocape");
    }

    /** Skin cache key → reflective GameProfile (with textures when known). Never null once resolved. */
    public static Object cachedProfile(String skinKey, boolean cape) {
        resolve();
        return skinKey == null ? null : profileCache.get(cacheKey(skinKey, cape));
    }

    /**
     * Fetch a skin profile off the main thread and cache it. Player names go through the Mojang API
     * (uuid → session textures); direct textures.minecraft.net URLs build the property locally.
     * When {@code cape} is false the CAPE texture is stripped from the profile. Safe to call repeatedly.
     */
    public static void fetchSkin(final Plugin plugin, final String skinKey, final boolean cape) {
        if (plugin == null || skinKey == null || skinKey.isEmpty()) return;
        final String key = cacheKey(skinKey, cape);
        resolve();
        if (profileCtor == null || profileCache.containsKey(key)) return;
        if (skinKey.startsWith("http")) {
            profileCache.putIfAbsent(key, buildUrlProfile(skinKey, cape));
            return;
        }
        if (!skinKey.matches("[A-Za-z0-9_]{1,16}")) return;
        new BukkitRunnable() {
            @Override public void run() {
                Object profile = fetchNameProfile(skinKey, cape);
                if (profile != null) profileCache.put(key, profile);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Build a model (prebuilt packets) at {@code at}; null when the packet stack is unavailable. */
    public static Model create(Plugin plugin, Location at, String name, Object profile) {
        resolve();
        if (!ok || at == null || at.getWorld() == null || profile == null) return null;
        // Each NPC needs its OWN tab-list UUID. cachedProfile() shares one GameProfile per skin keyed to that
        // skin's real Mojang UUID, so several NPCs with the same skin (cosmetics + profile + both queue NPCs all
        // default to "Steve") collide on one tab entry — and one NPC's delayed tab-remove then strips the skin off
        // the others. Cloning with a random UUID keeps skins independent (the skin loads from the textures
        // property, never the UUID).
        profile = withRandomUuid(profile);
        String step = "start";
        try {
            step = "world-handle";
            World world = at.getWorld();
            Object worldHandle = world.getClass().getMethod("getHandle").invoke(world);
            step = "entity-player";
            Model model = new Model(NEXT_ID.getAndIncrement(), UUID.randomUUID(), name == null || name.isEmpty()
                ? "NPC" : (name.length() > 16 ? name.substring(0, 16) : name), at);
            if (entityPlayerCtor != null) {
                step = "server";
                Object server = serverGetter.invoke(null);
                step = "extra-arg";
                Object extra = pimCtor != null
                    ? pimCtor.newInstance(worldHandle)
                    : clientInfoDefault.invoke(null);
                step = "entity-player-ctor";
                Object handle = entityPlayerCtor.newInstance(server, worldHandle, profile, extra);
                step = "handle-location";
                setHandleLocation(handle, at);
                model.handle = handle;
                // CRITICAL: the spawn packet (namedSpawnCtor(handle)) carries the handle's REAL NMS entity id,
                // but look/head-rotation/destroy all key off model.entityId. If those differ, every follow-up
                // packet targets a non-existent entity: the head never turns (faces south) and destroy never
                // removes the client entity (duplicate until relog). Adopt the handle's id so all packets agree.
                int handleId = handleEntityId(handle);
                if (handleId != 0) model.entityId = handleId;
                if (infoPacketCtor != null) {
                    step = "tab-add";
                    model.tabAdd = infoPacketCtor.newInstance(infoAddAction, singleHandleArray(handle));
                    step = "tab-remove";
                    model.tabRemove = infoPacketCtor.newInstance(infoRemoveAction, singleHandleArray(handle));
                } else if (infoUpdateCollectionCtor != null) {
                    step = "tab-add-collection";
                    model.tabAdd = infoUpdateCollectionCtor.newInstance(enumSetOf(updateAddAction),
                        Arrays.asList(handle));
                }
                step = "spawn-packet";
                model.spawn = namedSpawnCtor.newInstance(handle);
            } else if (addEntityCtor != null && entryCtor != null && infoUpdateEntryListCtor != null) {
                step = "entry";
                Object entry = entryNineArg
                    ? entryCtor.newInstance(model.uuid, profile, Boolean.TRUE, Integer.valueOf(0),
                        gameTypeConst, null, Boolean.TRUE, Integer.valueOf(0), null)
                    : entryCtor.newInstance(model.uuid, profile, Boolean.TRUE, Integer.valueOf(0),
                        gameTypeConst, null, null);
                step = "tab-add-entry";
                model.tabAdd = infoUpdateEntryListCtor.newInstance(enumSetOf(updateAddAction),
                    Arrays.asList(entry));
                step = "spawn-add-entity";
                // The two float slots are (yRot, xRot) on 1.20.2–1.21.x but (xRot, yRot) on 26.2 (bytecode-verified).
                // Pass YAW for both: the body spawns correctly on either order, and the look packets sent in the
                // same burst fix the pitch — a wrong slot order here spawned the model body-facing-south with the
                // head at placement yaw (the "head backwards" bug).
                model.spawn = addEntityCtor.newInstance(Integer.valueOf(model.entityId), model.uuid,
                    Double.valueOf(at.getX()), Double.valueOf(at.getY()), Double.valueOf(at.getZ()),
                    Float.valueOf(at.getYaw()), Float.valueOf(at.getYaw()),
                    playerEntityType, Integer.valueOf(0),
                    vecCtor.newInstance(Double.valueOf(0), Double.valueOf(0), Double.valueOf(0)),
                    Double.valueOf(at.getYaw()));
                if (infoRemoveCtor != null) {
                    model.tabRemove = infoRemoveCtor.newInstance(Arrays.asList(model.uuid));
                }
            }
            if (model.spawn == null || model.tabAdd == null) {
                logOnce("PacketNpcs model build failed at " + step + ": spawn or tab packet missing");
                return null;
            }
            model.plugin = plugin;
            interceptPlugin = plugin;
            MODELS_BY_ID.put(Integer.valueOf(model.entityId), model);
            return model;
        } catch (Throwable t) {
            logOnce("PacketNpcs model build failed at " + step + ": " + t);
            return null;
        }
    }

    /** One-shot diagnostics so a silent packet failure never hides behind an invisible NPC. */
    private static volatile boolean failureLogged;

    private static void logOnce(String message) {
        if (failureLogged) return;
        failureLogged = true;
        String at = "";
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (frame.getClassName().equals(PacketNpcs.class.getName())) {
                at = " (" + frame.getMethodName() + ")";
                break;
            }
        }
        Bukkit.getLogger().info("[PacketNpcs] " + message + at);
    }

    private static volatile boolean resolveDebugged;

    /** One-shot startup diagnosis for the 26.2 packet player-model resolve path. */
    private static void resolveDebug(String reason) {
        if (resolveDebugged) return;
        resolveDebugged = true;
        Bukkit.getLogger().info("[PacketNpcs] 26.2 resolve diagnosis: " + reason);
    }

    /** Send tab-add + spawn to one viewer, then remove the tab entry after a delay so the NPC does NOT clutter
     * the Tab player list. Safe now that textures are SIGNED: on 1.12.2 the client resolves a player entity's
     * skin from the tab list on first render (AbstractClientPlayer.getPlayerInfo) and then CACHES it on the
     * entity, so removing the entry a couple seconds later keeps the skin. The delay must outlast the client's
     * first render of the entity — hence 40 ticks (2s), not the old 30. (The earlier "skins only show Steve/
     * Alex" bug was the missing signature, not this removal.) Idempotent per viewer. */
    public static void show(final Player viewer, final Model model) {
        if (viewer == null || model == null || !ok) return;
        if (model.shown.putIfAbsent(viewer.getUniqueId(), Boolean.TRUE) != null) return;
        installInterceptor(viewer);
        try {
            send(viewer, model.tabAdd);
            send(viewer, model.spawn);
            if (model.tabRemove != null && model.plugin != null && model.plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(model.plugin, new Runnable() {
                    @Override public void run() {
                        // Only remove if the viewer is still a viewer — a re-show must re-add first.
                        if (viewer.isOnline() && model.shown.get(viewer.getUniqueId()) != null) send(viewer, model.tabRemove);
                    }
                }, 40L);
            }
            look(model, model.location.getYaw(), model.location.getPitch());
            swing(model); // Citizens-style arm swing the moment the model loads for this viewer
            // The look/head-rotation above rides the SAME packet burst as the spawn. Many clients register the
            // entity only after the burst is drained, so a head-rotation that arrives first is dropped and the
            // fake player's HEAD stays at its default (south) until it moves — the "all NPCs face south" bug.
            // Re-send the facing a few ticks later, once the client has the entity, so the placed yaw sticks.
            if (model.plugin != null && model.plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(model.plugin, new Runnable() {
                    @Override public void run() {
                        if (viewer.isOnline() && model.shown.get(viewer.getUniqueId()) != null) {
                            look(model, model.location.getYaw(), model.location.getPitch());
                        }
                    }
                }, 3L);
            }
        } catch (Throwable t) {
            model.shown.remove(viewer.getUniqueId());
            logOnce("PacketNpcs show failed: " + t);
        }
    }

    /** Destroy the model for one viewer (and drop them from the shown set so they can be re-shown). */
    public static void hide(Player viewer, Model model) {
        if (viewer == null || model == null || !ok) return;
        if (model.shown.remove(viewer.getUniqueId()) == null) return;
        try {
            send(viewer, destroyCtor.newInstance(new int[]{model.entityId}));
            if (model.tabRemove != null) send(viewer, model.tabRemove);
        } catch (Throwable ignored) {
        }
    }

    /** Add our reflective Netty inbound handler to a viewer's channel (idempotent per session). Best-effort: any
     *  failure just skips interception and the real-body click path remains. */
    /** Temporary click-path diagnostics (INFO log, capped so it can't spam). Flip off once the 1.8 click path is fixed. */
    static final boolean DEBUG = true;
    private static final AtomicInteger DEBUG_INBOUND = new AtomicInteger(0);

    private static void installInterceptor(Player viewer) {
        if (viewer == null || interceptPlugin == null) return;
        if (!INTERCEPTED.add(viewer.getUniqueId())) return;
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = connectionField.get(handle);
            Object channel = channelOf(connection);
            if (channel == null) {
                INTERCEPTED.remove(viewer.getUniqueId());
                if (DEBUG) Bukkit.getLogger().info("[PacketNpcs][dbg] no channel for " + viewer.getName() + " — interceptor NOT installed");
                return;
            }
            // Resolve pipeline methods from the PUBLIC interfaces (Channel / ChannelPipeline), never from the
            // concrete impl class: DefaultChannelPipeline is not accessible from our classloader, so a Method
            // taken off pipeline.getClass() throws IllegalAccessException on invoke (the 1.8 "interceptor never
            // installed" bug — the fake-player click path was dead and only the real body registered clicks).
            ClassLoader cl = channel.getClass().getClassLoader();
            Class<?> channelIface = Class.forName("io.netty.channel.Channel", false, cl);
            Class<?> pipelineIface = Class.forName("io.netty.channel.ChannelPipeline", false, cl);
            Class<?> handlerIface = Class.forName("io.netty.channel.ChannelHandler", false, cl);
            Object pipeline = channelIface.getMethod("pipeline").invoke(channel);
            if (pipelineIface.getMethod("get", String.class).invoke(pipeline, "bedlam_npc_in") != null) return;
            Object handler = createInboundHandler(cl, viewer);
            try {
                pipelineIface.getMethod("addBefore", String.class, String.class, handlerIface)
                    .invoke(pipeline, "packet_handler", "bedlam_npc_in", handler);
            } catch (Throwable noPacketHandler) {
                pipelineIface.getMethod("addLast", String.class, handlerIface)
                    .invoke(pipeline, "bedlam_npc_in", handler);
            }
            if (DEBUG) Bukkit.getLogger().info("[PacketNpcs][dbg] interceptor installed for " + viewer.getName());
        } catch (Throwable t) {
            INTERCEPTED.remove(viewer.getUniqueId());
            if (DEBUG) Bukkit.getLogger().info("[PacketNpcs][dbg] interceptor install FAILED for " + viewer.getName() + ": " + t);
        }
    }

    /** Forget a disconnected viewer so a reconnect (fresh channel) re-installs the interceptor. */
    public static void clearViewer(UUID viewer) {
        if (viewer == null) return;
        INTERCEPTED.remove(viewer);
        LAST_CLICK.remove(viewer);
    }

    /** The io.netty Channel reachable from the player's connection (listener → NetworkManager → channel). */
    private static Object channelOf(Object listener) {
        if (listener == null) return null;
        try {
            for (Class<?> c = listener.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(listener);
                        if (val == null) continue;
                        Object ch = channelField(val);
                        if (ch != null) return ch;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object channelField(Object holder) {
        Class<?> channelClass;
        try {
            channelClass = Class.forName("io.netty.channel.Channel", false, holder.getClass().getClassLoader());
        } catch (Throwable t) {
            return null;
        }
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!channelClass.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(holder);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** A dynamic-proxy ChannelInboundHandler: inspects inbound interact packets, forwards every event unchanged. */
    private static Object createInboundHandler(ClassLoader cl, final Player viewer) throws Exception {
        Class<?> inbound = Class.forName("io.netty.channel.ChannelInboundHandler", false, cl);
        java.lang.reflect.InvocationHandler h = new java.lang.reflect.InvocationHandler() {
            @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                String n = method.getName();
                Object ctx = (args != null && args.length > 0) ? args[0] : null;
                if ("channelRead".equals(n) && args != null && args.length > 1) {
                    try { onInbound(viewer, args[1]); } catch (Throwable ignored) { }
                    return fire(ctx, "fireChannelRead", Object.class, args[1]);
                }
                if ("exceptionCaught".equals(n) && args != null && args.length > 1) {
                    return fire(ctx, "fireExceptionCaught", Throwable.class, args[1]);
                }
                if ("userEventTriggered".equals(n) && args != null && args.length > 1) {
                    return fire(ctx, "fireUserEventTriggered", Object.class, args[1]);
                }
                if ("channelRegistered".equals(n)) return fire0(ctx, "fireChannelRegistered");
                if ("channelUnregistered".equals(n)) return fire0(ctx, "fireChannelUnregistered");
                if ("channelActive".equals(n)) return fire0(ctx, "fireChannelActive");
                if ("channelInactive".equals(n)) return fire0(ctx, "fireChannelInactive");
                if ("channelReadComplete".equals(n)) return fire0(ctx, "fireChannelReadComplete");
                if ("channelWritabilityChanged".equals(n)) return fire0(ctx, "fireChannelWritabilityChanged");
                if ("isSharable".equals(n)) return Boolean.FALSE;
                if ("equals".equals(n)) return Boolean.valueOf(proxy == (args != null ? args[0] : null));
                if ("hashCode".equals(n)) return Integer.valueOf(System.identityHashCode(proxy));
                if ("toString".equals(n)) return "bedlam_npc_in";
                return null; // handlerAdded / handlerRemoved / anything else: no-op
            }
        };
        return java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{inbound}, h);
    }

    // Resolve fire* methods from the PUBLIC ChannelHandlerContext interface, NOT ctx.getClass() (a non-public
    // impl like AbstractChannelHandlerContext → IllegalAccessException on invoke → the event is silently dropped).
    // Dropping inbound events breaks EVERY client→server packet flowing through us: chat, window/inventory clicks,
    // keepalive (→ disconnect). This is the same access bug that hid the interceptor install on 1.8.
    private static volatile Class<?> ctxIfaceCache;
    private static Class<?> ctxIface(Object ctx) throws ClassNotFoundException {
        Class<?> cached = ctxIfaceCache;
        if (cached != null) return cached;
        cached = Class.forName("io.netty.channel.ChannelHandlerContext", false, ctx.getClass().getClassLoader());
        ctxIfaceCache = cached;
        return cached;
    }

    private static Object fire0(Object ctx, String method) {
        try { return ctxIface(ctx).getMethod(method).invoke(ctx); } catch (Throwable ignored) { return null; }
    }

    private static Object fire(Object ctx, String method, Class<?> argType, Object arg) {
        try { return ctxIface(ctx).getMethod(method, argType).invoke(ctx, arg); } catch (Throwable ignored) { return null; }
    }

    /** If an inbound packet is an interact/use-entity naming one of our model ids, dispatch that model's click on
     *  the main thread. Debounced per viewer only to collapse the INTERACT_AT + INTERACT pair a single right-click
     *  sends (1.8 and 1.9+ both send both, same tick). Window is short so a deliberate second click still registers
     *  — 250ms swallowed retry clicks on 1.8, forcing the "click many times / shift-left" workaround. */
    private static final long CLICK_DEDUP_MS = 60L;
    private static void onInbound(final Player viewer, Object packet) {
        if (packet == null || interceptPlugin == null) return;
        String cn = packet.getClass().getName();
        if (!cn.contains("UseEntity") && !cn.contains("Interact")) return;
        int id = firstIntField(packet);
        final Model model = MODELS_BY_ID.get(Integer.valueOf(id));
        if (DEBUG && DEBUG_INBOUND.getAndIncrement() < 60) {
            Bukkit.getLogger().info("[PacketNpcs][dbg] inbound " + cn + " id=" + id
                + " model=" + (model != null) + " onClick=" + (model != null && model.onClick != null)
                + " knownIds=" + MODELS_BY_ID.keySet());
        }
        if (model == null || model.onClick == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.get(viewer.getUniqueId());
        if (last != null && now - last.longValue() < CLICK_DEDUP_MS) return;
        LAST_CLICK.put(viewer.getUniqueId(), Long.valueOf(now));
        final ClickHandler handler = model.onClick;
        try {
            Bukkit.getScheduler().runTask(interceptPlugin, new Runnable() {
                @Override public void run() { if (viewer.isOnline()) handler.click(viewer); }
            });
        } catch (Throwable ignored) {
        }
    }

    private static int firstIntField(Object packet) {
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                try { f.setAccessible(true); return f.getInt(packet); } catch (Throwable ignored) { }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Destroy for everyone and reset the shown set (body despawn / relocate). */
    public static void destroy(Model model) {
        if (model == null || destroyCtor == null) return;
        MODELS_BY_ID.remove(Integer.valueOf(model.entityId), model);
        Object destroyPacket;
        try {
            destroyPacket = destroyCtor.newInstance(new int[]{model.entityId});
        } catch (Throwable t) {
            logOnce("destroy packet build failed: " + t);
            return;
        }
        // Send the destroy to EVERY online player, not only the shown set. A stale entry (relog, missed forget,
        // shown drift) left the old fake player rendering client-side forever — the "old NPC stays after relocate"
        // duplicate. A destroy packet for an id the client doesn't have is a harmless no-op, so this is safe.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                send(viewer, destroyPacket);
                if (model.tabRemove != null) send(viewer, model.tabRemove);
            } catch (Throwable t) {
                logOnce("destroy failed for " + viewer.getName() + ": " + t);
            }
        }
        model.shown.clear();
    }

    /** Rotate the model toward yaw/pitch for every current viewer. Uses the look-only entity packet plus the
     * head-rotation packet, both carrying the SAME yaw so head and body always agree. Never sends a position
     * delta — a relative move-look nudge here made every model drift one block per update off its pin (the
     * duplicate-NPC bug). */
    public static void look(Model model, float yaw, float pitch) {
        if (model == null || !ok) return;
        // Keep stored location in sync so show() → look(model, location.yaw, location.pitch)
        // sends the CURRENT rotation to new viewers, not the stale original placement yaw.
        model.location.setYaw(yaw);
        model.location.setPitch(pitch);
        try {
            Object lookPacket = lookCtor.newInstance(Integer.valueOf(model.entityId),
                Byte.valueOf(angleByte(yaw)), Byte.valueOf(angleByte(pitch)), Boolean.FALSE);
            Object headPacket = buildHeadRot(model.entityId, yaw);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (model.shown.get(viewer.getUniqueId()) == null) continue;
                send(viewer, lookPacket);
                if (headPacket != null) send(viewer, headPacket);
            }
        } catch (Throwable t) {
            logOnce("look failed: " + t);
        }
    }

    /** Send look + head-rotation packets for a REAL (server) entity to players near it, so a mob whose rotation
     *  was set server-side (setRotation with AI off, no tracker delta) visibly turns on clients. */
    public static void lookEntity(org.bukkit.entity.Entity entity, float yaw, float pitch) {
        resolve();
        if (!ok || entity == null || entity.getWorld() == null) return;
        int id = entity.getEntityId();
        try {
            Object lookPacket = lookCtor.newInstance(Integer.valueOf(id),
                Byte.valueOf(angleByte(yaw)), Byte.valueOf(angleByte(pitch)), Boolean.FALSE);
            Object headPacket = buildHeadRot(id, yaw);
            double rangeSq = 48.0 * 48.0;
            Location at = entity.getLocation();
            for (Player viewer : entity.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) > rangeSq) continue;
                send(viewer, lookPacket);
                if (headPacket != null) send(viewer, headPacket);
            }
        } catch (Throwable t) {
            logOnce("lookEntity failed: " + t);
        }
    }

    /** Re-show to players who lost the model (join/respawn/chunk reload/out-of-range). Cheap: shown-set gated.
     *  A fake-player NPC is client-side only — the server's entity tracker does NOT remove it when a viewer leaves
     *  view distance, so once shown it stays flagged as "shown" forever until relog/skin-reset. When a player then
     *  returns to range (back to their island), {@link #show} sees the stale "shown" flag and skips the re-spawn —
     *  the skinned shopkeeper stays invisible (the "shopkeeper disappears after leaving the island / dying" bug;
     *  real villager shopkeepers are unaffected because the server re-sends real entities automatically). Fix:
     *  drop a viewer from the shown set once they are clearly out of range (1.5x hysteresis avoids boundary
     *  flicker) so a later re-entry re-shows them. */ 
    public static void ensureViewers(Model model, double range) {
        if (model == null || !ok) return;
        double rangeSq = range * range;
        double forgetSq = range * range * 2.25; // (1.5 * range)^2 — beyond this the client has dropped the fake player
        for (Player viewer : model.location.getWorld().getPlayers()) {
            if (EntityVisibility.isSpectator(viewer)) continue;
            double dSq = viewer.getLocation().distanceSquared(model.location);
            if (dSq <= rangeSq) {
                show(viewer, model);
            } else if (dSq > forgetSq) {
                model.forget(viewer.getUniqueId());
            }
        }
    }

    // ------------------------------------------------------------------ internals

    /** Arm-swing packet for one model (cached on the model after first build); null when unsupported. */
    private static Object buildSwing(Model model) {
        if (model.swing != null) return model.swing;
        if (swingCtor == null) return null;
        try {
            Object packet;
            if (swingCtor.getParameterTypes().length == 0) {
                packet = swingCtor.newInstance();
                setIntField(packet, model.entityId);
            } else if (swingNeedsEntity) {
                Player any = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
                if (any == null) return null;
                Object handle = any.getClass().getMethod("getHandle").invoke(any);
                packet = swingActionIsEnum
                    ? swingCtor.newInstance(handle, swingAction)
                    : swingCtor.newInstance(handle, Integer.valueOf(0));
                setIntField(packet, model.entityId);
            } else {
                packet = swingActionIsEnum
                    ? swingCtor.newInstance(Integer.valueOf(model.entityId), swingAction)
                    : swingCtor.newInstance(Integer.valueOf(model.entityId), Integer.valueOf(0));
            }
            model.swing = packet;
            return packet;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Swing the model's arm once for every current viewer (the Citizens-style punch on load). */
    public static void swing(Model model) {
        if (model == null || !ok) return;
        try {
            Object packet = buildSwing(model);
            if (packet == null) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (model.shown.get(viewer.getUniqueId()) == null) continue;
                send(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object buildHeadRot(int entityId, float yaw) throws Exception {
        Object packet;
        if (headRotCtor.getParameterTypes().length == 0) {
            packet = headRotCtor.newInstance();
            setIntField(packet, entityId);
            setByteField(packet, angleByte(yaw));
        } else {
            Player any = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
            if (any == null) return null;
            Object handle = any.getClass().getMethod("getHandle").invoke(any);
            packet = headRotCtor.newInstance(handle, Byte.valueOf(angleByte(yaw)));
            setIntField(packet, entityId);
        }
        return packet;
    }

    private static Class<?> entityPlayerClass;

    /** Explicit EntityPlayer[] for the varargs info packet — bypasses reflection varargs wrapping quirks. */
    private static Object singleHandleArray(Object handle) {
        Object array = java.lang.reflect.Array.newInstance(entityPlayerClass, 1);
        java.lang.reflect.Array.set(array, 0, handle);
        return array;
    }

    /** Real client-side entity id of an NMS handle. getBukkitEntity().getEntityId() is stable on every version;
     * getId() is the fallback. 0 = couldn't read (caller keeps the synthetic id). */
    private static int handleEntityId(Object handle) {
        try {
            Object bukkit = handle.getClass().getMethod("getBukkitEntity").invoke(handle);
            if (bukkit instanceof org.bukkit.entity.Entity) return ((org.bukkit.entity.Entity) bukkit).getEntityId();
        } catch (Throwable ignored) {
        }
        try {
            Object id = handle.getClass().getMethod("getId").invoke(handle);
            if (id instanceof Number) return ((Number) id).intValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void setHandleLocation(Object handle, Location at) {
        String[] names = {"setLocation", "setPositionRotation", "moveTo", "absSnapTo"};
        for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Method m = c.getDeclaredMethod(name, double.class, double.class, double.class,
                        float.class, float.class);
                    m.setAccessible(true);
                    m.invoke(handle, at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
                    break;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
        }
        // Field write is the SOURCE OF TRUTH for the spawn packet: PacketPlayOutNamedEntitySpawn reads
        // entity.yaw/pitch/locX/Y/Z directly, and on 1.12 the client sets the fake player's HEAD yaw from the
        // spawn yaw. Guessing the setLocation method name per version was fragile — a silent failure left yaw=0
        // so every NPC faced south. Pin the rotation fields ourselves (no-op on mappings that rename them; the
        // method call above covers those). Spigot keeps readable NMS field names on all versioned builds.
        setDoubleFieldByName(handle, "locX", at.getX());
        setDoubleFieldByName(handle, "locY", at.getY());
        setDoubleFieldByName(handle, "locZ", at.getZ());
        setFloatFieldByName(handle, "yaw", at.getYaw());
        setFloatFieldByName(handle, "pitch", at.getPitch());
    }

    private static void setFloatFieldByName(Object handle, String name, float value) {
        for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (f.getType() != float.class) return;
                f.setAccessible(true);
                f.setFloat(handle, value);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static void setDoubleFieldByName(Object handle, String name, double value) {
        for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (f.getType() != double.class) return;
                f.setAccessible(true);
                f.setDouble(handle, value);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static void setIntField(Object packet, int value) throws IllegalAccessException {
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.setInt(packet, value);
                    return;
                }
            }
        }
    }

    private static void setByteField(Object packet, byte value) throws IllegalAccessException {
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == byte.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.setByte(packet, value);
                    return;
                }
            }
        }
    }

    private static byte angleByte(float degrees) {
        return (byte) Math.floor(degrees * 256.0f / 360.0f);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumSetOf(Object constant) {
        // EnumSet.of erases to of(Enum), not of(Object), so getMethod("of", Object.class) throws
        // NoSuchMethodException. Build the set directly instead — no reflection, works on every JVM.
        EnumSet set = EnumSet.noneOf(((Enum) constant).getDeclaringClass());
        set.add(constant);
        return set;
    }

    private static boolean send(Player viewer, Object packet) {
        if (packet == null) return false;
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = connectionField.get(handle);
            if (connection == null) return false;
            sendMethod.invoke(connection, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------ profiles

    /** Clone a (cached, shared) GameProfile onto a fresh random UUID, copying its textures property. Keeps each
     *  NPC's tab-list identity unique so same-skin NPCs don't fight over one entry. Returns the input on failure. */
    private static Object withRandomUuid(Object profile) {
        try {
            Object name = profileNameAccessor().invoke(profile);
            Object srcProps = getPropertiesMethod.invoke(profile);
            Object textures = srcProps.getClass().getMethod("get", Object.class).invoke(srcProps, "textures");
            Object property = null;
            if (textures instanceof Iterable) {
                for (Object p : (Iterable<?>) textures) { property = p; break; }
            }
            return profileWith(UUID.randomUUID(), name == null ? "§7§7" : name.toString(), property);
        } catch (Throwable ignored) {
            return profile;
        }
    }

    /** Build a GameProfile carrying {@code property} (may be null) across authlib versions. Legacy authlib exposes
     *  a MUTABLE properties multimap (getProperties().put); authlib 9.x (Paper 1.21+/26.x) returns an IMMUTABLE
     *  multimap and throws UnsupportedOperationException on put — there we populate a fresh mutable PropertyMap and
     *  hand it to the GameProfile(UUID,String,PropertyMap) record constructor. */
    private static Object profileWith(UUID uuid, String name, Object property) throws Exception {
        Object profile = profileCtor.newInstance(uuid, name);
        if (property == null) return profile;
        try {
            Object properties = getPropertiesMethod.invoke(profile);
            propertiesPut.invoke(properties, "textures", property);
            return profile;
        } catch (Throwable immutable) {
            if (gameProfilePropMapCtor == null || propertyMapWrapCtor == null
                || multimapCreate == null || multimapPut == null) {
                throw (immutable instanceof Exception) ? (Exception) immutable : new Exception(immutable);
            }
            Object multimap = multimapCreate.invoke(null);
            multimapPut.invoke(multimap, "textures", property);
            Object propMap = propertyMapWrapCtor.newInstance(multimap);
            return gameProfilePropMapCtor.newInstance(uuid, name, propMap);
        }
    }

    /** GameProfile property accessor across authlib versions: {@code getProperties()} (legacy) or
     *  {@code properties()} (record accessor, authlib 9.x — Paper 1.21+/26.x). */
    private static Method profilePropertiesAccessor() throws Exception {
        try {
            return profileClass().getMethod("getProperties");
        } catch (NoSuchMethodException e) {
            return profileClass().getMethod("properties");
        }
    }

    /** GameProfile display-name accessor: {@code getName()} (legacy) or {@code name()} (record accessor, authlib 9.x). */
    private static Method profileNameAccessor() throws Exception {
        try {
            return profileClass().getMethod("getName");
        } catch (NoSuchMethodException e) {
            return profileClass().getMethod("name");
        }
    }

    private static Object newProfile(UUID uuid, String name, String textureValue, String signature, boolean cape) throws Exception {
        // Color-code-only display name: the tab entry (and therefore the head nametag) renders empty —
        // Citizens-style hidden nameplate. The skin still loads from the textures property; holograms own labels.
        //
        // SIGNATURE IS MANDATORY on 1.12.2: the client loads other players' skins with requireSecure=true, so an
        // unsigned "textures" property throws InsecureTextureException client-side and the NPC falls back to the
        // default Steve/Alex skin. We fetch value+signature together (sessionserver ?unsigned=false) and pass both.
        // Stripping the cape would change the value and INVALIDATE that signature (isSignatureValid fails → same
        // default-skin fallback), so only strip capes from UNSIGNED (URL) values — signed skins keep their cape.
        if (textureValue != null && !cape && signature == null) textureValue = stripCape(textureValue);
        Object property = null;
        if (textureValue != null) {
            property = (signature != null && propertyCtor3 != null)
                ? propertyCtor3.newInstance("textures", textureValue, signature)
                : propertyCtor.newInstance("textures", textureValue);
        }
        return profileWith(uuid, "\u00A77\u00A77", property);
    }

    /**
     * Remove the CAPE entry from a base64 textures value so the fake player shows no cape (cape OFF).
     * The value is stored unsigned (no signature property), so editing it is safe. ponytail: crude regex
     * strip — assumes CAPE is a plain {"url":...} with no nested braces; returns the input unchanged on any
     * decode/parse failure, so a weird payload just keeps its cape rather than breaking the skin.
     */
    private static String stripCape(String base64Value) {
        try {
            String json = new String(java.util.Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            if (!json.contains("\"CAPE\"")) return base64Value;
            json = json.replaceAll(",?\\s*\"CAPE\"\\s*:\\s*\\{[^}]*\\}", "").replace("{,", "{");
            return java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return base64Value;
        }
    }

    private static Class<?> profileClass() throws ClassNotFoundException {
        return Class.forName("com.mojang.authlib.GameProfile");
    }

    private static Object buildUrlProfile(String url, boolean cape) {
        try {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            String value = java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            // No signature possible for a self-built URL texture — unsigned. On 1.12.2 the client's requireSecure
            // check rejects unsigned skins (default-skin fallback); newer clients render it. Usernames (below) are
            // signed by Mojang and work everywhere — prefer a username over a raw URL for 1.12.2.
            return newProfile(UUID.randomUUID(), "NPC", value, null, cape);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object fetchNameProfile(String name, boolean cape) {
        try {
            String idJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + name);
            if (idJson == null) return null;
            String id = extract(idJson, "\"id\"\\s*:\\s*\"([a-f0-9]{32})\"");
            if (id == null) return null;
            UUID uuid = uuidFromDashless(id);
            String texJson = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/"
                + id.replace("-", "") + "?unsigned=false");
            if (texJson == null) return newProfile(uuid, name, null, null, cape);
            String value = extract(texJson, "\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            String signature = extract(texJson, "\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            return newProfile(uuid, name, value, signature, cape);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String httpGet(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "BedlamCore-NPC");
            int status = connection.getResponseCode();
            if (status != 200) return null;
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String extract(String json, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static UUID uuidFromDashless(String dashless) {
        String s = dashless.length() == 32
            ? dashless.substring(0, 8) + "-" + dashless.substring(8, 12) + "-" + dashless.substring(12, 16)
            + "-" + dashless.substring(16, 20) + "-" + dashless.substring(20)
            : dashless;
        return UUID.fromString(s);
    }

    // ------------------------------------------------------------------ resolution

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            String v = EntityVisibility.nmsVersion();
            boolean versioned = v != null && v.startsWith("v1_");
            String gamePkg = versioned ? "net.minecraft.server." + v : "net.minecraft.network.protocol.game";

            profileCtor = profileClass().getConstructor(UUID.class, String.class);
            propertyCtor = Class.forName("com.mojang.authlib.properties.Property")
                .getConstructor(String.class, String.class);
            try {
                propertyCtor3 = Class.forName("com.mojang.authlib.properties.Property")
                    .getConstructor(String.class, String.class, String.class);
            } catch (Throwable ignored) {
            }
            Method getProps = profilePropertiesAccessor();
            getPropertiesMethod = getProps;
            propertiesPut = getProps.getReturnType().getMethod("put", Object.class, Object.class);
            // authlib 9.x immutable-properties path (best-effort; absent/unused on legacy authlib).
            try {
                Class<?> propMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
                multimapCreate = Class.forName("com.google.common.collect.LinkedHashMultimap").getMethod("create");
                multimapPut = multimapClass.getMethod("put", Object.class, Object.class);
                propertyMapWrapCtor = propMapClass.getConstructor(multimapClass);
                gameProfilePropMapCtor = profileClass().getConstructor(UUID.class, String.class, propMapClass);
            } catch (Throwable ignored) {
            }

            // send plumbing
            String craftPkg = versioned ? "org.bukkit.craftbukkit." + v : "org.bukkit.craftbukkit";
            Class<?> craftPlayer = Class.forName(craftPkg + ".entity.CraftPlayer");
            Class<?> handleClass = craftPlayer.getMethod("getHandle").getReturnType();
            connectionField = findConnectionField(handleClass);
            if (connectionField == null) return;
            Class<?> packetInterface = null;
            for (String name : new String[]{
                versioned ? "net.minecraft.server." + v + ".Packet" : "",
                "net.minecraft.network.protocol.Packet"}) {
                if (name.isEmpty()) continue;
                try {
                    packetInterface = Class.forName(name);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (packetInterface == null) return;
            for (Method m : connectionField.getType().getMethods()) {
                if ((!m.getName().equals("sendPacket") && !m.getName().equals("send"))
                    || m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0].isAssignableFrom(packetInterface)) {
                    sendMethod = m;
                    break;
                }
            }
            if (sendMethod == null) return;

            // destroy
            for (String name : new String[]{
                gamePkg + ".PacketPlayOutEntityDestroy",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy",
                "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket"}) {
                try {
                    destroyCtor = Class.forName(name).getConstructor(int[].class);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (destroyCtor == null) return;

            // head rotation
            for (String name : new String[]{
                gamePkg + ".PacketPlayOutEntityHeadRotation",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotation",
                "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket"}) {
                Class<?> c;
                try {
                    c = Class.forName(name);
                } catch (Throwable ignored) {
                    continue;
                }
                try {
                    headRotCtor = c.getConstructor();
                    headRotNeedsEntity = false;
                    break;
                } catch (Throwable ignored) {
                }
                for (Class<?> nmsEntity : nmsEntityClasses(v, versioned)) {
                    try {
                        headRotCtor = c.getConstructor(nmsEntity, byte.class);
                        headRotNeedsEntity = true;
                        break;
                    } catch (Throwable ignored) {
                    }
                }
                if (headRotCtor != null) break;
            }
            if (headRotCtor == null) return;

            // look-only rotation (body stream — no position delta, never skipped by the client)
            for (String name : new String[]{
                gamePkg + ".PacketPlayOutEntity$PacketPlayOutEntityLook",
                "net.minecraft.network.protocol.game.PacketPlayOutEntity$PacketPlayOutEntityLook",
                "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot"}) {
                if (name.isEmpty()) continue;
                try {
                    lookCtor = Class.forName(name).getConstructor(
                        int.class, byte.class, byte.class, boolean.class);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (lookCtor == null) return;

            // arm-swing animation (cosmetic — never gates availability)
            resolveSwing(v, versioned, gamePkg);

            // EntityPlayer path (1.8–1.20.4)
            for (String name : new String[]{
                versioned ? "net.minecraft.server." + v + ".MinecraftServer" : "",
                "net.minecraft.server.MinecraftServer"}) {
                if (name.isEmpty()) continue;
                try {
                    Class<?> serverClass = Class.forName(name);
                    serverGetter = serverClass.getMethod("getServer");
                    break;
                } catch (Throwable ignored) {
                }
            }
            Class<?> gameProfile = profileClass();
            List<Class<?>> nmsEntities = nmsEntityClasses(v, versioned);
            Class<?> worldServerClass = versioned
                ? classFor("net.minecraft.server." + v + ".WorldServer", "net.minecraft.server.level.WorldServer")
                : classFor("net.minecraft.server.level.WorldServer");
            Class<?> epClassResolved = null;
            if (serverGetter != null && worldServerClass != null && !nmsEntities.isEmpty()) {
                // EntityPlayer(MinecraftServer, WorldServer, GameProfile, PlayerInteractManager|ClientInformation)
                String[][] epCandidates = {
                    {versioned ? "net.minecraft.server." + v + ".EntityPlayer" : "",
                        versioned ? "net.minecraft.server." + v + ".PlayerInteractManager" : ""},
                    {"net.minecraft.server.level.EntityPlayer", "net.minecraft.server.level.PlayerInteractManager"},
                    {"net.minecraft.server.level.EntityPlayer", "net.minecraft.server.level.ClientInformation"}
                };
                for (String[] cand : epCandidates) {
                    if (cand[0].isEmpty()) continue;
                    Class<?> epClass;
                    Class<?> extraClass;
                    try {
                        epClass = Class.forName(cand[0]);
                        extraClass = Class.forName(cand[1]);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    try {
                        entityPlayerCtor = epClass.getConstructor(
                            serverGetter.getReturnType(), worldServerClass, gameProfile, extraClass);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (cand[1].endsWith("PlayerInteractManager")) {
                        // The manager ctor takes WorldServer on 1.9+ but plain World on 1.8 — try the
                        // WorldServer type first, then walk up its superclass chain.
                        Constructor<?> managerCtor = null;
                        for (Class<?> worldType = worldServerClass;
                            worldType != null && worldType != Object.class;
                            worldType = worldType.getSuperclass()) {
                            try {
                                managerCtor = extraClass.getConstructor(worldType);
                                break;
                            } catch (Throwable ignored) {
                            }
                        }
                        if (managerCtor == null) {
                            entityPlayerCtor = null;
                            continue;
                        }
                        pimCtor = managerCtor;
                    } else {
                        for (Method m : extraClass.getMethods()) {
                            if (m.getParameterCount() == 0
                                && m.getReturnType().equals(extraClass)
                                && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                                clientInfoDefault = m;
                                break;
                            }
                        }
                        if (clientInfoDefault == null) {
                            entityPlayerCtor = null;
                            continue;
                        }
                    }
                    epClassResolved = epClass;
                    entityPlayerClass = epClass;
                    break;
                }
            }
            if (entityPlayerCtor != null && epClassResolved != null) {
                // versioned tab packet — ctor is (EnumPlayerInfoAction, EntityPlayer...) so the exact
                // parameter type is the EntityPlayer ARRAY, not Entity/EntityPlayer (getConstructor is exact).
                if (versioned) {
                    try {
                        Class<?> infoClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutPlayerInfo");
                        Class<?> actionClass = Class.forName(
                            "net.minecraft.server." + v + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
                        infoAddAction = actionClass.getField("ADD_PLAYER").get(null);
                        infoRemoveAction = actionClass.getField("REMOVE_PLAYER").get(null);
                        Class<?> epArray = java.lang.reflect.Array.newInstance(epClassResolved, 0).getClass();
                        infoPacketCtor = infoClass.getConstructor(actionClass, epArray);
                    } catch (Throwable ignored) {
                    }
                    // spawn packet ctor takes EntityHuman (EntityPlayer's super) — try exact types in order.
                    Class<?> entityHuman = classFor(
                        "net.minecraft.server." + v + ".EntityHuman",
                        "net.minecraft.world.entity.player.EntityHuman");
                    for (Class<?> human : new Class<?>[]{entityHuman, epClassResolved,
                        nmsEntities.isEmpty() ? null : nmsEntities.get(0)}) {
                        if (human == null) continue;
                        try {
                            namedSpawnCtor = classFor(
                                "net.minecraft.server." + v + ".PacketPlayOutNamedEntitySpawn",
                                "net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawn",
                                "net.minecraft.network.protocol.game.ClientboundAddPlayerPacket")
                                .getConstructor(human);
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                } else {
                    // Mojang-layout (1.20.4): update-by-collection + spawn-by-handle + remove-by-uuid-list
                    try {
                        Class<?> update = Class.forName(
                            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
                        Class<?> actionClass = Class.forName(
                            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$a");
                        updateAddAction = actionClass.getEnumConstants()[0]; // ADD_PLAYER
                        infoUpdateCollectionCtor = update.getConstructor(java.util.EnumSet.class,
                            java.util.Collection.class);
                    } catch (Throwable ignored) {
                    }
                    try {
                        spawnEntityCtor = Class.forName(
                            "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity")
                            .getConstructor(nmsEntities.get(0));
                    } catch (Throwable ignored) {
                    }
                    try {
                        infoRemoveCtor = Class.forName(
                            "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                            .getConstructor(java.util.List.class);
                    } catch (Throwable ignored) {
                    }
                }
                if ((infoPacketCtor != null && namedSpawnCtor != null)
                    || (infoUpdateCollectionCtor != null && spawnEntityCtor != null && infoRemoveCtor != null)) {
                    finishResolve(true);
                    return;
                }
            }

            // 26.2 Entry path
            StringBuilder dbg = new StringBuilder();
            try {
                dbg.append("update=");
                Class<?> update = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
                dbg.append("ok");
                // Inner-class names differ: Mojang-mapped 1.20.4 uses $a/$b, newer 26.x uses $Action/$Entry.
                dbg.append(";action=");
                Class<?> actionClass = classFor(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$a",
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
                if (actionClass == null) throw new ClassNotFoundException("player info action");
                updateAddAction = actionClass.getEnumConstants()[0]; // ADD_PLAYER
                dbg.append(actionClass.getSimpleName());
                dbg.append(";entry=");
                Class<?> entryClass = classFor(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$b",
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
                if (entryClass == null) throw new ClassNotFoundException("player info entry");
                dbg.append(entryClass.getSimpleName());
                dbg.append(";gameProfile=").append(gameProfile == null ? "null" : gameProfile.getSimpleName());
                entryCtor = null;
                for (Constructor<?> ctor : entryClass.getConstructors()) {
                    Class<?>[] p = ctor.getParameterTypes();
                    if (p.length >= 2 && p[0] == UUID.class && p[1] == gameProfile) {
                        entryCtor = ctor;
                        entryNineArg = p.length >= 9;
                        break;
                    }
                }
                dbg.append(";entryCtor=").append(entryCtor == null ? "null" : (entryNineArg ? "9arg" : "6arg"));
                infoUpdateEntryListCtor = update.getConstructor(java.util.EnumSet.class, java.util.List.class);
                dbg.append(";listCtor=ok");
                if (entryCtor == null) throw new ClassNotFoundException("entry ctor");
                // game type constant: prefer SURVIVAL, else the first constant
                Class<?> gameType = entryCtor.getParameterTypes()[4];
                try {
                    gameTypeConst = gameType.getField("SURVIVAL").get(null);
                } catch (Throwable ignored) {
                    gameTypeConst = gameType.getEnumConstants()[0];
                }
                dbg.append(";gameType=").append(gameTypeConst == null ? "null" : "set");
                // add-entity packet: (int, UUID, DDD, FF, EntityTypes, int, Vec3, double)
                Class<?> addEntity = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
                Class<?> vecClass = null;
                Class<?> entityTypeClass = null;
                addEntityCtor = null;
                StringBuilder ae = new StringBuilder();
                for (Constructor<?> ctor : addEntity.getConstructors()) {
                    Class<?>[] p = ctor.getParameterTypes();
                    StringBuilder sig = new StringBuilder("(");
                    for (int i = 0; i < p.length; i++) {
                        if (i > 0) sig.append(",");
                        sig.append(p[i].getSimpleName());
                    }
                    sig.append(")");
                    if (ae.length() > 0) ae.append(" | ");
                    ae.append(sig);
                    if (p.length != 11 || p[0] != int.class || p[1] != UUID.class) continue;
                    // The 11-arg spawn ctor is (int, UUID, DDD, FF, EntityType, int, Vec3, double).
                    // Find the EntityType and Vec3 params by name rather than fixed index: arg 8 is a
                    // primitive int sandwiched between them, which broke the old "p[8] non-primitive" check.
                    Class<?> et = null, vec = null;
                    for (Class<?> pc : p) {
                        String sn = pc.getSimpleName();
                        if (vec == null && sn.equals("Vec3")) vec = pc;
                        else if (et == null && (sn.equals("EntityType") || sn.equals("EntityTypes"))) et = pc;
                    }
                    if (et != null && vec != null) {
                        addEntityCtor = ctor;
                        entityTypeClass = et;
                        vecClass = vec;
                        break;
                    }
                }
                dbg.append(";addEntityCtor=").append(addEntityCtor == null ? "null" : "ok")
                    .append(" [").append(ae).append("]");
                if (addEntityCtor != null) {
                    vecCtor = vecClass.getConstructor(double.class, double.class, double.class);
                    playerEntityType = resolvePlayerEntityType(entityTypeClass);
                    dbg.append(";playerType=").append(playerEntityType == null ? "null" : "ok");
                }
                infoRemoveCtor = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                    .getConstructor(java.util.List.class);
                dbg.append(";remove=ok");
                if (entryCtor != null && addEntityCtor != null
                    && playerEntityType != null && infoRemoveCtor != null) {
                    finishResolve(true);
                    return;
                }
                dbg.append(";FINAL_NULL");
                throw new ClassNotFoundException("final incomplete");
            } catch (Throwable t) {
                resolveDebug(dbg.length() == 0
                    ? (t.getClass().getSimpleName() + ": " + t.getMessage())
                    : (dbg.toString() + " -> threw " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
            finishResolve(false);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[PacketNpcs] resolve crashed: " + t);
            finishResolve(false);
        }
    }

    private static void finishResolve(boolean result) {
        ok = result;
        // Only speak up when packet NPCs are UNAVAILABLE (falls back to armor-stand look) — success is silent.
        if (!ok) Bukkit.getLogger().warning("[PacketNpcs] packet player-model NPCs unavailable on this server "
            + "version — using armor-stand NPCs.");
    }

    /** Resolve the arm-swing animation packet. Best-effort: when nothing matches, models simply never punch. */
    private static void resolveSwing(String v, boolean versioned, String gamePkg) {
        Class<?> swingClass = classFor(
            versioned ? "net.minecraft.server." + v + ".PacketPlayOutAnimation" : "",
            "net.minecraft.network.protocol.game.PacketPlayOutAnimation",
            "net.minecraft.network.protocol.game.ClientboundAnimatePacket");
        if (swingClass == null) return;
        for (Constructor<?> ctor : swingClass.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length != 2) continue;
            boolean enumAction = p[1].isEnum();
            if (!enumAction && p[1] != int.class) continue;
            if (p[0] == int.class) {
                swingCtor = ctor;
                swingActionIsEnum = enumAction;
                break;
            }
            if (!p[0].isPrimitive()) {
                swingCtor = ctor;
                swingNeedsEntity = true;
                swingActionIsEnum = enumAction;
                break;
            }
        }
        if (swingCtor == null) {
            // legacy no-arg layout (1.8 PacketPlayOutAnimation()): int fields default to action 0 = swing.
            for (Constructor<?> ctor : swingClass.getConstructors()) {
                if (ctor.getParameterTypes().length == 0) { swingCtor = ctor; break; }
            }
            return;
        }
        if (swingActionIsEnum) {
            Class<?> actionClass = swingCtor.getParameterTypes()[1];
            try {
                swingAction = actionClass.getField("SWING_MAIN_HAND").get(null);
            } catch (Throwable ignored) {
                Object[] constants = actionClass.getEnumConstants();
                swingAction = constants != null && constants.length > 0 ? constants[0] : null;
                if (swingAction == null) swingCtor = null;
            }
        }
    }

    private static List<Class<?>> nmsEntityClasses(String v, boolean versioned) {
        List<Class<?>> list = new ArrayList<Class<?>>();
        if (versioned) {
            try {
                list.add(Class.forName("net.minecraft.server." + v + ".Entity"));
            } catch (Throwable ignored) {
            }
        }
        try {
            list.add(Class.forName("net.minecraft.world.entity.Entity"));
        } catch (Throwable ignored) {
        }
        return list;
    }

    private static Class<?> classFor(String... names) {
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Resolve the EntityType.PLAYER constant. 1.20.4 and older keep it as a public static field on EntityType;
     * 26.x moved type constants into the BuiltInRegistries.ENTITY_TYPE registry, looked up by Identifier. */
    private static Object resolvePlayerEntityType(Class<?> entityTypeClass) {
        try {
            return entityTypeClass.getField("PLAYER").get(null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> registries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object registry = registries.getField("ENTITY_TYPE").get(null);
            Class<?> ident = Class.forName("net.minecraft.resources.Identifier");
            Object key = ident.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "minecraft", "player");
            Class<?> registryIface = Class.forName("net.minecraft.core.Registry");
            return registryIface.getMethod("getValue", ident).invoke(registry, key);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findConnectionField(Class<?> handleClass) {
        for (Class<?> c = handleClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : new String[]{"playerConnection", "connection"}) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
