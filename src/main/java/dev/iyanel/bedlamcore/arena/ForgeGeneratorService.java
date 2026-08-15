package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Match iron/gold forges + diamond/emerald gens. Owned by ArenaManager. */
final class ForgeGeneratorService {
    private final ArenaManager manager;
    private int gameSeconds;
    private int diamondTier = 1;
    private int emeraldTier = 1;
    /** Per-team latch: bonus chat already sent while that ore is still waiting at the forge. */
    private final Map<TeamColor, EnumSet<Material>> forgeBonusAnnounced = new EnumMap<TeamColor, EnumSet<Material>>(TeamColor.class);

    ForgeGeneratorService(ArenaManager manager) {
        this.manager = manager;
    }

    int gameSeconds() { return gameSeconds; }
    int diamondTier() { return diamondTier; }
    int emeraldTier() { return emeraldTier; }

    void resetForMatch() {
        gameSeconds = 0;
        diamondTier = 1;
        emeraldTier = 1;
        forgeBonusAnnounced.clear();
    }

    String nextGeneratorUpgrade() {
        int next = Integer.MAX_VALUE;
        String name = "Maxed";
        int diamondTwo = upgradeAt("diamond", 2);
        int diamondThree = upgradeAt("diamond", 3);
        int emeraldTwo = upgradeAt("emerald", 2);
        int emeraldThree = upgradeAt("emerald", 3);
        if (diamondTier < 2 && diamondTwo > gameSeconds && diamondTwo < next) { next = diamondTwo; name = "Diamond II"; }
        else if (diamondTier < 3 && diamondThree > gameSeconds && diamondThree < next) { next = diamondThree; name = "Diamond III"; }
        if (emeraldTier < 2 && emeraldTwo > gameSeconds && emeraldTwo < next) { next = emeraldTwo; name = "Emerald II"; }
        else if (emeraldTier < 3 && emeraldThree > gameSeconds && emeraldThree < next) { next = emeraldThree; name = "Emerald III"; }
        if (next == Integer.MAX_VALUE) return name;
        int remaining = next - gameSeconds;
        return name + " in " + (remaining / 60) + ":" + (remaining % 60 < 10 ? "0" : "") + remaining % 60;
    }

    String nextGeneratorUpgradeLine() {
        String raw = nextGeneratorUpgrade();
        int idx = raw.lastIndexOf(" in ");
        if (idx < 0) return ChatColor.WHITE + raw;
        return ChatColor.WHITE + raw.substring(0, idx + 4) + ChatColor.GREEN + raw.substring(idx + 4);
    }

