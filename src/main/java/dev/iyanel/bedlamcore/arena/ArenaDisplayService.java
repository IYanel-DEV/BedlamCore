package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.PacketNpcs;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shop/gen holograms + pin/visibility tick. Owned by ArenaManager. */
final class ArenaDisplayService {
    private final ArenaManager manager;
    private final Map<UUID, Entity> displays = new HashMap<UUID, Entity>();
    private final Map<UUID, Location> displayPins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> displayHolograms = new HashMap<UUID, Boolean>();
    private final Map<UUID, String> generatorKinds = new HashMap<UUID, String>();
    /** Shopkeeper-skin packet models per shop-villager uuid (team-shared, one skin each). */
    private final Map<UUID, PacketNpcs.Model> shopModels = new HashMap<UUID, PacketNpcs.Model>();
    /** Shop villagers whose skin is still downloading — retried from the display loop once cached. */
    private final Map<UUID, String> shopSkinPending = new HashMap<UUID, String>();
    private int displayTask = -1;
    private int visibilityTick;

    ArenaDisplayService(ArenaManager manager) {
        this.manager = manager;
    }

    boolean owns(UUID uuid) { return displays.containsKey(uuid); }

    /** Drop a viewer from every shop skin model's shown set (death/respawn). A fake-player NPC is client-side
     *  only and not tracked by the server entity tracker, so a respawning/teleporting client drops the fake
     *  entity regardless of distance. Forgetting lets ensureViewers re-show it on the next tick — otherwise the
     *  skinned shopkeeper stays invisible after the player dies and respawns (real villager shopkeepers are
     *  unaffected; the server re-sends real entities automatically). */
    void forgetViewer(Player viewer) {
        if (viewer == null) return;
        UUID id = viewer.getUniqueId();
        for (PacketNpcs.Model model : shopModels.values()) model.forget(id);
    }

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
            spawnShop(arena.settings().team(team).itemShop(), "ITEM", ChatColor.GREEN + "ITEM SHOP", team);
            spawnShop(arena.settings().team(team).upgradeShop(), "UPGRADE", ChatColor.AQUA + "TEAM UPGRADES", team);
        }
        for (Location location : arena.settings().diamondGenerators()) spawnGeneratorDisplay(location, Material.DIAMOND_BLOCK, "diamond");
        for (Location location : arena.settings().emeraldGenerators()) spawnGeneratorDisplay(location, Material.EMERALD_BLOCK, "emerald");
        if (displays.isEmpty()) return;
        displayTask = new BukkitRunnable() {
            @Override public void run() {
                Set<UUID> outOfPlay = outOfPlayPlayers();
                for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
                    Entity entity = entry.getValue();
                    Location pin = displayPins.get(entry.getKey());
                    if (entity == null || entity.isDead() || pin == null) continue;
                    if (entity.getVelocity().lengthSquared() > 0.0001) entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    if (entity.hasMetadata("bedlamGeneratorDisplay")) pin.setYaw(pin.getYaw() + 3F);
                    if (entity.getLocation().distanceSquared(pin) > 0.0001 || entity.hasMetadata("bedlamGeneratorDisplay")) entity.teleport(pin);
                    // Shop villagers: smooth per-tick look-at toward the nearest player (kills the jerky vanilla
                    // head-track). Skinned shops drive their packet model; plain villagers get real look packets.
                    if (entity.hasMetadata("bedlamShop")) tickShop(entry.getKey(), entity, pin, outOfPlay);
                }
                if (++visibilityTick % GameRules.DISPLAY_VISIBILITY_INTERVAL == 0) updateDisplayVisibility();
            }
        }.runTaskTimer(manager.plugin(), 1L, 1L).getTaskId();
    }

    void clear() {
        if (displayTask != -1) Bukkit.getScheduler().cancelTask(displayTask);
        displayTask = -1;
        for (PacketNpcs.Model model : shopModels.values()) PacketNpcs.destroy(model);
        shopModels.clear();
        shopSkinPending.clear();
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

    private void spawnShop(Location location, String kind, String name, TeamColor team) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        pin.setYaw(location.getYaw());
        pin.setPitch(0F);
        Entity villager = location.getWorld().spawnEntity(pin, EntityType.VILLAGER);
        villager.setMetadata("bedlamShop", new FixedMetadataValue(manager.plugin(), kind));
        villager.setMetadata("bedlamShopTeam", new FixedMetadataValue(manager.plugin(), team.name()));
        // Holograms carry the label; hide vanilla nametag when looking at the villager.
        villager.setCustomName(" ");
        villager.setCustomNameVisible(false);
        LobbyNpcService.freeze(villager, false);
        pin(villager, pin, false);
        // Shopkeeper Skins are applied at match start (applyShopSkins) — teams aren't assigned yet during WAITING.
        spawnHologram(pin.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 0), 0), name);
        spawnHologram(pin.clone().add(0, GameRules.labelY(GameRules.NPC_HOLO_TOP, 1), 0), ChatColor.YELLOW + "Right Click");
    }

    /** Called at match start (teams now assigned): skin each shop villager per its team's equipped shopkeeper
     *  skins. Item-shop NPC = 1st member's skin, upgrades = 2nd (solo → same). Null → plain villager. */
    void applyShopSkins() {
        for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
            Entity entity = entry.getValue();
            if (entity == null || !entity.hasMetadata("bedlamShop")) continue;
            if (shopModels.containsKey(entry.getKey()) || shopSkinPending.containsKey(entry.getKey())) continue;
            TeamColor team = shopTeam(entity);
            if (team == null) continue;
            String kind = entity.getMetadata("bedlamShop").get(0).asString();
            String skin = manager.plugin().cosmetics().shopkeeperSkin(manager.arena(), team, "UPGRADE".equals(kind));
            Location pin = displayPins.get(entry.getKey());
            if (skin != null && pin != null) attachShopSkin(entity, skin, pin);
        }
    }

    private TeamColor shopTeam(Entity entity) {
        if (!entity.hasMetadata("bedlamShopTeam") || entity.getMetadata("bedlamShopTeam").isEmpty()) return null;
        try {
            return TeamColor.valueOf(entity.getMetadata("bedlamShopTeam").get(0).asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Hide the villager body (invisible click hitbox) and render a packet fake-player with the skin on top.
     *  When the skin is still downloading, record it for a retry from the display loop. */
    private void attachShopSkin(Entity villager, String skinKey, Location pin) {
        if (villager instanceof LivingEntity) {
            try {
                ((LivingEntity) villager).addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false), true);
            } catch (Throwable ignored) { }
        }
        PacketNpcs.fetchSkin(manager.plugin(), skinKey, false);
        Object profile = PacketNpcs.cachedProfile(skinKey, false);
        if (profile == null) { shopSkinPending.put(villager.getUniqueId(), skinKey); return; }
        PacketNpcs.Model model = PacketNpcs.create(manager.plugin(), pin, "NPC", profile);
        if (model != null) {
            final Entity shopBody = villager;
            model.onClick(new PacketNpcs.ClickHandler() {
                @Override public void click(Player viewer) {
                    if (manager.isSoftSpectating(viewer)) return;
                    String kind = shop(shopBody);
                    if (kind == null) return;
                    if (kind.equals("ITEM")) manager.plugin().gui().openShop(viewer);
                    else manager.plugin().gui().openUpgrades(viewer);
                }
            });
            shopModels.put(villager.getUniqueId(), model);
            PacketNpcs.ensureViewers(model, 48.0);
        }
    }

    /** Per-tick shop NPC upkeep: face the nearest player. Skinned shops drive their packet model (and retry the
     *  attach once the async skin lands); plain villagers get real look packets so the client actually sees the turn. */
    /** Players who must not draw a shop NPC's gaze: arena soft-spectators (invis, never GameMode.SPECTATOR),
     *  respawning players in their death countdown, and eliminated players. True GameMode.SPECTATOR / dead
     *  players are filtered inside faceNearestPlayerInRange itself. */
    private Set<UUID> outOfPlayPlayers() {
        Set<UUID> set = new HashSet<UUID>(manager.arena().eliminated());
        World world = Bukkit.getWorld(manager.arena().settings().worldName());
        if (world != null) {
            for (Player player : world.getPlayers()) {
                if (manager.isSoftSpectating(player) || manager.isRespawning(player.getUniqueId())) {
                    set.add(player.getUniqueId());
                }
            }
        }
        return set;
    }

    private void tickShop(UUID id, Entity entity, Location pin, Set<UUID> outOfPlay) {
        float[] look = LobbyNpcService.faceNearestPlayerInRange(entity, pin, outOfPlay);
        float yaw = look != null ? look[0] : pin.getYaw();
        float pitch = look != null ? look[1] : pin.getPitch();
        PacketNpcs.Model model = shopModels.get(id);
        if (model == null) {
            String pending = shopSkinPending.get(id);
            if (pending != null && PacketNpcs.cachedProfile(pending, false) != null) {
                attachShopSkin(entity, pending, pin);
                shopSkinPending.remove(id);
                model = shopModels.get(id);
            }
        }
        if (model != null) {
            PacketNpcs.look(model, yaw, pitch);
            if (visibilityTick % 20 == 0) PacketNpcs.ensureViewers(model, 48.0);
        } else {
            PacketNpcs.lookEntity(entity, yaw, pitch);
        }
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
