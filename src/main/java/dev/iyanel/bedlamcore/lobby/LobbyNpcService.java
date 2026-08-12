package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LobbyNpcService {
    public static final String META_MODE = "bedlamNpcMode";
    private static final EntityType[] TYPES = {
        EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SKELETON,
        EntityType.CREEPER, EntityType.BLAZE, EntityType.IRON_GOLEM, EntityType.ARMOR_STAND
    };

    private final BedlamCore plugin;
    private final Map<GameType, UUID> entities = new EnumMap<GameType, UUID>(GameType.class);
    private final Map<UUID, Location> pins = new HashMap<UUID, Location>();

    public LobbyNpcService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() { pinEntities(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void respawnAll() {
        removeAll();
        for (GameType type : GameType.values()) {
            LobbySettings.NpcSettings settings = plugin.lobby().npc(type);
            if (settings.location() != null) spawn(type, settings.location(), settings.entityType());
        }
    }

    public Entity spawn(GameType mode, Location location, EntityType entityType) {
        remove(mode);
        Entity entity = location.getWorld().spawnEntity(location, entityType);
        entity.setMetadata(META_MODE, new FixedMetadataValue(plugin, mode.name()));
        entity.setCustomName(mode == GameType.SOLO ? ChatColor.AQUA + "Solo Bed Wars" : ChatColor.GOLD + "Doubles Bed Wars");
        entity.setCustomNameVisible(true);
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            living.setRemoveWhenFarAway(false);
            invokeBoolean(living, "setAI", false);
            invokeBoolean(living, "setSilent", true);
            invokeBoolean(living, "setInvulnerable", true);
        }
        entities.put(mode, entity.getUniqueId());
        pins.put(entity.getUniqueId(), location.clone());
        return entity;
    }

    public GameType mode(Entity entity) {
        if (!entity.hasMetadata(META_MODE) || entity.getMetadata(META_MODE).isEmpty()) return null;
        return GameType.parse(entity.getMetadata(META_MODE).get(0).asString());
    }

    public EntityType next(EntityType current) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i] == current) return TYPES[(i + 1) % TYPES.length];
        return TYPES[0];
    }

    public void removeAll() {
        for (GameType type : GameType.values()) remove(type);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) if (entity.hasMetadata(META_MODE)) entity.remove();
        }
        pins.clear();
    }

    public void remove(GameType type) {
        UUID uuid = entities.remove(type);
        if (uuid == null) return;
        pins.remove(uuid);
        Entity entity = find(uuid);
        if (entity != null) entity.remove();
    }

    private void pinEntities() {
        for (Map.Entry<UUID, Location> entry : new HashMap<UUID, Location>(pins).entrySet()) {
            Entity entity = find(entry.getKey());
            if (entity == null || entity.isDead()) {
                pins.remove(entry.getKey());
                continue;
            }
            if (entity.getLocation().distanceSquared(entry.getValue()) > 0.01) entity.teleport(entry.getValue());
        }
    }

    private Entity find(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getUniqueId().equals(uuid)) return entity;
        return null;
    }

    private static void invokeBoolean(Object target, String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
        } catch (Exception ignored) { }
    }
}
