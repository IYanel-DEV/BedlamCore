package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.compat.Skins;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LobbyNpcService {
    public static final String META_MODE = "bedlamNpcMode";
    private static final EntityType[] TYPES = {
        EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SKELETON,
        EntityType.CREEPER, EntityType.BLAZE, EntityType.IRON_GOLEM
    };

    private final BedlamCore plugin;
    private final Map<GameType, UUID> entities = new EnumMap<GameType, UUID>(GameType.class);
    private final Map<GameType, Object> citizens = new EnumMap<GameType, Object>(GameType.class);
    private final Map<UUID, Location> pins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> lookAtPlayers = new HashMap<UUID, Boolean>();
    private Object citizensRegistry;

    public LobbyNpcService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() { @Override public void run() { pinEntities(); } }.runTaskTimer(plugin, 1L, 1L);
    }

    public void respawnAll() {
        removeAll();
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            if (settings.location() != null) spawn(type, settings);
        }
    }

    public Entity spawn(GameType mode, LobbySettings.NpcSettings settings) {
        remove(mode);
        Location location = settings.location();
        if (location == null || location.getWorld() == null) return null;
        Entity entity = settings.human() ? spawnCitizen(mode, settings) : null;
        if (entity == null) entity = settings.human() ? spawnHumanStand(location, settings.skin()) : location.getWorld().spawnEntity(location, settings.entityType());
        entity.setMetadata(META_MODE, new FixedMetadataValue(plugin, mode.name()));
        entity.setCustomName(mode == GameType.SOLO ? ChatColor.AQUA + "Solo Bed Wars" : ChatColor.GOLD + "Doubles Bed Wars");
        entity.setCustomNameVisible(true);
        freeze(entity, settings.baby());
        entities.put(mode, entity.getUniqueId());
        pins.put(entity.getUniqueId(), location.clone());
        lookAtPlayers.put(entity.getUniqueId(), settings.lookAtPlayers());
        return entity;
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
        for (GameType type : GameType.values()) remove(type);
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.hasMetadata(META_MODE)) entity.remove();
        pins.clear();
        lookAtPlayers.clear();
    }

    public void remove(GameType type) {
        Object npc = citizens.remove(type);
        if (npc != null) invoke(npc, "destroy");
        UUID uuid = entities.remove(type);
        if (uuid == null) return;
        pins.remove(uuid);
        lookAtPlayers.remove(uuid);
        Entity entity = find(uuid);
        if (entity != null) entity.remove();
    }

    private Entity spawnCitizen(GameType mode, LobbySettings.NpcSettings settings) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            if (citizensRegistry == null) citizensRegistry = api.getMethod("createInMemoryNPCRegistry", String.class).invoke(null, "bedlamcore");
            Object registry = citizensRegistry;
            Object npc = registry.getClass().getMethod("createNPC", EntityType.class, String.class)
                .invoke(registry, EntityType.PLAYER, mode.displayName() + " Bed Wars");
            if (settings.skin() != null) {
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
            invokeBoolean(npc, "setProtected", true);
            npc.getClass().getMethod("spawn", Location.class).invoke(npc, settings.location());
            Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
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
        for (Map.Entry<UUID, Location> entry : new HashMap<UUID, Location>(pins).entrySet()) {
            Entity entity = find(entry.getKey());
            if (entity == null || entity.isDead()) { pins.remove(entry.getKey()); lookAtPlayers.remove(entry.getKey()); continue; }
            entity.setVelocity(new Vector(0, 0, 0));
            Location pinned = entry.getValue().clone();
            if (Boolean.TRUE.equals(lookAtPlayers.get(entry.getKey()))) faceNearestPlayer(entity, pinned);
            entity.teleport(pinned);
        }
    }

    private static void faceNearestPlayer(Entity entity, Location location) {
        PlayerTarget nearest = null;
        for (org.bukkit.entity.Player player : entity.getWorld().getPlayers()) {
            if (player.equals(entity)) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (nearest == null || distance < nearest.distance) nearest = new PlayerTarget(player.getEyeLocation(), distance);
        }
        if (nearest == null) return;
        Vector direction = nearest.location.toVector().subtract(location.toVector());
        location.setYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
        location.setPitch((float) Math.toDegrees(-Math.atan2(direction.getY(), Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ()))));
    }

    public static void freeze(Entity entity, boolean baby) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) entity;
        living.setRemoveWhenFarAway(false);
        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), true);
        invokeBoolean(living, "setAI", false);
        invokeBoolean(living, "setSilent", true);
        invokeBoolean(living, "setInvulnerable", true);
        invokeBoolean(living, "setCollidable", false);
        if (living instanceof Ageable) { if (baby) ((Ageable) living).setBaby(); else ((Ageable) living).setAdult(); }
        if (living instanceof Zombie) ((Zombie) living).setBaby(baby);
    }

    private Entity find(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getUniqueId().equals(uuid)) return entity;
        return null;
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
}