    void start() {
        BedlamCore plugin = manager.plugin();
        Arena arena = manager.arena();
        int iron = plugin.getConfig().getInt("generator-periods.iron", 20);
        int gold = plugin.getConfig().getInt("generator-periods.gold", 80);
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location forge = arena.settings().team(team).forge();
            forgeGenerator(forge, new ItemStack(Material.IRON_INGOT), "iron", iron, team);
            forgeGenerator(forge, new ItemStack(Material.GOLD_INGOT), "gold", gold, team);
        }
        for (Location location : arena.settings().diamondGenerators()) generator(location, new ItemStack(Material.DIAMOND), "diamond", 600);
        for (Location location : arena.settings().emeraldGenerators()) generator(location, new ItemStack(Material.EMERALD), "emerald", 1200);
        int id = new BukkitRunnable() {
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                gameSeconds++;
                int nextDiamond = GameRules.generatorTier(gameSeconds, upgradeAt("diamond", 2), upgradeAt("diamond", 3));
                int nextEmerald = GameRules.generatorTier(gameSeconds, upgradeAt("emerald", 2), upgradeAt("emerald", 3));
                if (nextDiamond != diamondTier) {
                    diamondTier = nextDiamond;
                    manager.broadcast(ChatColor.AQUA + "Diamond Generators" + ChatColor.GRAY
                        + " have been upgraded to Tier " + ChatColor.AQUA + roman(diamondTier));
                    for (Player online : manager.arenaPlayers()) Sounds.generatorUpgrade(online);
                    manager.refreshGeneratorLabels();
                }
                if (nextEmerald != emeraldTier) {
                    emeraldTier = nextEmerald;
                    manager.broadcast(ChatColor.GREEN + "Emerald Generators" + ChatColor.GRAY
                        + " have been upgraded to Tier " + ChatColor.GREEN + roman(emeraldTier));
                    for (Player online : manager.arenaPlayers()) Sounds.generatorUpgrade(online);
                    manager.refreshGeneratorLabels();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    /** Ground pile at team forge (soft-specs cannot pick until living again). */
    void dropMatchOresAtForge(TeamColor team, int[] counts) {
        if (team == null || !GameRules.hasMatchOres(counts)) return;
        Location forge = manager.arena().settings().team(team).forge();
        if (forge == null) return;
        World world = Bukkit.getWorld(manager.arena().settings().worldName());
        if (world != null) forge.setWorld(world);
        if (counts[GameRules.RES_IRON] > 0) spawnForgeDrop(forge, new ItemStack(Material.IRON_INGOT, counts[GameRules.RES_IRON]));
        if (counts[GameRules.RES_GOLD] > 0) spawnForgeDrop(forge, new ItemStack(Material.GOLD_INGOT, counts[GameRules.RES_GOLD]));
        if (counts[GameRules.RES_DIAMOND] > 0) spawnForgeDrop(forge, new ItemStack(Material.DIAMOND, counts[GameRules.RES_DIAMOND]));
        if (counts[GameRules.RES_EMERALD] > 0) spawnForgeDrop(forge, new ItemStack(Material.EMERALD, counts[GameRules.RES_EMERALD]));
    }

    private void forgeGenerator(final Location location, final ItemStack stack, final String kind, final int fallbackTicks, final TeamColor team) {
        final Arena arena = manager.arena();
        int id = new BukkitRunnable() {
            private int waited;
            private final java.util.Random random = new java.util.Random();
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                waited++;
                int period = Math.max(1, (generatorPeriod(kind, fallbackTicks) + 19) / 20);
                int forge = arena.forgeLevel(team);
                if (forge > 0) period = Math.max(1, period - forge);
                if (waited < period) return;
                waited = 0;
                // Clear latch when teammate collected ground diamond/emerald since last tick
                syncForgeBonusLatch(location, team, Material.DIAMOND);
                syncForgeBonusLatch(location, team, Material.EMERALD);
                deliverForge(location, stack, team);
                if (GameRules.forgeBonusHits(GameRules.forgeDiamondChance(forge), random.nextDouble())) {
                    deliverForge(location, new ItemStack(Material.DIAMOND), team);
                    maybeAnnounceForgeBonus(location, team, Material.DIAMOND);
                }
                if (GameRules.forgeBonusHits(GameRules.forgeEmeraldChance(forge), random.nextDouble())) {
                    deliverForge(location, new ItemStack(Material.EMERALD), team);
                    maybeAnnounceForgeBonus(location, team, Material.EMERALD);
                }
            }
        }.runTaskTimer(manager.plugin(), 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    /**
     * Announce once while bonus ore is waiting (ground pile or uncollected share).
     * Latch clears when forge ground count for that material hits 0 (pickup or share into inventory).
     */
    private void maybeAnnounceForgeBonus(Location forge, TeamColor team, Material ore) {
        syncForgeBonusLatch(forge, team, ore);
        if (forgeBonusAnnounced(team).contains(ore)) return;
        announceForgeBonus(forge, team, ore);
        forgeBonusAnnounced(team).add(ore);
        syncForgeBonusLatch(forge, team, ore);
    }

    private void syncForgeBonusLatch(Location forge, TeamColor team, Material ore) {
        EnumSet<Material> set = forgeBonusAnnounced.get(team);
        if (set == null || !set.contains(ore)) return;
        if (!GameRules.forgeBonusAnnounceLatch(true, forgeBonusGround(forge, ore))) set.remove(ore);
    }

    private EnumSet<Material> forgeBonusAnnounced(TeamColor team) {
        EnumSet<Material> set = forgeBonusAnnounced.get(team);
        if (set == null) {
            set = EnumSet.noneOf(Material.class);
            forgeBonusAnnounced.put(team, set);
        }
        return set;
    }

    private int forgeBonusGround(Location forge, Material ore) {
        return groundAmount(Locations.forgeDropPoint(forge), ore);
    }

    /** Team chat + nearby forgeCollect when L2/L3 rare ore hits (not on normal iron/gold). */
    private void announceForgeBonus(Location forge, TeamColor team, Material ore) {
        String oreName = ore == Material.DIAMOND ? ChatColor.AQUA + "Diamond" : ChatColor.GREEN + "Emerald";
        String line = ChatColor.GREEN + "Iron Forge " + ChatColor.GRAY + "produced a " + oreName + ChatColor.GRAY + "!";
        Arena arena = manager.arena();
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (entry.getValue() != team || arena.eliminated().contains(entry.getKey())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            player.sendMessage(line);
        }
        for (Player nearby : forgeRecipients(forge, team)) Sounds.forgeCollect(nearby);
    }

    /** Teammates in share range each get a copy; else low ground drop. Enemies never share. */
    private void deliverForge(Location location, ItemStack stack, TeamColor team) {
        if (location == null || location.getWorld() == null || stack == null) return;
        List<Player> recipients = forgeRecipients(location, team);
        if (recipients.isEmpty()) {
            if (groundBlocked(Locations.forgeDropPoint(location), stack.getType(), forgeGroundCap(stack.getType(), team))) return;
            spawnForgeDrop(location, stack);
            return;
        }
        for (Player player : recipients) {
            giveForgeItem(player, stack.clone(), location);
        }
    }

    private List<Player> forgeRecipients(Location forge, TeamColor team) {
        Arena arena = manager.arena();
        List<Player> recipients = new ArrayList<Player>();
        double cx = forge.getBlockX() + 0.5;
        double cy = forge.getBlockY() + 0.5;
        double cz = forge.getBlockZ() + 0.5;
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (entry.getValue() != team || arena.eliminated().contains(entry.getKey())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.getWorld().equals(forge.getWorld())) continue;
            if (manager.isSoftSpectating(player)) continue;
            Location at = player.getLocation();
            if (GameRules.forgeShareInRange(at.getX() - cx, at.getY() - cy, at.getZ() - cz)) recipients.add(player);
        }
        return recipients;
    }

    private void giveForgeItem(Player player, ItemStack stack, Location forge) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            TeamColor team = manager.arena().team(player.getUniqueId());
            Location pin = Locations.forgeDropPoint(forge);
            if (team == null || !groundBlocked(pin, stack.getType(), forgeGroundCap(stack.getType(), team))) {
                for (ItemStack remain : leftover.values()) {
                    Item drop = player.getWorld().dropItem(player.getLocation().add(0, 0.2, 0), remain);
                    drop.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    drop.setPickupDelay(0);
                    manager.arena().generatedItems().add(drop.getUniqueId());
                }
            }
        }
        double cx = forge.getBlockX() + 0.5;
        double cz = forge.getBlockZ() + 0.5;
        Location at = player.getLocation();
        if (GameRules.forgeStandingInRange(at.getX() - cx, at.getZ() - cz)) Sounds.forgeCollect(player);
        else Sounds.forgeShare(player);
    }

