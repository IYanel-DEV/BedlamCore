package dev.iyanel.bedlamcore.cosmetics;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Particles;
import dev.iyanel.bedlamcore.compat.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Victory win-effect + mounted-dragon subsystem. Extracted from {@link CosmeticsService}; own Listener. */
final class WinEffectController implements Listener {
    private static final String META_WIN_ANVIL = "bedlamWinAnvil";
    private static final String META_WIN_DRAGON = "bedlamWinDragon";
    private static final String META_WIN_FIREBALL = "bedlamWinFireball";
    private static final String META_WIN_SHEEP = "bedlamWinSheep";

    private final BedlamCore plugin;
    private final CosmeticsService cosmetics;
    /** Winner UUID -> active win-dragon entity UUID. */
    private final Map<UUID, UUID> winDragons = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, Long> dragonFireballAt = new ConcurrentHashMap<UUID, Long>();
    /** Winner UUID -> rainbow sheep entity UUIDs. */
    private final Map<UUID, List<UUID>> winSheep = new ConcurrentHashMap<UUID, List<UUID>>();
    /** Owner UUIDs whose dragon is mid-move (eject/remount) - dismount cancel must not fight it. */
    private final Map<UUID, Boolean> dragonMoving = new ConcurrentHashMap<UUID, Boolean>();
    /**
     * Authoritative flight position per owner. On 1.8 the EnderDragon's AI cannot be disabled
     * (setAI is 1.9+), so reading dragon.getLocation() would compound the AI's drift each tick and
     * the dragon flies off. We advance and teleport from this tracked position instead, snapping the
     * dragon back onto the controlled path every tick regardless of where its AI tried to wander.
     */
    private final Map<UUID, Location> dragonPos = new ConcurrentHashMap<UUID, Location>();

