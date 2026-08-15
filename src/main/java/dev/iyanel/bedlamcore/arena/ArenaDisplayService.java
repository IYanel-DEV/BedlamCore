package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shop/gen holograms + pin/visibility tick. Owned by ArenaManager. */
final class ArenaDisplayService {
    private final ArenaManager manager;
    private final Map<UUID, Entity> displays = new HashMap<UUID, Entity>();
    private final Map<UUID, Location> displayPins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> displayHolograms = new HashMap<UUID, Boolean>();
    private final Map<UUID, String> generatorKinds = new HashMap<UUID, String>();
    private int displayTask = -1;
    private int visibilityTick;

    ArenaDisplayService(ArenaManager manager) {
        this.manager = manager;
    }

    boolean owns(UUID uuid) { return displays.containsKey(uuid); }

    String shop(Entity entity) {
        if (!entity.hasMetadata("bedlamShop") || entity.getMetadata("bedlamShop").isEmpty()) return null;
        return entity.getMetadata("bedlamShop").get(0).asString();
    }

    boolean isDisplay(Entity entity) {
        return entity.hasMetadata("bedlamShop") || entity.hasMetadata("bedlamGeneratorDisplay") || entity.hasMetadata("bedlamHologram");
    }

    void spawnAll() {
        purgeStrayArmorStands();
        Arena arena = manager.arena();
        for (TeamColor team : arena.settings().configuredTeams()) {
            spawnShop(arena.settings().team(team).itemShop(), "ITEM", ChatColor.GREEN + "ITEM SHOP");
            spawnShop(arena.settings().team(team).upgradeShop(), "UPGRADE", ChatColor.AQUA + "TEAM UPGRADES");
        }
        for (Location location : arena.settings().diamondGenerators()) spawnGeneratorDisplay(location, Material.DIAMOND_BLOCK, "diamond");
        for (Location location : arena.settings().emeraldGenerators()) spawnGeneratorDisplay(location, Material.EMERALD_BLOCK, "emerald");
        if (displays.isEmpty()) return;
        displayTask = new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
                    Entity entity = entry.getValue();
                    Location pin = displayPins.get(entry.getKey());
                    if (entity == null || entity.isDead() || pin == null) continue;
                    if (entity.getVelocity().lengthSquared() > 0.0001) entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    if (entity.hasMetadata("bedlamGeneratorDisplay")) pin.setYaw(pin.getYaw() + 3F);
                    if (entity.getLocation().distanceSquared(pin) > 0.0001 || entity.hasMetadata("bedlamGeneratorDisplay")) entity.teleport(pin);
                }
                if (++visibilityTick % GameRules.DISPLAY_VISIBILITY_INTERVAL == 0) updateDisplayVisibility();
            }
        }.runTaskTimer(manager.plugin(), 1L, 1L).getTaskId();
    }

    void clear() {
        if (displayTask != -1) Bukkit.getScheduler().cancelTask(displayTask);
        displayTask = -1;
        for (Entity entity : displays.values()) if (entity != null) entity.remove();
        displays.clear();
        displayPins.clear();
        displayHolograms.clear();
        generatorKinds.clear();
    }

    void spawnHologram(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        LobbyNpcService.prepareArmorStand(stand, true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setMetadata("bedlamHologram", new FixedMetadataValue(manager.plugin(), true));
        pin(stand, location, true);
    }

    void refreshGeneratorLabels() {
        // Cheap path: clear and respawn generator displays only.
        Arena arena = manager.arena();
        List<Location> diamonds = new ArrayList<Location>(arena.settings().diamondGenerators());
        List<Location> emeralds = new ArrayList<Location>(arena.settings().emeraldGenerators());
        for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
            Entity entity = entry.getValue();
            if (entity == null) continue;
            if (entity.hasMetadata("bedlamGeneratorDisplay") || (entity.hasMetadata("bedlamHologram") && nearAnyGenerator(displayPins.get(entry.getKey())))) {
                entity.remove();
                displays.remove(entry.getKey());
                displayPins.remove(entry.getKey());
                displayHolograms.remove(entry.getKey());
                generatorKinds.remove(entry.getKey());
            }
        }
        for (Location location : diamonds) spawnGeneratorDisplay(location, Material.DIAMOND_BLOCK, "diamond");
        for (Location location : emeralds) spawnGeneratorDisplay(location, Material.EMERALD_BLOCK, "emerald");
    }

    void clearWildMobs() {
        World world = Bukkit.getWorld(manager.arena().settings().worldName());
        if (world == null) return;
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (!(entity instanceof Monster)) continue;
            if (isDisplay(entity) || LobbyNpcService.isPluginNpc(entity) || LobbyNpcService.isPet(entity)) continue;
            if (manager.defenderTeam(entity) != null) continue;
            entity.remove();
        }
    }

    /** World-saved setup stands lose metadata on reload — wipe any ArmorStand we are not pinning. */
    void purgeStrayArmorStands() {
        World world = Bukkit.getWorld(manager.arena().settings().worldName());
        if (world == null) return;
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (!(entity instanceof ArmorStand)) continue;
            // META_MODE: do not call plugin.npcs() — it is still null during ArenaManager ctor / onEnable
            if (displays.containsKey(entity.getUniqueId()) || entity.hasMetadata(LobbyNpcService.META_MODE)) continue;
            entity.remove();
        }
    }

    private void spawnShop(Location location, String kind, String name) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        pin.setYaw(location.getYaw());
        pin.setPitch(0F);
        Entity villager = location.getWorld().spawnEntity(pin, EntityType.VILLAGER);
        villager.setMetadata("bedlamShop", new FixedMetadataValue(manager.plugin(), kind));
        // Holograms carry the label; hide vanilla nametag when looking at the villager.
        villager.setCustomName(" ");
        villager.setCustomNameVisible(false);
        LobbyNpcService.freeze(villager, false);
        pin(villager, pin, false);
        spawnHologram(pin.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 0), 0), name);
        spawnHologram(pin.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 1), 0), ChatColor.YELLOW + "Right Click");
    }

    private void spawnGeneratorDisplay(Location location, Material block, String kind) {
        if (location == null || location.getWorld() == null) return;
        Location base = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Location standPin = base.clone().add(0, GameRules.GEN_STAND_Y, 0);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(standPin, EntityType.ARMOR_STAND);
        // Full-size pin (not miniature); shop/chest holograms stay small via spawnHologram.
        LobbyNpcService.prepareArmorStand(stand, false);
        stand.setVisible(false);
        stand.getEquipment().setHelmet(new ItemStack(block));
        stand.setMetadata("bedlamGeneratorDisplay", new FixedMetadataValue(manager.plugin(), kind));
        pin(stand, standPin, false);
        generatorKinds.put(stand.getUniqueId(), kind);
        String label = kind.equals("diamond") ? ChatColor.AQUA + "Diamond" : ChatColor.GREEN + "Emerald";
        spawnHologram(base.clone().add(0, GameRules.labelY(GameRules.GEN_HOLO_TOP, 0), 0), label);
        int tier = kind.equals("diamond") ? manager.diamondTier() : manager.emeraldTier();
        spawnHologram(base.clone().add(0, GameRules.labelY(GameRules.GEN_HOLO_TOP, 1), 0), ChatColor.YELLOW + "Tier " + roman(tier));
    }

    private boolean nearAnyGenerator(Location loc) {
        if (loc == null) return false;
        Arena arena = manager.arena();
        double titleY = GameRules.labelY(GameRules.GEN_HOLO_TOP, 0);
        for (Location gen : arena.settings().diamondGenerators()) {
            if (Locations.near(loc, gen.getBlock().getLocation().add(0.5, titleY, 0.5), 2.0)) return true;
        }
        for (Location gen : arena.settings().emeraldGenerators()) {
            if (Locations.near(loc, gen.getBlock().getLocation().add(0.5, titleY, 0.5), 2.0)) return true;
        }
        return false;
    }

    private void pin(Entity entity, Location location, boolean hologram) {
        displays.put(entity.getUniqueId(), entity);
        displayPins.put(entity.getUniqueId(), location.clone());
        displayHolograms.put(entity.getUniqueId(), hologram);
    }

    private void updateDisplayVisibility() {
        double limit = GameRules.DISPLAY_VIEW * GameRules.DISPLAY_VIEW;
        for (Map.Entry<UUID, Entity> entry : displays.entrySet()) {
            Entity entity = entry.getValue();
            Location pin = displayPins.get(entry.getKey());
            if (entity == null || pin == null || pin.getWorld() == null) continue;
            boolean anyNear = false;
            for (Player player : pin.getWorld().getPlayers()) {
                boolean near = player.getLocation().distanceSquared(pin) <= limit;
                if (near && !EntityVisibility.isSpectator(player)) anyNear = true;
                EntityVisibility.apply(manager.plugin(), player, entity, near);
            }
            if (entity.hasMetadata("bedlamShop")) entity.setCustomNameVisible(false);
            else if (entity.hasMetadata("bedlamHologram")) entity.setCustomNameVisible(anyNear);
        }
    }

    private static String roman(int tier) {
        return new String[] {"I", "II", "III"}[Math.max(1, Math.min(3, tier)) - 1];
    }
}
