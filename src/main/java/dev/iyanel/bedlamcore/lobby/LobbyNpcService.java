package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.PacketNpcs;
import dev.iyanel.bedlamcore.compat.Skins;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.NpcSoundListener;
import dev.iyanel.bedlamcore.game.ProfileStats;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardCategory;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyNpcService implements Listener {
    /** Scrub radius for orphan cosmetics holograms around an NPC pin (blocks). */
    public static final double HOLO_SCRUB_RADIUS = 3.0;
    public static final String META_MODE = "bedlamNpcMode";
    public static final String META_HOLO = "bedlamLobbyHolo";
    public static final String META_SILENT = "bedlamSilent";
    public static final String META_COSMETICS = "bedlamCosmetics";
    public static final String META_PROFILE = "bedlamProfile";
    public static final String META_PROFILE_OWNER = "bedlamProfileOwner";
    public static final String META_LEADERBOARD = "bedlamLeaderboard";
    /** Default skin for the lobby leaderboard NPC when the admin hasn't set one. */
    public static final String LEADERBOARD_SKIN = "Hypixel";
    /** Bottom line Y of the leaderboard board column; the title/rows stack rises from here. */
    public static final double LEADERBOARD_HOLO_BOTTOM = 2.4;
    /** Feet Y of the lowest click proxy, and vertical spacing between stacked proxies (< 1.975 hitbox → no gap). */
    private static final double LEADERBOARD_PROXY_BOTTOM = 0.3;
    private static final double LEADERBOARD_PROXY_STEP = 1.8;
    /** Hidden board-line placeholder: renders as nothing but is non-blank so reloaded orphans get scrubbed. */
    private static final String BLANK_LINE = ChatColor.DARK_GRAY.toString();
    /** Dream Defender / team pets — spared by monster purge. */
    public static final String META_PET = "bedlamPet";
    /** Profile hologram stack top (6 stat lines rise from here). */
    public static final double PROFILE_HOLO_TOP = 3.55;
    /** Profile shows the nearest player's live stat lines (shared NPC — no per-viewer packets on 1.12.2). */
    public static final int PROFILE_HOLO_LINES = ProfileStats.hologramLines(null).length;
    /** Fixed head for the shared profile NPC body — no per-player skin without Citizens. */
    public static final String PROFILE_SKIN = "Steve";
    /** Look-at only within this many blocks; outside keep placement yaw. */
    public static final double LOOK_RANGE = 8.0;
    public static final double LOOK_RANGE_SQ = LOOK_RANGE * LOOK_RANGE;
    private static final EntityType[] TYPES = {
        EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SKELETON,
        EntityType.CREEPER, EntityType.BLAZE, EntityType.IRON_GOLEM
    };
    private static final Map<UUID, Entity> SILENT_ENTITIES = new ConcurrentHashMap<UUID, Entity>();

    private final BedlamCore plugin;
    /** Citizens soft-dep: live NPC handles so we destroy them (not just entity.remove) on despawn. */
    private final Map<GameType, Object> citizens = new EnumMap<GameType, Object>(GameType.class);
    private Object citizensRegistry;
    private Object cosmeticsCitizen;
    private Object profileCitizen;
    private final Map<GameType, UUID> entities = new EnumMap<GameType, UUID>(GameType.class);
    private final Map<GameType, List<UUID>> holograms = new EnumMap<GameType, List<UUID>>(GameType.class);
    private final Map<UUID, Location> pins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> lookAtPlayers = new HashMap<UUID, Boolean>();
    private final Map<UUID, Entity> tracked = new HashMap<UUID, Entity>();
    private UUID cosmeticsEntity;
    private final List<UUID> cosmeticsHolograms = new ArrayList<UUID>();
    /** Last cosmetics pin — used to scrub orphans after despawn / relocate. */
    private Location cosmeticsPin;
    /** Profile NPC: single shared body + hologram stack (no per-viewer clones). */
    private UUID profileEntity;
    private final List<UUID> profileHolograms = new ArrayList<UUID>();
    private Location profilePin;
    /** Leaderboard board: a hologram-only text column (no body/model) + invisible click proxies. */
    private final List<UUID> leaderboardProxies = new ArrayList<UUID>();
    private final List<UUID> leaderboardHolograms = new ArrayList<UUID>();
    private Location leaderboardPin;
    private boolean respawningLeaderboard;
    private long lastLeaderboardSpawnTick;
    private int leaderboardRefreshTick;
    /** Last rendered board text per line — diffed so only changed lines re-set (no lobby flicker). */
    private final List<String> leaderboardLastLines = new ArrayList<String>();
    /** Name whose skull is currently on the shared profile body — only re-equip when the nearest player changes. */
    private String profileHeadOwner;
    private int profileRefreshTick;
    private int visibilityTick;
    private int muteTick;
    private boolean respawningCosmetics;
    private boolean respawningProfile;
    /** Rate-limit respawn-failure warnings per subsystem so a broken spawn logs once, not every tick. */
    private final Map<String, Long> lastRespawnWarn = new HashMap<String, Long>();
    /** Throttle full cosmetics respawns so rapid destroy+respawn cycles can't duplicate/flicker the villager. */
    private long lastCosmeticsSpawnTick;
    /** Throttle full profile respawns (same reason as cosmetics). */
    private long lastProfileSpawnTick;
    /** Grace period per queue NPC: skip respawn right after a spawn. */
    private final Map<GameType, Long> queueLastSpawn = new EnumMap<GameType, Long>(GameType.class);
    /** Last applied yaw/pitch per entity — only push a rotation packet when it actually changed. */
    private final Map<UUID, float[]> lastLook = new HashMap<UUID, float[]>();
    /** Citizens-free packet player-models keyed by their (invisible) body uuid — see {@link PacketNpcs}. */
    private final Map<UUID, PacketNpcs.Model> packetModels = new HashMap<UUID, PacketNpcs.Model>();
    /** Requested skin per body — retried once the async Mojang fetch lands in the cache. */
    private final Map<UUID, String> packetSkinKeys = new HashMap<UUID, String>();
    /** Cape flag per body (parallel to {@link #packetSkinKeys}) so the async retry rebuilds the right profile. */
    private final Map<UUID, Boolean> packetCapes = new HashMap<UUID, Boolean>();
    private static final float LOOK_PITCH_CLAMP = 30f;
    /** Kept for legacy checks; profile is now a single shared NPC ensured every tick like cosmetics. */
    public static final int PROFILE_ENSURE_INTERVAL = 20;

    public LobbyNpcService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() {
                ensureCosmeticsAlive();
                ensureProfileAlive();
                ensureLeaderboardAlive();
                ensureQueueNpcsAlive();
                pinEntities();
                updateProfileDisplay();
                updateLeaderboardDisplay();
                long now = System.currentTimeMillis();
                if (now - lastSweepTick >= 10000L) { lastSweepTick = now; sweepStrayLobbyMobs(); }
                if (++visibilityTick % GameRules.DISPLAY_VISIBILITY_INTERVAL == 0) updateHologramVisibility();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private long lastSweepTick;

    /** Log a respawn failure at most once per 10s per subsystem — a broken spawn must retry, not spam the console. */
    private void warnRespawnFailure(String what, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = lastRespawnWarn.get(what);
        if (last != null && now - last < 10000L) return;
        lastRespawnWarn.put(what, now);
        plugin.getLogger().warning("Lobby " + what + " NPC respawn failed (will retry next tick): " + t);
    }

    public void respawnAll() {
        removeAll();
        sweepStrayLobbyMobs();
        // Lobby holograms are marker armor stands saved into the (autosaving) lobby world. On restart they
        // reload WITHOUT their runtime metadata, so META_HOLO scrubbing misses them and fresh ones spawn on
        // top → old holograms left floating. Purge reloaded orphans around each pin before respawning.
        for (GameType type : GameType.values()) {
            scrubReloadedHolograms(plugin.lobby().npc(type).location());
            scrubReloadedBodies(plugin.lobby().npc(type).location());
        }
        scrubReloadedHolograms(plugin.lobby().cosmeticsNpc());
        scrubReloadedBodies(plugin.lobby().cosmeticsNpc());
        scrubReloadedHolograms(plugin.lobby().profileNpc());
        scrubReloadedBodies(plugin.lobby().profileNpc());
        scrubReloadedHolograms(plugin.lobby().leaderboardNpc());
        scrubReloadedBodies(plugin.lobby().leaderboardNpc());
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            if (settings.location() != null) spawn(type, settings);
        }
        spawnCosmetics(plugin.lobby().cosmeticsNpc());
        spawnProfile(plugin.lobby().profileNpc());
        if (GameRules.LEADERBOARD_ENABLED && GameRules.LEADERBOARD_NPC_ENABLED) {
            spawnLeaderboard(plugin.lobby().leaderboardNpc());
        }
    }

    /** Lobby world anchor for cleanup sweeps: live pins first, then saved config locations. */
    private Location lobbyAnchor() {
        if (profilePin != null) return profilePin;
        if (leaderboardPin != null) return leaderboardPin;
        if (cosmeticsPin != null) return cosmeticsPin;
        if (plugin.lobby() == null) return null;
        if (plugin.lobby().spawn() != null) return plugin.lobby().spawn();
        if (plugin.lobby().cosmeticsNpc() != null) return plugin.lobby().cosmeticsNpc();
        if (plugin.lobby().profileNpc() != null) return plugin.lobby().profileNpc();
        for (GameType type : GameType.values()) {
            Location pin = plugin.lobby().npc(type).location();
            if (pin != null) return pin;
        }
        return null;
    }

    /**
     * Remove stray mobs/villagers from the LOBBY world's loaded chunks — leftover NPC bodies from old spawns
     * or relocations (the "invisible villager" clutter). Loaded chunks only (a full-world scan freezes Paper
     * 26.x); lobby worlds are tiny so this is cheap. Players, Citizens NPCs, pets and decorative armor stands
     * are spared; arena worlds are never touched. Runs at respawnAll AND periodically (leaks from relocations
     * or late-loading chunks must die mid-session, not wait for a restart).
     */
    private void sweepStrayLobbyMobs() {
        Location anchor = lobbyAnchor();
        if (anchor == null || anchor.getWorld() == null) return;
        World world = anchor.getWorld();
        int removed = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
                if (entity instanceof ArmorStand) continue;
                if (tracked.containsKey(entity.getUniqueId())) continue;
                if (entity.hasMetadata("NPC") || entity.hasMetadata(META_PET)) continue;
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) plugin.getLogger().info("Lobby cleanup: removed " + removed + " stray mob(s)/villager(s).");
    }

    /**
     * Remove reloaded orphan holograms around a pin whose runtime metadata was lost on world save/reload.
     * Tightly scoped: only invisible marker armor stands that carry a custom name (exactly our hologram
     * shape) and are not currently tracked — real visible/decorative stands are never touched.
     */
    private void scrubReloadedHolograms(Location around) {
        if (around == null || around.getWorld() == null) return;
        // At onEnable the pin chunk isn't loaded yet, so bailing here left the reloaded orphans in place while
        // spawn() force-loaded the chunk and stacked fresh holograms on top. Load it first so the scan sees them.
        around.getWorld().getChunkAt(around).load();
        double r = HOLO_SCRUB_RADIUS;
        Location focus = around.clone().add(0, 1.5, 0);
        for (Entity entity : around.getWorld().getNearbyEntities(focus, r, r + 3.0, r)) {
            if (!(entity instanceof ArmorStand)) continue;
            if (tracked.containsKey(entity.getUniqueId())) continue;
            ArmorStand stand = (ArmorStand) entity;
            // Invisible armor stand carrying a custom name = our hologram shape. Don't require the marker flag:
            // older builds saved non-marker holograms, so a marker check let those survive every restart.
            if (stand.isVisible()) continue;
            String customName = stand.getCustomName();
            if (customName == null || customName.trim().isEmpty()) continue;
            entity.remove();
        }
    }

    /**
     * Remove reloaded NPC bodies (villager / mob / visible armor stand) sitting on an exact pin whose runtime
     * metadata was lost on world save/reload. Tiny footprint around the pin only — that cell is reserved for the
     * NPC, so a decorative entity is never exactly there. Never scans the world (freezes Paper 26.x).
     */
    private void scrubReloadedBodies(Location around) {
        if (around == null || around.getWorld() == null) return;
        around.getWorld().getChunkAt(around).load();
        int scrubbed = 0;
        for (Entity entity : around.getWorld().getNearbyEntities(around, 1.2, 2.0, 1.2)) {
            if (tracked.containsKey(entity.getUniqueId())) continue;
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
            // Never delete a live Citizens NPC body: right after a Citizens remount the fresh backing entity is
            // not yet in `tracked`, and deleting it here would leave the body gone while holograms float — the
            // exact "NPC vanishes until restart" bug. Citizens NPCs are in-memory (never world-saved), so a real
            // reloaded orphan is always a plain world mob and never carries the "NPC" metadata.
            if (entity.hasMetadata("NPC")) continue;
            entity.remove();
            scrubbed++;
        }
    }

    /**
     * Citizens-free player-model NPC: hide the real body (it stays the click hitbox) and render a packet
     * player model with a real skin on top. No-op when the packet stack is unavailable (keeps the old look).
     */
    /** Rate-limited once-per-JVM notice when the packet model stack is unavailable. */
    private static boolean packetModelUnavailableLogged;

    /**
     * @param faceLocation the ORIGINAL placement location whose yaw determines which way the packet model
     *                     faces. On 1.12.2 villager entities return yaw=0 from getLocation() regardless of
     *                     spawn location, so using the body's location would always face south. Passing the
     *                     admin-placed location preserves the intended facing.
     */
    private void attachPacketModel(Entity body, String skinKey, boolean cape, Location faceLocation) {
        if (body == null) return;
        // Destroy any model already attached to this body FIRST — an orphaned old model keeps rendering at its
        // last position forever (stacked/duplicate player models).
        detachPacketModel(body.getUniqueId());
        if (!PacketNpcs.available()) {
            if (!packetModelUnavailableLogged) {
                packetModelUnavailableLogged = true;
                plugin.getLogger().info("Packet NPC models unsupported on this server — using armor-stand NPCs.");
            }
            return;
        }
        if (skinKey != null && !skinKey.isEmpty()) {
            packetSkinKeys.put(body.getUniqueId(), skinKey);
            packetCapes.put(body.getUniqueId(), cape);
        }
        if (body instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) body;
            stand.setVisible(false);
            // Clear EVERYTHING — held items still render on an invisible stand (the floating paper bug).
            EntityEquipment gear = stand.getEquipment();
            if (gear != null) {
                gear.setHelmet(null);
                gear.setChestplate(null);
                gear.setLeggings(null);
                gear.setBoots(null);
                gear.setItemInHand(null);
            }
        } else if (body instanceof LivingEntity) {
            // setInvisible doesn't exist in older Bukkit APIs — an infinite no-particle invisibility
            // potion hides mobs on every version (the reflective call silently no-opped on 1.12).
            try {
                ((LivingEntity) body).removePotionEffect(PotionEffectType.INVISIBILITY);
            } catch (Throwable ignored) {
            }
            try {
                ((LivingEntity) body).addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false), true);
            } catch (Throwable ignored) {
                invokeBoolean(body, "setInvisible", true);
            }
        }
        PacketNpcs.fetchSkin(plugin, skinKey, cape);
        Object profile = PacketNpcs.cachedProfile(skinKey, cape);
        if (profile == null) return; // skin still downloading — retried from the pin loop once cached
        String name = skinKey != null && skinKey.matches("[A-Za-z0-9_]{1,16}") ? skinKey : "NPC";
        // Use the original placement location for yaw — body.getLocation() returns yaw=0 on 1.12.2 villagers.
        Location useLoc = faceLocation != null ? faceLocation : body.getLocation();
        PacketNpcs.Model model = PacketNpcs.create(plugin, useLoc, name, profile);
        if (model != null) {
            final Entity npcBody = body;
            model.onClick(new PacketNpcs.ClickHandler() {
                @Override public void click(Player viewer) { handleNpcClick(viewer, npcBody); }
            });
            packetModels.put(body.getUniqueId(), model);
            PacketNpcs.ensureViewers(model, 48.0);
        }
    }

    /** Route a click on a lobby NPC body (cosmetics / profile / queue) to its GUI. Shared by the real-body
     *  interact event and the packet fake-player click interceptor; admins sneak-clicking open the config editor. */
    public void handleNpcClick(Player viewer, Entity body) {
        if (viewer == null || body == null) return;
        boolean adminSneak = viewer.isSneaking() && plugin.isAdmin(viewer);
        if (isCosmetics(body)) {
            if (adminSneak) plugin.gui().openSpecialNpcEditor(viewer, "COSMETICS");
            else plugin.gui().openCosmetics(viewer);
            return;
        }
        if (isProfile(body)) {
            if (adminSneak) { plugin.gui().openSpecialNpcEditor(viewer, "PROFILE"); return; }
            UUID owner = profileOwner(body);
            if (owner == null || owner.equals(viewer.getUniqueId())) plugin.gui().openProfileStats(viewer);
            return;
        }
        if (isLeaderboard(body)) {
            if (adminSneak) { plugin.gui().openSpecialNpcEditor(viewer, "LEADERBOARD"); return; }
            if (EntityVisibility.isSpectator(viewer)) return; // spectators can't open lobby GUIs
            plugin.gui().openLeaderboard(viewer);
            return;
        }
        GameType mode = mode(body);
        if (mode != null) {
            if (adminSneak) plugin.gui().openNpcEditor(viewer, mode);
            else plugin.gui().openQueue(viewer, mode);
        }
    }

    private void detachPacketModel(UUID bodyUuid) {
        if (bodyUuid == null) return;
        packetSkinKeys.remove(bodyUuid);
        packetCapes.remove(bodyUuid);
        PacketNpcs.Model model = packetModels.remove(bodyUuid);
        if (model != null) PacketNpcs.destroy(model);
    }

    /** Spawn the single shared profile NPC + its static hologram stack. */
    public Entity spawnProfile(Location location) {
        Location previous = profilePin == null ? null : profilePin.clone();
        removeProfile();
        scrubOrphanHolograms(previous);
        scrubOrphanPapers(previous);
        scrubOrphanHolograms(location);
        scrubOrphanPapers(location);
        if (location == null || location.getWorld() == null) return null;
        // Kill any reloaded orphan body sitting on the pin before spawning — the exact cause of duplicate NPCs.
        scrubReloadedBodies(location);
        // Own packet fake-player first (Citizens-free). Body is an ARMOR STAND hidden via setVisible(false) —
        // NOT a villager with an invisibility potion. The potion is client-rendered as swirling particles on 1.8
        // (the particles=false flag is ignored by the 1.8 client) and its hitbox fought the click. Clicks land on
        // the fake-player model via the Netty interceptor (reliable now that the shown-set is refreshed on world
        // change), so the body no longer needs to be the raycast target.
        Entity body = null;
        if (PacketNpcs.available()) body = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        if (body == null) body = spawnCitizenProfile(location);
        // Fallback: visible armor stand with head (no packet model, no right-click)
        if (body == null) body = spawnProfileStand(location);
        String profileSkin = plugin.lobby() != null && plugin.lobby().profileSkin() != null ? plugin.lobby().profileSkin() : PROFILE_SKIN;
        boolean profileCape = plugin.lobby() != null && plugin.lobby().profileCape();
        if (body != null && !body.hasMetadata("NPC")) attachPacketModel(body, profileSkin, profileCape, location);
        body.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
        hideBodyName(body);
        freeze(body, false);
        // Hittable so shift-left-click opens the skin editor (onNpcHit); damage is cancelled in onQueueNpcDamage.
        invokeBoolean(body, "setInvulnerable", false);
        profileEntity = body.getUniqueId();
        profilePin = location.clone();
        profileHeadOwner = null; // force updateProfileHead() to re-equip the nearest player's skull next tick
        tracked.put(body.getUniqueId(), body);
        pins.put(body.getUniqueId(), location.clone());
        // Placement facing LOCKED (no player-tracking) — see spawn().
        lookAtPlayers.put(body.getUniqueId(), Boolean.FALSE);
        // Static default stats initially; updateProfileDisplay() swaps in the nearest player's live stats next tick.
        String[] lines = ProfileStats.hologramLines(null);
        for (int i = 0; i < lines.length; i++) {
            profileHolograms.add(hologram(location.clone().add(0, GameRules.labelY(PROFILE_HOLO_TOP, i), 0), lines[i]).getUniqueId());
        }
        return body;
    }

    /** Chunk unload / body despawn — recreate the shared profile body + holograms. */
    public void ensureProfileAlive() {
        if (respawningProfile) return;
        Location location = profilePin != null ? profilePin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().profileNpc());
        if (location == null || location.getWorld() == null) return;
        if (!chunkLoaded(location)) return;
        if (alive(profileEntity) && profileHologramsAlive()) return;
        long now = System.currentTimeMillis();
        if (now - lastProfileSpawnTick < 2000L) return;
        respawningProfile = true;
        try {
            spawnProfile(location);
            // Timestamp only AFTER success: writing it up front meant a thrown spawn still consumed the 2s
            // window, and spawnProfile() nulls profileEntity/profilePin first — so a mid-spawn throw could leave
            // the body gone with holograms floating until restart. On failure we retry next tick instead.
            lastProfileSpawnTick = System.currentTimeMillis();
        } catch (Throwable t) {
            warnRespawnFailure("profile", t);
        } finally {
            respawningProfile = false;
        }
    }

    public boolean isProfile(Entity entity) {
        return entity != null && entity.hasMetadata(META_PROFILE);
    }

    /**
     * Shared profile body wears the nearest player's skull and its hologram shows that player's live stats, so
     * walking up to it shows YOUR head + YOUR stats (as close as a single shared entity gets without per-viewer
     * Citizens on 1.12.2). Head/holo text are refreshed when the nearest player changes and every ~2s for live
     * stat updates. Falls back to {@link #PROFILE_SKIN} / default stats when nobody is near.
     */
    private void updateProfileDisplay() {
        if (profileEntity == null || profilePin == null || profilePin.getWorld() == null) return;
        Entity body = find(profileEntity);
        if (body == null) return;
        Player nearest = nearestProfileViewer();
        boolean changed = false;
        // Armor-stand fallback wears the nearest player's head; a Citizens NPC or packet model keeps its
        // fixed skin (no swap — the packet-model body is an invisible hitbox).
        if (body instanceof ArmorStand && !packetModels.containsKey(body.getUniqueId())) {
            String owner = nearest == null ? PROFILE_SKIN : nearest.getName();
            changed = !owner.equals(profileHeadOwner);
            if (changed) {
                profileHeadOwner = owner;
                EntityEquipment gear = ((ArmorStand) body).getEquipment();
                if (gear != null) gear.setHelmet(Skins.head(owner));
            }
        }
        if (changed || ++profileRefreshTick % 40 == 0) {
            String[] lines = ProfileStats.hologramLines(nearest == null ? null : plugin.stats().get(nearest.getUniqueId()));
            for (int i = 0; i < profileHolograms.size() && i < lines.length; i++) {
                Entity holo = find(profileHolograms.get(i));
                if (holo != null) { holo.setCustomName(lines[i]); holo.setCustomNameVisible(true); }
            }
        }
    }

    private Player nearestProfileViewer() {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        double limit = GameRules.DISPLAY_VIEW * GameRules.DISPLAY_VIEW;
        for (Player player : profilePin.getWorld().getPlayers()) {
            if (EntityVisibility.isSpectator(player)) continue;
            double distance = player.getLocation().distanceSquared(profilePin);
            if (distance <= limit && distance < best) { best = distance; nearest = player; }
        }
        return nearest;
    }

    /** Shared profile NPC has no owner — a click always opens the clicking player's own stats. */
    public UUID profileOwner(Entity entity) {
        return null;
    }

    /** Visible arm'd armor stand with a fixed head + paper — a person-shaped body on every build (no Citizens). */
    private static ArmorStand spawnProfileStand(Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setSmall(false);
        stand.setGravity(false);
        stand.setVisible(true);
        EntityEquipment gear = stand.getEquipment();
        if (gear != null) {
            gear.setHelmet(Skins.head(PROFILE_SKIN));
            gear.setItemInHand(new ItemStack(Material.PAPER));
        }
        return stand;
    }

    public void removeProfile() {
        Location scrubAt = profilePin == null ? null : profilePin.clone();
        for (UUID uuid : new ArrayList<UUID>(profileHolograms)) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        profileHolograms.clear();
        if (profileEntity != null) {
            pins.remove(profileEntity);
            lookAtPlayers.remove(profileEntity);
            detachPacketModel(profileEntity);
            Entity entity = tracked.remove(profileEntity);
            if (entity != null) entity.remove();
            else removeWorldEntity(profileEntity, scrubAt);
            profileEntity = null;
        }
        if (profileCitizen != null) {
            invoke(profileCitizen, "destroy");
            profileCitizen = null;
        }
        scrubOrphanHolograms(scrubAt);
        scrubOrphanPapers(scrubAt);
        // Kill orphan body sitting on the old pin — prevents the duplicate profile NPC
        // when relocating. Entity was removed from tracked above, so scrubReloadedBodies finds and
        // deletes it. Force-loads the chunk so getNearbyEntities sees it.
        scrubReloadedBodies(scrubAt);
        profilePin = null;
    }

    // ------------------------------------------------------------------ lobby leaderboard NPC + board

    /**
     * Spawn the Hypixel-style leaderboard: a floating text column (title + subtitle + top rows) with NO
     * body/model underneath — plus invisible, hittable click proxies stacked up the column so a click
     * anywhere on the board opens the GUI. Returns {@code null} (there is no body to hand back).
     */
    public Entity spawnLeaderboard(Location location) {
        Location previous = leaderboardPin == null ? null : leaderboardPin.clone();
        removeLeaderboard();
        scrubOrphanHolograms(previous);
        scrubOrphanPapers(previous);
        scrubOrphanHolograms(location);
        scrubOrphanPapers(location);
        if (location == null || location.getWorld() == null) return null;
        // Delete any leftover invisible villager body from an older build sitting on the pin.
        scrubReloadedBodies(location);
        leaderboardPin = location.clone();
        leaderboardHolograms.clear();
        leaderboardLastLines.clear();
        leaderboardProxies.clear();
        // Bottom-anchored column built from the ACTUAL rendered lines — never from capacity (3+topN). The old
        // capacity build padded missing rows with blank stands AND raised the whole board's top by (capacity-1)
        // lines, so the board floated ever higher as topN grew and showed empty slots. With real line count the
        // footer sits at LEADERBOARD_HOLO_BOTTOM and the title rises only as far as there is content: a short
        // board hugs the ground, a full board is tall — no blanks, no sky-float.
        List<String> lines = currentBoardLines();
        int n = lines.size();
        double top = LEADERBOARD_HOLO_BOTTOM + Math.max(0, n - 1) * GameRules.HOLO_LINE;
        for (int i = 0; i < n; i++) {
            ArmorStand stand = hologram(location.clone().add(0, GameRules.labelY(top, i), 0), lines.get(i));
            leaderboardHolograms.add(stand.getUniqueId());
            leaderboardLastLines.add(lines.get(i));
        }
        // Invisible full-size hittable armor-stand proxies covering the whole text column, so any reasonable
        // click on the board (left or right) opens the GUI — no villager body (per the Hypixel-clean NPC rule).
        for (double y = LEADERBOARD_PROXY_BOTTOM; y <= top + 0.2; y += LEADERBOARD_PROXY_STEP) {
            ArmorStand proxy = spawnLeaderboardProxy(location.clone().add(0, y, 0));
            leaderboardProxies.add(proxy.getUniqueId());
        }
        return null;
    }

    /** Invisible, silent, hittable armor stand (non-marker so it keeps a click hitbox) tagged as the board. */
    private ArmorStand spawnLeaderboardProxy(Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        // Tag first; blank hidden name so a world-reloaded orphan is caught by the hologram scrubbers.
        stand.setMetadata(META_LEADERBOARD, new FixedMetadataValue(plugin, true));
        stand.setCustomName(BLANK_LINE);
        stand.setCustomNameVisible(false);
        stand.setVisible(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setSmall(false); // full-size = a tall hitbox that spans a chunk of the column
        invokeBoolean(stand, "setMarker", false);   // marker stands have NO hitbox — must stay non-marker
        invokeBoolean(stand, "setCollidable", false);
        invokeBoolean(stand, "setInvulnerable", false); // hittable so a LEFT-click fires onNpcHit
        EntityEquipment gear = stand.getEquipment();
        if (gear != null) {
            gear.setHelmet(null); gear.setChestplate(null); gear.setLeggings(null);
            gear.setBoots(null); gear.setItemInHand(null);
        }
        hideBodyName(stand);
        mute(stand);
        tracked.put(stand.getUniqueId(), stand);
        pins.put(stand.getUniqueId(), location.clone());
        return stand;
    }

    /** Chunk unload / body despawn — recreate the leaderboard body + board. */
    public void ensureLeaderboardAlive() {
        if (respawningLeaderboard) return;
        if (!GameRules.LEADERBOARD_ENABLED || !GameRules.LEADERBOARD_NPC_ENABLED) return;
        Location location = leaderboardPin != null ? leaderboardPin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().leaderboardNpc());
        if (location == null || location.getWorld() == null) return;
        if (!chunkLoaded(location)) return;
        if (leaderboardHologramsAlive() && leaderboardProxiesAlive()) return;
        long now = System.currentTimeMillis();
        if (now - lastLeaderboardSpawnTick < 2000L) return;
        respawningLeaderboard = true;
        try {
            spawnLeaderboard(location);
            lastLeaderboardSpawnTick = System.currentTimeMillis();
        } catch (Throwable t) {
            warnRespawnFailure("leaderboard", t);
        } finally {
            respawningLeaderboard = false;
        }
    }

    /** Refresh the board text (~1s cadence; the service itself throttles the underlying recompute). */
    private void updateLeaderboardDisplay() {
        if (leaderboardPin == null || leaderboardPin.getWorld() == null || leaderboardHolograms.isEmpty()) return;
        if (++leaderboardRefreshTick % 20 != 0) return;
        List<String> lines = currentBoardLines();
        // Line-count changed (players became ranked / dropped out): the column must grow or shrink. Rebuild it
        // atomically from the pin so there are never blank holes or a stale proxy stack of the wrong height.
        if (lines.size() != leaderboardHolograms.size()) { spawnLeaderboard(leaderboardPin.clone()); return; }
        for (int i = 0; i < leaderboardHolograms.size(); i++) {
            String text = lines.get(i);
            if (text.equals(leaderboardLastLines.get(i))) continue; // diff: only touch changed lines (no flicker)
            Entity holo = find(leaderboardHolograms.get(i));
            if (holo == null) continue;
            holo.setCustomName(text);
            holo.setCustomNameVisible(true);
            leaderboardLastLines.set(i, text);
        }
    }

    private List<String> currentBoardLines() {
        LeaderboardService service = plugin.leaderboards();
        if (service == null) return new ArrayList<String>();
        return service.boardLines(LeaderboardCategory.WINS, null);
    }

    private boolean leaderboardHologramsAlive() {
        if (leaderboardHolograms.isEmpty()) return false;
        for (UUID uuid : leaderboardHolograms) if (!alive(uuid)) return false;
        return true;
    }

    private boolean leaderboardProxiesAlive() {
        if (leaderboardProxies.isEmpty()) return false;
        for (UUID uuid : leaderboardProxies) if (!alive(uuid)) return false;
        return true;
    }

    public boolean isLeaderboard(Entity entity) {
        return entity != null && entity.hasMetadata(META_LEADERBOARD);
    }

    public void removeLeaderboard() {
        Location scrubAt = leaderboardPin == null ? null : leaderboardPin.clone();
        for (UUID uuid : new ArrayList<UUID>(leaderboardHolograms)) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        leaderboardHolograms.clear();
        leaderboardLastLines.clear();
        for (UUID uuid : new ArrayList<UUID>(leaderboardProxies)) {
            pins.remove(uuid);
            lookAtPlayers.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        leaderboardProxies.clear();
        scrubOrphanHolograms(scrubAt);
        scrubOrphanPapers(scrubAt);
        scrubReloadedBodies(scrubAt);
        leaderboardPin = null;
    }

    public Entity spawnCosmetics(Location location) {
        Location previous = cosmeticsPin == null ? null : cosmeticsPin.clone();
        removeCosmetics();
        scrubOrphanHolograms(previous);
        scrubOrphanHolograms(location);
        if (location == null || location.getWorld() == null) return null;
        // Kill any reloaded orphan body sitting on the pin before spawning — prevents the duplicate villager.
        scrubReloadedBodies(location);
        // Own packet fake-player first: an ARMOR STAND body hidden via setVisible(false) (no invisibility potion
        // → no 1.8 particle swirls, no fighting hitbox); the fake-player model is the click target via the Netty
        // interceptor. Citizens / plain villager only as fallbacks.
        Entity entity = null;
        if (PacketNpcs.available()) entity = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        if (entity == null) entity = spawnCitizenNamed(location, "Cosmetics", EntityType.VILLAGER);
        if (entity == null) entity = location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        String cosmeticsSkin = plugin.lobby() != null && plugin.lobby().cosmeticsSkin() != null ? plugin.lobby().cosmeticsSkin() : PROFILE_SKIN;
        boolean cosmeticsCape = plugin.lobby() != null && plugin.lobby().cosmeticsCape();
        if (entity != null && !entity.hasMetadata("NPC")) attachPacketModel(entity, cosmeticsSkin, cosmeticsCape, location);
        entity.setMetadata(META_COSMETICS, new FixedMetadataValue(plugin, true));
        hideBodyName(entity);
        freeze(entity, false);
        // Hittable so shift-left-click opens the skin editor (onNpcHit); damage is cancelled in onQueueNpcDamage.
        invokeBoolean(entity, "setInvulnerable", false);
        cosmeticsEntity = entity.getUniqueId();
        cosmeticsPin = location.clone();
        tracked.put(entity.getUniqueId(), entity);
        pins.put(entity.getUniqueId(), location.clone());
        // Placement facing LOCKED (no player-tracking) — see spawn().
        lookAtPlayers.put(entity.getUniqueId(), Boolean.FALSE);
        // Cosmetics height was already fine — only queue holos use LOBBY_NPC_HOLO_TOP.
        cosmeticsHolograms.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 0), 0),
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "COSMETICS").getUniqueId());
        cosmeticsHolograms.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 1), 0),
            ChatColor.YELLOW + "Click to browse!").getUniqueId());
        return entity;
    }

    /** Chunk unload / Citizens remount / villager despawn — recreate body + holograms. */
    public void ensureCosmeticsAlive() {
        if (respawningCosmetics) return;
        // Prefer live pin (unsaved lobby-setup placement); else saved lobby config.
        Location location = cosmeticsPin != null ? cosmeticsPin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().cosmeticsNpc());
        if (location == null || location.getWorld() == null) return;
        if (!chunkLoaded(location)) return;
        if (alive(cosmeticsEntity) && cosmeticsHologramsAlive()) return;
        // Citizens remount rebind before a full respawn — keeps cosmetics holograms from flickering.
        if (rebindCosmeticsBody(location) && cosmeticsHologramsAlive()) return;
        // Minimum interval between full respawns — rapid destroy+respawn cycles cause flicker and duplicate NPCs.
        long now = System.currentTimeMillis();
        if (now - lastCosmeticsSpawnTick < 2000L) return;
        // Rebind failed — destroy the old Citizens NPC so no duplicate accumulates in the registry.
        if (cosmeticsCitizen != null) {
            invoke(cosmeticsCitizen, "destroy");
            cosmeticsCitizen = null;
        }
        respawningCosmetics = true;
        try {
            spawnCosmetics(location);
            // Timestamp only AFTER success: writing it before meant a thrown spawn (failed world.spawnEntity /
            // Citizens reflection) still consumed the 2s window, and spawnCosmetics() nulls cosmeticsEntity/
            // cosmeticsPin up front — so a mid-spawn throw could leave the villager gone until restart. On
            // failure we log (rate-limited) and retry next tick.
            lastCosmeticsSpawnTick = System.currentTimeMillis();
        } catch (Throwable t) {
            warnRespawnFailure("cosmetics", t);
        } finally {
            respawningCosmetics = false;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // A rejoining client never received the old spawn packets — drop them from every shown set so
        // ensureViewers re-shows (else NPCs stay invisible after relog until a full respawn).
        UUID viewer = event.getPlayer().getUniqueId();
        for (PacketNpcs.Model model : packetModels.values()) model.forget(viewer);
        PacketNpcs.clearViewer(viewer); // reconnect gets a fresh channel — re-install the click interceptor
    }

    /**
     * Root cause of "NPC clicks go dead after a game (1.8)": a fake-player model is client-side only, so a
     * lobby → arena → lobby round trip (no quit) leaves the viewer flagged as still-shown even though the
     * client dropped the model on the world change. show() then skips the re-spawn and the model is invisible +
     * unclickable until relog — with no hidden body to fall back on. Forgetting every model on ANY world change
     * makes the next ensureViewers tick re-send the spawn the moment they are back in the lobby.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID viewer = event.getPlayer().getUniqueId();
        for (PacketNpcs.Model model : packetModels.values()) model.forget(viewer);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Location cosmetics = cosmeticsPin != null ? cosmeticsPin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().cosmeticsNpc());
        if (cosmetics != null && cosmetics.getWorld() != null
            && event.getWorld().equals(cosmetics.getWorld()) && sameChunk(event.getChunk(), cosmetics)) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { if (!respawningCosmetics) ensureCosmeticsAlive(); }
            });
        }
        Location profile = profilePin != null ? profilePin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().profileNpc());
        if (profile != null && profile.getWorld() != null
            && event.getWorld().equals(profile.getWorld()) && sameChunk(event.getChunk(), profile)) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { if (!respawningProfile) ensureProfileAlive(); }
            });
        }
        Location leaderboard = leaderboardPin != null ? leaderboardPin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().leaderboardNpc());
        if (leaderboard != null && leaderboard.getWorld() != null
            && event.getWorld().equals(leaderboard.getWorld()) && sameChunk(event.getChunk(), leaderboard)) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { if (!respawningLeaderboard) ensureLeaderboardAlive(); }
            });
        }
    }

    public boolean isCosmetics(Entity entity) {
        return entity != null && entity.hasMetadata(META_COSMETICS);
    }

    public Entity spawn(GameType mode, LobbySettings.NpcSettings settings) {
        remove(mode);
        Location location = settings.location();
        if (location == null || location.getWorld() == null) return null;
        // Kill any reloaded orphan body sitting on the pin before spawning — prevents duplicate queue NPCs.
        scrubReloadedBodies(location);
        // Fake Player mode (settings.human): the body is an ARMOR STAND hidden via setVisible(false) — no
        // invisibility potion (its particles show on 1.8 and its hitbox fought clicks) — and a packet player model
        // renders on top; clicks land on the model via the Netty interceptor. Mob mode: the configured entity
        // shows as a real mob, no packet overlay. Skin: configured, else the shared profile skin.
        String skin = settings.skin() != null && !settings.skin().isEmpty() ? settings.skin() : PROFILE_SKIN;
        Entity entity = null;
        if (PacketNpcs.available()) entity = location.getWorld().spawnEntity(location,
            settings.human() ? EntityType.ARMOR_STAND : settings.entityType());
        if (entity == null) entity = spawnCitizen(mode, settings);
        if (entity == null) {
            if (settings.human()) entity = spawnHumanStand(location, settings.skin());
            if (entity == null) entity = location.getWorld().spawnEntity(location, settings.entityType());
        }
        if (entity != null && !entity.hasMetadata("NPC") && settings.human()) {
            attachPacketModel(entity, skin, settings.cape(), location);
        }
        entity.setMetadata(META_MODE, new FixedMetadataValue(plugin, mode.name()));
        // Holograms carry the label; hide vanilla nametag (same as shop villagers).
        hideBodyName(entity);
        freeze(entity, settings.baby());
        // Queue NPC bodies stay HITTABLE (freeze() made them invulnerable) so a left-click fires
        // EntityDamageByEntityEvent → onNpcHit opens the queue / admin editor. They can't actually be
        // hurt: onNpcHit cancels player attacks and onQueueNpcDamage cancels every other damage cause.
        invokeBoolean(entity, "setInvulnerable", false);
        entities.put(mode, entity.getUniqueId());
        tracked.put(entity.getUniqueId(), entity);
        pins.put(entity.getUniqueId(), location.clone());
        // Honor the "Look at Players" toggle. When OFF the NPC holds its placed yaw/pitch; when ON it tracks the
        // nearest player (pinEntities → faceNearestPlayerInRange → applyLook + PacketNpcs.look). The old "faces
        // backward" bug was the yaw convention, since fixed — so the toggle is safe to respect again.
        lookAtPlayers.put(entity.getUniqueId(), settings.lookAtPlayers());
        spawnQueueHolograms(mode, location);
        return entity;
    }

    private void spawnQueueHolograms(GameType mode, Location location) {
        removeHolograms(mode);
        scrubOrphanHolograms(location);
        List<UUID> ids = new ArrayList<UUID>();
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 0), 0), queueTitle(mode)).getUniqueId());
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 1), 0), ChatColor.YELLOW + "Click to play!").getUniqueId());
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 2), 0), ChatColor.GRAY + (mode.teamSize() + " per team")).getUniqueId());
        holograms.put(mode, ids);
    }

    /** SOLO/DOUBLES titles kept byte-identical to the historical labels; new modes use their display name. */
    private static String queueTitle(GameType mode) {
        switch (mode) {
            case SOLO: return ChatColor.AQUA + "" + ChatColor.BOLD + "SOLO QUEUE";
            case DOUBLES: return ChatColor.GOLD + "" + ChatColor.BOLD + "DOUBLES QUEUE";
            case TRIOS: return ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "3v3v3v3 QUEUE";
            case QUADS: return ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "4v4v4v4 QUEUE";
            default: return ChatColor.GOLD + "" + ChatColor.BOLD + mode.displayName().toUpperCase() + " QUEUE";
        }
    }

    private ArmorStand hologram(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        // Tag META_HOLO BEFORE prepareArmorStand()→freeze() so freeze's setPersistent(false) exemption applies —
        // holograms are marker stands that must survive chunk reloads (else they flicker/vanish on reload).
        stand.setMetadata(META_HOLO, new FixedMetadataValue(plugin, true));
        prepareArmorStand(stand, true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        pins.put(stand.getUniqueId(), location.clone());
        tracked.put(stand.getUniqueId(), stand);
        return stand;
    }

    private void removeHolograms(GameType mode) {
        List<UUID> ids = holograms.remove(mode);
        if (ids == null) return;
        for (UUID uuid : ids) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
        }
    }

    private void updateHologramVisibility() {
        double limit = GameRules.DISPLAY_VIEW * GameRules.DISPLAY_VIEW;
        List<List<UUID>> groups = new ArrayList<List<UUID>>();
        groups.addAll(holograms.values());
        if (!cosmeticsHolograms.isEmpty()) groups.add(cosmeticsHolograms);
        if (!profileHolograms.isEmpty()) groups.add(profileHolograms);
        if (!leaderboardHolograms.isEmpty()) groups.add(leaderboardHolograms);
        for (List<UUID> ids : groups) {
            for (UUID uuid : ids) {
                Entity entity = find(uuid);
                Location pin = pins.get(uuid);
                if (entity == null || pin == null || pin.getWorld() == null) continue;
                boolean anyNear = false;
                for (Player player : pin.getWorld().getPlayers()) {
                    boolean near = player.getLocation().distanceSquared(pin) <= limit;
                    if (near && !EntityVisibility.isSpectator(player)) anyNear = true;
                    EntityVisibility.apply(plugin, player, entity, near);
                }
                // 1.8 fallback when packets/hideEntity unavailable: at least drop the nametag
                if (entity instanceof ArmorStand) entity.setCustomNameVisible(anyNear);
            }
        }
        List<UUID> bodyIds = new ArrayList<UUID>(entities.values());
        if (cosmeticsEntity != null) bodyIds.add(cosmeticsEntity);
        if (profileEntity != null) bodyIds.add(profileEntity);
        bodyIds.addAll(leaderboardProxies);
        for (UUID uuid : bodyIds) {
            Entity entity = find(uuid);
            Location pin = pins.get(uuid);
            if (entity == null || pin == null || pin.getWorld() == null) continue;
            boolean anyNear = false;
            for (Player player : pin.getWorld().getPlayers()) {
                boolean near = player.getLocation().distanceSquared(pin) <= limit;
                // The body is an invisible click hitbox (villager) under the packet player model. Potion
                // invisibility is see-through for SPECTATORS, so they'd see the raw villager. Spectators can't
                // right-click anyway, so packet-destroy the body for them (re-spawned when they leave spectator).
                if (EntityVisibility.isSpectator(player)) {
                    EntityVisibility.hide(plugin, player, entity);
                    continue;
                }
                if (near) anyNear = true;
                EntityVisibility.apply(plugin, player, entity, near);
            }
            entity.setCustomNameVisible(false);
        }
    }

    /** Shop / lobby / hologram / gen displays — never play ambient/hurt/death sounds. */
    public static boolean isPluginNpc(Entity entity) {
        if (entity == null) return false;
        return entity.hasMetadata(META_MODE) || entity.hasMetadata(META_HOLO) || entity.hasMetadata(META_SILENT)
            || entity.hasMetadata(META_COSMETICS) || entity.hasMetadata(META_PROFILE)
            || entity.hasMetadata(META_LEADERBOARD)
            || entity.hasMetadata(META_PET)
            || entity.hasMetadata("bedlamShop")
            || entity.hasMetadata("bedlamGeneratorDisplay") || entity.hasMetadata("bedlamHologram");
    }

    public static boolean isPet(Entity entity) {
        return entity != null && entity.hasMetadata(META_PET);
    }

    public GameType mode(Entity entity) {
        if (!entity.hasMetadata(META_MODE) || entity.getMetadata(META_MODE).isEmpty()) return null;
        return GameType.parse(entity.getMetadata(META_MODE).get(0).asString());
    }

    public EntityType next(EntityType current, int direction) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i] == current) return TYPES[(i + direction + TYPES.length) % TYPES.length];
        return TYPES[0];
    }

    public void removeAll() {
        removeProfile();
        removeLeaderboard();
        removeCosmetics();
        for (GameType type : GameType.values()) remove(type);
        for (Entity entity : new ArrayList<Entity>(tracked.values())) {
            if (entity != null) entity.remove();
        }
        for (PacketNpcs.Model model : packetModels.values()) PacketNpcs.destroy(model);
        packetModels.clear();
        pins.clear();
        lookAtPlayers.clear();
        holograms.clear();
        tracked.clear();
        cosmeticsPin = null;
        profilePin = null;
    }

    public void removeCosmetics() {
        Location scrubAt = cosmeticsPin == null ? null : cosmeticsPin.clone();
        if (cosmeticsCitizen != null) {
            invoke(cosmeticsCitizen, "destroy");
            cosmeticsCitizen = null;
        }
        for (UUID uuid : new ArrayList<UUID>(cosmeticsHolograms)) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        cosmeticsHolograms.clear();
        if (cosmeticsEntity != null) {
            pins.remove(cosmeticsEntity);
            lookAtPlayers.remove(cosmeticsEntity);
            detachPacketModel(cosmeticsEntity);
            Entity entity = tracked.remove(cosmeticsEntity);
            if (entity != null) entity.remove();
            else removeWorldEntity(cosmeticsEntity, scrubAt);
            cosmeticsEntity = null;
        }
        scrubOrphanHolograms(scrubAt);
        // Kill orphan body sitting on the old pin — prevents the duplicate cosmetics NPC
        // when relocating.
        scrubReloadedBodies(scrubAt);
        cosmeticsPin = null;
    }

    public void remove(GameType type) {
        removeHolograms(type);
        Object npc = citizens.remove(type);
        if (npc != null) invoke(npc, "destroy");
        UUID uuid = entities.remove(type);
        if (uuid == null) return;
        pins.remove(uuid);
        lookAtPlayers.remove(uuid);
        detachPacketModel(uuid);
        Entity entity = tracked.remove(uuid);
        if (entity != null) entity.remove();
    }

    /** Drop floating nametags left after NPC despawn / relocate. Nearby only — never world.getEntities. */
    public void scrubOrphanHolograms(Location around) {
        if (around == null || around.getWorld() == null) return;
        double r = HOLO_SCRUB_RADIUS;
        Location focus = around.clone().add(0, 1.5, 0);
        for (Entity entity : around.getWorld().getNearbyEntities(focus, r, r + 2.0, r)) {
            if (!(entity instanceof ArmorStand)) continue;
            // Live holograms we currently own must NEVER be scrubbed by a neighbouring NPC's respawn. Lobby pins
            // sit ~2 blocks apart — well inside HOLO_SCRUB_RADIUS — so scrubbing tracked neighbours deleted their
            // holograms, which made those NPCs respawn, which scrubbed back: a respawn cascade (the NPC flicker).
            // Only untracked orphans (reloaded-from-disk holos / leaked nametags) are removed here.
            if (tracked.containsKey(entity.getUniqueId())) continue;
            if (!entity.hasMetadata(META_HOLO)) {
                // Reloaded orphan: metadata is runtime-only and lost on world save, so a META_HOLO check misses
                // holograms that reloaded from disk. Match the hologram shape instead — invisible marker stand
                // carrying a custom name. Real visible/decorative stands are untouched.
                ArmorStand stand = (ArmorStand) entity;
                if (stand.isVisible()) continue;
                String customName = stand.getCustomName();
                if (customName == null || customName.trim().isEmpty()) continue;
            }
            pins.remove(entity.getUniqueId());
            tracked.remove(entity.getUniqueId());
            cosmeticsHolograms.remove(entity.getUniqueId());
            profileHolograms.remove(entity.getUniqueId());
            leaderboardHolograms.remove(entity.getUniqueId());
            leaderboardProxies.remove(entity.getUniqueId());
            for (List<UUID> ids : holograms.values()) ids.remove(entity.getUniqueId());
            entity.remove();
        }
    }

    /** Drop leaked paper items (NPC hand re-equip / remount) around a pin. */
    public void scrubOrphanPapers(Location around) {
        if (around == null || around.getWorld() == null) return;
        double r = HOLO_SCRUB_RADIUS;
        for (Entity entity : around.getWorld().getNearbyEntities(around, r, r, r)) {
            if (!(entity instanceof org.bukkit.entity.Item)) continue;
            ItemStack stack = ((org.bukkit.entity.Item) entity).getItemStack();
            if (stack != null && stack.getType() == Material.PAPER) entity.remove();
        }
    }

    /** Pure helper for checks: true when body/holos need a respawn. */
    public static boolean needsCosmeticsRespawn(boolean bodyAlive, boolean holosAlive) {
        return !bodyAlive || !holosAlive;
    }

    public static int profileHologramLineCount() {
        return PROFILE_HOLO_LINES;
    }

    public static boolean sameChunk(Chunk chunk, Location location) {
        if (chunk == null || location == null) return false;
        return chunk.getX() == (location.getBlockX() >> 4) && chunk.getZ() == (location.getBlockZ() >> 4);
    }

    /** Cosmetics NPC via Citizens soft-dep; null falls back to the built-in villager. */
    private Entity spawnCitizenNamed(Location location, String name, EntityType type) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            // Destroy any existing registry NPC already sitting on this pin before creating a new one — otherwise
            // a stale entity ref plus a fresh createNPC leaves two Citizens NPCs stacked (the duplicate villager).
            try {
                for (Object existing : (Iterable<?>) citizensRegistry.getClass().getMethod("getNPCs").invoke(citizensRegistry)) {
                    Entity e = (Entity) existing.getClass().getMethod("getEntity").invoke(existing);
                    if (e != null && e.isValid() && e.getLocation().distanceSquared(location) < 4.0) {
                        existing.getClass().getMethod("destroy").invoke(existing);
                    }
                }
            } catch (Throwable ignored) { }
            // Blank Citizens name — holograms own the label; vanilla/Citizens nametag stays off.
            Object npc = citizensRegistry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(citizensRegistry, type, " ");
            citizensDisableLookAi(npc);
            citizensSetLookClose(npc, true); // cosmetics faces the player — smooth via Citizens LookClose
            invokeBoolean(npc, "setProtected", true);
            citizensSilent(npc);
            citizensHideNameplate(npc);
            npc.getClass().getMethod("spawn", Location.class).invoke(npc, location);
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            mute(entity);
            hideBodyName(entity);
            cosmeticsCitizen = npc;
            return entity;
        } catch (Exception exception) {
            plugin.getLogger().warning("Citizens cosmetics NPC failed; using villager: " + exception.getMessage());
            return null;
        }
    }

    /** Profile NPC via Citizens soft-dep (fixed-skin player); null falls back to the head-showing armor stand. */
    private Entity spawnCitizenProfile(Location location) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            // Destroy any registry NPC already on this pin so we never stack duplicate profile bodies.
            try {
                for (Object existing : (Iterable<?>) citizensRegistry.getClass().getMethod("getNPCs").invoke(citizensRegistry)) {
                    Entity e = (Entity) existing.getClass().getMethod("getEntity").invoke(existing);
                    if (e != null && e.isValid() && e.getLocation().distanceSquared(location) < 4.0) {
                        existing.getClass().getMethod("destroy").invoke(existing);
                    }
                }
            } catch (Throwable ignored) { }
            Object npc = citizensRegistry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(citizensRegistry, EntityType.PLAYER, " ");
            try {
                Class<?> skinTrait = Class.forName("net.citizensnpcs.trait.SkinTrait");
                Object trait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTrait);
                trait.getClass().getMethod("setSkinName", String.class).invoke(trait, PROFILE_SKIN);
            } catch (Throwable ignored) { }
            citizensDisableLookAi(npc);
            citizensSetLookClose(npc, true); // profile faces the nearest player
            invokeBoolean(npc, "setProtected", true);
            citizensSilent(npc);
            citizensHideNameplate(npc);
            npc.getClass().getMethod("spawn", Location.class).invoke(npc, location);
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            mute(entity);
            hideBodyName(entity);
            profileCitizen = npc;
            return entity;
        } catch (Exception exception) {
            plugin.getLogger().warning("Citizens profile NPC failed; using armor stand: " + exception.getMessage());
            return null;
        }
    }

    /** Queue NPC via Citizens soft-dep (player + skin when human); null falls back to armor stand / mob. */
    private Entity spawnCitizen(GameType mode, LobbySettings.NpcSettings settings) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            Object registry = citizensRegistry;
            EntityType npcType = settings.human() ? EntityType.PLAYER : settings.entityType();
            Object npc = registry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(registry, npcType, " ");
            if (settings.human() && settings.skin() != null) {
                Class<?> skinTrait = Class.forName("net.citizensnpcs.trait.SkinTrait");
                Object trait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTrait);
                if (settings.skin().matches("[A-Za-z0-9_]{1,16}")) {
                    trait.getClass().getMethod("setSkinName", String.class).invoke(trait, settings.skin());
                } else {
                    String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + settings.skin() + "\"}}}";
                    String texture = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                    trait.getClass().getMethod("setSkinPersistent", String.class, String.class, String.class)
                        .invoke(trait, UUID.randomUUID().toString(), null, texture);
                }
            }
            citizensDisableLookAi(npc);
            citizensSetLookClose(npc, settings.lookAtPlayers()); // static unless this NPC is configured to face players
            invokeBoolean(npc, "setProtected", true);
            citizensSilent(npc);
            citizensHideNameplate(npc);
            npc.getClass().getMethod("spawn", Location.class).invoke(npc, settings.location());
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            mute(entity);
            hideBodyName(entity);
            citizens.put(mode, npc);
            return entity;
        } catch (Exception exception) {
            plugin.getLogger().warning("Citizens player NPC failed; using armor stand: " + exception.getMessage());
            return null;
        }
    }

    private static ArmorStand spawnHumanStand(Location location, String skin) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setSmall(false);
        stand.getEquipment().setHelmet(Skins.head(skin));
        return stand;
    }

    private void pinEntities() {
        boolean remute = (++muteTick % 20 == 0);
        // Orphan-model sweep: a model whose body entity no longer exists (relocated/despawned/killed body)
        // must be destroyed — otherwise the old Steve keeps rendering at its last spot forever (the
        // "old NPC stays" duplicate). Checks BOTH the pin map AND actual entity validity.
        for (UUID orphan : new ArrayList<UUID>(packetModels.keySet())) {
            if (!pins.containsKey(orphan) || !alive(find(orphan))) detachPacketModel(orphan);
        }
        for (Map.Entry<UUID, Location> entry : new HashMap<UUID, Location>(pins).entrySet()) {
            Entity entity = find(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                // Cosmetics / queue NPCs: keep pin; ensure*Alive respawns. Don't orphan holograms by dropping pin.
                if (isManagedLobbyId(entry.getKey())) continue;
                pins.remove(entry.getKey());
                lookAtPlayers.remove(entry.getKey());
                tracked.remove(entry.getKey());
                lastLook.remove(entry.getKey());
                continue;
            }
            if (entity.getVelocity().lengthSquared() > 0.0001) entity.setVelocity(new Vector(0, 0, 0));
            // Pin yaw/pitch is placement facing — never overwrite with look-at (that caused snap jitter).
            Location pinned = entry.getValue().clone();
            float yaw = pinned.getYaw();
            float pitch = pinned.getPitch();
            boolean wantLook = Boolean.TRUE.equals(lookAtPlayers.get(entry.getKey()));
            // Citizens NPCs get smooth rotation from their own LookClose trait; driving setRotation here on top
            // of that is what made cosmetics/profile jitter. Only steer our own (non-Citizens) fallback bodies.
            boolean citizensNpc = entity.hasMetadata("NPC");
            if (wantLook && !citizensNpc) {
                float[] look = faceNearestPlayerInRange(entity, pinned);
                if (look != null) {
                    yaw = look[0];
                    pitch = look[1];
                }
            }
            pinned.setYaw(yaw);
            pinned.setPitch(pitch);
            // Look-at fallback bodies rotate in place only — teleporting to change facing resets client
            // interpolation and shows as a snap. Only teleport Citizens NPCs / non-look bodies for position drift.
            if (citizensNpc || !wantLook) {
                if (needsTeleport(entity.getLocation(), pinned)) entity.teleport(pinned);
            }
            // Only push a rotation packet when yaw/pitch actually changed — 20 identical packets/sec was the jitter.
            PacketNpcs.Model model = packetModels.get(entry.getKey());
            if (wantLook && !citizensNpc) {
                float[] prev = lastLook.get(entry.getKey());
                if (prev == null || Math.abs(yaw - prev[0]) > 0.5f || Math.abs(pitch - prev[1]) > 0.5f) {
                    applyLook(entity, yaw, pitch);
                    lastLook.put(entry.getKey(), new float[]{yaw, pitch});
                    if (model != null) PacketNpcs.look(model, yaw, pitch);
                }
            }
            // Packet player-model: re-show to viewers who lost it (join/respawn/chunk reload), and retry the
            // attach once an async skin fetch lands.
            if (model == null) {
                String pendingSkin = packetSkinKeys.get(entry.getKey());
                boolean pendingCape = Boolean.TRUE.equals(packetCapes.get(entry.getKey()));
                if (pendingSkin != null && PacketNpcs.cachedProfile(pendingSkin, pendingCape) != null) {
                    attachPacketModel(entity, pendingSkin, pendingCape, entry.getValue());
                    model = packetModels.get(entry.getKey());
                }
            }
            if (model != null && (visibilityTick % 20) == 0) PacketNpcs.ensureViewers(model, 48.0);
            // Citizens remount clears NMS silent / nameplate. Never remute holograms — hideBodyName
            // + givePaper on META_PROFILE holos caused 1Hz nametag/paper flicker. setAI(false) is applied once at
            // spawn (freeze) — repeating it here reset the entity's navigation and nudged its position (jitter).
            if (remute && !entity.hasMetadata(META_HOLO)
                && (entity.hasMetadata(META_MODE) || entity.hasMetadata(META_COSMETICS)
                || entity.hasMetadata(META_PROFILE) || entity.hasMetadata(META_LEADERBOARD))) {
                mute(entity);
                hideBodyName(entity);
            }
        }
    }

    private void ensureQueueNpcsAlive() {
        if (plugin.lobby() == null) return;
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            Location location = settings.location();
            if (location == null || location.getWorld() == null || !chunkLoaded(location)) continue;
            UUID bodyId = entities.get(type);
            if (alive(bodyId) && queueHologramsAlive(type)) continue;
            // Citizens may remount its backing entity (new UUID) without our holograms dying. Rebind the body
            // instead of spawn(), which would destroy+respawn the holograms and make them flicker/vanish.
            if (rebindQueueBody(type, location) && queueHologramsAlive(type)) continue;
            // Grace period: covers Paper chunk-load lag where holograms briefly read as dead. Don't churn a
            // destroy+respawn within 2s of the last successful spawn.
            Long last = queueLastSpawn.get(type);
            if (last != null && System.currentTimeMillis() - last < 2000L) continue;
            try {
                spawn(type, settings);
                queueLastSpawn.put(type, System.currentTimeMillis());
            } catch (Throwable t) {
                // A thrown spawn must not abort the whole per-tick runnable (pin/visibility work) nor wedge the
                // NPC gone until restart — log rate-limited and retry next tick.
                warnRespawnFailure("queue " + type.name(), t);
            }
        }
    }

    private boolean rebindQueueBody(GameType type, Location location) {
        Object npc = citizens.get(type);
        if (npc == null) return false;
        try {
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            if (!alive(entity)) return false;
            UUID current = entities.get(type);
            if (entity.getUniqueId().equals(current)) return true;
            if (current != null) { pins.remove(current); lookAtPlayers.remove(current); tracked.remove(current); }
            UUID id = entity.getUniqueId();
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            entity.setMetadata(META_MODE, new FixedMetadataValue(plugin, type.name()));
            hideBodyName(entity);
            freeze(entity, settings.baby());
            entities.put(type, id);
            tracked.put(id, entity);
            pins.put(id, location.clone());
            lookAtPlayers.put(id, settings.lookAtPlayers());
            return true;
        } catch (Exception ignored) { return false; }
    }

    private boolean rebindCosmeticsBody(Location location) {
        if (cosmeticsCitizen == null) return false;
        try {
            Entity entity = (Entity) cosmeticsCitizen.getClass().getMethod("getEntity").invoke(cosmeticsCitizen);
            if (!alive(entity)) return false;
            if (entity.getUniqueId().equals(cosmeticsEntity)) return true;
            if (cosmeticsEntity != null) { pins.remove(cosmeticsEntity); lookAtPlayers.remove(cosmeticsEntity); tracked.remove(cosmeticsEntity); }
            UUID id = entity.getUniqueId();
            entity.setMetadata(META_COSMETICS, new FixedMetadataValue(plugin, true));
            hideBodyName(entity);
            freeze(entity, false);
            cosmeticsEntity = id;
            tracked.put(id, entity);
            pins.put(id, location.clone());
            lookAtPlayers.put(id, Boolean.TRUE);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private boolean isManagedLobbyId(UUID uuid) {
        if (uuid == null) return false;
        if (uuid.equals(cosmeticsEntity) || cosmeticsHolograms.contains(uuid)) return true;
        if (uuid.equals(profileEntity) || profileHolograms.contains(uuid)) return true;
        if (leaderboardProxies.contains(uuid) || leaderboardHolograms.contains(uuid)) return true;
        for (UUID id : entities.values()) if (uuid.equals(id)) return true;
        for (List<UUID> ids : holograms.values()) if (ids.contains(uuid)) return true;
        return false;
    }

    private boolean cosmeticsHologramsAlive() {
        if (cosmeticsHolograms.size() < 2) return false;
        for (UUID uuid : cosmeticsHolograms) if (!alive(uuid)) return false;
        return true;
    }

    private boolean profileHologramsAlive() {
        if (profileHolograms.size() < PROFILE_HOLO_LINES) return false;
        for (UUID uuid : profileHolograms) if (!alive(uuid)) return false;
        return true;
    }

    private boolean queueHologramsAlive(GameType type) {
        List<UUID> ids = holograms.get(type);
        if (ids == null || ids.size() < 3) return false;
        for (UUID uuid : ids) if (!alive(uuid)) return false;
        return true;
    }

    private boolean alive(UUID uuid) {
        return alive(find(uuid));
    }

    private static boolean alive(Entity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private static boolean chunkLoaded(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Remove by tracked ref or tiny nearby box only.
     * Never CraftChunk.getEntities / world.getEntities — freezes Paper 26.x (watchdog).
     */
    private void removeWorldEntity(UUID uuid, Location near) {
        if (uuid == null) return;
        Entity known = tracked.remove(uuid);
        if (known != null) {
            known.remove();
            return;
        }
        if (near == null || near.getWorld() == null || !chunkLoaded(near)) return;
        double r = HOLO_SCRUB_RADIUS;
        for (Entity entity : near.getWorld().getNearbyEntities(near, r, r + 4.0, r)) {
            if (uuid.equals(entity.getUniqueId())) {
                entity.remove();
                return;
            }
        }
    }

    public static void tagSilent(Entity entity) {
        if (entity == null) return;
        SILENT_ENTITIES.put(entity.getUniqueId(), entity);
        if (entity.hasMetadata(META_SILENT)) return;
        org.bukkit.plugin.Plugin owner = Bukkit.getPluginManager().getPlugin("BedlamCore");
        if (owner != null) entity.setMetadata(META_SILENT, new FixedMetadataValue(owner, true));
    }

    public static void mute(Entity entity) {
        tagSilent(entity);
        NpcSoundListener.silence(entity);
    }

    public static void remuteTaggedEntities() {
        for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(SILENT_ENTITIES).entrySet()) {
            Entity entity = entry.getValue();
            if (entity == null || entity.isDead() || !entity.isValid()) SILENT_ENTITIES.remove(entry.getKey());
            else NpcSoundListener.silence(entity);
        }
    }

    /** Citizens soft-dep: NPC.SILENT_METADATA so the registry keeps the entity muted across remounts. */
    private static void citizensSilent(Object npc) {
        citizensData(npc, "SILENT_METADATA", Boolean.TRUE);
    }

    private static void citizensHideNameplate(Object npc) {
        citizensData(npc, "NAMEPLATE_VISIBLE_METADATA", Boolean.FALSE);
    }

    private static void citizensData(Object npc, String field, Object value) {
        try {
            Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
            Object key = npcClass.getField(field).get(null);
            Object data = npc.getClass().getMethod("data").invoke(npc);
            for (Method method : data.getClass().getMethods()) {
                if (!method.getName().equals("set") || method.getParameterTypes().length != 2) continue;
                method.invoke(data, key, value);
                return;
            }
        } catch (Throwable ignored) { }
    }

    /** Kill LookClose + Minecraft AI so our applyLook is the only rotation source. */
    private static void citizensDisableLookAi(Object npc) {
        try {
            Class<?> lookClose = Class.forName("net.citizensnpcs.trait.LookClose");
            Object look = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, lookClose);
            look.getClass().getMethod("lookClose", boolean.class).invoke(look, false);
        } catch (Throwable ignored) { }
        try {
            npc.getClass().getMethod("setUseMinecraftAI", boolean.class).invoke(npc, false);
        } catch (Throwable ignored) { }
        try {
            Object nav = npc.getClass().getMethod("getNavigator").invoke(npc);
            nav.getClass().getMethod("cancelNavigation").invoke(nav);
        } catch (Throwable ignored) { }
    }

    /** Let Citizens smoothly rotate the NPC toward nearby players (its LookClose is interpolated; ours snapped). */
    private static void citizensSetLookClose(Object npc, boolean enabled) {
        try {
            Class<?> lookClose = Class.forName("net.citizensnpcs.trait.LookClose");
            Object look = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, lookClose);
            look.getClass().getMethod("lookClose", boolean.class).invoke(look, enabled);
            if (enabled) { try { look.getClass().getMethod("setRange", int.class).invoke(look, (int) LOOK_RANGE); } catch (Throwable ignored) { } }
        } catch (Throwable ignored) { }
    }

    private static void invoke(Object target, String methodName) {
        try { target.getClass().getMethod(methodName).invoke(target); }
        catch (Exception ignored) { }
    }

    private static void hideBodyName(Entity entity) {
        if (entity == null) return;
        entity.setCustomName(" ");
        entity.setCustomNameVisible(false);
    }

    /**
     * Aim NPC eyes at player eyes; pitch clamped to kill up/down teak.
     * Pure helper so GameRulesCheck can assert clamp without a world.
     */
    public static float[] lookYawPitch(Vector fromEye, Vector toEye) {
        Vector direction = toEye.clone().subtract(fromEye);
        if (direction.lengthSquared() < 1.0E-8) return new float[]{0f, 0f};
        direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, direction.getY()))));
        if (pitch > LOOK_PITCH_CLAMP) pitch = LOOK_PITCH_CLAMP;
        else if (pitch < -LOOK_PITCH_CLAMP) pitch = -LOOK_PITCH_CLAMP;
        return new float[]{yaw, pitch};
    }

    /**
     * Nearest player within {@link #LOOK_RANGE}, or null to keep placement facing.
     * Pure enough for GameRulesCheck via {@link #inLookRange(double)}.
     */
    public static float[] faceNearestPlayerInRange(Entity entity, Location location) {
        return faceNearestPlayerInRange(entity, location, null);
    }

    /** Nearest player within {@link #LOOK_RANGE}, or null to keep placement facing. Never tracks spectators or
     *  dead players; {@code ignore} additionally skips arena soft-spectators / respawning / eliminated players so
     *  in-match shop NPCs only turn toward players who are actually alive and in play. */
    public static float[] faceNearestPlayerInRange(Entity entity, Location location, Set<UUID> ignore) {
        PlayerTarget nearest = null;
        for (org.bukkit.entity.Player player : entity.getWorld().getPlayers()) {
            if (player.equals(entity)) continue;
            if (player.isDead() || EntityVisibility.isSpectator(player)) continue;
            if (ignore != null && ignore.contains(player.getUniqueId())) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (!inLookRange(distance)) continue;
            if (nearest == null || distance < nearest.distance) nearest = new PlayerTarget(player.getEyeLocation(), distance);
        }
        if (nearest == null) return null;
        double eyeY = location.getY() + (entity instanceof LivingEntity ? ((LivingEntity) entity).getEyeHeight() : 1.62);
        return lookYawPitch(new Vector(location.getX(), eyeY, location.getZ()), nearest.location.toVector());
    }

    public static boolean inLookRange(double distanceSquared) {
        return distanceSquared <= LOOK_RANGE_SQ;
    }

    /**
     * Smoothly face the nearest player within {@link #LOOK_RANGE} from a fixed pin. Shared with match shop
     * villagers so they track the player like Hypixel instead of the jerky vanilla head-look AI. Forces AI off
     * each call so the mob's own look goal cannot fight our per-tick rotation (that fight is the "head twitch").
     */
    public static void lookAtNearestPlayer(Entity entity, Location pin) {
        if (entity == null || pin == null) return;
        invokeBoolean(entity, "setAI", false);
        float[] look = faceNearestPlayerInRange(entity, pin);
        if (look != null) applyLook(entity, look[0], look[1]);
    }

    /** Body + head yaw/pitch together (teleport alone leaves 1.8 NPC necks twisted). */
    private static void applyLook(Entity entity, float yaw, float pitch) {
        if (entity == null) return;
        try {
            entity.getClass().getMethod("setRotation", float.class, float.class).invoke(entity, yaw, pitch);
            // setRotation handles body + head on 1.9+. Running the ArmorStand setHeadPose block afterwards would
            // overwrite the rotation it just set (a visible snap), so return immediately on modern versions.
            return;
        } catch (Throwable ignored) { }
        if (entity instanceof ArmorStand) {
            ((ArmorStand) entity).setHeadPose(new org.bukkit.util.EulerAngle(
                Math.toRadians(pitch), 0, 0));
            ((ArmorStand) entity).setBodyPose(new org.bukkit.util.EulerAngle(0, 0, 0));
        }
        // The raw field pokes below are a 1.8-only fallback: those single-letter names (aI/aJ/aK/aL) are 1.8
        // mappings and hit UNRELATED fields on newer Paper, corrupting entity state — that was the look-at jitter.
        // Only touch NMS when setRotation isn't available (1.8).
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            setNmsFloat(handle, "yaw", yaw);
            setNmsFloat(handle, "pitch", pitch);
            // EntityLiving 1.8: aI body, aK head — set both so mobs don't keep body forward / head twitch.
            Class<?> clazz = handle.getClass();
            while (clazz != null) {
                setNmsFloat(clazz, handle, "aI", yaw);
                setNmsFloat(clazz, handle, "aJ", yaw);
                setNmsFloat(clazz, handle, "aK", yaw);
                setNmsFloat(clazz, handle, "aL", yaw);
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) { }
    }

    private static void setNmsFloat(Object handle, String name, float value) {
        setNmsFloat(handle.getClass(), handle, name, value);
    }

    private static void setNmsFloat(Class<?> clazz, Object handle, String name, float value) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            field.setFloat(handle, value);
        } catch (Throwable ignored) { }
    }

    /** Invisible marker stand: empty gear, no body; spectators still need EntityVisibility.hide. */
    public static void prepareArmorStand(ArmorStand stand, boolean small) {
        stand.setVisible(false);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setArms(false);
        stand.setSmall(small);
        invokeBoolean(stand, "setMarker", true);
        EntityEquipment gear = stand.getEquipment();
        if (gear != null) {
            gear.setHelmet(null);
            gear.setChestplate(null);
            gear.setLeggings(null);
            gear.setBoots(null);
            gear.setItemInHand(null);
        }
        freeze(stand, false);
    }

    public static void freeze(Entity entity, boolean baby) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) entity;
        living.setRemoveWhenFarAway(false);
        // Never write our own world-spawned bodies / holograms to the lobby world save: on restart they reload
        // WITHOUT their runtime metadata and pile up beside freshly spawned ones (stale armor stands / old
        // holograms). The ensure*Alive loops respawn any a chunk unload drops, so non-persistence is free here.
        // Skip Citizens NPCs: they keep an in-memory registry (never world-saved). Marking a Citizens entity
        // non-persistent makes Paper drop + Citizens respawn it every tick — that churn was the profile flicker.
        // Holograms (META_HOLO) must survive chunk reload: they are invisible marker stands carrying no runtime
        // state that would cause a stale-data bug, and setPersistent(false) makes Paper drop them on chunk unload
        // → visible respawn flicker. Only NPC bodies (villager/mob/player model) get non-persistence.
        if (!living.hasMetadata("NPC") && !living.hasMetadata(META_HOLO)) invokeBoolean(living, "setPersistent", false);
        // The SLOW 255 freeze causes visual head-tilt on 1.12.2+ where potion rendering differs; setAI/gravity/
        // invulnerable below already immobilise the body there. Only apply SLOW on legacy (no setRotation = 1.8).
        boolean modern = true;
        try { Entity.class.getMethod("setRotation", float.class, float.class); }
        catch (NoSuchMethodException e) { modern = false; }
        // Armor-stand bodies never move (no AI/navigation), so SLOW is pointless there AND the 1.8 client renders
        // its particles — the exact swirl the invisible packet-NPC body must not show. Skip it for armor stands.
        if (!modern && !(living instanceof ArmorStand)) living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), true);
        invokeBoolean(living, "setAI", false);
        invokeBoolean(living, "setGravity", false);
        mute(living);
        invokeBoolean(living, "setInvulnerable", true);
        invokeBoolean(living, "setCollidable", false);
        if (living instanceof Ageable) { if (baby) ((Ageable) living).setBaby(); else ((Ageable) living).setAdult(); }
        if (living instanceof Zombie) ((Zombie) living).setBaby(baby);
    }

    /** Tracked UUID map only — chunk entity scans hang modern Paper. */
    private Entity find(UUID uuid) {
        Entity entity = tracked.get(uuid);
        if (alive(entity)) return entity;
        if (entity != null) tracked.remove(uuid);
        return null;
    }

    private static boolean needsTeleport(Location current, Location target) {
        if (current == null || target == null || current.getWorld() == null || !current.getWorld().equals(target.getWorld())) return true;
        // Position drift only. Rotation (look-at) is applied by applyLook; teleporting to change facing resets
        // client interpolation and causes the visible snap/jitter, so never teleport for yaw/pitch. Threshold is
        // 1cm (0.01) — sub-pixel float drift from the look-at math must not trigger a teleport.
        return current.distanceSquared(target) > 0.01;
    }

    private static void invokeBoolean(Object target, String methodName, boolean value) {
        try { target.getClass().getMethod(methodName, boolean.class).invoke(target, value); }
        catch (Exception ignored) { }
    }

    private static final class PlayerTarget {
        private final Location location;
        private final double distance;
        private PlayerTarget(Location location, double distance) { this.location = location; this.distance = distance; }
    }
}
