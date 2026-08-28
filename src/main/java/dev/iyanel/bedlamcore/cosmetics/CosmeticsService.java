package dev.iyanel.bedlamcore.cosmetics;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Particles;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lobby cosmetics shop + match hooks. Catalog from config.yml; owned/equipped in stats.yml. */
public final class CosmeticsService {
    public static final String CAT_KILL_MESSAGE = "KILL_MESSAGE";
    public static final String CAT_KILL_EFFECT = "KILL_EFFECT";
    public static final String CAT_WIN_EFFECT = "WIN_EFFECT";
    public static final String CAT_WOOD_SKIN = "WOOD_SKIN";
    public static final String CAT_FINAL_KILL_EFFECT = "FINAL_KILL_EFFECT";
    public static final String CAT_PRESTIGE = "PRESTIGE";
    public static final String CAT_PROJECTILE_TRAIL = "PROJECTILE_TRAIL";
    public static final String CAT_BED_DESTROY = "BED_DESTROY";
    public static final String CAT_SHOPKEEPER_SKIN = "SHOPKEEPER_SKIN";

    private final BedlamCore plugin;
    private final Map<String, Cosmetic> byId = new LinkedHashMap<String, Cosmetic>();
    private final Map<String, List<Cosmetic>> byCategory = new LinkedHashMap<String, List<Cosmetic>>();
    /** Victory win-effect + mounted-dragon subsystem (its own Listener). */
    private final WinEffectController win;

    public CosmeticsService(BedlamCore plugin) {
        this.plugin = plugin;
        reload();
        this.win = new WinEffectController(plugin, this);
    }