    WinEffectController(BedlamCore plugin, CosmeticsService cosmetics) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerEntityDismountCancel();
    }
    /**
     * Play equipped win effect at the living winner for ~4–6s (Hypixel-style short show).
     * No equipped cosmetic → no effect. Caps particle rate via 5-tick period.
     */
    public void playWinEffect(Player winner) {
        if (winner == null || winner.getWorld() == null) return;
        String id = plugin.stats().equippedCosmetic(winner.getUniqueId(), CosmeticsService.CAT_WIN_EFFECT);
        CosmeticsService.Cosmetic cosmetic = cosmetics.get(id);
        if (cosmetic == null) return;
        final String effect = cosmetic.effect == null || cosmetic.effect.isEmpty()
            ? "burst" : cosmetic.effect.toLowerCase();
        final String[] particles = cosmetic.particles.isEmpty()
            ? null : cosmetic.particles.toArray(new String[0]);
        final UUID uuid = winner.getUniqueId();
        // ponytail: fixed cadence; dragon/wither ~7.5s ride, rainbow sheep ~5s follow
        final boolean mountRide = "dragon".equals(effect) || "wither".equals(effect);
        final int durationTicks = mountRide ? 150 : ("rainbow".equals(effect) ? 100 : 80);
        final int period = 5;
        if (mountRide) spawnWinMount(winner, effect);
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
                dragonPos.remove(entry.getKey());
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
            // Real funnel: narrow at the base, flaring wide at the top, whole column swirling over
            // time. Plot a fine multi-turn helix (count=1, spread=0) so it reads as a crisp vortex
            // instead of random puffs — and so it still forms a funnel on 1.8 (count/spread ignored).
            final double height = 5.0;
            final int rings = 32;               // vertical samples along the funnel
            final double turns = 4.0;           // helix wraps base→top
            final double spin = elapsed * 0.6;  // whole funnel rotates
            final String[] dust = namesOr(particles, "CLOUD", "SMOKE");
            for (int i = 0; i <= rings; i++) {
                double t = (double) i / rings;              // 0 = base, 1 = top
                double y = t * height;
                double funnelR = 0.25 + t * t * 1.6;        // quadratic flare = funnel curve
                double ang = spin + t * turns * Math.PI * 2.0;
                for (int s = 0; s < 2; s++) {               // two opposite strands = denser vortex
                    double a = ang + s * Math.PI;
                    Location p = base.clone().add(Math.cos(a) * funnelR, y, Math.sin(a) * funnelR);
                    Particles.play(null, p, 1, 0.0, dust);
                }
            }
            // Debris kicked up orbiting the base.
            double da = spin * 1.7;
            Particles.play(null, base.clone().add(Math.cos(da) * 0.45, 0.1, Math.sin(da) * 0.45), 1, 0.0, "CRIT", "SMOKE");
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
        if ("dragon".equals(effect) || "wither".equals(effect)) {
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

    /** Spawn the win mount (dragon or wither) under the winner and start the flight loop. */
    private void spawnWinMount(Player winner, String effect) {
        boolean wither = "wither".equals(effect);
        World world = winner.getWorld();
        if (world == null) return;
        endWinDragon(winner.getUniqueId());
        // Spawn under the winner so setPassenger seats them Hypixel-style (orbit teleport was the mount blocker).
        Location at = winner.getLocation().clone().add(0.0, 1.2, 0.0);
        Entity mount;
        try {
            mount = world.spawnEntity(at, wither ? EntityType.WITHER : EntityType.ENDER_DRAGON);
        } catch (Throwable t) {
            Particles.play(null, at, 40, 1.2, "FLAME", "PORTAL", "SMOKE", "CRIT");
            Sounds.playAt(at, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
            return;
        }
        // Reuse the win-dragon tag: every protection (damage cancel, portal cancel, grief tracking,
        // match-reset cleanup) then applies to the wither identically.
        mount.setMetadata(META_WIN_DRAGON, new FixedMetadataValue(plugin, winner.getUniqueId().toString()));
        if (mount instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) mount;
            living.setRemoveWhenFarAway(false);
            if (wither) {
                // The client Wither has no isAIDisabled gate (unlike the dragon), so noAI is safe here:
                // it silences the wandering/shooting AI server-side without freezing the client.
                invokeBoolean(living, "setAI", false);
            }
            invokeBoolean(living, "setGravity", false);
            invokeBoolean(living, "setInvulnerable", true);
            invokeBoolean(living, "setCollidable", false);
        }
        if (!wither) {
            // NO setAI(false) on the dragon! noAI syncs to the client and the client EnderDragon skips its WHOLE
            // update — including the position lerp — while noAI is set (verified against 1.12.2 client
            // bytecode: onLivingUpdate gates on isAIDisabled). The dragon froze mid-air with any streamed
            // movement. Instead the HOVER phase keeps the server AI stationary (see setHoverPhase).
            setHoverPhase(mount);
        }
        winDragons.put(winner.getUniqueId(), mount.getUniqueId());
        dragonPos.put(winner.getUniqueId(), at.clone());
        mountPassenger(mount, winner);
        // Resolve the relative-move streaming stack once (packet ctors + send plumbing + tracker baseline).
        // Null on unknown servers → the vanilla tracker syncs the NMS-relocated mount alone.
        resolveDragonStream();
        // One absolute resync so every client starts the ride from the exact mounted position (doc contract),
        // then per-tick RELATIVE move streaming keeps the camera interpolated/silk (never teleport-strobed).
        sendDragonResync(mount);
        // Every tick: fly toward the rider's look + force remount (sneak/eject cannot stick).
        final UUID owner = winner.getUniqueId();
        final UUID mountId = mount.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!mountId.equals(winDragons.get(owner))) {
                    cancel();
                    return;
                }
                Player p = Bukkit.getPlayer(owner);
                if (p == null || !p.isOnline() || p.getWorld() == null) return;
                Entity d = entityByUuid(p.getWorld(), mountId);
                if (d == null || d.isDead()) {
                    winDragons.remove(owner);
                    cancel();
                    return;
                }
                flyWinDragon(owner, d, p, ticks);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // Safety net: some Paper builds eject the rider on the tick after spawn — re-seat once if so.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                Player p = Bukkit.getPlayer(owner);
                if (p == null || !p.isOnline() || p.getWorld() == null) return;
                Entity d = entityByUuid(p.getWorld(), mountId);
                if (d == null || d.isDead()) return;
                if (!isPassengerOf(d, p)) {
                    dragonMoving.put(owner, Boolean.TRUE);
                    try {
                        mountPassenger(d, p);
                    } finally {
                        dragonMoving.remove(owner);
                    }
                }
            }
        }, 2L);
        if (wither) {
            Sounds.playAt(at, "ENTITY_WITHER_SPAWN", "ENTITY_WITHER_SPAWN", "WITHER_SPAWN");
        } else {
            Sounds.playAt(at, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
        }
    }

    /**
     * The EnderDragon model renders its head toward -Z at yaw 0 (opposite normal mobs). Body yaw is computed from
     * the ACTUAL displacement each tick, then this single offset makes the head LEAD the motion instead of flying
     * tail-first. It is the ONE model-orientation constant — flip its sign here if the head ever trails.
     */
    private static final float DRAGON_MODEL_YAW_OFFSET = 180f;
    /** Clamp head/body pitch so climbs and dives read naturally without the model flipping over. */
    private static final double DRAGON_PITCH_CLAMP = 40.0;
    /** Fraction of the yaw gap closed per tick (~3 ticks to settle) so turning eases instead of snap-flipping. */
    private static final float DRAGON_YAW_LERP = 0.35f;

    /** One flight tick: move dragon+rider toward the rider's look, carve the swept path, keep them seated. */
    private void flyWinDragon(UUID owner, Entity dragon, Player rider, int ticks) {
        if (Boolean.TRUE.equals(dragonMoving.get(owner))) return; // a fallback eject/remount move is mid-flight
        Vector dir = rider.getEyeLocation().getDirection();
        if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        else dir.normalize();
        // Advance from our tracked position (not dragon.getLocation()) so a live AI on 1.8 cannot
        // drag the flight off course — the dragon is snapped back onto this path every tick.
        Location base = dragonPos.get(owner);
        if (base == null || base.getWorld() != dragon.getWorld()) base = dragon.getLocation();
        Location next = base.clone().add(dir.clone().multiply(winDragonPerTickStep()));
        // Carve the whole swept segment (current -> destination) to air BEFORE moving. The old code griefed only
        // the dragon's CURRENT cell AFTER the move, so climbing a hill / diving into ground teleported into solid
        // blocks the server clamped or rubber-banded — the "no vertical progress" bug.
        griefWinDragonPath(dragon.getWorld(), base, next);
        // Hold the flight AI onto the path: 1.8 pins its legacy target fields; 1.9+ re-asserts the HOVER phase.
        // Dragon-only — the wither runs noAI (its client has no isAIDisabled gate, see spawnWinMount).
        boolean dragonMount = dragon.getType() == EntityType.ENDER_DRAGON;
        if (dragonMount) {
            pinLegacyDragonAi(dragon, next);
            if (supportsAiToggle() && ticks % 10 == 0) setHoverPhase(dragon);
        }
        // If the destination is still blocked by an unbreakable (bedrock/barrier), slide along the wall instead
        // of stalling: drop the vertical component, optionally hop up <=1 block.
        next = slideIfBlocked(base, next);
        // Facing is derived from the ACTUAL displacement — never a hardcoded ±180 guess — so tail-first flight is
        // impossible on any version. Body yaw from dx/dz + the single model offset; pitch from dy.
        double dx = next.getX() - base.getX();
        double dy = next.getY() - base.getY();
        double dz = next.getZ() - base.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        // Facing comes from the actual displacement. The EnderDragon's client model is reversed (head toward
        // -Z at yaw 0), so it needs the +180 model offset; the WITHER uses the normal mob model (faces +Z at
        // yaw 0) and reusing the dragon's offset made it fly head-first BACKWARDS.
        float targetYaw = horiz > 1.0e-4
            ? (dragonMount ? dragonBodyYaw(dx, dz) : witherBodyYaw(dx, dz))
            : base.getYaw();
        float yaw = lerpYawShortest(base.getYaw(), targetYaw, DRAGON_YAW_LERP);
        float pitch = clampDragonPitch(dy, horiz);
        next.setYaw(yaw);
        next.setPitch(pitch);
        // Move; commit the tracked position to where the entity ACTUALLY ended up (never advance past a refused
        // move in the fallback path — that drift caused the snap/stall).
        Location applied = moveMountedDragon(owner, dragon, rider, next, ticks);
        applied.setYaw(yaw);
        applied.setPitch(pitch);
        dragonPos.put(owner, applied);
        if (DRAGON_DEBUG && ticks % 20 == 0) {
            plugin.getLogger().info("[windragon] sync=" + (dragonStream == null ? "tracker" : "stream")
                + " pos=" + applied.getBlockX() + "," + applied.getBlockY() + "," + applied.getBlockZ()
                + " seated=" + isPassengerOf(dragon, rider));
        }
        // Force the dragon's head rotation to follow (EnderDragon head yaw lags after a move on 1.11+; no-op 1.8).
        try {
            dragon.getClass().getMethod("setRotation", float.class, float.class)
                .invoke(dragon, Float.valueOf(yaw), Float.valueOf(pitch));
        } catch (Throwable ignored) {
        }
        if (ticks % 5 == 0) {
            Particles.play(null, dragon.getLocation().clone().add(0, 1.5, 0), 6, 0.5, "FLAME", "PORTAL", "SMOKE");
        }
        if (ticks % 20 == 0) {
            if (dragonMount) {
                Sounds.playAt(dragon.getLocation(), "ENTITY_ENDER_DRAGON_FLAP", "ENDERDRAGON_WINGS", "BAT_TAKEOFF");
            } else {
                Sounds.playAt(dragon.getLocation(), "ENTITY_WITHER_AMBIENT", "WITHER_AMBIENT", "ENTITY_WITHER_HURT");
            }
        }
    }

    /** Bukkit-free body yaw from a horizontal delta (standard Bukkit convention) + the EnderDragon model offset. */
    public static float dragonBodyYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz)) + DRAGON_MODEL_YAW_OFFSET;
    }

    /** Body yaw for the wither: a NORMAL mob model (faces +Z at yaw 0), so no EnderDragon offset. Applying the
     *  dragon's +180 model offset made the wither fly head-first backwards. */
    private static float witherBodyYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Bukkit-free pitch from the vertical/horizontal delta, clamped so the model never flips. */
    public static float clampDragonPitch(double dy, double horizontalDist) {
        if (horizontalDist <= 1.0e-4 && Math.abs(dy) <= 1.0e-4) return 0f;
        double pitch = Math.toDegrees(Math.atan2(dy, Math.max(horizontalDist, 1.0e-4)));
        if (pitch > DRAGON_PITCH_CLAMP) pitch = DRAGON_PITCH_CLAMP;
        else if (pitch < -DRAGON_PITCH_CLAMP) pitch = -DRAGON_PITCH_CLAMP;
        return (float) pitch;
    }

    /** Shortest-arc yaw interpolation (Bukkit-free) so turning eases over a few ticks instead of snapping. */
    public static float lerpYawShortest(float from, float to, float alpha) {
        float diff = to - from;
        while (diff < -180f) diff += 360f;
        while (diff > 180f) diff -= 360f;
        return from + diff * alpha;
    }

    /**
     * Move a mounted win dragon (and its rider) to {@code next}, returning where the dragon actually ended up.
     *
     * Streaming path (default): relocate the dragon handle at the NMS level ({@code setLocation} /
     * {@code setPositionRotation} / {@code moveTo} / {@code absSnapTo}) WITHOUT ejecting the rider — the server
     * repositions the seated rider itself — then stream a RELATIVE move+look packet to every viewer. Relative
     * moves are interpolated by the client over ~3 ticks, so the rider's camera glides with the dragon. Per-tick
     * absolute teleport packets (the old behaviour) apply instantly with zero client interpolation, which strobed
     * the camera 20x/second — that was the "tweaking" bug. After streaming, the vanilla tracker's last-sent
     * baseline is re-aligned to the streamed state so its own sync computes ~zero delta and stays quiet instead
     * of double-moving the client.
     *
     * If any piece of the streaming stack cannot be resolved on the running server, streaming is skipped
     * entirely and the vanilla tracker syncs the relocated entity on its own (mob-grade smoothness, never
     * broken). Only when even the NMS relocate is unavailable do we fall back to eject → teleport → remount.
     */
    private Location moveMountedDragon(UUID owner, Entity dragon, Player rider, Location next, int ticks) {
        Location prev = dragon.getLocation();
        if (nmsRelocate(dragon, next)) {
            if (dragonStream != null && streamDragonMove(dragon, prev, next, ticks)) {
                streamConsecutiveFailures = 0;
            } else if (dragonStream != null && ++streamConsecutiveFailures >= 5) {
                // Streaming broke mid-ride — latch off so the vanilla tracker syncs alone (never broken).
                dragonStream = null;
                if (DRAGON_DEBUG) plugin.getLogger().info("[windragon] streaming latched off -> tracker sync");
            }
            return next.clone();
        }
        // Deep fallback (NMS relocate unavailable — practically never): eject → teleport both → remount.
        // Teleporting the rider every tick yanks the camera; this path exists only so the dragon still moves.
        boolean seated = isPassengerOf(dragon, rider);
        if (seated) {
            dragonMoving.put(owner, Boolean.TRUE);
            try {
                ejectPassengers(dragon);
            } finally {
                dragonMoving.remove(owner);
            }
        }
        boolean moved = dragon.teleport(next);
        if (seated) {
            rider.teleport(next);
            mountPassenger(dragon, rider);
        }
        // Only commit past a move the server accepted; otherwise report the real spot so dragonPos can't drift.
        return moved ? next.clone() : dragon.getLocation();
    }

    /** Once-per-second flight diagnostics (sync mode, position delta, seat). Off in release builds. */
    private static final boolean DRAGON_DEBUG = false;

    /** Cached streaming stack (packet ctors + baseline fields); null = vanilla-tracker-only sync for the JVM. */
    private static volatile DragonStream dragonStream;
    private static boolean streamResolved;
    private static int streamConsecutiveFailures;

    /**
     * Relative-move client sync for the win dragon, resolved once per JVM. All-or-nothing: if the packet
     * constructors, the send plumbing, or the tracker baseline fields cannot be resolved, {@code null} is
     * cached and the vanilla tracker syncs alone (mob-grade smoothness — never broken, never double-synced).
     */
    private static final class DragonStream {
        /** (int entityId, [byte|short|long]×3 deltas, byte yaw, byte pitch, boolean onGround). */
        final Constructor<?> relMove;
        /** (nms Entity, byte headYaw). */
        final Constructor<?> headRot;
        /** (int entityId) absolute resync; null on Mojang-record layouts where a zero-delta relmove is used. */
        final Constructor<?> teleport;
        /** Delta fixed-point scale: 32 on 1.8.x, 4096 on 1.9+ (both mappings). */
        final int scale;
        /** Delta parameter type of the resolved ctor (long 1.12 / short 1.9+ / byte 1.8) — quantization width. */
        final Class<?> deltaType;
        /** Largest per-component delta the relmove packet can carry before an absolute resync is required. */
        final double maxDelta;
        /** getHandle → connection field → send method plumbing, resolved against the running server. */
        final Field connectionField;
        final Method sendMethod;

        DragonStream(Constructor<?> relMove, Constructor<?> headRot, Constructor<?> teleport,
                     int scale, Field connectionField, Method sendMethod) {
            this.relMove = relMove;
            this.headRot = headRot;
            this.teleport = teleport;
            this.scale = scale;
            this.deltaType = relMove.getParameterTypes()[1];
            this.maxDelta = deltaType == byte.class ? 3.9 : 8.0;
            this.connectionField = connectionField;
            this.sendMethod = sendMethod;
        }

        /**
         * Quantize one delta component into the ctor's exact parameter width. The 1.9+ packet carries
         * delta*4096 as a SHORT — casting that to byte truncates ~2048 to 0, which zeroed every streamed
         * move and left the client dragon frozen while the server-side seat advanced (the violent
         * rubber-band). Integer widens to the long params on 1.12.2; Short/Byte fit their own shapes.
         */
        Number quantize(double delta) {
            long scaled = Math.round(delta * scale);
            if (deltaType == long.class) return Integer.valueOf((int) scaled);
            if (deltaType == short.class) return Short.valueOf((short) scaled);
            return Byte.valueOf((byte) scaled);
        }
    }

    /** Resolved tracker-baseline fields for one entry class (EntityTrackerEntry / ServerEntity). */
    private static final class BaselineFields {
        final Field x, y, z;            // quantized last-sent position (int on 1.8, long on 1.12/1.16) — nullable
        final Field yawInt, pitchInt;   // quantized last-sent angles (int, 1.8–1.16) — nullable
        final Field yawByte, pitchByte; // Mojang-layout byte angles (ServerEntity, 26.2) — nullable
        final Field vecPos;             // exact last-sent position (Vec3D/Vec3) — nullable
        final Field entryCodecField;    // entry field holding the VecDeltaCodec — nullable
        final Field codecVecPos;        // VecDeltaCodec.base exact position — nullable
        final Constructor<?> vecCtor;   // (double,double,double) for the vec types above
        final boolean valid;

        BaselineFields(Class<?> entryClass) {
            Field vx = null, vy = null, vz = null, yi = null, pi = null, yb = null, pb = null,
                vec = null, codecField = null, codecVec = null;
            Constructor<?> vc = null;
            for (Class<?> c = entryClass; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    String n = f.getName();
                    Class<?> t = f.getType();
                    try {
                        if ((n.equals("xLoc") || n.equals("yLoc") || n.equals("zLoc"))
                            && (t == int.class || t == long.class)) {
                            f.setAccessible(true);
                            if (n.equals("xLoc")) vx = f;
                            else if (n.equals("yLoc")) vy = f;
                            else vz = f;
                        } else if (n.equals("yRot") && t == int.class) {
                            f.setAccessible(true);
                            yi = f;
                        } else if (n.equals("xRot") && t == int.class) {
                            f.setAccessible(true);
                            pi = f;
                        } else if (n.equals("lastSentYRot") && t == byte.class) {
                            f.setAccessible(true);
                            yb = f;
                        } else if (n.equals("lastSentXRot") && t == byte.class) {
                            f.setAccessible(true);
                            pb = f;
                        } else if (t.getSimpleName().equals("VecDeltaCodec")) {
                            f.setAccessible(true);
                            codecField = f;
                        } else if (isVecType(t) && !n.equals("lastSentMovement")) {
                            f.setAccessible(true);
                            vec = f;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (codecField != null) {
                for (Field cf : codecField.getType().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(cf.getModifiers())) continue;
                    if (isVecType(cf.getType())) {
                        cf.setAccessible(true);
                        codecVec = cf;
                        break;
                    }
                }
            }
            Class<?> vecClass = vec != null ? vec.getType() : (codecVec != null ? codecVec.getType() : null);
            if (vecClass != null) {
                try {
                    vc = vecClass.getConstructor(double.class, double.class, double.class);
                } catch (Throwable ignored) {
                    vc = null;
                    vec = null;
                    codecVec = null;
                }
            }
            this.x = vx;
            this.y = vy;
            this.z = vz;
            this.yawInt = yi;
            this.pitchInt = pi;
            this.yawByte = yb;
            this.pitchByte = pb;
            this.vecPos = vec;
            this.entryCodecField = codecField;
            this.codecVecPos = codecVec;
            this.vecCtor = vc;
            this.valid = (vx != null && vy != null && vz != null) || vec != null
                || codecVec != null || yb != null || yi != null;
        }

        private static boolean isVecType(Class<?> t) {
            String n = t.getName();
            return n.endsWith(".Vec3D") || n.endsWith(".Vec3");
        }

        /**
         * Re-align the tracker's last-sent state to {@code at}/{@code yaw}/{@code pitch} so its next sync
         * computes ~zero delta and stays quiet instead of double-moving the client.
         */
        void apply(Object entry, Location at, float yaw, float pitch, int scale) {
            try {
                if (x != null && y != null && z != null) {
                    setNumber(x, entry, (long) Math.floor(at.getX() * scale));
                    setNumber(y, entry, (long) Math.floor(at.getY() * scale));
                    setNumber(z, entry, (long) Math.floor(at.getZ() * scale));
                }
                if (yawInt != null) setNumber(yawInt, entry, (int) Math.floor(yaw * 256.0f / 360.0f));
                if (pitchInt != null) setNumber(pitchInt, entry, (int) Math.floor(pitch * 256.0f / 360.0f));
                if (yawByte != null) setNumber(yawByte, entry, angleByte(yaw));
                if (pitchByte != null) setNumber(pitchByte, entry, angleByte(pitch));
                if (vecPos != null && vecCtor != null) {
                    vecPos.set(entry, vecCtor.newInstance(at.getX(), at.getY(), at.getZ()));
                }
                if (entryCodecField != null && codecVecPos != null && vecCtor != null) {
                    Object codec = entryCodecField.get(entry);
                    if (codec != null) {
                        codecVecPos.set(codec, vecCtor.newInstance(at.getX(), at.getY(), at.getZ()));
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        private void setNumber(Field f, Object target, Number value) throws IllegalAccessException {
            Class<?> t = f.getType();
            if (t == int.class) f.setInt(target, value.intValue());
            else if (t == long.class) f.setLong(target, value.longValue());
            else if (t == byte.class) f.setByte(target, value.byteValue());
            else if (t == double.class) f.setDouble(target, value.doubleValue());
            else if (t == float.class) f.setFloat(target, value.floatValue());
        }
    }

    /** Baseline field sets cached per entry class (the shape is fixed per server). */
    private static final Map<Class<?>, BaselineFields> baselineCache =
        new java.util.concurrent.ConcurrentHashMap<Class<?>, BaselineFields>();

    /**
     * Stream this tick's dragon move to every nearby viewer as a RELATIVE move+look packet
     * (client-interpolated over ~3 ticks — silky) plus a head-rotation packet every 2 ticks, then re-align
     * the vanilla tracker baseline so its own sync stays quiet. Works because the dragon is NOT noAI:
     * with noAI set the client EnderDragon skips its entire update including the lerp (frozen dragon).
     * Deltas beyond the packet's fixed-point range fall back to one absolute resync. Returns false when
     * anything fails so the caller can latch to vanilla-tracker-only sync.
     */
    private boolean streamDragonMove(Entity dragon, Location prev, Location next, int ticks) {
        DragonStream s = dragonStream;
        if (s == null) return false;
        try {
            // All-or-nothing: align the vanilla tracker baseline BEFORE streaming. If the baseline cannot be
            // resolved on this server we must not stream at all — the tracker would double-move the client.
            if (!alignTrackerBaseline(dragon, next)) return false;
            double dx = next.getX() - prev.getX();
            double dy = next.getY() - prev.getY();
            double dz = next.getZ() - prev.getZ();
            Byte yawB = Byte.valueOf(angleByte(next.getYaw()));
            Byte pitchB = Byte.valueOf(angleByte(next.getPitch()));
            Object movePacket;
            if (Math.abs(dx) > s.maxDelta || Math.abs(dy) > s.maxDelta || Math.abs(dz) > s.maxDelta) {
                if (s.teleport == null) return false;
                Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
                movePacket = s.teleport.newInstance(handle);
            } else {
                movePacket = s.relMove.newInstance(Integer.valueOf(dragon.getEntityId()),
                    s.quantize(dx), s.quantize(dy), s.quantize(dz), yawB, pitchB, Boolean.FALSE);
            }
            boolean sent = false;
            for (Player viewer : dragon.getWorld().getPlayers()) {
                if (EntityVisibility.isSpectator(viewer)) continue;
                if (viewer.getLocation().distanceSquared(next) > 160.0 * 160.0) continue;
                if (sendPacket(s, viewer, movePacket)) sent = true;
            }
            if (!sent) return false;
            if (s.headRot != null && ticks % 2 == 0) {
                Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
                Object head = s.headRot.newInstance(handle, Byte.valueOf(angleByte(next.getYaw())));
                for (Player viewer : dragon.getWorld().getPlayers()) {
                    if (EntityVisibility.isSpectator(viewer)) continue;
                    if (viewer.getLocation().distanceSquared(next) > 160.0 * 160.0) continue;
                    sendPacket(s, viewer, head);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static byte angleByte(float degrees) {
        return (byte) Math.floor(degrees * 256.0f / 360.0f);
    }

    /**
     * Re-align the vanilla tracker's last-sent baseline so its own sync computes ~zero delta (stays quiet).
     * Returns false when the entry/baseline cannot be resolved — callers must not stream in that state.
     */
    private static boolean alignTrackerBaseline(Entity dragon, Location at) {
        try {
            Object entry = trackerEntryFor(dragon);
            if (entry == null) return false;
            BaselineFields bf = baselineCache.get(entry.getClass());
            if (bf == null) {
                bf = new BaselineFields(entry.getClass());
                baselineCache.put(entry.getClass(), bf);
            }
            if (!bf.valid) return false;
            DragonStream s = dragonStream;
            bf.apply(entry, at, at.getYaw(), at.getPitch(), s != null ? s.scale : 4096);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Locate the vanilla per-entity tracker entry for {@code dragon}, across three architectures:
     * moonrise (26.2+): handle.moonrise$getTrackedEntity().serverEntity;
     * classic (1.8/1.12): WorldServer.tracker (EntityTracker).trackedEntities(IntHashMap).get(id);
     * chunk-map (1.16/1.20): WorldServer.chunkProvider.playerChunkMap.trackedEntities(Int2ObjectMap).get(id)
     * → wrapper.trackerEntry. Returns null when the shape is unknown (caller degrades gracefully).
     */
    private static Object trackerEntryFor(Entity dragon) throws Exception {
        Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
        // moonrise (Paper 26.2+): per-entity TrackedEntity with a public serverEntity field. The accessor may be
        // declared on the entity class itself or inherited from the moonrise interface — scan both.
        Method moonrise = null;
        for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("moonrise$getTrackedEntity") && m.getParameterCount() == 0) {
                    moonrise = m;
                    break;
                }
            }
            if (moonrise != null) break;
        }
        if (moonrise == null) {
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("moonrise$getTrackedEntity") && m.getParameterCount() == 0) {
                    moonrise = m;
                    break;
                }
            }
        }
        if (moonrise != null) {
            moonrise.setAccessible(true);
            Object tracked = moonrise.invoke(handle);
            if (tracked == null) return null;
            for (Field f : tracked.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("ServerEntity")) {
                    f.setAccessible(true);
                    return f.get(tracked);
                }
            }
            return null;
        }
        Object worldHandle = dragon.getWorld().getClass().getMethod("getHandle").invoke(dragon.getWorld());
        // classic (1.8/1.12): WorldServer.tracker → IntHashMap trackedEntities.get(id) → entry.
        Object tracker = typedFieldGet(worldHandle, "EntityTracker");
        if (tracker != null && !tracker.getClass().getSimpleName().endsWith("Entry")) {
            Object map = typedFieldGet(tracker, "IntHashMap");
            if (map != null) {
                Object entry = invokeGet(map, dragon.getEntityId());
                if (entry != null) return entry;
            }
        }
        // chunk-map (1.16/1.20): chunkProvider → playerChunkMap → Int2ObjectMap trackedEntities.get(id)
        // → wrapper with an EntityTrackerEntry-typed field.
        Object chunkProvider = typedFieldGet(worldHandle, "ChunkProviderServer");
        if (chunkProvider == null) {
            for (Method m : worldHandle.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("ChunkProviderServer")) {
                    chunkProvider = m.invoke(worldHandle);
                    break;
                }
            }
        }
        if (chunkProvider == null) return null;
        Object chunkMap = typedFieldGet(chunkProvider, "PlayerChunkMap");
        if (chunkMap == null) return null;
        Object map = typedFieldGet(chunkMap, "Int2ObjectMap");
        if (map == null) return null;
        Object wrapper = invokeGet(map, dragon.getEntityId());
        if (wrapper == null) return null;
        return typedFieldGet(wrapper, "EntityTrackerEntry", "ServerEntity");
    }

    /** Read the first instance field of {@code target} whose type simple name matches any of {@code names}. */
    private static Object typedFieldGet(Object target, String... names) throws IllegalAccessException {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                String simple = f.getType().getSimpleName();
                for (String n : names) {
                    if (simple.equals(n)) {
                        f.setAccessible(true);
                        return f.get(target);
                    }
                }
            }
        }
        return null;
    }

    /** IntHashMap.get(int) / Int2ObjectMap.get(int) — both expose a public single-int {@code get}. */
    private static Object invokeGet(Object map, int entityId) throws Exception {
        for (Method m : map.getClass().getMethods()) {
            if (!m.getName().equals("get") || m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0] != int.class) continue;
            return m.invoke(map, Integer.valueOf(entityId));
        }
        return null;
    }

    /** Cached NMS relocation method (DDDFF) resolved once; null = use the deep eject/teleport fallback. */
    private static Method nmsSetLocation;
    private static boolean nmsSetLocationResolved;

    /**
     * Move the entity at the NMS level so a seated passenger is NOT ejected. Resolved reflectively by name
     * ({@code setLocation} 1.8 Spigot, {@code setPositionRotation} older CB, {@code moveTo} Mojang-mapped,
     * {@code absSnapTo} 1.21.2+), else by signature — Paper 1.20.4 keeps obfuscated member names, so any
     * void (double,double,double,float,float) method on the Entity hierarchy is accepted. Returns false if
     * nothing resolves or the call throws → caller uses the deep fallback.
     */
    private static boolean nmsRelocate(Entity entity, Location to) {
        Method setter = resolveNmsSetLocation(entity);
        if (setter == null) return false;
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            setter.invoke(handle, to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method resolveNmsSetLocation(Entity entity) {
        if (nmsSetLocationResolved) return nmsSetLocation;
        nmsSetLocationResolved = true;
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            for (String name : new String[]{"setLocation", "setPositionRotation", "moveTo", "absSnapTo"}) {
                Method m = findHandleMethod(handle.getClass(), name);
                if (m != null) {
                    nmsSetLocation = m;
                    return nmsSetLocation;
                }
            }
            // Signature fallback for obfuscated members (Paper 1.20.4): first void (DDDFF) on the hierarchy.
            for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 5 && p[0] == double.class && p[1] == double.class && p[2] == double.class
                        && p[3] == float.class && p[4] == float.class && m.getReturnType() == void.class) {
                        m.setAccessible(true);
                        nmsSetLocation = m;
                        return nmsSetLocation;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return nmsSetLocation;
    }

    private static Method findHandleMethod(Class<?> handleClass, String name) {
        for (Class<?> c = handleClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, double.class, double.class, double.class,
                    float.class, float.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** True when LivingEntity.setAI exists (1.9+). On 1.8 the dragon's flight AI cannot be disabled. */
    private static boolean supportsAiToggle() {
        if (aiToggle == null) {
            try {
                LivingEntity.class.getMethod("setAI", boolean.class);
                aiToggle = Boolean.TRUE;
            } catch (Throwable t) {
                aiToggle = Boolean.FALSE;
            }
        }
        return aiToggle.booleanValue();
    }

    private static Boolean aiToggle;

    /**
     * Park the dragon in the HOVER phase (Bukkit {@code EnderDragon.setPhase}, 1.9+). Hover keeps the
     * server AI stationary — no waypoints, no attacks, no block grief — WITHOUT noAI (which freezes the
     * client dragon: see the spawn comment). The phase also syncs to clients, whose own controller then
     * hovers passively and follows our streamed relative moves. Re-asserted periodically in case the AI
     * auto-switches phases mid-ride. No-op on 1.8 (the legacy AI-target pin handles that server).
     */
    private static void setHoverPhase(Entity dragon) {
        if (!(dragon instanceof org.bukkit.entity.EnderDragon)) return;
        try {
            Class<?> phaseEnum = Class.forName("org.bukkit.entity.EnderDragon$Phase");
            Object hover = phaseEnum.getField("HOVER").get(null);
            dragon.getClass().getMethod("setPhase", phaseEnum).invoke(dragon, hover);
        } catch (Throwable ignored) {
        }
    }
    /** 1.8.8 EntityEnderDragon flight-target fields (obfuscated a/b/c doubles = targetX/Y/Z). */
    private static Field legacyTargetX;
    private static Field legacyTargetY;
    private static Field legacyTargetZ;
    private static boolean legacyTargetResolved;

    /**
     * 1.8-only: the dragon's flight AI cannot be disabled (no setAI), so it constantly flies toward its own
     * waypoint target and fights the per-tick relocate — the client sees a violent back-and-forth zigzag.
     * Pin the AI target (handle fields a/b/c, verified = targetX/Y/Z against v1_8_R3 bytecode) onto the
     * desired path point each tick: the AI then holds position instead of dragging the dragon away.
     */
    private static void pinLegacyDragonAi(Entity dragon, Location to) {
        if (supportsAiToggle()) return;
        try {
            if (!legacyTargetResolved) {
                legacyTargetResolved = true;
                Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
                for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    if (!c.getSimpleName().equals("EntityEnderDragon")) continue;
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() != double.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        if (f.getName().equals("a")) legacyTargetX = f;
                        else if (f.getName().equals("b")) legacyTargetY = f;
                        else if (f.getName().equals("c")) legacyTargetZ = f;
                    }
                    if (legacyTargetX != null && legacyTargetY != null && legacyTargetZ != null) {
                        legacyTargetX.setAccessible(true);
                        legacyTargetY.setAccessible(true);
                        legacyTargetZ.setAccessible(true);
                    }
                    break;
                }
            }
            if (legacyTargetX == null || legacyTargetY == null || legacyTargetZ == null) return;
            Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
            legacyTargetX.setDouble(handle, to.getX());
            legacyTargetY.setDouble(handle, to.getY());
            legacyTargetZ.setDouble(handle, to.getZ());
        } catch (Throwable ignored) {
        }
    }

    /** Resolve the full streaming stack once per JVM; any missing piece → vanilla-tracker-only sync. */
    private static void resolveDragonStream() {
        if (streamResolved) return;
        streamResolved = true;
        try {
            String v = EntityVisibility.nmsVersion();
            boolean versioned = v != null && v.startsWith("v1_");
            int scale = (versioned && v.startsWith("v1_8")) ? 32 : 4096;

            String pkg = versioned ? "net.minecraft.server." + v : "net.minecraft.network.protocol.game";
            Class<?> nmsEntity = null;
            for (String name : new String[]{
                versioned ? "net.minecraft.server." + v + ".Entity" : "",
                "net.minecraft.world.entity.Entity"}) {
                if (name.isEmpty()) continue;
                try {
                    nmsEntity = Class.forName(name);
                    break;
                } catch (Throwable ignored) {
                }
            }
            Constructor<?> relMove = findRelMoveCtor(new String[]{
                pkg + ".PacketPlayOutEntity$PacketPlayOutRelEntityMoveLook",
                "net.minecraft.network.protocol.game.PacketPlayOutEntity$PacketPlayOutRelEntityMoveLook",
                "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$PosRot"});
            Constructor<?> headRot = nmsEntity == null ? null : findCtor(new String[]{
                pkg + ".PacketPlayOutEntityHeadRotation",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotation",
                "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket"}, nmsEntity, byte.class);
            Constructor<?> teleport = nmsEntity == null ? null : findCtor(new String[]{
                pkg + ".PacketPlayOutEntityTeleport",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport"}, nmsEntity);
            if (relMove == null || headRot == null) return;

            // Send plumbing shape: CraftPlayer.getHandle() → playerConnection|connection field → sendPacket|send.
            Class<?> craftPlayer;
            try {
                craftPlayer = Class.forName(
                    (versioned ? "org.bukkit.craftbukkit." + v : "org.bukkit.craftbukkit") + ".entity.CraftPlayer");
            } catch (Throwable ignored) {
                return;
            }
            Class<?> handleClass = craftPlayer.getMethod("getHandle").getReturnType();
            Field connectionField = findConnectionField(handleClass);
            if (connectionField == null) return;
            Method sendMethod = null;
            // The send method's parameter is the NMS Packet interface — named differently per package layout.
            Class<?> packetInterface = null;
            for (String name : new String[]{
                versioned ? "net.minecraft.server." + v + ".Packet" : "",
                "net.minecraft.network.protocol.Packet"}) {
                if (name.isEmpty()) continue;
                try {
                    packetInterface = Class.forName(name);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (packetInterface == null) return;
            for (Method m : connectionField.getType().getMethods()) {
                if (!m.getName().equals("sendPacket") && !m.getName().equals("send")) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0].isAssignableFrom(packetInterface)) {
                    sendMethod = m;
                    break;
                }
            }
            if (sendMethod == null) return;

            dragonStream = new DragonStream(relMove, headRot, teleport, scale, connectionField, sendMethod);
        } catch (Throwable ignored) {
            dragonStream = null;
        }
    }

    private static Constructor<?> findRelMoveCtor(String[] classNames) {
        for (String name : classNames) {
            Class<?> c;
            try {
                c = Class.forName(name);
            } catch (Throwable ignored) {
                continue;
            }
            // Byte args widen to byte/short/long params via reflection, so one call site fits every shape.
            for (Class<?>[] shape : new Class<?>[][] {
                {int.class, long.class, long.class, long.class, byte.class, byte.class, boolean.class},
                {int.class, short.class, short.class, short.class, byte.class, byte.class, boolean.class},
                {int.class, byte.class, byte.class, byte.class, byte.class, byte.class, boolean.class}}) {
                try {
                    Constructor<?> ctor = c.getConstructor(shape);
                    ctor.setAccessible(true);
                    return ctor;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Constructor<?> findCtor(String[] classNames, Class<?> first, Class<?> second) {
        for (String name : classNames) {
            try {
                Constructor<?> ctor = Class.forName(name).getConstructor(first, second);
                ctor.setAccessible(true);
                return ctor;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Constructor<?> findCtor(String[] classNames, Class<?> first) {
        for (String name : classNames) {
            try {
                Constructor<?> ctor = Class.forName(name).getConstructor(first);
                ctor.setAccessible(true);
                return ctor;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field findConnectionField(Class<?> handleClass) {
        for (Class<?> c = handleClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : new String[]{"playerConnection", "connection"}) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Send one pre-built NMS packet to a viewer via the resolved connection plumbing. */
    private static boolean sendPacket(DragonStream s, Player viewer, Object packet) {
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = s.connectionField.get(handle);
            if (connection == null) return false;
            s.sendMethod.invoke(connection, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** One absolute position+look resync to all viewers (post-mount / end-of-ride snapshots). */
    private static void sendDragonResync(Entity dragon) {
        DragonStream s = dragonStream;
        if (s == null) return;
        try {
            Location at = dragon.getLocation();
            Byte yawB = Byte.valueOf(angleByte(at.getYaw()));
            Byte pitchB = Byte.valueOf(angleByte(at.getPitch()));
            Object look = s.relMove.newInstance(Integer.valueOf(dragon.getEntityId()),
                s.quantize(0), s.quantize(0), s.quantize(0), yawB, pitchB, Boolean.FALSE);
            Object teleportPacket = null;
            if (s.teleport != null) {
                Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
                teleportPacket = s.teleport.newInstance(handle);
            }
            for (Player viewer : dragon.getWorld().getPlayers()) {
                if (EntityVisibility.isSpectator(viewer)) continue;
                if (viewer.getLocation().distanceSquared(at) > 160.0 * 160.0) continue;
                sendPacket(s, viewer, look);
                if (teleportPacket != null) sendPacket(s, viewer, teleportPacket);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Blocks/tick the win dragon cruises (≈ winDragonFlightSpeed per ~3 ticks — gentle Hypixel pace). */
    private static double winDragonPerTickStep() {
        return winDragonFlightSpeed() * 0.4;
    }

    private void tickWinDragon(Player winner, int elapsed) {
        // Flight, grief, particles and sound are driven by the per-tick loop in spawnWinDragon
        // (flyWinDragon). This 5-tick hook only keeps the tracked dragon alive-checked.
        UUID dragonId = winDragons.get(winner.getUniqueId());
        if (dragonId == null || winner.getWorld() == null) return;
        Entity dragon = entityByUuid(winner.getWorld(), dragonId);
        if (dragon == null || dragon.isDead()) {
            winDragons.remove(winner.getUniqueId());
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

    /**
     * Carve solid blocks along the swept flight segment (current → destination), radius 2, y−1..y+3 per sampled
     * cell — so the destination is air BEFORE the move and climbs/dives aren't rejected by terrain (Hypixel
     * victory dragon tunnels as it flies). Bedrock/barrier/portals are spared (see {@link #isWinDragonBreakable}).
     */
    private static void griefWinDragonPath(World world, Location from, Location to) {
        if (world == null || from == null || to == null) return;
        double dist = from.distance(to);
        int steps = Math.max(1, (int) Math.ceil(dist));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            griefWinDragonCell(world,
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);
        }
    }

    private static void griefWinDragonCell(World world, double wx, double wy, double wz) {
        int cx = (int) Math.floor(wx);
        int cy = (int) Math.floor(wy);
        int cz = (int) Math.floor(wz);
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

    /**
     * After carving, if the destination cell is still an unbreakable block, project the step onto the horizontal
     * plane (then try a ≤1-block hop) so the dragon slides along walls / bedrock instead of stalling.
     */
    private static Location slideIfBlocked(Location base, Location next) {
        World world = next.getWorld();
        if (world == null || winDragonCellPassable(world, next)) return next;
        Location horizontal = next.clone();
        horizontal.setY(base.getY());
        if (winDragonCellPassable(world, horizontal)) return horizontal;
        Location hop = horizontal.clone().add(0, 1, 0);
        if (winDragonCellPassable(world, hop)) return hop;
        return horizontal;
    }

    /** A cell is flyable if it is air or something the dragon already carved (breakable); unbreakable = blocked. */
    private static boolean winDragonCellPassable(World world, Location at) {
        Material type = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getType();
        return type == Material.AIR || isWinDragonBreakable(type);
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
        dragonPos.remove(owner);
        if (dragonId == null) return;
        Player player = Bukkit.getPlayer(owner);
        World world = player != null ? player.getWorld() : null;
        if (world != null) {
            Entity dragon = entityByUuid(world, dragonId);
            if (dragon != null) {
                // End-of-ride absolute snapshot (doc contract) before the despawn destroy packets go out.
                sendDragonResync(dragon);
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
                        Player rider = (Player) entity;
                        if (!isActiveWinDragon(rider, dismounted)) return;
                        if (dragonMoving.containsKey(rider.getUniqueId())) return; // our own move-eject
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
        boolean wither = dragon.getType() == EntityType.WITHER;
        // Mouth: ahead of the mount's body along look (dragon head ~+3y, wither head ~+2.2y).
        Location mouth = dragon.getLocation().clone()
            .add(0.0, wither ? 2.2 : 3.0, 0.0)
            .add(dir.clone().multiply(wither ? 3.0 : 4.5));
        Fireball projectile = wither
            ? spawnWinWitherSkull(player.getWorld(), mouth)
            : spawnWinDragonFireball(player.getWorld(), mouth);
        if (projectile == null) return true;
        projectile.setDirection(dir);
        projectile.setShooter(player);
        projectile.setIsIncendiary(false);
        projectile.setYield(winDragonFireballYield());
        projectile.setMetadata(META_WIN_FIREBALL, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        projectile.setVelocity(dir.clone().multiply(wither ? 1.5 : 1.35));
        if (wither) {
            Sounds.playAt(mouth, "ENTITY_WITHER_SHOOT", "ENTITY_WITHER_SHOOT", "WITHER_SHOOT");
            Particles.play(null, mouth, 12, 0.25, "SMOKE", "LARGE_SMOKE", "CRIT");
        } else {
            Sounds.playAt(mouth, "ENTITY_GHAST_SHOOT", "GHAST_FIREBALL", "GHAST_MOAN");
            Particles.play(null, mouth, 12, 0.25, "FLAME", "SMOKE", "CRIT");
        }
        final UUID ballId = projectile.getUniqueId();
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

    /** Wither skull for the win wither's left-click shot (big blast, no fire); falls back to a fireball. */
    private static Fireball spawnWinWitherSkull(World world, Location mouth) {
        if (world == null || mouth == null) return null;
        try {
            return world.spawn(mouth, org.bukkit.entity.WitherSkull.class);
        } catch (Throwable t) {
            return spawnWinDragonFireball(world, mouth);
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

    /**
     * Win mounts never acquire targets: the wither's AI would otherwise shoot skulls at players on servers
     * where noAI is unavailable (1.8). Belt-and-braces for 1.9+ where noAI already prevents this.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinMountTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        if (event.getEntity() != null && event.getEntity().hasMetadata(META_WIN_DRAGON)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWinDragonVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        Player rider = (Player) event.getExited();
        if (dragonMoving.containsKey(rider.getUniqueId())) return; // our own move-eject
        if (isActiveWinDragon(rider, event.getVehicle())) {
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
}
