package dev.iyanel.bedlamcore.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opt-in adapter for <b>Party and Friends</b> (Simonsator).
 *
 * <p>Like {@link BungeePartiesProvider}, the canonical plugin runs on BungeeCord, so this adapter only
 * activates when a Bukkit-side bridge class is present. It stays inactive (never throwing, never
 * inventing members) otherwise and BedlamCore falls back to the built-in provider.
 *
 * <pre>
 * # ============================ HOW TO BRIDGE A PARTY PLUGIN ============================
 * # Party and Friends "Extensions Standalone" can run Bukkit-side. If you install it, expose (or point
 * # BRIDGE_CLASS at) a class offering static  List&lt;UUID&gt; membersOf(UUID)  /  UUID leaderOf(UUID)  and
 * # enable the reflective calls below (all guarded by try/catch(Throwable) so a version mismatch just
 * # deactivates the adapter). Otherwise register your own PartyProvider straight into BedlamCore:
 * #
 * #     BedlamCore core = (BedlamCore) Bukkit.getPluginManager().getPlugin("BedlamCore");
 * #     core.partyService().registerProvider(myProvider);   // see PartyProvider javadoc
 * #
 * # When your source of truth is the proxy, push snapshots over Plugin Messaging and answer from the
 * # latest snapshot. Never hard-depend at compile time — keep it all behind Class.forName.
 * # =====================================================================================
 * </pre>
 */
public final class PartyAndFriendsProvider implements PartyProvider {
    /** Fully-qualified Bukkit-bridge API class to look for; absent by default (adapter stays inactive). */
    private static final String BRIDGE_CLASS = "de.simonsator.partyandfriends.spigot.api.party.PartyManager";

    private final boolean reachable;

    public PartyAndFriendsProvider() {
        boolean ok;
        try {
            Class.forName(BRIDGE_CLASS);
            ok = true;
        } catch (Throwable ignored) {
            ok = false;
        }
        this.reachable = ok;
    }

    @Override public String name() { return "partyandfriends"; }

    @Override public boolean active() { return reachable; }

    @Override public List<UUID> members(UUID player) { return new ArrayList<UUID>(); }

    @Override public UUID leader(UUID player) { return null; }

    @Override public boolean isInParty(UUID player) { return false; }

    @Override public int size(UUID player) { return 0; }

    @Override public boolean canQueueAsUnit() { return false; }
}