    public void reload() {
        byId.clear();
        byCategory.clear();
        byCategory.put(CAT_KILL_MESSAGE, new ArrayList<Cosmetic>());
        byCategory.put(CAT_KILL_EFFECT, new ArrayList<Cosmetic>());
        byCategory.put(CAT_WIN_EFFECT, new ArrayList<Cosmetic>());
        byCategory.put(CAT_WOOD_SKIN, new ArrayList<Cosmetic>());
        byCategory.put(CAT_FINAL_KILL_EFFECT, new ArrayList<Cosmetic>());
        byCategory.put(CAT_PRESTIGE, new ArrayList<Cosmetic>());
        byCategory.put(CAT_PROJECTILE_TRAIL, new ArrayList<Cosmetic>());
        byCategory.put(CAT_BED_DESTROY, new ArrayList<Cosmetic>());
        byCategory.put(CAT_SHOPKEEPER_SKIN, new ArrayList<Cosmetic>());
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection root = config.getConfigurationSection("cosmetics.items");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;
                String category = normalizeCategory(section.getString("category", ""));
                if (category == null) continue;
                String name = color(section.getString("name", id));
                int cost = Math.max(0, section.getInt("cost", 100));
                Map<String, String> messages = readMessages(section);
                String effect = section.getString("effect", "");
                List<String> particles = section.getStringList("particles");
                put(new Cosmetic(id, category, name, cost, messages, effect,
                    particles == null ? Collections.<String>emptyList() : particles));
            }
        }
        // Always merge built-in catalog so old server config.yml still gets the full shop.
        loadDefaults();
    }

    private static Map<String, String> readMessages(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        ConfigurationSection nested = section.getConfigurationSection("messages");
        if (nested != null) {
            for (String key : nested.getKeys(false)) {
                String value = nested.getString(key);
                if (value != null && !value.isEmpty()) out.put(key.toLowerCase(), value);
            }
        }
        String single = section.getString("message");
        if (single != null && !single.isEmpty() && !out.containsKey("kill")) out.put("kill", single);
        return out;
    }

    private void loadDefaults() {
        // Legacy single-line packs (kept so already-owned IDs still resolve).
        pack("km_crushed", "&cCrushed", 100,
            "%killer% &7crushed %victim%&7.",
            "%victim% &7was crushed into the void by %killer%&7.",
            "%victim% &7was crushed from afar by %killer%&7.",
            "%victim% &7was crushed off an edge by %killer%&7.",
            "%victim% &7was crushed in an explosion by %killer%&7.",
            "%victim% &7was crushed in flames by %killer%&7.");
        pack("km_rekt", "&6Rekt", 150,
            "%victim% &7got rekt by %killer%&7.",
            "%victim% &7got rekt into the void by %killer%&7.",
            "%victim% &7got sniped and rekt by %killer%&7.",
            "%victim% &7got rekt off a ledge by %killer%&7.",
            "%victim% &7got blown up and rekt by %killer%&7.",
            "%victim% &7got roasted by %killer%&7.");
        pack("km_swept", "&bSwept Away", 200,
            "%killer% &7swept %victim% &7off the map.",
            "%killer% &7swept %victim% &7into the void.",
            "%killer% &7swept %victim% &7away with a shot.",
            "%killer% &7swept %victim% &7off an edge.",
            "%killer% &7swept %victim% &7away in a blast.",
            "%killer% &7swept %victim% &7into the flames.");
        pack("km_obliterated", "&4Obliterated", 250,
            "%victim% &7was obliterated by %killer%&7.",
            "%victim% &7was obliterated into the void by %killer%&7.",
            "%victim% &7was obliterated from range by %killer%&7.",
            "%victim% &7was obliterated off a cliff by %killer%&7.",
            "%victim% &7was obliterated in a blast by %killer%&7.",
            "%victim% &7was obliterated in fire by %killer%&7.");

        // Hypixel Bed Wars–inspired kill message packs (per-cause templates).
        pack("km_fire", "&6Fire", 100,
            "%victim% &7was struck down by %killer%&7.",
            "%victim% &7was melted by %killer%&7.",
            "%victim% &7was turned to ash by %killer%&7.",
            "%victim% &7was turned to dust by %killer%&7.",
            "%victim% &7was fried by %killer%&7.",
            "%victim% &7was incinerated by %killer%&7.");
        pack("km_western", "&eWestern", 100,
            "%victim% &7was filled full of lead by %killer%&7.",
            "%victim% &7met their end by %killer%&7.",
            "%victim% &7lost the draw to %killer%&7.",
            "%victim% &7lost a drinking contest with %killer%&7.",
            "%victim% &7was killed with dynamite by %killer%&7.",
            "%victim% &7was iced by %killer%&7.");
        pack("km_honourable", "&aHonourable", 100,
            "%victim% &7died in close combat to %killer%&7.",
            "%victim% &7fought to the edge with %killer%&7.",
            "%victim% &7fell to the great marksmanship of %killer%&7.",
            "%victim% &7stumbled off a ledge with help by %killer%&7.",
            "%victim% &7tangoed with %killer%&7.",
            "%victim% &7had to raise the white flag to %killer%&7.");
        pack("km_love", "&dLove", 200,
            "%victim% &7was given the cold shoulder by %killer%&7.",
            "%victim% &7was hit off by a love bomb from %killer%&7.",
            "%victim% &7was struck with Cupid's arrow by %killer%&7.",
            "%victim% &7was out of the league of %killer%&7.",
            "%victim% &7was no match for %killer%&7.",
            "%victim% &7was dismantled by %killer%&7.");
        pack("km_bbq", "&cBBQ", 200,
            "%victim% &7was glazed in BBQ sauce by %killer%&7.",
            "%victim% &7slipped in BBQ sauce off the edge spilled by %killer%&7.",
            "%victim% &7was thrown chili powder at by %killer%&7.",
            "%victim% &7was not spicy enough for %killer%&7.",
            "%victim% &7was deep fried by %killer%&7.",
            "%victim% &7was sliced up by %killer%&7.");
        pack("km_woof", "&6Woof Woof", 200,
            "%victim% &7was bitten by %killer%&7.",
            "%victim% &7howled into the void for %killer%&7.",
            "%victim% &7caught the ball thrown by %killer%&7.",
            "%victim% &7was distracted by a puppy placed by %killer%&7.",
            "%victim% &7played too rough with %killer%&7.",
            "%victim% &7was ripped apart by %killer%&7.");
        pack("km_pirate", "&9Pirate", 300,
            "%victim% &7be sent to Davy Jones' locker by %killer%&7.",
            "%victim% &7be cannonballed to death by %killer%&7.",
            "%victim% &7be shot and killed by %killer%&7.",
            "%victim% &7be killed with magic by %killer%&7.",
            "%victim% &7be blown to bits by %killer%&7.",
            "%victim% &7be killed with metal by %killer%&7.");
        pack("km_spooky", "&5Literally Spooky", 350,
            "%victim% &7was spooked by %killer%&7.",
            "%victim% &7was spooked off the map by %killer%&7.",
            "%victim% &7was remotely spooked by %killer%&7.",
            "%victim% &7was totally spooked by %killer%&7.",
            "%victim% &7was blasted spooky by %killer%&7.",
            "%victim% &7was spooked in flames by %killer%&7.");
        pack("km_memed", "&bMemed", 350,
            "%victim% &7got rekt by %killer%&7.",
            "%victim% &7took the L to %killer%&7.",
            "%victim% &7got smacked by %killer%&7.",
            "%victim% &7got roasted by %killer%&7.",
            "%victim% &7got bamboozled by %killer%&7.",
            "%victim% &7got memed by %killer%&7.");
        pack("km_dramatic", "&4Dramatic", 350,
            "%victim% &7was tragically backstabbed by %killer%&7.",
            "%victim% &7was heartlessly let go by %killer%&7.",
            "%victim% &7's heart was pierced by %killer%&7.",
            "%victim% &7was delivered into nothingness by %killer%&7.",
            "%victim% &7was dreadfully corrupted by %killer%&7.",
            "%victim% &7was dismembered by %killer%&7.");
        pack("km_snow", "&fSnow Storm", 350,
            "%victim% &7was locked outside during a snow storm by %killer%&7.",
            "%victim% &7was pushed into a snowbank by %killer%&7.",
            "%victim% &7was hit with a snowball from %killer%&7.",
            "%victim% &7was shoved down an icy slope by %killer%&7.",
            "%victim% &7got snowed in by %killer%&7.",
            "%victim% &7was made into a snowman by %killer%&7.");
        pack("km_eggy", "&eEggy", 350,
            "%victim% &7was painted pretty by %killer%&7.",
            "%victim% &7was deviled into the void by %killer%&7.",
            "%victim% &7slipped into a pan placed by %killer%&7.",
            "%victim% &7was flipped off the edge by %killer%&7.",
            "%victim% &7was scrambled by %killer%&7.",
            "%victim% &7was made sunny side up by %killer%&7.");
        pack("km_celebratory", "&dCelebratory", 350,
            "%victim% &7was whacked with a party balloon by %killer%&7.",
            "%victim% &7was popped into the void by %killer%&7.",
            "%victim% &7was shot with a roman candle by %killer%&7.",
            "%victim% &7was launched like a firework by %killer%&7.",
            "%victim% &7exploded from a firework by %killer%&7.",
            "%victim% &7was lit up by %killer%&7.");
        pack("km_wrapped", "&cWrapped Up", 350,
            "%victim% &7was wrapped up by %killer%&7.",
            "%victim% &7was tied into a bow by %killer%&7.",
            "%victim% &7was glued up by %killer%&7.",
            "%victim% &7tripped over a present placed by %killer%&7.",
            "%victim% &7was stuffed with tissue paper by %killer%&7.",
            "%victim% &7was taped together by %killer%&7.");
        pack("km_moon", "&bTo The Moon", 300,
            "%victim% &7was crushed into moon dust by %killer%&7.",
            "%victim% &7was sent the wrong way by %killer%&7.",
            "%victim% &7was hit by an asteroid from %killer%&7.",
            "%victim% &7was blasted to the moon by %killer%&7.",
            "%victim% &7was blasted to dust by %killer%&7.",
            "%victim% &7was blown up by %killer%&7.");
        pack("km_festive", "&aFestive", 350,
            "%victim% &7was smothered in holiday cheer by %killer%&7.",
            "%victim% &7was banished into the ether by %killer%&7's holiday spirit.",
            "%victim% &7was sniped by a missile of festivity by %killer%&7.",
            "%victim% &7was pushed by %killer%&7's holiday spirit.",
            "%victim% &7was melted by %killer%&7's holiday spirit.",
            "%victim% &7was sung holiday tunes to by %killer%&7.");
        pack("km_roar", "&6Roar", 300,
            "%victim% &7was ripped to shreds by %killer%&7.",
            "%victim% &7was charged by %killer%&7.",
            "%victim% &7was pounced on by %killer%&7.",
            "%victim% &7was ripped and thrown by %killer%&7.",
            "%victim% &7was mauled in a blast by %killer%&7.",
            "%victim% &7was ripped to shreds by %killer%&7.");
        pack("km_buzz", "&eBuzz", 200,
            "%victim% &7was buzzed to death by %killer%&7.",
            "%victim% &7was bzzz'd into the void by %killer%&7.",
            "%victim% &7was startled by %killer%&7.",
            "%victim% &7was stung off the edge by %killer%&7.",
            "%victim% &7was stung by %killer%&7.",
            "%victim% &7was bee'd by %killer%&7.");
        pack("km_oink", "&dOink", 200,
            "%victim% &7was oinked by %killer%&7.",
            "%victim% &7slipped into void for %killer%&7.",
            "%victim% &7got attacked by a carrot from %killer%&7.",
            "%victim% &7was distracted by a piglet from %killer%&7.",
            "%victim% &7was gulped by %killer%&7.",
            "%victim% &7was oinked by %killer%&7.");
        pack("km_squeak", "&7Squeak", 200,
            "%victim% &7was chewed up by %killer%&7.",
            "%victim% &7was scared into the void by %killer%&7.",
            "%victim% &7stepped in a mouse trap placed by %killer%&7.",
            "%victim% &7was distracted by a rat dragging pizza from %killer%&7.",
            "%victim% &7squeaked apart by %killer%&7.",
            "%victim% &7squeaked around with %killer%&7.");
        pack("km_ox", "&6Ox'd", 200,
            "%victim% &7was trampled by %killer%&7.",
            "%victim% &7was back kicked into the void by %killer%&7.",
            "%victim% &7was impaled from a distance by %killer%&7.",
            "%victim% &7was headbutted off a cliff by %killer%&7.",
            "%victim% &7was impaled by %killer%&7.",
            "%victim% &7was trampled by %killer%&7.");
        pack("km_primal", "&cPrimal", 250,
            "%victim% &7was hunted down by %killer%&7.",
            "%victim% &7was thrown into a volcano by %killer%&7.",
            "%victim% &7got skewered by %killer%&7.",
            "%victim% &7stumbled on a trap set by %killer%&7.",
            "%victim% &7was sacrificed by %killer%&7.",
            "%victim% &7was mauled by %killer%&7.");
        pack("km_santa", "&cSanta's Workshop", 250,
            "%victim% &7was wrapped into a gift by %killer%&7.",
            "%victim% &7hit the hard-wood floor because of %killer%&7.",
            "%victim% &7was put on the naughty list by %killer%&7.",
            "%victim% &7was pushed down a slope by %killer%&7.",
            "%victim% &7was traded in for milk and cookies by %killer%&7.",
            "%victim% &7was turned to gingerbread by %killer%&7.");
        pack("km_bridging", "&aBridging for Dummies", 500,
            "%victim% &7had a small brain moment while fighting %killer%&7.",
            "%victim% &7was not able to block clutch against %killer%&7.",
            "%victim% &7got 360 no-scoped by %killer%&7.",
            "%victim% &7forgot how many blocks they had left while fighting %killer%&7.",
            "%victim% &7got absolutely destroyed by %killer%&7.",
            "%victim% &7has left the game after seeing %killer%&7.");
        pack("km_oldman", "&eOld Man", 500,
            "%victim% &7was yelled at by %killer%&7.",
            "%victim% &7was thrown off the lawn by %killer%&7.",
            "%victim% &7was accidentally spit on by %killer%&7.",
            "%victim% &7slipped on the fake teeth of %killer%&7.",
            "%victim% &7was sold in a garage sale by %killer%&7.",
            "%victim% &7was chased away by %killer%&7.");

        add("ke_blood", CAT_KILL_EFFECT, "&cBlood", 125, Collections.<String, String>emptyMap(), "", list("REDSTONE", "CRIT", "CRITICAL_HIT"));
        add("ke_flame", CAT_KILL_EFFECT, "&6Flame", 150, Collections.<String, String>emptyMap(), "", list("FLAME", "SMOKE"));
        add("ke_spark", CAT_KILL_EFFECT, "&eSpark", 175, Collections.<String, String>emptyMap(), "", list("FIREWORKS_SPARK", "FIREWORK", "CRIT"));
        add("ke_smoke", CAT_KILL_EFFECT, "&8Smoke Bomb", 100, Collections.<String, String>emptyMap(), "", list("LARGE_SMOKE", "SMOKE", "CLOUD"));

        // Hypixel Bed Wars–inspired win effects (particle/entity shows; timed playback in playWinEffect).
        add("we_firework", CAT_WIN_EFFECT, "&dFirework", 200, Collections.<String, String>emptyMap(), "firework", null);
        add("we_lightning", CAT_WIN_EFFECT, "&bLightning", 250, Collections.<String, String>emptyMap(), "lightning", null);
        add("we_burst", CAT_WIN_EFFECT, "&aBurst", 175, Collections.<String, String>emptyMap(), "burst", list("EXPLOSION_LARGE", "EXPLOSION", "FLAME"));
        add("we_hearts", CAT_WIN_EFFECT, "&cHearts", 150, Collections.<String, String>emptyMap(), "hearts", list("HEART", "VILLAGER_HAPPY", "HAPPY_VILLAGER"));
        add("we_cold_snap", CAT_WIN_EFFECT, "&bCold Snap", 200, Collections.<String, String>emptyMap(), "cold_snap",
            list("SNOWBALL", "SNOW_SHOVEL", "CLOUD", "FIREWORKS_SPARK"));
        add("we_burning_soul", CAT_WIN_EFFECT, "&6Burning Soul", 300, Collections.<String, String>emptyMap(), "burning_soul",
            list("SOUL_FIRE_FLAME", "FLAME", "SMOKE", "LARGE_SMOKE"));
        add("we_notes", CAT_WIN_EFFECT, "&dNotes", 250, Collections.<String, String>emptyMap(), "notes",
            list("NOTE", "NOTE_BLOCK", "VILLAGER_HAPPY"));
        add("we_blood", CAT_WIN_EFFECT, "&4Blood Explosion", 350, Collections.<String, String>emptyMap(), "blood",
            list("REDSTONE", "CRIT", "CRITICAL_HIT", "DAMAGE_INDICATOR"));
        add("we_cookie", CAT_WIN_EFFECT, "&6Cookie Fountain", 300, Collections.<String, String>emptyMap(), "cookie",
            list("CRIT", "VILLAGER_HAPPY", "HAPPY_VILLAGER", "CLOUD"));
        add("we_campfire", CAT_WIN_EFFECT, "&6Campfire", 275, Collections.<String, String>emptyMap(), "campfire",
            list("FLAME", "LAVA", "LARGE_SMOKE", "SMOKE"));
        add("we_glyphs", CAT_WIN_EFFECT, "&5Glyphs", 350, Collections.<String, String>emptyMap(), "glyphs",
            list("ENCHANTMENT_TABLE", "ENCHANT", "END_ROD", "CRIT"));
        add("we_snowball", CAT_WIN_EFFECT, "&fSnowball Fight", 225, Collections.<String, String>emptyMap(), "snowball",
            list("SNOWBALL", "SNOW_SHOVEL", "CLOUD", "CRIT"));
        add("we_tornado", CAT_WIN_EFFECT, "&7Tornado", 400, Collections.<String, String>emptyMap(), "tornado",
            list("CLOUD", "SMOKE", "CRIT", "SPELL"));
        add("we_meteor", CAT_WIN_EFFECT, "&cMeteor Shower", 400, Collections.<String, String>emptyMap(), "meteor",
            list("FLAME", "LAVA", "EXPLOSION", "SMOKE"));
        add("we_sparkler", CAT_WIN_EFFECT, "&eSparkler", 225, Collections.<String, String>emptyMap(), "sparkler",
            list("FIREWORKS_SPARK", "FIREWORK", "CRIT", "FLAME"));
        add("we_portal", CAT_WIN_EFFECT, "&5Portal", 300, Collections.<String, String>emptyMap(), "portal",
            list("PORTAL", "SPELL_WITCH", "SPELL", "CRIT"));
        add("we_rainbow", CAT_WIN_EFFECT, "&dRainbow", 450, Collections.<String, String>emptyMap(), "rainbow",
            list("REDSTONE", "SPELL_MOB", "SPELL", "CRIT", "FIREWORKS_SPARK"));
        add("we_anvil", CAT_WIN_EFFECT, "&8Anvil Rain", 350, Collections.<String, String>emptyMap(), "anvil",
            list("CRIT", "CRITICAL_HIT", "SMOKE", "CLOUD"));
        add("we_dragon", CAT_WIN_EFFECT, "&5Dragon", 500, Collections.<String, String>emptyMap(), "dragon",
            list("FLAME", "PORTAL", "SMOKE", "CRIT"));
        add("we_wither", CAT_WIN_EFFECT, "&8Wither", 500, Collections.<String, String>emptyMap(), "wither",
            list("SMOKE", "LARGE_SMOKE", "CRIT"));

        // Wood Skins — colored wood-block appearances (particle feedback on place; textures need a resource pack).
        add("ws_cherry", CAT_WOOD_SKIN, "&cCherry Wood", 200, Collections.<String, String>emptyMap(), "cherry", list("FLAME", "REDSTONE"));
        add("ws_oak", CAT_WOOD_SKIN, "&6Golden Oak", 150, Collections.<String, String>emptyMap(), "oak", list("FLAME", "VILLAGER_HAPPY"));
        add("ws_spruce", CAT_WOOD_SKIN, "&1Dark Spruce", 175, Collections.<String, String>emptyMap(), "spruce", list("SMOKE", "CLOUD"));
        add("ws_birch", CAT_WOOD_SKIN, "&fWhite Birch", 175, Collections.<String, String>emptyMap(), "birch", list("CLOUD", "SPELL"));
        add("ws_jungle", CAT_WOOD_SKIN, "&2Tropical Jungle", 200, Collections.<String, String>emptyMap(), "jungle", list("VILLAGER_HAPPY", "HAPPY_VILLAGER"));
        add("ws_acacia", CAT_WOOD_SKIN, "&4Acacia Orange", 200, Collections.<String, String>emptyMap(), "acacia", list("FLAME", "SMOKE"));
        add("ws_dark_oak", CAT_WOOD_SKIN, "&8Dark Oak", 225, Collections.<String, String>emptyMap(), "dark_oak", list("SMOKE", "LARGE_SMOKE"));
        add("ws_crimson", CAT_WOOD_SKIN, "&5Crimson", 300, Collections.<String, String>emptyMap(), "crimson", list("SOUL_FIRE_FLAME", "PORTAL"));
        add("ws_warped", CAT_WOOD_SKIN, "&3Warped", 300, Collections.<String, String>emptyMap(), "warped", list("SOUL_FIRE_FLAME", "SPELL"));

        // Final Kill Effects — dramatic particle/entity shows on a permanent (bed-gone) death.
        add("fke_soul_rip", CAT_FINAL_KILL_EFFECT, "&5Soul Rip", 300, Collections.<String, String>emptyMap(), "soul_rip",
            list("PORTAL", "SPELL_WITCH", "ENCHANTMENT_TABLE", "SMOKE"));
        add("fke_blood_burst", CAT_FINAL_KILL_EFFECT, "&4Blood Burst", 250, Collections.<String, String>emptyMap(), "blood_burst",
            list("REDSTONE", "CRIT", "CRITICAL_HIT", "DAMAGE_INDICATOR"));
        add("fke_lightning_strike", CAT_FINAL_KILL_EFFECT, "&eLightning Strike", 350, Collections.<String, String>emptyMap(), "lightning_strike",
            list("FIREWORK", "EXPLOSION", "FIREWORKS_SPARK", "CLOUD"));
        add("fke_void_collapse", CAT_FINAL_KILL_EFFECT, "&8Void Collapse", 400, Collections.<String, String>emptyMap(), "void_collapse",
            list("PORTAL", "SMOKE", "LARGE_SMOKE", "SPELL"));
        add("fke_frozen_shatter", CAT_FINAL_KILL_EFFECT, "&bFrozen Shatter", 325, Collections.<String, String>emptyMap(), "frozen_shatter",
            list("SNOWBALL", "SNOW_SHOVEL", "CLOUD", "FIREWORKS_SPARK"));
        add("fke_dragon_breath", CAT_FINAL_KILL_EFFECT, "&5Dragon Breath", 450, Collections.<String, String>emptyMap(), "dragon_breath",
            list("FLAME", "PORTAL", "SMOKE", "LARGE_SMOKE"));
        add("fke_soulfire", CAT_FINAL_KILL_EFFECT, "&1Soulfire", 375, Collections.<String, String>emptyMap(), "soulfire",
            list("SOUL_FIRE_FLAME", "FLAME", "SMOKE", "ENCHANT"));
        add("fke_nova", CAT_FINAL_KILL_EFFECT, "&6Nova", 500, Collections.<String, String>emptyMap(), "nova",
            list("EXPLOSION", "FLAME", "LAVA", "FIREWORKS_SPARK"));

        // Prestige Customizer — lobby name-tag colour (visual-only).
        add("pr_none", CAT_PRESTIGE, "&7Default", 0, Collections.<String, String>emptyMap(), "none", Collections.<String>emptyList());
        add("pr_gold", CAT_PRESTIGE, "&6Gold Prestige", 500, Collections.<String, String>emptyMap(), "gold", list("FIREWORKS_SPARK", "FIREWORK"));
        add("pr_diamond", CAT_PRESTIGE, "&bDiamond Prestige", 750, Collections.<String, String>emptyMap(), "diamond", list("FIREWORKS_SPARK", "CLOUD"));
        add("pr_emerald", CAT_PRESTIGE, "&2Emerald Prestige", 1000, Collections.<String, String>emptyMap(), "emerald", list("VILLAGER_HAPPY", "HAPPY_VILLAGER"));
        add("pr_netherite", CAT_PRESTIGE, "&8Netherite Prestige", 1500, Collections.<String, String>emptyMap(), "netherite", list("FLAME", "SMOKE", "LAVA"));
        add("pr_ender", CAT_PRESTIGE, "&5Ender Prestige", 2000, Collections.<String, String>emptyMap(), "ender", list("PORTAL", "ENCHANT", "END_ROD"));
        add("pr_rainbow", CAT_PRESTIGE, "&dRainbow Prestige", 3000, Collections.<String, String>emptyMap(), "rainbow", list("REDSTONE", "SPELL_MOB", "FIREWORKS_SPARK"));
        add("pr_hypixel", CAT_PRESTIGE, "&e&lHypixel Style", 5000, Collections.<String, String>emptyMap(), "hypixel", list("FIREWORK", "EXPLOSION", "FLAME"));

        // Projectile Trails — particles that follow a launched projectile in flight.
        add("pt_flame", CAT_PROJECTILE_TRAIL, "&6Flame Trail", 150, Collections.<String, String>emptyMap(), "flame", list("FLAME", "SMOKE"));
        add("pt_portal", CAT_PROJECTILE_TRAIL, "&5Portal Trail", 200, Collections.<String, String>emptyMap(), "portal", list("PORTAL", "SPELL_WITCH"));
        add("pt_smoke", CAT_PROJECTILE_TRAIL, "&8Smoke Trail", 100, Collections.<String, String>emptyMap(), "smoke", list("LARGE_SMOKE", "SMOKE", "CLOUD"));
        add("pt_enchant", CAT_PROJECTILE_TRAIL, "&bEnchant Trail", 225, Collections.<String, String>emptyMap(), "enchant", list("ENCHANTMENT_TABLE", "ENCHANT", "CRIT"));
        add("pt_snow", CAT_PROJECTILE_TRAIL, "&fSnow Trail", 175, Collections.<String, String>emptyMap(), "snow", list("SNOWBALL", "SNOW_SHOVEL", "CLOUD"));
        add("pt_blood", CAT_PROJECTILE_TRAIL, "&4Blood Trail", 200, Collections.<String, String>emptyMap(), "blood", list("REDSTONE", "CRIT", "CRITICAL_HIT"));
        add("pt_rainbow", CAT_PROJECTILE_TRAIL, "&dRainbow Trail", 300, Collections.<String, String>emptyMap(), "rainbow", list("REDSTONE", "SPELL_MOB", "FIREWORKS_SPARK"));
        add("pt_note", CAT_PROJECTILE_TRAIL, "&eNote Trail", 175, Collections.<String, String>emptyMap(), "note", list("NOTE", "NOTE_BLOCK", "VILLAGER_HAPPY"));
        add("pt_ender", CAT_PROJECTILE_TRAIL, "&5Ender Trail", 250, Collections.<String, String>emptyMap(), "ender", list("PORTAL", "END_ROD", "ENCHANT"));
        add("pt_firework", CAT_PROJECTILE_TRAIL, "&6Firework Trail", 225, Collections.<String, String>emptyMap(), "firework", list("FIREWORKS_SPARK", "FIREWORK", "FLAME"));

        // Bed Destroys — big particle shows when an enemy bed is broken.
        add("bd_explosion", CAT_BED_DESTROY, "&cExplosion", 150, Collections.<String, String>emptyMap(), "explosion", list("EXPLOSION", "EXPLOSION_LARGE", "FLAME", "SMOKE"));
        add("bd_fire", CAT_BED_DESTROY, "&6Inferno", 200, Collections.<String, String>emptyMap(), "fire", list("FLAME", "LAVA", "LARGE_SMOKE", "SMOKE"));
        add("bd_soul", CAT_BED_DESTROY, "&5Soul Rupture", 250, Collections.<String, String>emptyMap(), "soul", list("SOUL_FIRE_FLAME", "PORTAL", "SMOKE", "ENCHANT"));
        add("bd_frost", CAT_BED_DESTROY, "&bFrost Shatter", 225, Collections.<String, String>emptyMap(), "frost", list("SNOWBALL", "SNOW_SHOVEL", "CLOUD", "FIREWORKS_SPARK"));
        add("bd_blood", CAT_BED_DESTROY, "&4Blood Burst", 200, Collections.<String, String>emptyMap(), "blood", list("REDSTONE", "CRIT", "CRITICAL_HIT", "DAMAGE_INDICATOR"));
        add("bd_void", CAT_BED_DESTROY, "&8Void Collapse", 300, Collections.<String, String>emptyMap(), "void", list("PORTAL", "SMOKE", "LARGE_SMOKE", "SPELL"));
        add("bd_rainbow", CAT_BED_DESTROY, "&dRainbow Explosion", 350, Collections.<String, String>emptyMap(), "rainbow", list("REDSTONE", "SPELL_MOB", "FIREWORKS_SPARK", "FIREWORK"));
        add("bd_dragon", CAT_BED_DESTROY, "&5Dragon Breath", 400, Collections.<String, String>emptyMap(), "dragon", list("FLAME", "PORTAL", "SMOKE", "LARGE_SMOKE"));
        add("bd_lightning", CAT_BED_DESTROY, "&eLightning Strike", 300, Collections.<String, String>emptyMap(), "lightning", list("FIREWORK", "EXPLOSION", "CLOUD", "FIREWORKS_SPARK"));
        add("bd_glyph", CAT_BED_DESTROY, "&5Enchant Glyph", 275, Collections.<String, String>emptyMap(), "glyph", list("ENCHANTMENT_TABLE", "ENCHANT", "END_ROD", "CRIT"));

        // Shopkeeper Skins — reskin your team's in-match shop NPCs. effect = a real signed Mojang username
        // (all verified to resolve + return textures). Team-shared, split ITEM SHOP / TEAM UPGRADES by join order.
        addSkin("sk_zombie", "&2Zombie", 150, "MHF_Zombie");
        addSkin("sk_skeleton", "&7Skeleton", 150, "MHF_Skeleton");
        addSkin("sk_creeper", "&aCreeper", 175, "MHF_Creeper");
        addSkin("sk_spider", "&8Spider", 150, "MHF_Spider");
        addSkin("sk_cavespider", "&1Cave Spider", 175, "MHF_CaveSpider");
        addSkin("sk_pig", "&dPig", 120, "MHF_Pig");
        addSkin("sk_sheep", "&fSheep", 120, "MHF_Sheep");
        addSkin("sk_cow", "&6Cow", 120, "MHF_Cow");
        addSkin("sk_mooshroom", "&cMooshroom", 200, "MHF_MushroomCow");
        addSkin("sk_chicken", "&eChicken", 120, "MHF_Chicken");
        addSkin("sk_squid", "&9Squid", 150, "MHF_Squid");
        addSkin("sk_slime", "&aSlime", 175, "MHF_Slime");
        addSkin("sk_magmacube", "&6Magma Cube", 250, "MHF_LavaSlime");
        addSkin("sk_ocelot", "&eOcelot", 200, "MHF_Ocelot");
        addSkin("sk_rabbit", "&fRabbit", 175, "MHF_Rabbit");
        addSkin("sk_wolf", "&7Wolf", 200, "MHF_Wolf");
        addSkin("sk_blaze", "&6Blaze", 300, "MHF_Blaze");
        addSkin("sk_ghast", "&fGhast", 300, "MHF_Ghast");
        addSkin("sk_pigman", "&cZombie Pigman", 300, "MHF_PigZombie");
        addSkin("sk_enderman", "&5Enderman", 400, "MHF_Enderman");
        addSkin("sk_guardian", "&3Guardian", 400, "MHF_Guardian");
        addSkin("sk_witherskeleton", "&8Wither Skeleton", 500, "MHF_WSkeleton");
        addSkin("sk_irongolem", "&7Iron Golem", 500, "MHF_Golem");
        addSkin("sk_villager", "&aVillager", 150, "MHF_Villager");
        addSkin("sk_herobrine", "&8Herobrine", 750, "MHF_Herobrine");
        addSkin("sk_steve", "&fSteve", 100, "MHF_Steve");
        addSkin("sk_alex", "&6Alex", 100, "MHF_Alex");
        addSkin("sk_pumpkin", "&6Pumpkin", 175, "MHF_Pumpkin");
        addSkin("sk_tnt", "&cTNT", 200, "MHF_TNT");
        addSkin("sk_present_red", "&cRed Present", 200, "MHF_Present1");
        addSkin("sk_present_green", "&aGreen Present", 200, "MHF_Present2");
        addSkin("sk_cactus", "&2Cactus", 175, "MHF_Cactus");
        addSkin("sk_melon", "&aMelon", 175, "MHF_Melon");
        addSkin("sk_chest", "&6Chest", 175, "MHF_Chest");
        addSkin("sk_notch", "&6&lNotch", 1500, "Notch");
        addSkin("sk_jeb", "&e&ljeb_", 1200, "jeb_");
        addSkin("sk_dinnerbone", "&f&lDinnerbone", 800, "Dinnerbone");
        addSkin("sk_grumm", "&f&lGrumm", 800, "Grumm");
        addSkin("sk_technoblade", "&c&lTechnoblade", 750, "Technoblade");
        addSkin("sk_dream", "&a&lDream", 500, "Dream");
        addSkin("sk_georgenotfound", "&fGeorgeNotFound", 350, "GeorgeNotFound");
        addSkin("sk_sapnap", "&cSapnap", 350, "Sapnap");
        addSkin("sk_tommyinnit", "&eTommyInnit", 300, "TommyInnit");
        addSkin("sk_tubbo", "&aTubbo", 300, "Tubbo");
        addSkin("sk_ranboo", "&8Ranboo", 300, "Ranboo");
        addSkin("sk_skeppy", "&bSkeppy", 300, "Skeppy");
        addSkin("sk_badboyhalo", "&0BadBoyHalo", 300, "BadBoyHalo");
        addSkin("sk_captainsparklez", "&9CaptainSparklez", 350, "CaptainSparklez");
        addSkin("sk_hypixel", "&e&lHypixel", 2000, "Hypixel");
        addSkin("sk_refraction", "&bRefraction", 400, "Refraction");
    }

    /** Register a shopkeeper skin cosmetic: effect = the Mojang username to render on the shop NPC. */
    private void addSkin(String id, String name, int cost, String username) {
        add(id, CAT_SHOPKEEPER_SKIN, name, cost, Collections.<String, String>emptyMap(), username, Collections.<String>emptyList());
    }

    private void pack(String id, String name, int cost,
                      String kill, String voidKill, String shot, String fall, String explosion, String fire) {
        Map<String, String> messages = new LinkedHashMap<String, String>();
        messages.put("kill", kill);
        messages.put("void_kill", voidKill);
        messages.put("shot", shot);
        messages.put("fall", fall);
        messages.put("explosion", explosion);
        messages.put("fire", fire);
        add(id, CAT_KILL_MESSAGE, name, cost, messages, "", null);
    }

    private void add(String id, String category, String name, int cost, Map<String, String> messages, String effect, List<String> particles) {
        if (byId.containsKey(id)) return; // config wins; defaults fill gaps only
        put(new Cosmetic(id, category, color(name), cost,
            messages == null ? Collections.<String, String>emptyMap() : messages,
            effect, particles == null ? Collections.<String>emptyList() : particles));
    }

    private void put(Cosmetic cosmetic) {
        byId.put(cosmetic.id, cosmetic);
        List<Cosmetic> list = byCategory.get(cosmetic.category);
        if (list != null) list.add(cosmetic);
    }

    private static List<String> list(String... values) {
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, values);
        return out;
    }

    public Cosmetic get(String id) { return id == null ? null : byId.get(id); }

    public int catalogSize() { return byId.size(); }

    public int killMessagePackCount() {
        List<Cosmetic> list = byCategory.get(CAT_KILL_MESSAGE);
        return list == null ? 0 : list.size();
    }

    public int winEffectCount() {
        List<Cosmetic> list = byCategory.get(CAT_WIN_EFFECT);
        return list == null ? 0 : list.size();
    }

    public int woodSkinCount() {
        List<Cosmetic> list = byCategory.get(CAT_WOOD_SKIN);
        return list == null ? 0 : list.size();
    }

    public int finalKillEffectCount() {
        List<Cosmetic> list = byCategory.get(CAT_FINAL_KILL_EFFECT);
        return list == null ? 0 : list.size();
    }

    public int prestigeCount() {
        List<Cosmetic> list = byCategory.get(CAT_PRESTIGE);
        return list == null ? 0 : list.size();
    }

    public int projectileTrailCount() {
        List<Cosmetic> list = byCategory.get(CAT_PROJECTILE_TRAIL);
        return list == null ? 0 : list.size();
    }

    public int bedDestroyCount() {
        List<Cosmetic> list = byCategory.get(CAT_BED_DESTROY);
        return list == null ? 0 : list.size();
    }

    public int shopkeeperSkinCount() {
        List<Cosmetic> list = byCategory.get(CAT_SHOPKEEPER_SKIN);
        return list == null ? 0 : list.size();
    }

    /**
     * Skin username for a team's shop NPC, or null for the default villager. Team-shared, split by join order:
     * item-shop NPC = first teammate's equipped skin; upgrades NPC = second teammate's (solo → same member on
     * both). Only equipped ("paid") skins skin an NPC; null everywhere else keeps the plain villager.
     */
    public String shopkeeperSkin(Arena arena, TeamColor team, boolean upgradeNpc) {
        if (arena == null || team == null) return null;
        List<UUID> members = new ArrayList<UUID>();
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (entry.getValue() == team) members.add(entry.getKey());
        }
        if (members.isEmpty()) return null;
        UUID member = !upgradeNpc ? members.get(0)
            : (members.size() >= 2 ? members.get(1) : members.get(0));
        Cosmetic cosmetic = get(plugin.stats().equippedCosmetic(member, CAT_SHOPKEEPER_SKIN));
        if (cosmetic == null || cosmetic.effect == null || cosmetic.effect.isEmpty()) return null;
        return cosmetic.effect;
    }

    /** Smoke-check IDs for default kill-message packs (merged when config lacks them). */
    public static String[] defaultKillMessagePackIds() {
        return new String[] {
            "km_crushed", "km_rekt", "km_swept", "km_obliterated",
            "km_fire", "km_western", "km_honourable", "km_love", "km_bbq", "km_woof",
            "km_pirate", "km_spooky", "km_memed", "km_dramatic", "km_snow", "km_eggy",
            "km_celebratory", "km_wrapped", "km_moon", "km_festive", "km_roar", "km_buzz",
            "km_oink", "km_squeak", "km_ox", "km_primal", "km_santa", "km_bridging", "km_oldman"
        };
    }

    /** Smoke-check IDs for default Hypixel-like win effects (merged when config lacks them). */
    public static String[] defaultWinEffectIds() {
        return new String[] {
            "we_firework", "we_lightning", "we_burst", "we_hearts",
            "we_cold_snap", "we_burning_soul", "we_notes", "we_blood",
            "we_cookie", "we_campfire", "we_glyphs", "we_snowball",
            "we_tornado", "we_meteor", "we_sparkler", "we_portal",
            "we_rainbow", "we_anvil", "we_dragon", "we_wither"
        };
    }

    public static String[] defaultWoodSkinIds() {
        return new String[] {
            "ws_cherry", "ws_oak", "ws_spruce", "ws_birch", "ws_jungle",
            "ws_acacia", "ws_dark_oak", "ws_crimson", "ws_warped"
        };
    }

    public static String[] defaultFinalKillEffectIds() {
        return new String[] {
            "fke_soul_rip", "fke_blood_burst", "fke_lightning_strike", "fke_void_collapse",
            "fke_frozen_shatter", "fke_dragon_breath", "fke_soulfire", "fke_nova"
        };
    }

    public static String[] defaultPrestigeIds() {
        return new String[] {
            "pr_none", "pr_gold", "pr_diamond", "pr_emerald",
            "pr_netherite", "pr_ender", "pr_rainbow", "pr_hypixel"
        };
    }

    public static String[] defaultProjectileTrailIds() {
        return new String[] {
            "pt_flame", "pt_portal", "pt_smoke", "pt_enchant", "pt_snow",
            "pt_blood", "pt_rainbow", "pt_note", "pt_ender", "pt_firework"
        };
    }

    public static String[] defaultBedDestroyIds() {
        return new String[] {
            "bd_explosion", "bd_fire", "bd_soul", "bd_frost", "bd_blood",
            "bd_void", "bd_rainbow", "bd_dragon", "bd_lightning", "bd_glyph"
        };
    }

    public List<Cosmetic> category(String category) {
        List<Cosmetic> list = byCategory.get(normalizeCategory(category));
        return list == null ? Collections.<Cosmetic>emptyList() : Collections.unmodifiableList(list);
    }

    public static String categoryDisplay(String category) {
        if (CAT_KILL_MESSAGE.equals(category)) return "Kill Messages";
        if (CAT_KILL_EFFECT.equals(category)) return "Kill Effects";
        if (CAT_WIN_EFFECT.equals(category)) return "Win Effects";
        if (CAT_WOOD_SKIN.equals(category)) return "Wood Skins";
        if (CAT_FINAL_KILL_EFFECT.equals(category)) return "Final Kill Effects";
        if (CAT_PRESTIGE.equals(category)) return "Prestige Customizer";
        if (CAT_PROJECTILE_TRAIL.equals(category)) return "Projectile Trails";
        if (CAT_BED_DESTROY.equals(category)) return "Bed Destroys";
        if (CAT_SHOPKEEPER_SKIN.equals(category)) return "Shopkeeper Skins";
        return category;
    }

    /** Shop lore: "Unlocked: 3/29 (10%)". */
    public static String unlockProgress(int owned, int total) {
        int pct = total <= 0 ? 0 : (owned * 100) / total;
        return "Unlocked: " + owned + "/" + total + " (" + pct + "%)";
    }

    /** Shop lore: "Currently Selected: Fire" or NONE. */
    public static String selectedLabel(String equippedName) {
        String name = equippedName == null ? "" : equippedName.trim();
        return "Currently Selected: " + (name.isEmpty() ? "NONE" : name);
    }

    // ---------------------------------------------------------------- shop presentation (icons / rarity)

    /** Thematic icon per cosmetic id: {modern material name, legacy 1.8 material name}. */
    private static final Map<String, String[]> ICONS = new LinkedHashMap<String, String[]>();

    /** Category default icons for ids without a specific entry: {modern, legacy}. */
    private static String[] categoryIcon(String category) {
        if (CAT_KILL_MESSAGE.equals(category)) return new String[]{"WRITABLE_BOOK", "BOOK_AND_QUILL"};
        if (CAT_KILL_EFFECT.equals(category)) return new String[]{"BLAZE_POWDER", "BLAZE_POWDER"};
        if (CAT_WIN_EFFECT.equals(category)) return new String[]{"FIREWORK_ROCKET", "FIREWORK"};
        if (CAT_WOOD_SKIN.equals(category)) return new String[]{"OAK_LOG", "LOG"};
        if (CAT_FINAL_KILL_EFFECT.equals(category)) return new String[]{"REDSTONE", "REDSTONE"};
        if (CAT_PRESTIGE.equals(category)) return new String[]{"NAME_TAG", "NAME_TAG"};
        if (CAT_PROJECTILE_TRAIL.equals(category)) return new String[]{"ARROW", "ARROW"};
        if (CAT_BED_DESTROY.equals(category)) return new String[]{"RED_BED", "BED"};
        if (CAT_SHOPKEEPER_SKIN.equals(category)) return new String[]{"VILLAGER_SPAWN_EGG", "MONSTER_EGG"};
        return new String[]{"PAPER", "PAPER"};
    }

    static {
        ICONS.put("km_crushed", new String[]{"ANVIL", "ANVIL"});
        ICONS.put("km_rekt", new String[]{"TNT", "TNT"});
        ICONS.put("km_swept", new String[]{"FEATHER", "FEATHER"});
        ICONS.put("km_obliterated", new String[]{"NETHER_STAR", "NETHER_STAR"});
        ICONS.put("km_fire", new String[]{"BLAZE_POWDER", "BLAZE_POWDER"});
        ICONS.put("km_western", new String[]{"HAY_BLOCK", "HAY_BLOCK"});
        ICONS.put("km_honourable", new String[]{"GOLDEN_SWORD", "GOLD_SWORD"});
        ICONS.put("km_love", new String[]{"POPPY", "RED_ROSE"});
        ICONS.put("km_bbq", new String[]{"COOKED_PORKCHOP", "GRILLED_PORK"});
        ICONS.put("km_woof", new String[]{"BONE", "BONE"});
        ICONS.put("km_pirate", new String[]{"COMPASS", "COMPASS"});
        ICONS.put("km_spooky", new String[]{"JACK_O_LANTERN", "JACK_O_LANTERN"});
        ICONS.put("km_memed", new String[]{"GOLDEN_APPLE", "GOLDEN_APPLE"});
        ICONS.put("km_dramatic", new String[]{"MUSIC_DISC_CAT", "RECORD_4"});
        ICONS.put("km_snow", new String[]{"SNOWBALL", "SNOWBALL"});
        ICONS.put("km_eggy", new String[]{"EGG", "EGG"});
        ICONS.put("km_celebratory", new String[]{"FIREWORK_ROCKET", "FIREWORK"});
        ICONS.put("km_wrapped", new String[]{"STRING", "STRING"});
        ICONS.put("km_moon", new String[]{"ENDER_EYE", "EYE_OF_ENDER"});
        ICONS.put("km_festive", new String[]{"COOKIE", "COOKIE"});
        ICONS.put("km_roar", new String[]{"ROTTEN_FLESH", "ROTTEN_FLESH"});
        ICONS.put("km_buzz", new String[]{"BEE_SPAWN_EGG", "MONSTER_EGG"});
        ICONS.put("km_oink", new String[]{"PORKCHOP", "PORK"});
        ICONS.put("km_squeak", new String[]{"WHEAT_SEEDS", "SEEDS"});
        ICONS.put("km_ox", new String[]{"LEATHER", "LEATHER"});
        ICONS.put("km_primal", new String[]{"STONE_AXE", "STONE_AXE"});
        ICONS.put("km_santa", new String[]{"COAL", "COAL"});
        ICONS.put("km_bridging", new String[]{"OAK_PLANKS", "WOOD"});
        ICONS.put("km_oldman", new String[]{"STICK", "STICK"});

        ICONS.put("ke_blood", new String[]{"REDSTONE", "REDSTONE"});
        ICONS.put("ke_flame", new String[]{"BLAZE_POWDER", "BLAZE_POWDER"});
        ICONS.put("ke_spark", new String[]{"GLOWSTONE_DUST", "GLOWSTONE_DUST"});
        ICONS.put("ke_smoke", new String[]{"GUNPOWDER", "SULPHUR"});

        ICONS.put("we_firework", new String[]{"FIREWORK_ROCKET", "FIREWORK"});
        ICONS.put("we_lightning", new String[]{"TRIDENT", "IRON_SWORD"});
        ICONS.put("we_burst", new String[]{"FIRE_CHARGE", "FIREBALL"});
        ICONS.put("we_hearts", new String[]{"POPPY", "RED_ROSE"});
        ICONS.put("we_cold_snap", new String[]{"PACKED_ICE", "PACKED_ICE"});
        ICONS.put("we_burning_soul", new String[]{"MAGMA_CREAM", "MAGMA_CREAM"});
        ICONS.put("we_notes", new String[]{"NOTE_BLOCK", "NOTE_BLOCK"});
        ICONS.put("we_blood", new String[]{"REDSTONE", "REDSTONE"});
        ICONS.put("we_cookie", new String[]{"COOKIE", "COOKIE"});
        ICONS.put("we_campfire", new String[]{"CAMPFIRE", "FURNACE"});
        ICONS.put("we_glyphs", new String[]{"ENCHANTING_TABLE", "ENCHANTMENT_TABLE"});
        ICONS.put("we_snowball", new String[]{"SNOWBALL", "SNOWBALL"});
        ICONS.put("we_tornado", new String[]{"WATER_BUCKET", "WATER_BUCKET"});
        ICONS.put("we_meteor", new String[]{"MAGMA_BLOCK", "MAGMA_CREAM"});
        ICONS.put("we_sparkler", new String[]{"GLOWSTONE_DUST", "GLOWSTONE_DUST"});
        ICONS.put("we_portal", new String[]{"OBSIDIAN", "OBSIDIAN"});
        ICONS.put("we_rainbow", new String[]{"ELYTRA", "FEATHER"});
        ICONS.put("we_anvil", new String[]{"ANVIL", "ANVIL"});
        ICONS.put("we_dragon", new String[]{"DRAGON_HEAD", "SKULL_ITEM"});
        ICONS.put("we_wither", new String[]{"WITHER_SKELETON_SKULL", "SKULL_ITEM"});

        ICONS.put("ws_cherry", new String[]{"CHERRY_PLANKS", "WOOD"});
        ICONS.put("ws_oak", new String[]{"OAK_LOG", "LOG"});
        ICONS.put("ws_spruce", new String[]{"SPRUCE_LOG", "LOG"});
        ICONS.put("ws_birch", new String[]{"BIRCH_LOG", "LOG"});
        ICONS.put("ws_jungle", new String[]{"JUNGLE_LOG", "LOG"});
        ICONS.put("ws_acacia", new String[]{"ACACIA_LOG", "LOG_2"});
        ICONS.put("ws_dark_oak", new String[]{"DARK_OAK_LOG", "LOG_2"});
        ICONS.put("ws_crimson", new String[]{"CRIMSON_STEM", "LOG"});
        ICONS.put("ws_warped", new String[]{"WARPED_STEM", "LOG"});

        ICONS.put("fke_soul_rip", new String[]{"SOUL_SAND", "SOUL_SAND"});
        ICONS.put("fke_blood_burst", new String[]{"REDSTONE", "REDSTONE"});
        ICONS.put("fke_lightning_strike", new String[]{"LIGHTNING_ROD", "BLAZE_ROD"});
        ICONS.put("fke_void_collapse", new String[]{"OBSIDIAN", "OBSIDIAN"});
        ICONS.put("fke_frozen_shatter", new String[]{"BLUE_ICE", "PACKED_ICE"});
        ICONS.put("fke_dragon_breath", new String[]{"DRAGON_BREATH", "GLASS_BOTTLE"});
        ICONS.put("fke_soulfire", new String[]{"SOUL_SOIL", "COAL_BLOCK"});
        ICONS.put("fke_nova", new String[]{"NETHER_STAR", "NETHER_STAR"});

        ICONS.put("pr_none", new String[]{"GRAY_DYE", "INK_SACK"});
        ICONS.put("pr_gold", new String[]{"GOLD_INGOT", "GOLD_INGOT"});
        ICONS.put("pr_diamond", new String[]{"DIAMOND", "DIAMOND"});
        ICONS.put("pr_emerald", new String[]{"EMERALD", "EMERALD"});
        ICONS.put("pr_netherite", new String[]{"NETHERITE_INGOT", "BRICK"});
        ICONS.put("pr_ender", new String[]{"ENDER_EYE", "EYE_OF_ENDER"});
        ICONS.put("pr_rainbow", new String[]{"ELYTRA", "FEATHER"});
        ICONS.put("pr_hypixel", new String[]{"GOLD_BLOCK", "GOLD_BLOCK"});

        ICONS.put("pt_flame", new String[]{"BLAZE_POWDER", "BLAZE_POWDER"});
        ICONS.put("pt_portal", new String[]{"OBSIDIAN", "OBSIDIAN"});
        ICONS.put("pt_smoke", new String[]{"GUNPOWDER", "SULPHUR"});
        ICONS.put("pt_enchant", new String[]{"ENCHANTING_TABLE", "ENCHANTMENT_TABLE"});
        ICONS.put("pt_snow", new String[]{"SNOWBALL", "SNOWBALL"});
        ICONS.put("pt_blood", new String[]{"REDSTONE", "REDSTONE"});
        ICONS.put("pt_rainbow", new String[]{"ELYTRA", "FEATHER"});
        ICONS.put("pt_note", new String[]{"NOTE_BLOCK", "NOTE_BLOCK"});
        ICONS.put("pt_ender", new String[]{"ENDER_EYE", "EYE_OF_ENDER"});
        ICONS.put("pt_firework", new String[]{"FIREWORK_ROCKET", "FIREWORK"});

        ICONS.put("bd_explosion", new String[]{"TNT", "TNT"});
        ICONS.put("bd_fire", new String[]{"BLAZE_POWDER", "BLAZE_POWDER"});
        ICONS.put("bd_soul", new String[]{"SOUL_SAND", "SOUL_SAND"});
        ICONS.put("bd_frost", new String[]{"BLUE_ICE", "PACKED_ICE"});
        ICONS.put("bd_blood", new String[]{"REDSTONE", "REDSTONE"});
        ICONS.put("bd_void", new String[]{"OBSIDIAN", "OBSIDIAN"});
        ICONS.put("bd_rainbow", new String[]{"ELYTRA", "FEATHER"});
        ICONS.put("bd_dragon", new String[]{"DRAGON_HEAD", "SKULL_ITEM"});
        ICONS.put("bd_lightning", new String[]{"LIGHTNING_ROD", "BLAZE_ROD"});
        ICONS.put("bd_glyph", new String[]{"ENCHANTING_TABLE", "ENCHANTMENT_TABLE"});
    }

    /** Thematic shop icon for a cosmetic: {modern material name, legacy 1.8 name}. Never null. */
    public static String[] iconFor(Cosmetic cosmetic) {
        if (cosmetic == null) return new String[]{"PAPER", "PAPER"};
        String[] icon = ICONS.get(cosmetic.id);
        return icon != null ? icon : categoryIcon(cosmetic.category);
    }

    /** Rarity tier from price point: Common < 250 ≤ Rare < 500 ≤ Epic < 1500 ≤ Legendary. */
    public static int rarityOf(Cosmetic cosmetic) {
        if (cosmetic == null) return 0;
        if (cosmetic.cost >= 1500) return 3;
        if (cosmetic.cost >= 500) return 2;
        if (cosmetic.cost >= 250) return 1;
        return 0;
    }

    /** Colored rarity label for shop lore: Common/Rare/Epic/Legendary. */
    public static String rarityLabel(Cosmetic cosmetic) {
        switch (rarityOf(cosmetic)) {
            case 3: return "\u00A76\u00A7lLEGENDARY";
            case 2: return "\u00A75\u00A7lEPIC";
            case 1: return "\u00A7b\u00A7lRARE";
            default: return "\u00A77COMMON";
        }
    }

    /** Short flavor line shown under the item name in the shop. */
    public static String flavorFor(Cosmetic cosmetic) {
        if (cosmetic == null) return "";
        String category = cosmetic.category;
        String effect = cosmetic.effect == null ? "" : cosmetic.effect.toLowerCase();
        if (CAT_KILL_MESSAGE.equals(category)) return "Chat lines when you defeat someone.";
        if (CAT_KILL_EFFECT.equals(category)) return "Particles at your victim's body.";
        if (CAT_WIN_EFFECT.equals(category)) {
            if ("dragon".equals(effect)) return "Summon the dragon on victory!";
            if ("lightning".equals(effect)) return "Strike down the lobby on victory.";
            if ("anvil".equals(effect)) return "It is raining anvils!";
            if ("rainbow".equals(effect)) return "A colorful celebration.";
            return "A show when your team wins.";
        }
        if (CAT_WOOD_SKIN.equals(category)) return "Recolors wood you place.";
        if (CAT_FINAL_KILL_EFFECT.equals(category)) return "A dramatic finale on final kills.";
        if (CAT_PRESTIGE.equals(category)) return "Colors your lobby name.";
        if (CAT_PROJECTILE_TRAIL.equals(category)) return "Follows your arrows and fireballs.";
        if (CAT_BED_DESTROY.equals(category)) return "A spectacle on bed breaks.";
        if (CAT_SHOPKEEPER_SKIN.equals(category)) return "Reskins your team's shop NPCs.";
        return "";
    }

    public static String normalizeCategory(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (value.equals("KILL_MESSAGES")) value = CAT_KILL_MESSAGE;
        if (value.equals("KILL_EFFECTS")) value = CAT_KILL_EFFECT;
        if (value.equals("WIN_EFFECTS")) value = CAT_WIN_EFFECT;
        if (value.equals("WOOD_SKINS")) value = CAT_WOOD_SKIN;
        if (value.equals("FINAL_KILL_EFFECTS")) value = CAT_FINAL_KILL_EFFECT;
        if (value.equals("PRESTIGE_CUSTOMIZER")) value = CAT_PRESTIGE;
        if (value.equals("PROJECTILE_TRAILS")) value = CAT_PROJECTILE_TRAIL;
        if (value.equals("BED_DESTROYS")) value = CAT_BED_DESTROY;
        if (value.equals("SHOPKEEPER_SKINS")) value = CAT_SHOPKEEPER_SKIN;
        if (CAT_KILL_MESSAGE.equals(value) || CAT_KILL_EFFECT.equals(value) || CAT_WIN_EFFECT.equals(value)
            || CAT_WOOD_SKIN.equals(value) || CAT_FINAL_KILL_EFFECT.equals(value) || CAT_PRESTIGE.equals(value)
            || CAT_PROJECTILE_TRAIL.equals(value) || CAT_BED_DESTROY.equals(value)
            || CAT_SHOPKEEPER_SKIN.equals(value)) return value;
        return null;
    }

    /** Custom kill line when killer has a message cosmetic; otherwise null (use GameRules default). */
    public String killMessage(Player killer, String victimColored, String killerColored, String mode, boolean finalKill) {
        if (killer == null) return null;
        String id = plugin.stats().equippedCosmetic(killer.getUniqueId(), CAT_KILL_MESSAGE);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return null;
        String template = cosmetic.templateFor(mode);
        if (template == null || template.isEmpty()) return null;
        return formatKillMessage(template, victimColored, killerColored, finalKill);
    }

    public void playKillEffect(Player killer, Location at) {
        if (killer == null || at == null || at.getWorld() == null) return;
        String id = plugin.stats().equippedCosmetic(killer.getUniqueId(), CAT_KILL_EFFECT);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return;
        Location eye = at.clone().add(0, 1.0, 0);
        if (!cosmetic.particles.isEmpty()) {
            Particles.play(null, eye, 24, 0.35, cosmetic.particles.toArray(new String[0]));
            return;
        }
        Particles.play(null, eye, 18, 0.3, "CRIT", "CRITICAL_HIT", "FLAME");
    }

    /** Dramatic one-shot show at the victim's location when the killer lands a final kill. */
    public void playFinalKillEffect(Player killer, Location at) {
        if (killer == null || at == null || at.getWorld() == null) return;
        String id = plugin.stats().equippedCosmetic(killer.getUniqueId(), CAT_FINAL_KILL_EFFECT);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return;
        final Location center = at.clone().add(0, 1.0, 0);
        String effect = cosmetic.effect == null ? "" : cosmetic.effect.toLowerCase();
        String[] particles = cosmetic.particles.isEmpty()
            ? new String[] {"REDSTONE", "CRIT", "PORTAL"} : cosmetic.particles.toArray(new String[0]);

        // Base burst — every effect gets this.
        Particles.play(null, center, 40, 0.8, particles);
        Sounds.playAt(center, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");

        if ("soul_rip".equals(effect) || "void_collapse".equals(effect)) {
            for (int i = 0; i < 20; i++) {
                final int tick = i;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        double y = tick * 0.3;
                        double angle = tick * 0.5;
                        Location spiral = center.clone().add(Math.cos(angle) * 1.5, y, Math.sin(angle) * 1.5);
                        Particles.play(null, spiral, 4, 0.1, "PORTAL", "SMOKE");
                    }
                }, i);
            }
        } else if ("lightning_strike".equals(effect)) {
            World world = center.getWorld();
            if (world != null) world.strikeLightningEffect(center);
        } else if ("frozen_shatter".equals(effect)) {
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2.0 * i) / 12;
                Location ring = center.clone().add(Math.cos(angle) * 2.0, 0.2, Math.sin(angle) * 2.0);
                Particles.play(null, ring, 6, 0.15, "SNOWBALL", "CLOUD");
            }
        } else if ("nova".equals(effect)) {
            for (int r = 1; r <= 4; r++) {
                final int radius = r;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        for (int i = 0; i < 16; i++) {
                            double angle = (Math.PI * 2.0 * i) / 16;
                            Location ring = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                            Particles.play(null, ring, 3, 0.1, "FLAME", "EXPLOSION");
                        }
                    }
                }, r * 2L);
            }
        }
        // blood_burst / dragon_breath / soulfire use the base burst above.
    }

    /** Particles for the shooter's equipped projectile trail, or null when none equipped. */
    public String[] getProjectileTrailParticles(Player player) {
        if (player == null) return null;
        String id = plugin.stats().equippedCosmetic(player.getUniqueId(), CAT_PROJECTILE_TRAIL);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return null;
        if (cosmetic.particles.isEmpty()) return new String[] {"FLAME", "SMOKE"};
        return cosmetic.particles.toArray(new String[0]);
    }

    /** Spawn the shooter's trail particles along a projectile's flight path (up to 5s). */
    public void startProjectileTrail(final Player shooter, final Projectile projectile) {
        if (shooter == null || projectile == null) return;
        final String[] particles = getProjectileTrailParticles(shooter);
        if (particles == null) return;
        final UUID shooterId = shooter.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 100 || projectile.isDead() || !projectile.isValid()) {
                    cancel();
                    return;
                }
                Player p = Bukkit.getPlayer(shooterId);
                if (p == null || !p.isOnline()) { cancel(); return; }
                Location loc = projectile.getLocation();
                Particles.play(null, loc, 2, 0.05, particles);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Dramatic one-shot show at a destroyed enemy bed for the breaker's equipped bed-destroy cosmetic. */
    public void playBedDestroyEffect(Player breaker, Location bedLocation) {
        if (breaker == null || bedLocation == null || bedLocation.getWorld() == null) return;
        String id = plugin.stats().equippedCosmetic(breaker.getUniqueId(), CAT_BED_DESTROY);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return;
        final Location center = bedLocation.clone().add(0.5, 0.5, 0.5);
        String effect = cosmetic.effect == null ? "" : cosmetic.effect.toLowerCase();
        String[] particles = cosmetic.particles.isEmpty()
            ? new String[] {"EXPLOSION", "FLAME", "SMOKE"} : cosmetic.particles.toArray(new String[0]);

        // Base burst — every effect gets this.
        Particles.play(null, center, 40, 1.0, particles);
        Sounds.playAt(center, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");

        if ("explosion".equals(effect)) {
            for (int i = 0; i < 3; i++) {
                final int delay = i;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        double r = (delay + 1) * 1.5;
                        for (int j = 0; j < 8; j++) {
                            double angle = (Math.PI * 2.0 * j) / 8;
                            Location ring = center.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
                            Particles.play(null, ring, 4, 0.1, "EXPLOSION", "FLAME");
                        }
                    }
                }, i * 3L);
            }
        } else if ("fire".equals(effect) || "inferno".equals(effect)) {
            for (int y = 0; y < 8; y++) {
                final int height = y;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        Location fire = center.clone().add(0, height * 0.4, 0);
                        Particles.play(null, fire, 8, 0.3, "FLAME", "LAVA", "SMOKE");
                    }
                }, y);
            }
        } else if ("frost".equals(effect)) {
            for (int r = 1; r <= 4; r++) {
                final int radius = r;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        for (int i = 0; i < 12; i++) {
                            double angle = (Math.PI * 2.0 * i) / 12;
                            Location ring = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                            Particles.play(null, ring, 3, 0.1, "SNOWBALL", "CLOUD");
                        }
                    }
                }, r * 2L);
            }
        } else if ("void".equals(effect)) {
            for (int i = 0; i < 16; i++) {
                final int tick = i;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        double y = tick * 0.25;
                        double angle = tick * 0.6;
                        Location spiral = center.clone().add(Math.cos(angle) * 1.2, y, Math.sin(angle) * 1.2);
                        Particles.play(null, spiral, 4, 0.1, "PORTAL", "SMOKE");
                    }
                }, i);
            }
        } else if ("lightning".equals(effect)) {
            World world = center.getWorld();
            if (world != null) world.strikeLightningEffect(center);
        } else if ("glyph".equals(effect)) {
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2.0 * i) / 16;
                Location ring = center.clone().add(Math.cos(angle) * 2.0, 0.1, Math.sin(angle) * 2.0);
                Particles.play(null, ring, 4, 0.05, "ENCHANTMENT_TABLE", "ENCHANT");
            }
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2.0 * i) / 8;
                Location ring = center.clone().add(Math.cos(angle) * 1.0, 0.1, Math.sin(angle) * 1.0);
                Particles.play(null, ring, 3, 0.05, "END_ROD", "CRIT");
            }
        }
        // soul / blood / rainbow / dragon use the base burst above.
    }

    /**
     * Colour-code prefix for a player's lobby name based on their equipped prestige cosmetic,
     * or null when none/default. Callers prepend this to the display name.
     */
    public String applyPrestige(Player player) {
        if (player == null) return null;
        String id = plugin.stats().equippedCosmetic(player.getUniqueId(), CAT_PRESTIGE);
        if (id == null || "pr_none".equals(id)) return null;
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return null;
        String effect = cosmetic.effect == null ? "" : cosmetic.effect.toLowerCase();
        if ("gold".equals(effect)) return "§6";
        if ("diamond".equals(effect)) return "§b";
        if ("emerald".equals(effect)) return "§2";
        if ("netherite".equals(effect)) return "§8";
        if ("ender".equals(effect)) return "§5";
        if ("rainbow".equals(effect)) return "§d";
        if ("hypixel".equals(effect)) return "§e§l";
        return null;
    }

    /** Equipped prestige display name (colour codes stripped), e.g. "Gold Prestige", or "" when none/default. */
    public String prestigeName(Player player) {
        if (player == null) return "";
        String id = plugin.stats().equippedCosmetic(player.getUniqueId(), CAT_PRESTIGE);
        if (id == null || "pr_none".equals(id)) return "";
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return "";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', cosmetic.name));
    }

    /** Play the winner's equipped win effect (delegates to the win-effect subsystem). */
    public void playWinEffect(Player winner) { win.playWinEffect(winner); }

    /** True while any win dragon is still tracked (grief may be present in that live world). */
    public boolean hasActiveWinDragon() { return win.hasActiveWinDragon(); }

    /** True if this live world currently hosts a tagged win dragon (do not pristine-snapshot). */
    public boolean worldHasWinDragonGrief(World world) { return win.worldHasWinDragonGrief(world); }

    /** Despawn win dragons / sheep / anvils / cosmetic fireballs in a world (match reset). */
    public void clearWorldEffects(World world) { win.clearWorldEffects(world); }

    /** Buy if needed, then equip; clicking equipped unequips. Returns status message. */
    public String clickOffer(Player player, String id) {
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return ChatColor.RED + "Unknown cosmetic.";
        StatsStore stats = plugin.stats();
        UUID uuid = player.getUniqueId();
        String equipped = stats.equippedCosmetic(uuid, cosmetic.category);
        if (id.equals(equipped)) {
            stats.equipCosmetic(uuid, cosmetic.category, null);
            return ChatColor.YELLOW + "Unequipped " + cosmetic.name + ChatColor.YELLOW + ".";
        }
        if (!stats.ownsCosmetic(uuid, id)) {
            if (!stats.spendTokens(uuid, cosmetic.cost)) {
                return ChatColor.RED + "Need " + cosmetic.cost + " tokens (you have " + stats.get(uuid).tokens + ").";
            }
            stats.ownCosmetic(uuid, id);
            stats.equipCosmetic(uuid, cosmetic.category, id);
            return ChatColor.GREEN + "Purchased & equipped " + cosmetic.name + ChatColor.GREEN + "!";
        }
        stats.equipCosmetic(uuid, cosmetic.category, id);
        return ChatColor.GREEN + "Equipped " + cosmetic.name + ChatColor.GREEN + ".";
    }

    public static String formatKillMessage(String template, String victimColored, String killerColored, boolean finalKill) {
        String killer = killerColored == null ? ChatColor.GRAY + "Unknown" : killerColored;
        String victim = victimColored == null ? ChatColor.GRAY + "Unknown" : victimColored;
        String msg = color(template)
            .replace("%killer%", killer)
            .replace("%victim%", victim)
            .replace("{killer}", killer)
            .replace("{victim}", victim);
        if (finalKill) msg += " \u00A7c\u00A7lFINAL KILL!";
        return msg;
    }

    /** Same as ChatColor.translateAlternateColorCodes but Bukkit-free for coreCheck. */
    public static String color(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] != '&') continue;
            char code = chars[i + 1];
            if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(code) < 0) continue;
            chars[i] = '\u00A7';
            chars[i + 1] = Character.toLowerCase(code);
        }
        return new String(chars);
    }

    public static final class Cosmetic {
        public final String id;
        public final String category;
        public final String name;
        public final int cost;
        public final Map<String, String> messages;
        public final String effect;
        public final List<String> particles;

        public Cosmetic(String id, String category, String name, int cost, Map<String, String> messages, String effect, List<String> particles) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.cost = cost;
            this.messages = messages == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<String, String>(messages));
            this.effect = effect == null ? "" : effect;
            this.particles = particles;
        }

        /** Legacy single-line accessor (melee / kill). */
        public String message() { return templateFor("kill"); }

        public String templateFor(String mode) {
            if (messages.isEmpty()) return null;
            if (mode != null) {
                String direct = messages.get(mode.toLowerCase());
                if (direct != null && !direct.isEmpty()) return direct;
            }
            String kill = messages.get("kill");
            if (kill != null && !kill.isEmpty()) return kill;
            return messages.values().iterator().next();
        }
    }
}
