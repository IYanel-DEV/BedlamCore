package dev.iyanel.bedlamcore.config;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Loads {@code config.yml} + {@code game.yml} + {@code generators.yml} and pushes balance values into the
 * {@link GameRules} statics. Every read validates and clamps: a missing key keeps today's default, and a garbage
 * value ({@code abc} / {@code NaN} / out of range) logs a WARNING and falls back to the default — never crashes.
 *
 * Lookup order for a key is: the owning file (game/generators) first, then the legacy {@code config.yml} key
 * (so old single-file configs keep working), then the hardcoded default.
 */
public final class BedlamSettings {
    private final BedlamCore plugin;
    private FileConfiguration game;
    private FileConfiguration generators;

    public BedlamSettings(BedlamCore plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-read all three files and re-apply to GameRules. config.yml itself is reloaded by the caller. */
    public void reload() {
        game = load("game.yml");
        generators = load("generators.yml");
        applyToGameRules();
    }

    private FileConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            try { plugin.saveResource(name, false); } catch (IllegalArgumentException missingResource) { }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------------ clamped readers

    /** primary file if it sets the path, else config.yml, else default — validated and clamped to [min,max]. */
    private double num(FileConfiguration primary, String path, double def, double min, double max) {
        FileConfiguration src = primary != null && primary.isSet(path) ? primary
            : plugin.getConfig().isSet(path) ? plugin.getConfig() : null;
        if (src == null) return def;
        Object raw = src.get(path);
        if (!(raw instanceof Number)) { warn(path, raw, def); return def; }
        double v = ((Number) raw).doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v)) { warn(path, raw, def); return def; }
        if (v < min || v > max) {
            double clamped = Math.min(max, Math.max(min, v));
            warn(path, raw, clamped);
            return clamped;
        }
        return v;
    }

    private int gi(String path, int def, int min, int max) { return (int) Math.round(num(game, path, def, min, max)); }
    private double gd(String path, double def, double min, double max) { return num(game, path, def, min, max); }
    private double cd(String path, double def, double min, double max) { return num(plugin.getConfig(), path, def, min, max); }
    private int ci(String path, int def, int min, int max) { return (int) Math.round(cd(path, def, min, max)); }
    private int ni(String path, int def, int min, int max) { return (int) Math.round(num(generators, path, def, min, max)); }
    private long gl(String path, long def, long min, long max) { return (long) num(game, path, def, min, max); }

    private void warn(String path, Object raw, Object used) {
        plugin.getLogger().warning("[config] invalid value for '" + path + "' (" + raw + ") — using " + used);
    }

    // ------------------------------------------------------------------ typed getters used at call sites

    /** timers/mode caps read game.yml then config.yml (legacy top-level) then default. */
    public int countdownSeconds() { return gi("countdown-seconds", 10, 0, 3600); }
    public int endingSeconds() { return gi("ending-seconds", 8, 0, 3600); }
    public int respawnSeconds() { return gi("respawn-seconds", 5, 0, 3600); }

    public int minimumPlayers(String mode) {
        String m = mode == null ? "" : mode.toLowerCase();
        if (game != null && game.isSet("modes." + m + ".minimum-players")) return gi("modes." + m + ".minimum-players", 2, 1, 64);
        if (game != null && game.isSet("defaults.minimum-players")) return gi("defaults.minimum-players", 2, 1, 64);
        return ci("modes." + m + ".minimum-players", 2, 1, 64);
    }

    /** generator drop period (ticks) for kind at tier, from generators.yml then config.yml legacy then default. */
    public int generatorPeriod(String kind, int tier, int def) {
        if (tier <= 1) return ni("generator-periods." + kind, def, 1, 72000);
        return ni("generator-upgrades." + kind + ".tier-" + tier + "-period", def, 1, 72000);
    }

    public int generatorTierSeconds(String kind, int tier, int def) {
        return ni("generator-upgrades." + kind + ".tier-" + tier + "-seconds", def, 0, 86400);
    }

    // ------------------------------------------------------------------ push balance values into GameRules

    private void applyToGameRules() {
        // Rewards (game.yml). counts >= 0.
        GameRules.TOKENS_PLAY = gi("rewards.tokens.play", 10, 0, 1_000_000);
        GameRules.TOKENS_KILL = gi("rewards.tokens.kill", 5, 0, 1_000_000);
        GameRules.TOKENS_FINAL_KILL = gi("rewards.tokens.final-kill", 10, 0, 1_000_000);
        GameRules.TOKENS_BED = gi("rewards.tokens.bed", 25, 0, 1_000_000);
        GameRules.TOKENS_WIN = gi("rewards.tokens.win", 50, 0, 1_000_000);
        GameRules.XP_PLAY = gi("rewards.xp.play", 25, 0, 1_000_000);
        GameRules.XP_KILL = gi("rewards.xp.kill", 10, 0, 1_000_000);
        GameRules.XP_FINAL_KILL = gi("rewards.xp.final-kill", 25, 0, 1_000_000);
        GameRules.XP_BED = gi("rewards.xp.bed", 50, 0, 1_000_000);
        GameRules.XP_WIN = gi("rewards.xp.win", 100, 0, 1_000_000);
        GameRules.XP_PER_LEVEL = gi("progression.xp-per-level", 5000, 1, 100_000_000);
        GameRules.XP_BAR_SLOTS = gi("progression.xp-bar-slots", 10, 1, 100);

        // Potions (game.yml). durations ticks, amplifiers >=0, costs >=0.
        GameRules.POTION_SPEED_TICKS = gi("potions.speed.ticks", 900, 1, 72000);
        GameRules.POTION_JUMP_TICKS = gi("potions.jump.ticks", 900, 1, 72000);
        GameRules.POTION_INVIS_TICKS = gi("potions.invis.ticks", 600, 1, 72000);
        GameRules.POTION_SPEED_AMPLIFIER = gi("potions.speed.amplifier", 1, 0, 255);
        GameRules.POTION_JUMP_AMPLIFIER = gi("potions.jump.amplifier", 4, 0, 255);
        GameRules.POTION_SPEED_COST_EMERALD = gi("potions.speed.cost-emerald", 1, 0, 1000);
        GameRules.POTION_JUMP_COST_EMERALD = gi("potions.jump.cost-emerald", 1, 0, 1000);
        GameRules.POTION_INVIS_COST_EMERALD = gi("potions.invis.cost-emerald", 2, 0, 1000);

        // Tools / combat / bridge egg / fireball / heal pool (game.yml).
        GameRules.TOOL_TIER_MAX = gi("tools.tool-tier-max", 4, 1, 16);
        GameRules.BUILD_FLOOR_BELOW = gi("combat.build-floor-below", 7, 0, 256);
        GameRules.COMBAT_CREDIT_MILLIS = gl("combat.combat-credit-ms", 15_000L, 0, 600_000);
        GameRules.SPAWN_PROTECTION_SECONDS = gi("combat.spawn-protection-seconds", 0, 0, 3600);
        GameRules.HEAL_POOL_RADIUS = gd("combat.heal-pool-radius", 8.0, 0, 256);
        GameRules.KILL_LOOT_IRON = gd("combat.kill-loot.iron", 1.0, 0, 1);
        GameRules.KILL_LOOT_GOLD = gd("combat.kill-loot.gold", 0.5, 0, 1);
        GameRules.KILL_LOOT_DIAMOND = gd("combat.kill-loot.diamond", 0.5, 0, 1);
        GameRules.KILL_LOOT_EMERALD = gd("combat.kill-loot.emerald", 0.5, 0, 1);
        GameRules.DEFENDER_LIFETIME_MILLIS = (long) (gd("combat.dream-defender-seconds", 240, 1, 86400) * 1000L);
        GameRules.FIREBALL_YIELD = (float) gd("combat.fireball.yield", 0.0, 0, 100);
        GameRules.FIREBALL_RADIUS = gd("combat.fireball.radius", 3.5, 0, 256);
        GameRules.FIREBALL_KB_HORIZONTAL = gd("combat.fireball.kb-horizontal", 1.75, 0, 100);
        GameRules.FIREBALL_KB_VERTICAL = gd("combat.fireball.kb-vertical", 0.5, 0, 100);
        GameRules.BRIDGE_EGG_MAX_PATH = gi("combat.bridge-egg.max-path", 20, 1, 1000);
        GameRules.BRIDGE_EGG_DIP_START = gd("combat.bridge-egg.dip-start", 0.6, 0, 1);
        GameRules.BRIDGE_EGG_MAX_TICKS = gi("combat.bridge-egg.max-ticks", 40, 1, 6000);
        GameRules.BRIDGE_EGG_MAX_DISTANCE = gd("combat.bridge-egg.max-distance", 20.0, 1, 1000);

        // Traps (game.yml).
        GameRules.TRAP_TRIGGER_RADIUS = gd("traps.trigger-radius", 10.0, 0, 256);
        GameRules.TRAP_PAD_XZ = gd("traps.pad-xz", 4.0, 0, 256);
        GameRules.TRAP_Y_BELOW_SPAWN = gd("traps.y-below", 2.0, 0, 256);
        GameRules.TRAP_Y_ABOVE_SPAWN = gd("traps.y-above", 8.0, 0, 256);
        GameRules.TRAP_FORGE_NATURAL_RANGE = gd("traps.forge-natural-range", 12.0, 0, 256);
        GameRules.TRAP_QUEUE_MAX = gi("traps.queue-max", 3, 1, 64);
        GameRules.TRAP_COOLDOWN_TICKS = gi("traps.cooldown-ticks", 40, 0, 72000);
        GameRules.MAGIC_MILK_IMMUNITY_MILLIS = gl("traps.magic-milk-immunity-ms", 30_000L, 0, 3_600_000);

        // Team upgrade / trap diamond costs (game.yml upgrades.*). Single-value or per-level lists.
        GameRules.SHARPNESS_COST = gi("upgrades.sharpened-swords", 4, 0, 1_000_000);
        GameRules.HEAL_POOL_COST = gi("upgrades.heal-pool", 1, 0, 1_000_000);
        GameRules.PROTECTION_COSTS = intList("upgrades.reinforced-armor", new int[]{2, 4, 8, 16});
        GameRules.HASTE_COSTS = intList("upgrades.maniac-miner", new int[]{2, 4});
        GameRules.FORGE_UPGRADE_COSTS = intList("upgrades.iron-forge", new int[]{2, 4, 6, 8});
        GameRules.BOOTS_COSTS = intList("upgrades.cushioned-boots", new int[]{1, 2});
        GameRules.TRAP_QUEUE_COSTS = intList("upgrades.trap-queue", new int[]{1, 2, 4});

        // Display / holograms / protect (config.yml per spec).
        GameRules.PROTECT_PLUS = ci("protect-plus", 1, 0, 64);
        GameRules.DISPLAY_VIEW = cd("display.view", 20.0, 1, 256);
        GameRules.DISPLAY_VISIBILITY_INTERVAL = ci("display.visibility-interval", 5, 1, 1200);
        GameRules.HOLO_LINE = cd("holograms.line-gap", 0.30, 0.01, 16);
        GameRules.NPC_HOLO_TOP = cd("holograms.npc-top", 2.25, 0, 64);
        GameRules.LOBBY_NPC_HOLO_TOP = cd("holograms.lobby-npc-top", 2.95, 0, 64);
        GameRules.GEN_HOLO_TOP = cd("holograms.gen-top", 3.15, 0, 64);
        GameRules.CHEST_HOLO_Y = cd("holograms.chest-y", 1.1, 0, 64);
        GameRules.GEN_STAND_Y = cd("holograms.gen-stand-y", 2.5, 0, 64);
        GameRules.recomputeHoloLines();

        // Generators engine (generators.yml + config.yml legacy generator-* / GameRules).
        GameRules.FORGE_SHARE_RADIUS = ng("share-radius", 2.5, 0, 256);
        GameRules.FORGE_STANDING_RADIUS = ng("standing-radius", 1.2, 0, 256);
        GameRules.FORGE_SHARE_Y = ng("share-y-slack", 3.0, 0, 256);
        GameRules.FORGE_DROP_Y = ng("drop-y", 0.15, 0, 64);
        GameRules.GEN_GROUND_CAP_RADIUS = ng("ground-cap-radius", 2.5, 0, 256);
        GameRules.FORGE_LEVEL_MAX = (int) ng("forge-level-max", 4, 1, 64);
        GameRules.FORGE_L2_DIAMOND = ng("forge-bonus.l2-diamond", 0.02, 0, 1);
        GameRules.FORGE_L2_EMERALD = ng("forge-bonus.l2-emerald", 0.01, 0, 1);
        GameRules.FORGE_L3_DIAMOND = ng("forge-bonus.l3-diamond", 0.04, 0, 1);
        GameRules.FORGE_L3_EMERALD = ng("forge-bonus.l3-emerald", 0.025, 0, 1);
        GameRules.GEN_DIAMOND_GROUND_CAP = (int) ng("caps.diamond", 4, 0, 4096);
        GameRules.GEN_EMERALD_GROUND_CAP = (int) ng("caps.emerald", 4, 0, 4096);
        GameRules.FORGE_IRON_GROUND_CAP = (int) ng("caps.iron", 64, 0, 4096);
        GameRules.FORGE_GOLD_GROUND_CAP = (int) ng("caps.gold", 16, 0, 4096);
        GameRules.FORGE_IRON_GROUND_CAP_MAXED = (int) ng("caps.iron-maxed", 124, 0, 4096);
        GameRules.FORGE_GOLD_GROUND_CAP_MAXED = (int) ng("caps.gold-maxed", 32, 0, 4096);

        // Party system (config.yml party.*). Booleans read directly; numerics clamped like the rest.
        GameRules.PARTY_ENABLED = plugin.getConfig().getBoolean("party.enabled", true);
        GameRules.PARTY_MAX_SIZE = ci("party.max-size", 4, 2, 16);
        GameRules.PARTY_INVITE_TIMEOUT = ci("party.invite-timeout-seconds", 60, 1, 3600);
        GameRules.PARTY_QUEUE_AS_TEAM = plugin.getConfig().getBoolean("party.queue-as-team", true);
        GameRules.PARTY_ALLOW_OPEN = plugin.getConfig().getBoolean("party.allow-party-in-open-games", false);
        GameRules.PARTY_PERSISTENT = plugin.getConfig().getBoolean("party.persistent", false);

        // Leaderboards (config.yml leaderboards.*). Booleans/strings read directly; numerics clamped.
        GameRules.LEADERBOARD_ENABLED = plugin.getConfig().getBoolean("leaderboards.enabled", true);
        GameRules.LEADERBOARD_TOP_N = ci("leaderboards.top-n", 10, 1, 50);
        GameRules.LEADERBOARD_MIN_GAMES = ci("leaderboards.minimum-games", 1, 0, 1_000_000);
        GameRules.LEADERBOARD_REFRESH_SECONDS = ci("leaderboards.refresh-seconds", 30, 1, 3600);
        GameRules.LEADERBOARD_MAX_ROWS_PER_PAGE = ci("leaderboards.max-rows-per-page", 45, 9, 45);
        GameRules.LEADERBOARD_COMMAND_MAX_LINES = ci("leaderboards.command-max-lines", 15, 1, 50);
        GameRules.LEADERBOARD_NPC_ENABLED = plugin.getConfig().getBoolean("leaderboards.npc.enabled", true);
        GameRules.LEADERBOARD_NPC_TITLE = plugin.getConfig().getString("leaderboards.npc.title", "&6&lBED WARS &f&lLEADERBOARDS");
        GameRules.LEADERBOARD_NPC_SUBTITLE = plugin.getConfig().getString("leaderboards.npc.subtitle", "&7Click for details");
        GameRules.LEADERBOARD_FMT_FIRST = plugin.getConfig().getString("leaderboards.formats.first", "&6");
        GameRules.LEADERBOARD_FMT_SECOND = plugin.getConfig().getString("leaderboards.formats.second", "&7");
        GameRules.LEADERBOARD_FMT_THIRD = plugin.getConfig().getString("leaderboards.formats.third", "&e");
        GameRules.LEADERBOARD_FMT_OTHER = plugin.getConfig().getString("leaderboards.formats.other", "&8");
        GameRules.LEADERBOARD_FMT_VALUE = plugin.getConfig().getString("leaderboards.formats.value", "&b");
        GameRules.LEADERBOARD_FMT_YOU = plugin.getConfig().getString("leaderboards.formats.you", "&a");

        // Shop offers: apply game.yml shop.items.<key> overrides onto the code defaults.
        dev.iyanel.bedlamcore.gui.ShopCatalog.reload(game);
    }

    /** generators.yml value else default, clamped — generators keys live under their own file. */
    private double ng(String path, double def, double min, double max) { return num(generators, path, def, min, max); }

    /** Per-level cost list from game.yml (all entries >= 0), else the default array. */
    private int[] intList(String path, int[] def) {
        if (game == null || !game.isList(path)) return def;
        List<Integer> list = game.getIntegerList(path);
        if (list.isEmpty()) return def;
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            int v = list.get(i);
            if (v < 0) { warn(path, list, "default"); return def; }
            out[i] = v;
        }
        return out;
    }
}
