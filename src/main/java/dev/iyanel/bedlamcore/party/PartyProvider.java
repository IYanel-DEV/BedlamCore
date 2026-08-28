package dev.iyanel.bedlamcore.party;

import java.util.List;
import java.util.UUID;

/**
 * Source of party membership. BedlamCore ships a built-in {@link BedlamProvider}, but the whole
 * party-aware queue path reads only through this interface, so an external party plugin (BungeeParties,
 * Party and Friends, …) can drive BedlamCore's matching by registering its own implementation via
 * {@link PartyService#registerProvider(PartyProvider)}.
 *
 * <p>Every method must be null-safe and non-throwing: an unreachable backend should report
 * {@link #active()} {@code false} and return empty/neutral values rather than crash.
 *
 * <pre>
 * # ============================ HOW TO BRIDGE A PARTY PLUGIN ============================
 * # A third-party (or Bungee-bridge) plugin can teach BedlamCore about its parties without
 * # BedlamCore depending on it at compile time:
 * #
 * #   1. Add BedlamCore as a soft-dependency in your plugin.yml.
 * #   2. On enable, look up BedlamCore and register a provider:
 * #
 * #        Plugin bedlam = getServer().getPluginManager().getPlugin("BedlamCore");
 * #        if (bedlam != null) {
 * #            BedlamCore core = (BedlamCore) bedlam;
 * #            core.partyService().registerProvider(new PartyProvider() {
 * #                public String  name()               { return "myparties"; }
 * #                public boolean active()             { return myBackendReachable(); }
 * #                public List&lt;UUID&gt; members(UUID p)  { return myMembersOf(p); }   // empty if none
 * #                public UUID    leader(UUID p)        { return myLeaderOf(p); }    // null if none
 * #                public boolean isInParty(UUID p)     { return members(p).size() &gt; 1; }
 * #                public int     size(UUID p)          { return members(p).size(); }
 * #                public boolean canQueueAsUnit()      { return true; }
 * #            });
 * #        }
 * #
 * #   3. If your backend lives on BungeeCord, forward membership to the Bukkit side over Plugin
 * #      Messaging (see the note in plugin.yml / PartyService) and answer members()/leader() from
 * #      the last snapshot you received. Never fabricate members you cannot confirm.
 * # =====================================================================================
 * </pre>
 */
public interface PartyProvider {

    /** @return short stable id, e.g. {@code "bedlam"}, {@code "bungeeparties"}. */
    String name();

    /** @return {@code false} when this provider cannot serve requests (backend down / not present),
     *          so {@link PartyService} falls back to another provider or to no-party behavior. */
    boolean active();

    /** @return the player's party members (including the player), or an empty list when solo/unknown. */
    List<UUID> members(UUID player);

    /** @return the party leader for the player, or {@code null} when the player is not in a party. */
    UUID leader(UUID player);

    /** @return {@code true} when the player is in a party of more than one. */
    boolean isInParty(UUID player);

    /** @return party size for the player (0 or 1 when effectively solo). */
    int size(UUID player);

    /** @return {@code true} when this provider lets a whole party join one server together. */
    boolean canQueueAsUnit();
}
