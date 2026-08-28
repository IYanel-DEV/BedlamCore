package dev.iyanel.bedlamcore.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opt-in adapter for the <b>BungeeParties</b> plugin.
 *
 * <p>BungeeParties is a BungeeCord-side plugin — its party data is not visible from a Bukkit server
 * unless a Bukkit-side bridge exposes it. This adapter therefore attempts a clean reflective lookup of
 * such a bridge API and reports {@link #active()} {@code false} on any failure, so BedlamCore silently
 * falls back to another provider. It never fabricates members.
 *
 * <pre>
 * # ============================ HOW TO BRIDGE A PARTY PLUGIN ============================
 * # BungeeParties/Party-and-Friends run on the proxy. To let BedlamCore queue their parties,
 * # a Bukkit-side companion must forward membership to this server. Two clean options:
 * #
 * #   A) Expose a Bukkit API class this adapter can find reflectively. Point BRIDGE_CLASS at a class
 * #      with static  List&lt;UUID&gt; membersOf(UUID)  and  UUID leaderOf(UUID)  methods, then flip the
 * #      reflective wiring below on. Keep it exception-guarded so a missing class never crashes.
 * #
 * #   B) (Recommended, generic) Ignore this adapter and register your own PartyProvider directly:
 * #        BedlamCore core = (BedlamCore) Bukkit.getPluginManager().getPlugin("BedlamCore");
 * #        core.partyService().registerProvider(myProvider);   // see PartyProvider javadoc
 * #      Answer members()/leader() from the latest snapshot your proxy pushed over Plugin Messaging.
 * #
 * # Do NOT hard-depend on BungeeParties at compile time; keep everything behind Class.forName so the
 * # single multi-version jar still loads where the plugin is absent.
 * # =====================================================================================
 * </pre>
 */
public final class BungeePartiesProvider implements PartyProvider {
    /** Fully-qualified Bukkit-bridge API class to look for; absent by default (adapter stays inactive). */
    private static final String BRIDGE_CLASS = "de.simonsator.partyandfriends.api.PAFExtensionStandalone";

    private final boolean reachable;

    public BungeePartiesProvider() {
        boolean ok;
        try {
            Class.forName(BRIDGE_CLASS);
            ok = true;
        } catch (Throwable ignored) {
            ok = false; // class missing / bridge not installed → inactive, never throws
        }
        this.reachable = ok;
    }

    @Override public String name() { return "bungeeparties"; }

    @Override public boolean active() { return reachable; }

    // Without a confirmed bridge we never invent members: report solo/empty.
    @Override public List<UUID> members(UUID player) { return new ArrayList<UUID>(); }

    @Override public UUID leader(UUID player) { return null; }

    @Override public boolean isInParty(UUID player) { return false; }

    @Override public int size(UUID player) { return 0; }

    @Override public boolean canQueueAsUnit() { return false; }
}