    private void generator(final Location location, final ItemStack stack, final String kind, final int fallbackTicks) {
        final Arena arena = manager.arena();
        int id = new BukkitRunnable() {
            private int waited;
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                waited++;
                int seconds = Math.max(1, (generatorPeriod(kind, fallbackTicks) + 19) / 20);
                if (waited < seconds) return;
                waited = 0;
                if (groundBlocked(Locations.genDropPoint(location), stack.getType(), GameRules.generatorGroundCap(kind))) return;
                spawnGenDrop(location, stack);
            }
        }.runTaskTimer(manager.plugin(), 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    private void spawnGenDrop(Location location, ItemStack stack) {
        spawnPinnedDrop(Locations.genDropPoint(location), stack);
    }

    void spawnForgeDrop(Location location, ItemStack stack) {
        spawnPinnedDrop(Locations.forgeDropPoint(location), stack);
    }

    private boolean groundBlocked(Location at, Material material, int cap) {
        return GameRules.groundSpawnBlocked(groundAmount(at, material), cap);
    }

    /** Stack amounts of nearby Item entities of that material (not player inventory). */
    private int groundAmount(Location at, Material material) {
        if (at == null || at.getWorld() == null || material == null) return 0;
        double r = GameRules.GEN_GROUND_CAP_RADIUS;
        int total = 0;
        for (Entity entity : at.getWorld().getNearbyEntities(at, r, r, r)) {
            if (!(entity instanceof Item)) continue;
            ItemStack stack = ((Item) entity).getItemStack();
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private int forgeGroundCap(Material material, TeamColor team) {
        int level = manager.arena().forgeLevel(team);
        if (material == Material.IRON_INGOT) return GameRules.forgeIronGroundCap(level);
        if (material == Material.GOLD_INGOT) return GameRules.forgeGoldGroundCap(level);
        return Integer.MAX_VALUE;
    }

    private void spawnPinnedDrop(Location at, ItemStack stack) {
        Arena arena = manager.arena();
        if (arena.state() != Arena.State.RUNNING) return;
        if (at == null || at.getWorld() == null || stack == null) return;
        Item item = at.getWorld().dropItem(at, stack.clone());
        item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        item.setPickupDelay(10);
        try { item.getClass().getMethod("setInvulnerable", boolean.class).invoke(item, true); } catch (Throwable ignored) { }
        arena.generatedItems().add(item.getUniqueId());
        final Item pinnedItem = item;
        final Location pin = at.clone();
        Bukkit.getScheduler().runTask(manager.plugin(), new Runnable() {
            @Override public void run() {
                if (pinnedItem.isValid() && !pinnedItem.isDead()) {
                    pinnedItem.teleport(pin);
                    pinnedItem.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        });
    }

    private int generatorPeriod(String kind, int fallback) {
        int tier = kind.equals("diamond") ? diamondTier : kind.equals("emerald") ? emeraldTier : 1;
        if (tier == 1) return manager.plugin().getConfig().getInt("generator-periods." + kind, fallback);
        int tierFallback = kind.equals("diamond") ? (tier == 2 ? 460 : 240) : (tier == 2 ? 900 : 600);
        return manager.plugin().getConfig().getInt("generator-upgrades." + kind + ".tier-" + tier + "-period", tierFallback);
    }

    private int upgradeAt(String kind, int tier) {
        int fallback = kind.equals("diamond") ? (tier == 2 ? 360 : 720) : (tier == 2 ? 720 : 1080);
        return manager.plugin().getConfig().getInt("generator-upgrades." + kind + ".tier-" + tier + "-seconds", fallback);
    }

    private static String roman(int tier) {
        return new String[] {"I", "II", "III"}[Math.max(1, Math.min(3, tier)) - 1];
    }

}
