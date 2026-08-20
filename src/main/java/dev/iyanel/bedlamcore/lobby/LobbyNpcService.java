package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.Skins;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.NpcSoundListener;
import dev.iyanel.bedlamcore.game.ProfileStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
    /** Name whose skull is currently on the shared profile body — only re-equip when the nearest player changes. */
    private String profileHeadOwner;
    private int profileRefreshTick;
    private int visibilityTick;
    private int muteTick;
    private boolean respawningCosmetics;
    private boolean respawningProfile;
    /** Throttle full cosmetics respawns so rapid destroy+respawn cycles can't duplicate/flicker the villager. */
    private long lastCosmeticsSpawnTick;
    /** Throttle full profile respawns (same reason as cosmetics). */
    private long lastProfileSpawnTick;
    /** Grace period per queue NPC: skip respawn right after a spawn. */
    private final Map<GameType, Long> queueLastSpawn = new EnumMap<GameType, Long>(GameType.class);
    /** Last applied yaw/pitch per entity — only push a rotation packet when it actually changed. */
    private final Map<UUID, float[]> lastLook = new HashMap<UUID, float[]>();
    private static final float LOOK_PITCH_CLAMP = 30f;
    /** Kept for legacy checks; profile is now a single shared NPC ensured every tick like cosmetics. */
    public static final int PROFILE_ENSURE_INTERVAL = 20;

    public LobbyNpcService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() {
                ensureCosmeticsAlive();
                ensureProfileAlive();
                ensureQueueNpcsAlive();
                pinEntities();
                updateProfileDisplay();
                if (++visibilityTick % GameRules.DISPLAY_VISIBILITY_INTERVAL == 0) updateHologramVisibility();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void respawnAll() {
        removeAll();
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
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            if (settings.location() != null) spawn(type, settings);
        }
        spawnCosmetics(plugin.lobby().cosmeticsNpc());
        spawnProfile(plugin.lobby().profileNpc());
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
        for (Entity entity : around.getWorld().getNearbyEntities(around, 1.2, 2.0, 1.2)) {
            if (tracked.containsKey(entity.getUniqueId())) continue;
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
            entity.remove();
        }
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
        // Citizens present -> real player-model NPC (fixed skin). Else the head-showing armor stand.
        Entity body = spawnCitizenProfile(location);
        if (body == null) body = spawnProfileStand(location);
        body.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
        hideBodyName(body);
        freeze(body, false);
        profileEntity = body.getUniqueId();
        profilePin = location.clone();
        profileHeadOwner = null; // force updateProfileHead() to re-equip the nearest player's skull next tick
        tracked.put(body.getUniqueId(), body);
        pins.put(body.getUniqueId(), location.clone());
        lookAtPlayers.put(body.getUniqueId(), Boolean.TRUE);
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
        lastProfileSpawnTick = now;
        respawningProfile = true;
        try {
            spawnProfile(location);
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
        // Armor-stand fallback wears the nearest player's head; a Citizens NPC keeps its fixed skin (no swap).
        if (body instanceof ArmorStand) {
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
        profilePin = null;
    }

    public Entity spawnCosmetics(Location location) {
        Location previous = cosmeticsPin == null ? null : cosmeticsPin.clone();
        removeCosmetics();
        scrubOrphanHolograms(previous);
        scrubOrphanHolograms(location);
        if (location == null || location.getWorld() == null) return null;
        // Kill any reloaded orphan body sitting on the pin before spawning — prevents the duplicate villager.
        scrubReloadedBodies(location);
        Entity entity = spawnCitizenNamed(location, "Cosmetics", EntityType.VILLAGER);
        if (entity == null) entity = location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        entity.setMetadata(META_COSMETICS, new FixedMetadataValue(plugin, true));
        hideBodyName(entity);
        freeze(entity, false);
        cosmeticsEntity = entity.getUniqueId();
        cosmeticsPin = location.clone();
        tracked.put(entity.getUniqueId(), entity);
        pins.put(entity.getUniqueId(), location.clone());
        lookAtPlayers.put(entity.getUniqueId(), Boolean.TRUE);
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
        lastCosmeticsSpawnTick = now;
        // Rebind failed — destroy the old Citizens NPC so no duplicate accumulates in the registry.
        if (cosmeticsCitizen != null) {
            invoke(cosmeticsCitizen, "destroy");
            cosmeticsCitizen = null;
        }
        respawningCosmetics = true;
        try {
            spawnCosmetics(location);
        } finally {
            respawningCosmetics = false;
        }
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
        Entity entity = spawnCitizen(mode, settings);
        if (entity == null) entity = settings.human() ? spawnHumanStand(location, settings.skin()) : location.getWorld().spawnEntity(location, settings.entityType());
        entity.setMetadata(META_MODE, new FixedMetadataValue(plugin, mode.name()));
        // Holograms carry the label; hide vanilla nametag (same as shop villagers).
        hideBodyName(entity);
        freeze(entity, settings.baby());
        entities.put(mode, entity.getUniqueId());
        tracked.put(entity.getUniqueId(), entity);
        pins.put(entity.getUniqueId(), location.clone());
        lookAtPlayers.put(entity.getUniqueId(), settings.lookAtPlayers());
        spawnQueueHolograms(mode, location);
        return entity;
    }

    private void spawnQueueHolograms(GameType mode, Location location) {
        removeHolograms(mode);
        scrubOrphanHolograms(location);
        List<UUID> ids = new ArrayList<UUID>();
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 0), 0), mode == GameType.SOLO ? ChatColor.AQUA + "" + ChatColor.BOLD + "SOLO QUEUE" : ChatColor.GOLD + "" + ChatColor.BOLD + "DOUBLES QUEUE").getUniqueId());
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 1), 0), ChatColor.YELLOW + "Click to play!").getUniqueId());
        ids.add(hologram(location.clone().add(0, GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 2), 0), ChatColor.GRAY + (mode == GameType.SOLO ? "1 per team" : "2 per team")).getUniqueId());
        holograms.put(mode, ids);
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
        for (UUID uuid : bodyIds) {
            Entity entity = find(uuid);
            Location pin = pins.get(uuid);
            if (entity == null || pin == null || pin.getWorld() == null) continue;
            boolean anyNear = false;
            for (Player player : pin.getWorld().getPlayers()) {
                boolean near = player.getLocation().distanceSquared(pin) <= limit;
                if (near && !EntityVisibility.isSpectator(player)) anyNear = true;
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
        removeCosmetics();
        for (GameType type : GameType.values()) remove(type);
        for (Entity entity : new ArrayList<Entity>(tracked.values())) {
            if (entity != null) entity.remove();
        }
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
            Entity entity = tracked.remove(cosmeticsEntity);
            if (entity != null) entity.remove();
            else removeWorldEntity(cosmeticsEntity, scrubAt);
            cosmeticsEntity = null;
        }
        scrubOrphanHolograms(scrubAt);
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
            if (wantLook && !citizensNpc) {
                float[] prev = lastLook.get(entry.getKey());
                if (prev == null || Math.abs(yaw - prev[0]) > 0.5f || Math.abs(pitch - prev[1]) > 0.5f) {
                    applyLook(entity, yaw, pitch);
                    lastLook.put(entry.getKey(), new float[]{yaw, pitch});
                }
            }
            // Citizens remount clears NMS silent / nameplate. Never remute holograms — hideBodyName
            // + givePaper on META_PROFILE holos caused 1Hz nametag/paper flicker. setAI(false) is applied once at
            // spawn (freeze) — repeating it here reset the entity's navigation and nudged its position (jitter).
            if (remute && !entity.hasMetadata(META_HOLO)
                && (entity.hasMetadata(META_MODE) || entity.hasMetadata(META_COSMETICS)
                || entity.hasMetadata(META_PROFILE))) {
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
            spawn(type, settings);
            queueLastSpawn.put(type, System.currentTimeMillis());
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
    private static float[] faceNearestPlayerInRange(Entity entity, Location location) {
        PlayerTarget nearest = null;
        for (org.bukkit.entity.Player player : entity.getWorld().getPlayers()) {
            if (player.equals(entity)) continue;
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
        if (!modern) living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), true);
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
