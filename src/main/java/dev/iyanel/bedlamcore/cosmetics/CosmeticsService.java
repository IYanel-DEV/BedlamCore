package dev.iyanel.bedlamcore.cosmetics;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Particles;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lobby cosmetics shop + match hooks. Catalog from config.yml; owned/equipped in stats.yml. */
public final class CosmeticsService implements Listener {
    public static final String CAT_KILL_MESSAGE = "KILL_MESSAGE";
    public static final String CAT_KILL_EFFECT = "KILL_EFFECT";
    public static final String CAT_WIN_EFFECT = "WIN_EFFECT";

    private static final String META_WIN_ANVIL = "bedlamWinAnvil";
    private static final String META_WIN_DRAGON = "bedlamWinDragon";
    private static final String META_WIN_FIREBALL = "bedlamWinFireball";
    private static final String META_WIN_SHEEP = "bedlamWinSheep";

    private final BedlamCore plugin;
    private final Map<String, Cosmetic> byId = new LinkedHashMap<String, Cosmetic>();
    private final Map<String, List<Cosmetic>> byCategory = new LinkedHashMap<String, List<Cosmetic>>();
    /** Winner UUID → active win-dragon entity UUID. */
    private final Map<UUID, UUID> winDragons = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, Long> dragonFireballAt = new ConcurrentHashMap<UUID, Long>();
    /** Winner UUID → rainbow sheep entity UUIDs. */
    private final Map<UUID, List<UUID>> winSheep = new ConcurrentHashMap<UUID, List<UUID>>();

    public CosmeticsService(BedlamCore plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerEntityDismountCancel();
    }

