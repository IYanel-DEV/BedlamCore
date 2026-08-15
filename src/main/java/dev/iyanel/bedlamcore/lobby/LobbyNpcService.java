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
    /** Profile hologram stack top (6 lines). */
    public static final double PROFILE_HOLO_TOP = 3.55;
    /** Look-at only within this many blocks; outside keep placement yaw. */
    public static final double LOOK_RANGE = 8.0;
    public static final double LOOK_RANGE_SQ = LOOK_RANGE * LOOK_RANGE;
    private static final EntityType[] TYPES = {
        EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SKELETON,
        EntityType.CREEPER, EntityType.BLAZE, EntityType.IRON_GOLEM
    };
    private static final Map<UUID, Entity> SILENT_ENTITIES = new ConcurrentHashMap<UUID, Entity>();

    private final BedlamCore plugin;
    private final Map<GameType, UUID> entities = new EnumMap<GameType, UUID>(GameType.class);
    private final Map<GameType, Object> citizens = new EnumMap<GameType, Object>(GameType.class);
    private final Map<GameType, List<UUID>> holograms = new EnumMap<GameType, List<UUID>>(GameType.class);
    private final Map<UUID, Location> pins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> lookAtPlayers = new HashMap<UUID, Boolean>();
    private final Map<UUID, Entity> tracked = new HashMap<UUID, Entity>();
    private UUID cosmeticsEntity;
    private Object cosmeticsCitizen;
    private final List<UUID> cosmeticsHolograms = new ArrayList<UUID>();
    /** Last cosmetics pin — used to scrub orphans after despawn / relocate. */
    private Location cosmeticsPin;
    /** Profile NPC pin — personal per-viewer clones spawn here. */
    private Location profilePin;
    private final Map<UUID, PersonalProfile> personalProfiles = new HashMap<UUID, PersonalProfile>();
    private Object citizensRegistry;
    private int visibilityTick;
    private int muteTick;
    private int profileTextTick;
    private boolean respawningCosmetics;
    private static final float LOOK_PITCH_CLAMP = 30f;

    public LobbyNpcService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() {
                ensureCosmeticsAlive();
                ensureQueueNpcsAlive();
                ensureProfilePersonals();
                pinEntities();
                if (++visibilityTick % GameRules.DISPLAY_VISIBILITY_INTERVAL == 0) updateHologramVisibility();
                if (++profileTextTick % 40 == 0) refreshProfileHologramText();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void respawnAll() {
        removeAll();
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            if (settings.location() != null) spawn(type, settings);
        }
        spawnCosmetics(plugin.lobby().cosmeticsNpc());
        spawnProfile(plugin.lobby().profileNpc());
    }

    /** Set profile pin and scrub; personal clones appear for nearby players on the next tick. */
    public void spawnProfile(Location location) {
        Location previous = profilePin == null ? null : profilePin.clone();
        removeProfile();
        scrubOrphanHolograms(previous);
        scrubOrphanPapers(previous);
        scrubOrphanHolograms(location);
        scrubOrphanPapers(location);
        if (location == null || location.getWorld() == null) return;
        profilePin = location.clone();
    }

    public void ensureProfilePersonals() {
        Location location = profilePin != null ? profilePin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().profileNpc());
        if (location == null || location.getWorld() == null || !chunkLoaded(location)) {
            removeAllPersonalProfiles();
            return;
        }
        if (profilePin == null) profilePin = location.clone();
        double limit = GameRules.DISPLAY_VIEW * GameRules.DISPLAY_VIEW;
        Map<UUID, Player> near = new HashMap<UUID, Player>();
        for (Player player : location.getWorld().getPlayers()) {
            if (EntityVisibility.isSpectator(player)) continue;
            if (player.getLocation().distanceSquared(location) <= limit) near.put(player.getUniqueId(), player);
        }
        for (UUID uuid : new ArrayList<UUID>(personalProfiles.keySet())) {
            if (!near.containsKey(uuid)) removePersonalProfile(uuid);
        }
        for (Player player : near.values()) ensurePersonalProfile(player, location);
    }

    public boolean isProfile(Entity entity) {
        return entity != null && entity.hasMetadata(META_PROFILE);
    }

    public UUID profileOwner(Entity entity) {
        if (entity == null || !entity.hasMetadata(META_PROFILE_OWNER) || entity.getMetadata(META_PROFILE_OWNER).isEmpty()) {
            return null;
        }
        try { return UUID.fromString(entity.getMetadata(META_PROFILE_OWNER).get(0).asString()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private void ensurePersonalProfile(Player player, Location location) {
        PersonalProfile existing = personalProfiles.get(player.getUniqueId());
        if (existing != null) {
            rebindCitizenBody(existing, location);
            if (alive(existing.bodyId) && personalHologramsAlive(existing)) return;
            // Body alive, holos gone — refresh lines only; do not respawn the body/paper.
            if (alive(existing.bodyId) && !personalHologramsAlive(existing)) {
                respawnPersonalHolos(existing, player, location);
                return;
            }
        }
        removePersonalProfile(player.getUniqueId());
        scrubOrphanHolograms(location);
        scrubOrphanPapers(location);
        PersonalProfile profile = spawnPersonalProfile(player, location);
        if (profile != null) personalProfiles.put(player.getUniqueId(), profile);
    }

    /** Citizens remount swaps the Bukkit entity UUID; rebind pin/track without full respawn. */
    private void rebindCitizenBody(PersonalProfile profile, Location location) {
        if (profile == null || profile.citizen == null || alive(profile.bodyId)) return;
        try {
            Entity entity = (Entity) profile.citizen.getClass().getMethod("getEntity").invoke(profile.citizen);
            if (!alive(entity)) return;
            UUID old = profile.bodyId;
            if (old != null) {
                pins.remove(old);
                lookAtPlayers.remove(old);
                tracked.remove(old);
            }
            profile.bodyId = entity.getUniqueId();
            entity.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
            hideBodyName(entity);
            freeze(entity, false);
            givePaper(entity);
            tracked.put(profile.bodyId, entity);
            pins.put(profile.bodyId, location.clone());
            lookAtPlayers.put(profile.bodyId, Boolean.TRUE);
        } catch (Exception ignored) { }
    }

    private void respawnPersonalHolos(PersonalProfile profile, Player player, Location location) {
        Location scrubAt = location == null ? profilePin : location;
        for (UUID uuid : new ArrayList<UUID>(profile.holos)) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        profile.holos.clear();
        scrubOrphanHolograms(scrubAt);
        scrubOrphanPapers(scrubAt);
        String[] lines = ProfileStats.hologramLines(plugin.stats().get(player.getUniqueId()));
        for (int i = 0; i < lines.length; i++) {
            ArmorStand stand = hologram(location.clone().add(0, GameRules.labelY(PROFILE_HOLO_TOP, i), 0), lines[i]);
            stand.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
            stand.setMetadata(META_PROFILE_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            profile.holos.add(stand.getUniqueId());
        }
        applyPersonalVisibility(player, profile);
    }

    private PersonalProfile spawnPersonalProfile(Player player, Location location) {
        Entity body = spawnCitizenPlayer(location, player.getName());
        if (body == null) body = spawnProfileStand(location, player.getName());
        if (body == null) return null;
        body.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
        body.setMetadata(META_PROFILE_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        hideBodyName(body);
        freeze(body, false);
        givePaper(body);
        PersonalProfile profile = new PersonalProfile();
        profile.bodyId = body.getUniqueId();
        profile.citizen = lastSpawnedCitizen;
        lastSpawnedCitizen = null;
        tracked.put(body.getUniqueId(), body);
        pins.put(body.getUniqueId(), location.clone());
        lookAtPlayers.put(body.getUniqueId(), Boolean.TRUE);
        String[] lines = ProfileStats.hologramLines(plugin.stats().get(player.getUniqueId()));
        for (int i = 0; i < lines.length; i++) {
            ArmorStand stand = hologram(location.clone().add(0, GameRules.labelY(PROFILE_HOLO_TOP, i), 0), lines[i]);
            stand.setMetadata(META_PROFILE, new FixedMetadataValue(plugin, true));
            stand.setMetadata(META_PROFILE_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            profile.holos.add(stand.getUniqueId());
        }
        applyPersonalVisibility(player, profile);
        return profile;
    }

    private Object lastSpawnedCitizen;

    private Entity spawnCitizenPlayer(Location location, String skinName) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) {
                citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            }
            Object npc = citizensRegistry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(citizensRegistry, EntityType.PLAYER, " ");
            Class<?> skinTrait = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object trait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTrait);
            trait.getClass().getMethod("setSkinName", String.class).invoke(trait, skinName);
            citizensDisableLookAi(npc);
            invokeBoolean(npc, "setProtected", true);
            citizensSilent(npc);
            citizensHideNameplate(npc);
            npc.getClass().getMethod("spawn", Location.class).invoke(npc, location);
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            mute(entity);
            hideBodyName(entity);
            lastSpawnedCitizen = npc;
            return entity;
        } catch (Exception exception) {
            plugin.getLogger().warning("Citizens profile NPC failed; using armor stand: " + exception.getMessage());
            lastSpawnedCitizen = null;
            return null;
        }
    }

    private static ArmorStand spawnProfileStand(Location location, String skin) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setSmall(false);
        stand.setGravity(false);
        stand.setVisible(true);
        EntityEquipment gear = stand.getEquipment();
        if (gear != null) {
            gear.setHelmet(Skins.head(skin));
            gear.setItemInHand(new ItemStack(Material.PAPER));
        }
        return stand;
    }

    private static void givePaper(Entity entity) {
        if (!(entity instanceof LivingEntity)) return;
        EntityEquipment gear = ((LivingEntity) entity).getEquipment();
        if (gear == null) return;
        gear.setItemInHand(new ItemStack(Material.PAPER));
    }

    private void applyPersonalVisibility(Player owner, PersonalProfile profile) {
        if (owner == null || profile == null || profilePin == null || profilePin.getWorld() == null) return;
        List<UUID> ids = new ArrayList<UUID>(profile.holos);
        if (profile.bodyId != null) ids.add(profile.bodyId);
        for (UUID id : ids) {
            Entity entity = find(id);
            if (entity == null) continue;
            for (Player viewer : profilePin.getWorld().getPlayers()) {
                if (viewer.getUniqueId().equals(owner.getUniqueId())) EntityVisibility.show(plugin, viewer, entity);
                else EntityVisibility.hide(plugin, viewer, entity);
            }
            entity.setCustomNameVisible(entity.hasMetadata(META_HOLO));
        }
    }

    private void updatePersonalVisibility() {
        for (Map.Entry<UUID, PersonalProfile> entry : personalProfiles.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner != null) applyPersonalVisibility(owner, entry.getValue());
        }
    }

    private void refreshProfileHologramText() {
        for (Map.Entry<UUID, PersonalProfile> entry : personalProfiles.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PersonalProfile profile = entry.getValue();
            if (player == null || profile == null) continue;
            String[] lines = ProfileStats.hologramLines(plugin.stats().get(player.getUniqueId()));
            for (int i = 0; i < profile.holos.size() && i < lines.length; i++) {
                Entity entity = find(profile.holos.get(i));
                if (entity != null) {
                    entity.setCustomName(lines[i]);
                    entity.setCustomNameVisible(true);
                }
            }
        }
    }

    private boolean personalHologramsAlive(PersonalProfile profile) {
        if (profile == null || profile.holos.size() < ProfileStats.hologramLines(null).length) return false;
        for (UUID uuid : profile.holos) if (!alive(uuid)) return false;
        return true;
    }

    private void removeAllPersonalProfiles() {
        for (UUID uuid : new ArrayList<UUID>(personalProfiles.keySet())) removePersonalProfile(uuid);
    }

    private void removePersonalProfile(UUID owner) {
        PersonalProfile profile = personalProfiles.remove(owner);
        if (profile == null) return;
        if (profile.citizen != null) invoke(profile.citizen, "destroy");
        Location scrubAt = profilePin == null ? null : profilePin.clone();
        for (UUID uuid : new ArrayList<UUID>(profile.holos)) {
            pins.remove(uuid);
            Entity entity = tracked.remove(uuid);
            if (entity != null) entity.remove();
            else removeWorldEntity(uuid, scrubAt);
        }
        if (profile.bodyId != null) {
            pins.remove(profile.bodyId);
            lookAtPlayers.remove(profile.bodyId);
            Entity entity = tracked.remove(profile.bodyId);
            if (entity != null) entity.remove();
            else removeWorldEntity(profile.bodyId, scrubAt);
        }
    }

    public void removeProfile() {
        Location scrubAt = profilePin == null ? null : profilePin.clone();
        removeAllPersonalProfiles();
        scrubOrphanHolograms(scrubAt);
        profilePin = null;
    }

    public Entity spawnCosmetics(Location location) {
        Location previous = cosmeticsPin == null ? null : cosmeticsPin.clone();
        removeCosmetics();
        scrubOrphanHolograms(previous);
        scrubOrphanHolograms(location);
        if (location == null || location.getWorld() == null) return null;
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
                @Override public void run() { ensureCosmeticsAlive(); }
            });
        }
        Location profile = profilePin != null ? profilePin.clone()
            : (plugin.lobby() == null ? null : plugin.lobby().profileNpc());
        if (profile != null && profile.getWorld() != null
            && event.getWorld().equals(profile.getWorld()) && sameChunk(event.getChunk(), profile)) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { ensureProfilePersonals(); }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePersonalProfile(event.getPlayer().getUniqueId());
    }

    public boolean isCosmetics(Entity entity) {
        return entity != null && entity.hasMetadata(META_COSMETICS);
    }

    public Entity spawn(GameType mode, LobbySettings.NpcSettings settings) {
        remove(mode);
        Location location = settings.location();
        if (location == null || location.getWorld() == null) return null;
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
        prepareArmorStand(stand, true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setMetadata(META_HOLO, new FixedMetadataValue(plugin, true));
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
        updatePersonalVisibility();
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
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (entity.hasMetadata(META_MODE) || entity.hasMetadata(META_HOLO)
                || entity.hasMetadata(META_COSMETICS) || entity.hasMetadata(META_PROFILE)) entity.remove();
        }
        pins.clear();
        lookAtPlayers.clear();
        holograms.clear();
        tracked.clear();
        cosmeticsPin = null;
        profilePin = null;
        personalProfiles.clear();
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

    /** Drop floating nametags left after NPC despawn / relocate. */
    public void scrubOrphanHolograms(Location around) {
        if (around == null || around.getWorld() == null) return;
        double limit = HOLO_SCRUB_RADIUS * HOLO_SCRUB_RADIUS;
        Location focus = around.clone().add(0, 1.5, 0);
        for (Entity entity : around.getWorld().getEntities()) {
            if (!(entity instanceof ArmorStand) || !entity.hasMetadata(META_HOLO)) continue;
            if (entity.getWorld() == null || !entity.getWorld().equals(around.getWorld())) continue;
            if (entity.getLocation().distanceSquared(focus) > limit
                && entity.getLocation().distanceSquared(around) > limit) continue;
            pins.remove(entity.getUniqueId());
            tracked.remove(entity.getUniqueId());
            cosmeticsHolograms.remove(entity.getUniqueId());
            for (List<UUID> ids : holograms.values()) ids.remove(entity.getUniqueId());
            entity.remove();
        }
    }

    /** Drop leaked paper items (NPC hand re-equip / remount) around a pin. */
    public void scrubOrphanPapers(Location around) {
        if (around == null || around.getWorld() == null) return;
        double limit = HOLO_SCRUB_RADIUS * HOLO_SCRUB_RADIUS;
        for (Entity entity : around.getWorld().getEntities()) {
            if (!(entity instanceof org.bukkit.entity.Item)) continue;
            if (entity.getLocation().distanceSquared(around) > limit) continue;
            ItemStack stack = ((org.bukkit.entity.Item) entity).getItemStack();
            if (stack != null && stack.getType() == Material.PAPER) entity.remove();
        }
    }

    /** Pure helper for checks: true when body/holos need a respawn. */
    public static boolean needsCosmeticsRespawn(boolean bodyAlive, boolean holosAlive) {
        return !bodyAlive || !holosAlive;
    }

    public static int profileHologramLineCount() {
        return ProfileStats.hologramLines(null).length;
    }

    public static boolean sameChunk(Chunk chunk, Location location) {
        if (chunk == null || location == null) return false;
        return chunk.getX() == (location.getBlockX() >> 4) && chunk.getZ() == (location.getBlockZ() >> 4);
    }

    private Entity spawnCitizenNamed(Location location, String name, EntityType type) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            // Blank Citizens name — holograms own the label; vanilla/Citizens nametag stays off.
            Object npc = citizensRegistry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(citizensRegistry, type, " ");
            // We drive look-at in pinEntities; Citizens LookClose / mob AI fight and jitter.
            citizensDisableLookAi(npc);
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
                continue;
            }
            if (entity.getVelocity().lengthSquared() > 0.0001) entity.setVelocity(new Vector(0, 0, 0));
            // Pin yaw/pitch is placement facing — never overwrite with look-at (that caused snap jitter).
            Location pinned = entry.getValue().clone();
            float yaw = pinned.getYaw();
            float pitch = pinned.getPitch();
            boolean wantLook = Boolean.TRUE.equals(lookAtPlayers.get(entry.getKey()));
            if (wantLook) {
                float[] look = faceNearestPlayerInRange(entity, pinned);
                if (look != null) {
                    yaw = look[0];
                    pitch = look[1];
                }
            }
            pinned.setYaw(yaw);
            pinned.setPitch(pitch);
            if (needsTeleport(entity.getLocation(), pinned)) entity.teleport(pinned);
            // Every tick: mob AI / Citizens remount reset head; skip = look-ahead then snap-back.
            if (wantLook) applyLook(entity, yaw, pitch);
            // Citizens remount clears NMS silent / nameplate. Never remute holograms — hideBodyName
            // + givePaper on META_PROFILE holos caused 1Hz nametag/paper flicker.
            if (remute && !entity.hasMetadata(META_HOLO)
                && (entity.hasMetadata(META_MODE) || entity.hasMetadata(META_COSMETICS)
                || entity.hasMetadata(META_PROFILE))) {
                mute(entity);
                hideBodyName(entity);
                invokeBoolean(entity, "setAI", false);
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
            spawn(type, settings);
        }
    }

    private boolean isManagedLobbyId(UUID uuid) {
        if (uuid == null) return false;
        if (uuid.equals(cosmeticsEntity) || cosmeticsHolograms.contains(uuid)) return true;
        for (UUID id : entities.values()) if (uuid.equals(id)) return true;
        for (List<UUID> ids : holograms.values()) if (ids.contains(uuid)) return true;
        for (PersonalProfile profile : personalProfiles.values()) {
            if (uuid.equals(profile.bodyId) || profile.holos.contains(uuid)) return true;
        }
        return false;
    }

    private boolean cosmeticsHologramsAlive() {
        if (cosmeticsHolograms.size() < 2) return false;
        for (UUID uuid : cosmeticsHolograms) if (!alive(uuid)) return false;
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

    private void removeWorldEntity(UUID uuid, Location near) {
        if (uuid == null) return;
        if (near != null && near.getWorld() != null && chunkLoaded(near)) {
            for (Entity entity : near.getChunk().getEntities()) {
                if (uuid.equals(entity.getUniqueId())) { entity.remove(); return; }
            }
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (uuid.equals(entity.getUniqueId())) { entity.remove(); return; }
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

    /** Body + head yaw/pitch together (teleport alone leaves 1.8 NPC necks twisted). */
    private static void applyLook(Entity entity, float yaw, float pitch) {
        if (entity == null) return;
        try {
            entity.getClass().getMethod("setRotation", float.class, float.class).invoke(entity, yaw, pitch);
        } catch (Throwable ignored) { }
        if (entity instanceof ArmorStand) {
            ((ArmorStand) entity).setHeadPose(new org.bukkit.util.EulerAngle(
                Math.toRadians(pitch), 0, 0));
            ((ArmorStand) entity).setBodyPose(new org.bukkit.util.EulerAngle(0, 0, 0));
        }
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            // net.minecraft.server.Entity yaw/pitch
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
        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), true);
        invokeBoolean(living, "setAI", false);
        invokeBoolean(living, "setGravity", false);
        mute(living);
        invokeBoolean(living, "setInvulnerable", true);
        invokeBoolean(living, "setCollidable", false);
        if (living instanceof Ageable) { if (baby) ((Ageable) living).setBaby(); else ((Ageable) living).setAdult(); }
        if (living instanceof Zombie) ((Zombie) living).setBaby(baby);
    }

    private Entity find(UUID uuid) {
        Entity entity = tracked.get(uuid);
        if (alive(entity)) return entity;
        Location pin = pins.get(uuid);
        if (pin != null && pin.getWorld() != null && chunkLoaded(pin)) {
            for (Entity candidate : pin.getChunk().getEntities()) {
                if (uuid.equals(candidate.getUniqueId())) {
                    tracked.put(uuid, candidate);
                    return candidate;
                }
            }
        }
        if (entity != null) tracked.remove(uuid);
        return null;
    }

    private static boolean needsTeleport(Location current, Location target) {
        if (current == null || target == null || current.getWorld() == null || !current.getWorld().equals(target.getWorld())) return true;
        if (current.distanceSquared(target) > 0.0001) return true;
        return Math.abs(current.getYaw() - target.getYaw()) > 0.5F || Math.abs(current.getPitch() - target.getPitch()) > 0.5F;
    }

    private static void invokeBoolean(Object target, String methodName, boolean value) {
        try { target.getClass().getMethod(methodName, boolean.class).invoke(target, value); }
        catch (Exception ignored) { }
    }

    private static void invoke(Object target, String methodName) {
        try { target.getClass().getMethod(methodName).invoke(target); }
        catch (Exception ignored) { }
    }

    private static final class PlayerTarget {
        private final Location location;
        private final double distance;
        private PlayerTarget(Location location, double distance) { this.location = location; this.distance = distance; }
    }

    private static final class PersonalProfile {
        private UUID bodyId;
        private Object citizen;
        private final List<UUID> holos = new ArrayList<UUID>();
    }
}
