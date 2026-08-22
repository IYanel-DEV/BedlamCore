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
    /** Cached: does LivingEntity.setAI exist (1.9+)? On 1.8 it does not, so the dragon can't be tamed. */
    private static Boolean aiToggle;

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
        dragonPos.put(winner.getUniqueId(), at.clone());
        mountPassenger(dragon, winner);
        // Every tick: fly toward the rider's look + force remount (sneak/eject cannot stick).
        // Teleport of a vehicle is blocked on 1.9+, so flightStep does eject→teleport→remount.
        final UUID owner = winner.getUniqueId();
        final UUID dragonId = dragon.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;
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
                flyWinDragon(owner, d, p, ticks);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // Safety net: some Paper builds eject the rider on the tick after spawn — re-seat once if so.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                Player p = Bukkit.getPlayer(owner);
                if (p == null || !p.isOnline() || p.getWorld() == null) return;
                Entity d = entityByUuid(p.getWorld(), dragonId);
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
        Sounds.playAt(at, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
    }

    /** True when LivingEntity.setAI exists (1.9+); false on 1.8 where the dragon's flight AI can't be disabled. */
    static boolean supportsAiToggle() {
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
        // If the destination is still blocked by an unbreakable (bedrock/barrier), slide along the wall instead
        // of stalling: drop the vertical component, optionally hop up <=1 block.
        next = slideIfBlocked(base, next);
        // Facing is derived from the ACTUAL displacement — never a hardcoded ±180 guess — so tail-first flight is
        // impossible on any version. Body yaw from dx/dz + the single model offset; pitch from dy.
        double dx = next.getX() - base.getX();
        double dy = next.getY() - base.getY();
        double dz = next.getZ() - base.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = horiz > 1.0e-4 ? dragonBodyYaw(dx, dz) : base.getYaw();
        float yaw = lerpYawShortest(base.getYaw(), targetYaw, DRAGON_YAW_LERP);
        float pitch = clampDragonPitch(dy, horiz);
        next.setYaw(yaw);
        next.setPitch(pitch);
        // Move; commit the tracked position to where the entity ACTUALLY ended up (never advance past a refused
        // teleport in the fallback path — that drift caused the snap/stall).
        Location applied = moveMountedDragon(owner, dragon, rider, next);
        applied.setYaw(yaw);
        applied.setPitch(pitch);
        dragonPos.put(owner, applied);
        if (DRAGON_DEBUG && ticks % 20 == 0) {
            plugin.getLogger().info("[windragon] mode=" + (planADisabled ? "B" : "A")
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
            Sounds.playAt(dragon.getLocation(), "ENTITY_ENDER_DRAGON_FLAP", "ENDERDRAGON_WINGS", "BAT_TAKEOFF");
        }
    }

    /** Bukkit-free body yaw from a horizontal delta (standard Bukkit convention) + the EnderDragon model offset. */
    public static float dragonBodyYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz)) + DRAGON_MODEL_YAW_OFFSET;
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
     * Modern (1.9+): teleporting a vehicle WITH passengers is refused by CraftBukkit, and the old
     * eject→teleport→remount every tick caused the rider rubber-band + 1-2 tick dragon flicker. We instead
     * relocate the dragon at the NMS level ({@code setLocation}/{@code setPositionRotation}/{@code moveTo}) which
     * moves it without ejecting — the server repositions the seated rider onto it automatically, so flight is
     * smooth/Hypixel-like. If no NMS relocation method resolves we fall back to eject→teleport→remount, with
     * {@link #dragonMoving} suppressing our dismount/exit cancel handlers during our own programmatic eject.
     *
     * 1.8: the vehicle-teleport refusal is 1.9+, so a plain teleport carries the rider — keep that shortcut.
     */
    private Location moveMountedDragon(UUID owner, Entity dragon, Player rider, Location next) {
        if (!supportsAiToggle()) {
            boolean seated = isPassengerOf(dragon, rider);
            dragon.teleport(next);
            if (seated && !isPassengerOf(dragon, rider)) {
                dragonMoving.put(owner, Boolean.TRUE);
                try {
                    mountPassenger(dragon, rider);
                } finally {
                    dragonMoving.remove(owner);
                }
            }
            return dragon.getLocation();
        }
        // Modern (Plan A): relocate at the NMS level (no rider eject) then BROADCAST a teleport packet so clients
        // actually SEE the move. On 1.12.2, setLocation only mutates server-side position fields — the entity
        // tracker does not reliably broadcast raw mutations for a noAI flyer, so without the explicit packet the
        // dragon advances server-side while the client renders it frozen at spawn (the f4ce1c6 regression). If
        // relocate or the broadcast can't work, fall through to Plan B; after a few misses latch Plan B so we
        // don't repeat failed reflection every tick.
        if (!planADisabled) {
            if (nmsRelocate(dragon, next) && broadcastDragonMove(dragon, next.getYaw())) {
                planAConsecutiveFailures = 0;
                return next.clone();
            }
            if (++planAConsecutiveFailures >= 3) planADisabled = true;
        }
        // Plan B: eject → teleport both → remount (provably flew on 1.12.2 before f4ce1c6). Bukkit teleport marks
        // the entity moved for its tracker, so clients see it; minor per-tick flicker is acceptable here.
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

    /** Cached NMS relocation method (setLocation/setPositionRotation/moveTo) resolved once; null = use fallback. */
    private static Method nmsSetLocation;
    private static boolean nmsSetLocationResolved;

    /**
     * Move the entity at the NMS level so a seated passenger is NOT ejected. Mapping names differ across
     * versions, so resolve reflectively: {@code setLocation} (Spigot), {@code setPositionRotation} (older CB),
     * {@code moveTo} (Mojang-mapped Paper). Returns false if none resolve or the call throws → caller falls back.
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
            for (String name : new String[]{"setLocation", "setPositionRotation", "moveTo"}) {
                Class<?> c = handle.getClass();
                while (c != null) {
                    try {
                        Method m = c.getDeclaredMethod(name, double.class, double.class, double.class,
                            float.class, float.class);
                        m.setAccessible(true);
                        nmsSetLocation = m;
                        return nmsSetLocation;
                    } catch (NoSuchMethodException ignored) {
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
        return nmsSetLocation;
    }

    /** Once-per-second flight diagnostics (Plan A/B, position delta, seat). Off in release builds. */
    private static final boolean DRAGON_DEBUG = false;
    /** Consecutive Plan-A (relocate+broadcast) misses; after {@code >=3} we latch to Plan B for the JVM. */
    private static int planAConsecutiveFailures;
    private static boolean planADisabled;
    private static Constructor<?> teleportPacketCtor;
    private static Constructor<?> headRotPacketCtor;
    private static boolean movePacketsResolved;

    /**
     * Broadcast the dragon's post-relocate position to nearby clients so the NMS setLocation is actually visible.
     * Sends PacketPlayOutEntityTeleport (reads the handle's just-updated position) + PacketPlayOutEntityHeadRotation
     * (EnderDragon head yaw lags otherwise) to every non-spectator within ~160 blocks. Returns false when the
     * packet classes / send plumbing are unavailable (e.g. Mojang-mapped Paper) so the caller uses Plan B.
     */
    private boolean broadcastDragonMove(Entity dragon, float yaw) {
        if (!resolveMovePackets() || !EntityVisibility.packetsAvailable()) return false;
        Object handle;
        try {
            handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
        } catch (Throwable t) {
            return false;
        }
        Object tpPacket;
        try {
            tpPacket = teleportPacketCtor.newInstance(handle);
        } catch (Throwable t) {
            return false;
        }
        Object headPacket = null;
        try {
            headPacket = headRotPacketCtor.newInstance(handle, (byte) ((int) (yaw * 256.0F / 360.0F)));
        } catch (Throwable ignored) {
        }
        World world = dragon.getWorld();
        if (world == null) return false;
        Location at = dragon.getLocation();
        for (Player viewer : world.getPlayers()) {
            if (EntityVisibility.isSpectator(viewer)) continue;
            if (viewer.getLocation().distanceSquared(at) > 160.0 * 160.0) continue;
            EntityVisibility.sendRaw(viewer, tpPacket);
            if (headPacket != null) EntityVisibility.sendRaw(viewer, headPacket);
        }
        return true;
    }

    /** Resolve the versioned move-packet constructors once; unavailable on Mojang-mapped builds (→ Plan B). */
    private static boolean resolveMovePackets() {
        if (movePacketsResolved) return teleportPacketCtor != null;
        movePacketsResolved = true;
        try {
            String v = EntityVisibility.nmsVersion();
            if (v == null) return false;
            Class<?> nmsEntity = Class.forName("net.minecraft.server." + v + ".Entity");
            teleportPacketCtor = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityTeleport")
                .getConstructor(nmsEntity);
            headRotPacketCtor = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityHeadRotation")
                .getConstructor(nmsEntity, byte.class);
        } catch (Throwable ignored) {
            teleportPacketCtor = null;
            headRotPacketCtor = null;
        }
        return teleportPacketCtor != null;
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