    public void reload() {
        byId.clear();
        byCategory.clear();
        byCategory.put(CAT_KILL_MESSAGE, new ArrayList<Cosmetic>());
        byCategory.put(CAT_KILL_EFFECT, new ArrayList<Cosmetic>());
        byCategory.put(CAT_WIN_EFFECT, new ArrayList<Cosmetic>());
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
            "we_rainbow", "we_anvil", "we_dragon"
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

    public static String normalizeCategory(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (value.equals("KILL_MESSAGES")) value = CAT_KILL_MESSAGE;
        if (value.equals("KILL_EFFECTS")) value = CAT_KILL_EFFECT;
        if (value.equals("WIN_EFFECTS")) value = CAT_WIN_EFFECT;
        if (CAT_KILL_MESSAGE.equals(value) || CAT_KILL_EFFECT.equals(value) || CAT_WIN_EFFECT.equals(value)) return value;
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

    /**
     * Play equipped win effect at the living winner for ~4–6s (Hypixel-style short show).
     * No equipped cosmetic → no effect. Caps particle rate via 5-tick period.
     */
    public void playWinEffect(Player winner) {
        if (winner == null || winner.getWorld() == null) return;
        String id = plugin.stats().equippedCosmetic(winner.getUniqueId(), CAT_WIN_EFFECT);
        Cosmetic cosmetic = get(id);
        if (cosmetic == null) return;
        final String effect = cosmetic.effect == null || cosmetic.effect.isEmpty()
            ? "burst" : cosmetic.effect.toLowerCase();
        final String[] particles = cosmetic.particles.isEmpty()
            ? null : cosmetic.particles.toArray(new String[0]);
        final UUID uuid = winner.getUniqueId();
        // ponytail: fixed cadence; dragon ~7.5s ride, rainbow sheep ~5s follow
        final int durationTicks = "dragon".equals(effect) ? 150 : ("rainbow".equals(effect) ? 100 : 80);
        final int period = 5;
        if ("dragon".equals(effect)) spawnWinDragon(winner);
        if ("rainbow".equals(effect)) spawnWinSheep(winner);
        new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline() || player.getWorld() == null || elapsed >= durationTicks) {
                    endWinDragon(uuid);
                    endWinSheep(uuid);
                    cancel();
                    return;
                }
                tickWinEffect(player, effect, particles, elapsed);
                elapsed += period;
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    /** True while a win dragon is still tracked (grief may be present in that live world). */
    public boolean hasActiveWinDragon() {
        return !winDragons.isEmpty();
    }

    /** True if this live world currently hosts a tagged win dragon (do not pristine-snapshot). */
    public boolean worldHasWinDragonGrief(World world) {
        if (world == null) return false;
        for (UUID dragonId : winDragons.values()) {
            Entity dragon = entityByUuid(world, dragonId);
            if (dragon != null && !dragon.isDead()) return true;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.hasMetadata(META_WIN_DRAGON)) return true;
        }
        return false;
    }

    /** Despawn win dragons / sheep / anvils / cosmetic fireballs in a world (match reset). */
    public void clearWorldEffects(World world) {
        if (world == null) return;
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (entity.hasMetadata(META_WIN_DRAGON) || entity.hasMetadata(META_WIN_ANVIL)
                || entity.hasMetadata(META_WIN_FIREBALL) || entity.hasMetadata(META_WIN_SHEEP)) {
                entity.remove();
            }
        }
        Iterator<Map.Entry<UUID, UUID>> it = winDragons.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            Entity dragon = entityByUuid(world, entry.getValue());
            Player owner = Bukkit.getPlayer(entry.getKey());
            boolean ownerHere = owner != null && owner.getWorld() != null && owner.getWorld().equals(world);
            if (dragon != null) {
                ejectPassengers(dragon);
                dragon.remove();
            }
            if (dragon != null || ownerHere) {
                it.remove();
                dragonFireballAt.remove(entry.getKey());
            }
        }
        Iterator<Map.Entry<UUID, List<UUID>>> sheepIt = winSheep.entrySet().iterator();
        while (sheepIt.hasNext()) {
            Map.Entry<UUID, List<UUID>> entry = sheepIt.next();
            Player owner = Bukkit.getPlayer(entry.getKey());
            boolean ownerHere = owner != null && owner.getWorld() != null && owner.getWorld().equals(world);
            boolean any = false;
            for (UUID id : entry.getValue()) {
                Entity sheep = entityByUuid(world, id);
                if (sheep != null) {
                    sheep.remove();
                    any = true;
                }
            }
            if (any || ownerHere) sheepIt.remove();
        }
    }

    private void tickWinEffect(Player winner, String effect, String[] particles, int elapsed) {
        Location base = winner.getLocation().clone().add(0, 0.15, 0);
        double angle = elapsed * 0.35;
        double radius = 1.15;
        Location ring = base.clone().add(Math.cos(angle) * radius, 1.0 + (elapsed % 20) * 0.04, Math.sin(angle) * radius);
        Location head = base.clone().add(0, 1.2, 0);
        Location sky = base.clone().add(0, 4.5, 0);

        if ("firework".equals(effect)) {
            if (elapsed % 10 == 0) spawnFirework(base.clone().add((elapsed % 20 == 0) ? 0.8 : -0.8, 0.5, 0));
            Particles.play(null, head, 8, 0.25, "FIREWORKS_SPARK", "FIREWORK", "CRIT");
            return;
        }
        if ("lightning".equals(effect)) {
            if (elapsed % 20 == 0) {
                winner.getWorld().strikeLightningEffect(base);
                Sounds.playAt(base, "ENTITY_LIGHTNING_BOLT_THUNDER", "ENTITY_LIGHTNING_THUNDER", "AMBIENCE_THUNDER", "LIGHTNING_THUNDER");
            }
            Particles.play(null, head, 6, 0.2, "CRIT", "FIREWORKS_SPARK", "SPELL");
            return;
        }
        if ("cold_snap".equals(effect)) {
            Particles.play(null, ring, 10, 0.15, namesOr(particles, "SNOWBALL", "SNOW_SHOVEL", "CLOUD"));
            Particles.play(null, head, 6, 0.35, "CLOUD", "FIREWORKS_SPARK");
            if (elapsed % 15 == 0) Sounds.playAt(base, "BLOCK_GLASS_BREAK", "GLASS", "ENTITY_PLAYER_HURT_FREEZE");
            return;
        }
        if ("burning_soul".equals(effect)) {
            Particles.play(null, head, 10, 0.2, namesOr(particles, "SOUL_FIRE_FLAME", "FLAME", "SMOKE"));
            Particles.play(null, base.clone().add(0, 0.3, 0), 6, 0.15, "LARGE_SMOKE", "SMOKE", "FLAME");
            if (elapsed % 20 == 0) Sounds.playAt(base, "BLOCK_FIRE_AMBIENT", "FIRE", "ENTITY_BLAZE_BURN");
            return;
        }
        if ("notes".equals(effect)) {
            Particles.play(null, ring, 4, 0.05, namesOr(particles, "NOTE", "NOTE_BLOCK", "VILLAGER_HAPPY"));
            if (elapsed % 10 == 0) Sounds.playAt(base, "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
            return;
        }
        if ("blood".equals(effect)) {
            Particles.play(null, head, 18, 0.45, namesOr(particles, "REDSTONE", "CRIT", "CRITICAL_HIT", "DAMAGE_INDICATOR"));
            if (elapsed % 15 == 0) Particles.play(null, head, 8, 0.6, "EXPLOSION", "SMOKE");
            return;
        }
        if ("cookie".equals(effect)) {
            Location up = base.clone().add(0, 0.4 + (elapsed % 25) * 0.08, 0);
            Particles.play(null, up, 8, 0.2, namesOr(particles, "CRIT", "VILLAGER_HAPPY", "HAPPY_VILLAGER", "CLOUD"));
            return;
        }
        if ("campfire".equals(effect)) {
            Particles.play(null, base.clone().add(0, 0.4, 0), 10, 0.2, namesOr(particles, "FLAME", "LAVA", "LARGE_SMOKE"));
            Particles.play(null, head, 4, 0.25, "SMOKE", "CLOUD");
            return;
        }
        if ("glyphs".equals(effect)) {
            Particles.play(null, ring, 8, 0.1, namesOr(particles, "ENCHANTMENT_TABLE", "ENCHANT", "END_ROD", "CRIT"));
            Particles.play(null, head, 4, 0.2, "SPELL", "CRIT");
            return;
        }
        if ("snowball".equals(effect)) {
            Location lob = head.clone().add(Math.cos(angle + 1) * 0.9, 0.2, Math.sin(angle + 1) * 0.9);
            Particles.play(null, lob, 8, 0.2, namesOr(particles, "SNOWBALL", "SNOW_SHOVEL", "CLOUD", "CRIT"));
            if (elapsed % 10 == 0) Sounds.playAt(base, "ENTITY_SNOWBALL_THROW", "SHOOT_ARROW", "ENTITY_ARROW_SHOOT");
            return;
        }
        if ("tornado".equals(effect)) {
            // Dense rising spiral + vertical dust column (visible on 1.8 Effect + modern Particle).
            double spin = elapsed * 0.55;
            for (int i = 0; i < 14; i++) {
                double y = 0.15 + i * 0.42;
                double r = 0.35 + y * 0.38;
                double a = spin + i * 0.65;
                Location p = base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                Particles.play(null, p, 6, 0.04, namesOr(particles, "CLOUD", "SMOKE", "CRIT", "SPELL"));
                if (i % 2 == 0) {
                    Location p2 = base.clone().add(Math.cos(a + Math.PI) * (r * 0.55), y + 0.15, Math.sin(a + Math.PI) * (r * 0.55));
                    Particles.play(null, p2, 3, 0.03, "CRIT", "SMOKE", "CLOUD");
                }
            }
            for (int y = 0; y < 10; y++) {
                Particles.play(null, base.clone().add(0, y * 0.45, 0), 2, 0.12, "SMOKE", "CLOUD", "CRIT");
            }
            if (elapsed % 10 == 0) Sounds.playAt(base, "ENTITY_ENDER_DRAGON_FLAP", "ENDERDRAGON_WINGS", "BAT_TAKEOFF");
            return;
        }
        if ("meteor".equals(effect)) {
            double ox = Math.cos(angle) * 1.4;
            double oz = Math.sin(angle) * 1.4;
            Particles.play(null, sky.clone().add(ox, 0, oz), 6, 0.1, namesOr(particles, "FLAME", "LAVA", "SMOKE"));
            Particles.play(null, base.clone().add(ox * 0.4, 0.3, oz * 0.4), 4, 0.15, "EXPLOSION", "FLAME", "SMOKE");
            return;
        }
        if ("sparkler".equals(effect)) {
            Particles.play(null, ring, 12, 0.12, namesOr(particles, "FIREWORKS_SPARK", "FIREWORK", "CRIT", "FLAME"));
            return;
        }
        if ("portal".equals(effect)) {
            Particles.play(null, head, 14, 0.35, namesOr(particles, "PORTAL", "SPELL_WITCH", "SPELL", "CRIT"));
            Particles.play(null, ring, 4, 0.1, "PORTAL", "CRIT");
            return;
        }
        if ("rainbow".equals(effect)) {
            tickWinSheep(winner, elapsed);
            return;
        }
        if ("anvil".equals(effect)) {
            if (elapsed % 10 == 0) spawnWinAnvil(winner, angle);
            Particles.play(null, sky.clone().add(Math.cos(angle) * 0.8, 0, Math.sin(angle) * 0.8), 4, 0.1,
                namesOr(particles, "CRIT", "CRITICAL_HIT", "SMOKE"));
            return;
        }
        if ("dragon".equals(effect)) {
            tickWinDragon(winner, elapsed);
            return;
        }
        if ("hearts".equals(effect)) {
            Particles.play(null, head, 10, 0.4, namesOr(particles, "HEART", "VILLAGER_HAPPY", "HAPPY_VILLAGER"));
            Particles.play(null, ring, 3, 0.08, "HEART", "CRIT");
            return;
        }
        // burst / unknown / particle-only configs
        Particles.play(null, head, 12, 0.4, namesOr(particles, "EXPLOSION_LARGE", "EXPLOSION", "FLAME", "CRIT"));
        if (elapsed % 20 == 0) Particles.play(null, head, 6, 0.5, "EXPLOSION", "FLAME");
    }

    private void spawnWinAnvil(Player winner, double angle) {
        World world = winner.getWorld();
        if (world == null) return;
        Location at = winner.getLocation().clone().add(
            Math.cos(angle) * (0.6 + (Math.abs(angle) % 1.7)),
            8.0 + (Math.abs(Math.sin(angle)) * 2.5),
            Math.sin(angle) * (0.6 + (Math.abs(angle) % 1.7)));
        FallingBlock falling = spawnFallingAnvil(at);
        if (falling == null) {
            Particles.play(null, at, 8, 0.2, "CRIT", "SMOKE", "CLOUD");
            Sounds.playAt(at, "BLOCK_ANVIL_LAND", "ANVIL_LAND", "BLOCK_ANVIL_PLACE");
            return;
        }
        falling.setMetadata(META_WIN_ANVIL, new FixedMetadataValue(plugin, winner.getUniqueId().toString()));
        falling.setDropItem(false);
        invokeBoolean(falling, "setHurtEntities", false);
        invokeBoolean(falling, "setGravity", true);
        try {
            falling.setVelocity(new Vector(0, -0.35, 0));
        } catch (Throwable ignored) {
        }
        // Safety despawn if land event never fires (void / unloaded chunk).
        final UUID entityId = falling.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                Entity e = entityByUuid(world, entityId);
                if (e != null && e.hasMetadata(META_WIN_ANVIL)) e.remove();
            }
        }, 80L);
    }

    private static FallingBlock spawnFallingAnvil(Location at) {
        if (at == null || at.getWorld() == null) return null;
        Material anvil = Items.material("ANVIL");
        World world = at.getWorld();
        try {
            Method legacy = World.class.getMethod("spawnFallingBlock", Location.class, Material.class, byte.class);
            return (FallingBlock) legacy.invoke(world, at, anvil, Byte.valueOf((byte) 0));
        } catch (Throwable ignored) {
        }
        try {
            Class<?> blockData = Class.forName("org.bukkit.block.data.BlockData");
            Method create = Material.class.getMethod("createBlockData");
            Object data = create.invoke(anvil);
            Method modern = World.class.getMethod("spawnFallingBlock", Location.class, blockData);
            return (FallingBlock) modern.invoke(world, at, data);
        } catch (Throwable ignored) {
        }
        try {
            return (FallingBlock) world.spawnEntity(at, EntityType.FALLING_BLOCK);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void spawnWinDragon(Player winner) {
        World world = winner.getWorld();
        if (world == null) return;
        endWinDragon(winner.getUniqueId());
        // Spawn under the winner so setPassenger seats them Hypixel-style (orbit teleport was the mount blocker).
        Location at = winner.getLocation().clone().add(0.0, 1.2, 0.0);
        Entity dragon;
        try {
            dragon = world.spawnEntity(at, EntityType.ENDER_DRAGON);
        } catch (Throwable t) {
            Particles.play(null, at, 40, 1.2, "FLAME", "PORTAL", "SMOKE", "CRIT");
            Sounds.playAt(at, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
            return;
        }
        dragon.setMetadata(META_WIN_DRAGON, new FixedMetadataValue(plugin, winner.getUniqueId().toString()));
        if (dragon instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) dragon;
            living.setRemoveWhenFarAway(false);
            invokeBoolean(living, "setAI", false);
            invokeBoolean(living, "setGravity", false);
            invokeBoolean(living, "setInvulnerable", true);
            invokeBoolean(living, "setCollidable", false);
        }
        winDragons.put(winner.getUniqueId(), dragon.getUniqueId());
        mountPassenger(dragon, winner);
        // Every tick: force remount until effect ends (sneak/eject cannot stick).
        final UUID owner = winner.getUniqueId();
        final UUID dragonId = dragon.getUniqueId();
        new BukkitRunnable() {
            @Override public void run() {
                if (!dragonId.equals(winDragons.get(owner))) {
                    cancel();
                    return;
                }
                Player p = Bukkit.getPlayer(owner);
                if (p == null || !p.isOnline() || p.getWorld() == null) return;
                Entity d = entityByUuid(p.getWorld(), dragonId);
                if (d == null || d.isDead()) {
                    winDragons.remove(owner);
                    cancel();
                    return;
                }
                if (needsWinDragonRemount(true, isPassengerOf(d, p))) mountPassenger(d, p);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Sounds.playAt(at, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
    }

    private void tickWinDragon(Player winner, int elapsed) {
        UUID dragonId = winDragons.get(winner.getUniqueId());
        if (dragonId == null || winner.getWorld() == null) return;
        Entity dragon = entityByUuid(winner.getWorld(), dragonId);
        if (dragon == null || dragon.isDead()) {
            winDragons.remove(winner.getUniqueId());
            return;
        }
        // Hypixel-like: strong velocity toward eye look each tick (dive into islands OK).
        Vector dir = winner.getEyeLocation().getDirection();
        if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        else dir.normalize();
        double speed = winDragonFlightSpeed();
        Location next = dragon.getLocation().clone().add(dir.clone().multiply(speed * 0.85));
        next.setYaw(winner.getLocation().getYaw());
        next.setPitch(winner.getLocation().getPitch());
        dragon.teleport(next);
        dragon.setVelocity(dir.clone().multiply(speed));
        // Body grief: carve through solid blocks like Hypixel victory dragon.
        griefWinDragonBlocks(dragon);
        Particles.play(null, dragon.getLocation().clone().add(0, 1.5, 0), 6, 0.5, "FLAME", "PORTAL", "SMOKE");
        if (elapsed % 20 == 0) {
            Sounds.playAt(dragon.getLocation(), "ENTITY_ENDER_DRAGON_FLAP", "ENDERDRAGON_WINGS", "BAT_TAKEOFF");
        }
    }

    /** Bukkit-free flight speed for coreCheck + Hypixel-feel tuning. */
    public static double winDragonFlightSpeed() {
        return 1.28;
    }

    /** Fireball blast radius (blocks) — Hypixel win dragon grief. */
    public static float winDragonFireballYield() {
        return 2.8f;
    }

    /** Break solid blocks in a box around the dragon (match reset / pristine restores map). */
    private static void griefWinDragonBlocks(Entity dragon) {
        if (dragon == null || dragon.getWorld() == null) return;
        Location c = dragon.getLocation();
        World world = c.getWorld();
        int cx = c.getBlockX();
        int cy = c.getBlockY();
        int cz = c.getBlockZ();
        int r = 2;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - 1; y <= cy + 3; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isWinDragonBreakable(block.getType())) continue;
                    block.setType(Material.AIR);
                }
            }
        }
    }

    static boolean isWinDragonBreakable(Material type) {
        if (type == null || type == Material.AIR) return false;
        String name = type.name();
        if (name.equals("BEDROCK") || name.contains("COMMAND") || name.contains("BARRIER")
            || name.contains("PORTAL") || name.equals("END_PORTAL_FRAME") || name.equals("ENDER_PORTAL_FRAME")) {
            return false;
        }
        return type.isSolid() || name.contains("WOOL") || name.contains("GLASS") || name.contains("LEAVES")
            || name.contains("BED");
    }

    private void endWinDragon(UUID owner) {
        UUID dragonId = winDragons.remove(owner);
        dragonFireballAt.remove(owner);
        if (dragonId == null) return;
        Player player = Bukkit.getPlayer(owner);
        World world = player != null ? player.getWorld() : null;
        if (world != null) {
            Entity dragon = entityByUuid(world, dragonId);
            if (dragon != null) {
                ejectPassengers(dragon);
                dragon.remove();
            }
            if (player != null && player.isOnline()) softLand(player);
            for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
                if (entity.hasMetadata(META_WIN_FIREBALL)
                    && owner.toString().equals(String.valueOf(entity.getMetadata(META_WIN_FIREBALL).get(0).value()))) {
                    entity.remove();
                }
            }
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            Entity dragon = entityByUuid(w, dragonId);
            if (dragon != null) {
                ejectPassengers(dragon);
                dragon.remove();
                break;
            }
        }
    }

    private void spawnWinSheep(Player winner) {
        World world = winner.getWorld();
        if (world == null) return;
        endWinSheep(winner.getUniqueId());
        DyeColor[] colors = DyeColor.values();
        int count = 6;
        List<UUID> ids = new ArrayList<UUID>(count);
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0 * i) / count;
            Location at = winner.getLocation().clone().add(Math.cos(a) * 2.4, 0.0, Math.sin(a) * 2.4);
            Entity spawned;
            try {
                spawned = world.spawnEntity(at, EntityType.SHEEP);
            } catch (Throwable t) {
                continue;
            }
            if (!(spawned instanceof Sheep)) {
                spawned.remove();
                continue;
            }
            Sheep sheep = (Sheep) spawned;
            sheep.setColor(colors[i % colors.length]);
            sheep.setRemoveWhenFarAway(false);
            sheep.setMetadata(META_WIN_SHEEP, new FixedMetadataValue(plugin, winner.getUniqueId().toString()));
            invokeBoolean(sheep, "setAI", false);
            invokeBoolean(sheep, "setInvulnerable", true);
            invokeBoolean(sheep, "setSilent", true);
            invokeBoolean(sheep, "setCollidable", false);
            invokeBoolean(sheep, "setBreed", false);
            invokeBoolean(sheep, "setAgeLock", true);
            try { sheep.setAdult(); } catch (Throwable ignored) { }
            ids.add(sheep.getUniqueId());
        }
        if (!ids.isEmpty()) winSheep.put(winner.getUniqueId(), ids);
    }

    private void tickWinSheep(Player winner, int elapsed) {
        List<UUID> ids = winSheep.get(winner.getUniqueId());
        if (ids == null || ids.isEmpty() || winner.getWorld() == null) return;
        DyeColor[] colors = DyeColor.values();
        int n = ids.size();
        for (int i = 0; i < n; i++) {
            Entity entity = entityByUuid(winner.getWorld(), ids.get(i));
            if (!(entity instanceof Sheep) || entity.isDead()) continue;
            Sheep sheep = (Sheep) entity;
            int colorIdx = rainbowSheepColorIndex(elapsed, i, colors.length);
            sheep.setColor(colors[colorIdx]);
            double a = (Math.PI * 2.0 * i) / n + elapsed * 0.08;
            Location target = winner.getLocation().clone().add(Math.cos(a) * 2.4, 0.0, Math.sin(a) * 2.4);
            target.setYaw(winner.getLocation().getYaw());
            // Teleport-follow: reliable with AI off across 1.8 + modern.
            sheep.teleport(target);
        }
    }

    private void endWinSheep(UUID owner) {
        List<UUID> ids = winSheep.remove(owner);
        if (ids == null) return;
        Player player = Bukkit.getPlayer(owner);
        World world = player != null ? player.getWorld() : null;
        if (world != null) {
            for (UUID id : ids) {
                Entity e = entityByUuid(world, id);
                if (e != null) e.remove();
            }
            return;
        }
        for (UUID id : ids) {
            for (World w : Bukkit.getWorlds()) {
                Entity e = entityByUuid(w, id);
                if (e != null) {
                    e.remove();
                    break;
                }
            }
        }
    }

    /** Version-safe mount: modern addPassenger, else 1.8 setPassenger. */
    static void mountPassenger(Entity vehicle, Entity passenger) {
        if (vehicle == null || passenger == null) return;
        try {
            vehicle.getClass().getMethod("addPassenger", Entity.class).invoke(vehicle, passenger);
            return;
        } catch (Throwable ignored) {
        }
        try {
            vehicle.getClass().getMethod("setPassenger", Entity.class).invoke(vehicle, passenger);
        } catch (Throwable ignored) {
        }
    }

    /** True when effect is active and player is not currently seated on the win dragon. */
    public static boolean needsWinDragonRemount(boolean effectActive, boolean alreadyPassenger) {
        return effectActive && !alreadyPassenger;
    }

    static boolean isPassengerOf(Entity vehicle, Entity passenger) {
        if (vehicle == null || passenger == null) return false;
        try {
            Entity riding = passenger.getVehicle();
            if (vehicle.equals(riding)) return true;
        } catch (Throwable ignored) {
        }
        try {
            List<?> passengers = (List<?>) vehicle.getClass().getMethod("getPassengers").invoke(vehicle);
            if (passengers != null && passengers.contains(passenger)) return true;
        } catch (Throwable ignored) {
        }
        try {
            Entity p = (Entity) vehicle.getClass().getMethod("getPassenger").invoke(vehicle);
            return passenger.equals(p);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isActiveWinDragon(Player player, Entity vehicle) {
        if (player == null || vehicle == null) return false;
        UUID dragonId = winDragons.get(player.getUniqueId());
        return dragonId != null && dragonId.equals(vehicle.getUniqueId());
    }

    /** Modern Spigot/Paper: EntityDismountEvent (absent on 1.8). */
    @SuppressWarnings("unchecked")
    private void registerEntityDismountCancel() {
        try {
            final Class<? extends Event> type =
                (Class<? extends Event>) Class.forName("org.bukkit.event.entity.EntityDismountEvent");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGHEST, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) {
                    try {
                        Entity entity = (Entity) type.getMethod("getEntity").invoke(event);
                        if (!(entity instanceof Player)) return;
                        Entity dismounted = (Entity) type.getMethod("getDismounted").invoke(event);
                        if (!isActiveWinDragon((Player) entity, dismounted)) return;
                        type.getMethod("setCancelled", boolean.class).invoke(event, Boolean.TRUE);
                    } catch (Throwable ignored) {
                    }
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) {
        }
    }

    static void ejectPassengers(Entity vehicle) {
        if (vehicle == null) return;
        try {
            List<?> passengers = (List<?>) vehicle.getClass().getMethod("getPassengers").invoke(vehicle);
            if (passengers != null) {
                for (Object p : new ArrayList<Object>(passengers)) {
                    if (p instanceof Entity) {
                        try {
                            vehicle.getClass().getMethod("removePassenger", Entity.class).invoke(vehicle, p);
                        } catch (Throwable ignored) {
                            ((Entity) p).leaveVehicle();
                        }
                    }
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Entity p = (Entity) vehicle.getClass().getMethod("getPassenger").invoke(vehicle);
            if (p != null) {
                try {
                    vehicle.getClass().getMethod("eject").invoke(vehicle);
                } catch (Throwable ignored) {
                    p.leaveVehicle();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Soft land after dragon despawn — scan down for solid ground. */
    private static void softLand(Player player) {
        if (player == null || player.getWorld() == null) return;
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = Math.min(loc.getBlockY(), world.getMaxHeight() - 1);
        for (int y = startY; y >= 1; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR || type.name().contains("WATER") || type.name().contains("LAVA")) continue;
            if (!type.isSolid()) continue;
            Location land = new Location(world, loc.getX(), y + 1.0, loc.getZ(), loc.getYaw(), loc.getPitch());
            player.teleport(land);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
            return;
        }
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
    }

    /** Color slot for cycling rainbow sheep wool (Bukkit-free arithmetic for coreCheck). */
    public static int rainbowSheepColorIndex(int elapsedTicks, int sheepIndex, int colorCount) {
        if (colorCount <= 0) return 0;
        int idx = (elapsedTicks / 5) + sheepIndex;
        idx %= colorCount;
        if (idx < 0) idx += colorCount;
        return idx;
    }

    /** Which Bukkit mount API name to prefer (for coreCheck). */
    public static String passengerMountMethod(boolean hasAddPassenger) {
        return hasAddPassenger ? "addPassenger" : "setPassenger";
    }

    private boolean tryWinDragonFireball(Player player) {
        if (player == null) return false;
        UUID dragonId = winDragons.get(player.getUniqueId());
        if (dragonId == null || player.getWorld() == null) return false;
        Entity dragon = entityByUuid(player.getWorld(), dragonId);
        if (dragon == null || dragon.isDead()) return false;
        if (!isPassengerOf(dragon, player)) return false;
        long now = System.currentTimeMillis();
        Long last = dragonFireballAt.get(player.getUniqueId());
        if (last != null && now - last < 450L) return true;
        dragonFireballAt.put(player.getUniqueId(), now);
        Vector dir = player.getEyeLocation().getDirection();
        if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        else dir.normalize();
        // Mouth: ahead of dragon body along look (not player feet).
        Location mouth = dragon.getLocation().clone().add(0.0, 3.0, 0.0).add(dir.clone().multiply(4.5));
        Fireball fireball = spawnWinDragonFireball(player.getWorld(), mouth);
        if (fireball == null) return true;
        fireball.setDirection(dir);
        fireball.setShooter(player);
        fireball.setIsIncendiary(false);
        fireball.setYield(winDragonFireballYield());
        fireball.setMetadata(META_WIN_FIREBALL, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        fireball.setVelocity(dir.clone().multiply(1.35));
        Sounds.playAt(mouth, "ENTITY_GHAST_SHOOT", "GHAST_FIREBALL", "GHAST_MOAN");
        Particles.play(null, mouth, 12, 0.25, "FLAME", "SMOKE", "CRIT");
        final UUID ballId = fireball.getUniqueId();
        final World world = player.getWorld();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                Entity e = entityByUuid(world, ballId);
                if (e != null) e.remove();
            }
        }, 80L);
        return true;
    }

    /** Prefer LargeFireball when present (visible dragon blast); else Fireball. */
    private static Fireball spawnWinDragonFireball(World world, Location mouth) {
        if (world == null || mouth == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Fireball> large =
                (Class<? extends Fireball>) Class.forName("org.bukkit.entity.LargeFireball");
            return world.spawn(mouth, large);
        } catch (Throwable ignored) {
        }
        try {
            return world.spawn(mouth, Fireball.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Entity entityByUuid(World world, UUID id) {
        if (world == null || id == null) return null;
        for (Entity entity : world.getEntities()) {
            if (id.equals(entity.getUniqueId())) return entity;
        }
        return null;
    }

    private static void invokeBoolean(Object target, String method, boolean value) {
        try {
            target.getClass().getMethod(method, boolean.class).invoke(target, Boolean.valueOf(value));
        } catch (Throwable ignored) {
        }
    }

    private static String metaOwner(Entity entity, String key) {
        if (entity == null || !entity.hasMetadata(key) || entity.getMetadata(key).isEmpty()) return null;
        Object value = entity.getMetadata(key).get(0).value();
        return value == null ? null : String.valueOf(value);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinAnvilLand(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity.hasMetadata(META_WIN_DRAGON) || entity.hasMetadata(META_WIN_SHEEP)) {
            event.setCancelled(true);
            return;
        }
        if (!(entity instanceof FallingBlock) || !entity.hasMetadata(META_WIN_ANVIL)) return;
        event.setCancelled(true);
        Location at = entity.getLocation();
        entity.remove();
        Sounds.playAt(at, "BLOCK_ANVIL_LAND", "ANVIL_LAND", "BLOCK_ANVIL_PLACE");
        Particles.play(null, at, 10, 0.25, "SMOKE", "CLOUD", "CRIT");
        // Remove any anvil item the server still tried to drop.
        final World world = at.getWorld();
        if (world == null) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                for (Item item : new ArrayList<Item>(world.getEntitiesByClass(Item.class))) {
                    if (item.getLocation().distanceSquared(at) > 9.0) continue;
                    Material type = item.getItemStack() == null ? null : item.getItemStack().getType();
                    if (type != null && type.name().contains("ANVIL")) item.remove();
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinAnvilCrush(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata(META_WIN_DRAGON) || event.getEntity().hasMetadata(META_WIN_SHEEP)) {
            event.setCancelled(true);
            return;
        }
        if (!"FALLING_BLOCK".equals(event.getCause().name())) return;
        for (Entity nearby : event.getEntity().getNearbyEntities(3.0, 3.0, 3.0)) {
            if (nearby.hasMetadata(META_WIN_ANVIL)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinCosmeticDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager.hasMetadata(META_WIN_DRAGON) || damager.hasMetadata(META_WIN_FIREBALL)
            || damager.hasMetadata(META_WIN_ANVIL) || damager.hasMetadata(META_WIN_SHEEP)) {
            event.setCancelled(true);
            return;
        }
        if (damager instanceof Fireball) {
            Entity shooter = ((Fireball) damager).getShooter() instanceof Entity
                ? (Entity) ((Fireball) damager).getShooter() : null;
            if (shooter != null && shooter.hasMetadata(META_WIN_DRAGON)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinDragonPortal(org.bukkit.event.entity.EntityCreatePortalEvent event) {
        if (event.getEntity() != null && event.getEntity().hasMetadata(META_WIN_DRAGON)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWinDragonVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        if (isActiveWinDragon((Player) event.getExited(), event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWinDragonSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        UUID dragonId = winDragons.get(player.getUniqueId());
        if (dragonId == null || player.getWorld() == null) return;
        Entity dragon = entityByUuid(player.getWorld(), dragonId);
        if (dragon != null && isPassengerOf(dragon, player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWinDragonInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (tryWinDragonFireball(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWinDragonClick(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked != null && clicked.hasMetadata(META_WIN_DRAGON)) {
            String owner = metaOwner(clicked, META_WIN_DRAGON);
            if (owner != null && owner.equals(event.getPlayer().getUniqueId().toString())) {
                event.setCancelled(true);
                tryWinDragonFireball(event.getPlayer());
            }
        }
    }

    /** Mounted interact often fails — arm swing / left-click is the reliable fire trigger. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWinDragonArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        tryWinDragonFireball(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWinDragonPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        if (!tryWinDragonFireball(player)) return;
        event.setCancelled(true);
    }

    private static String[] namesOr(String[] particles, String... fallback) {
        return particles != null && particles.length > 0 ? particles : fallback;
    }

    private static void spawnFirework(Location at) {
        EntityType type = fireworkEntityType();
        if (type == null || at.getWorld() == null) {
            Particles.play(null, at.clone().add(0, 1.0, 0), 20, 0.35, "FIREWORKS_SPARK", "FIREWORK", "FLAME");
            return;
        }
        try {
            Firework firework = (Firework) at.getWorld().spawnEntity(at, type);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.YELLOW, Color.FUCHSIA, Color.LIME)
                .withFade(Color.ORANGE, Color.WHITE)
                .trail(true)
                .flicker(true)
                .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            try { firework.getClass().getMethod("detonate").invoke(firework); }
            catch (Throwable ignored) { }
        } catch (Throwable ignored) {
            Particles.play(null, at.clone().add(0, 1.0, 0), 24, 0.4, "FIREWORKS_SPARK", "FIREWORK", "FLAME");
        }
    }

    private static EntityType fireworkEntityType() {
        try { return EntityType.valueOf("FIREWORK"); } catch (IllegalArgumentException ignored) { }
        try { return EntityType.valueOf("FIREWORK_ROCKET"); } catch (IllegalArgumentException ignored) { }
        return null;
    }

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
