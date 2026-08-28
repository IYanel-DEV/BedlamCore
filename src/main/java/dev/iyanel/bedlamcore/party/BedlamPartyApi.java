package dev.iyanel.bedlamcore.party;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Stable public surface other plugins use via {@code BedlamCore.party()} to create, inspect, and
 * disband BedlamCore parties. Implemented by {@link PartyService}. All methods are null-safe.
 */
public interface BedlamPartyApi {

    /** @return the newly created party led by {@code leader}, or the leader's existing party if any. */
    Party create(Player leader);

    /** @return the party the given player belongs to, or {@code null} when they are not in one. */
    Party partyOf(UUID player);

    /** @return {@code true} when the player is currently in a party of any size. */
    boolean isInParty(UUID player);

    /** @return {@code true} if the party was found and disbanded, {@code false} otherwise. */
    boolean disband(UUID leader);

    /** @return {@code true} if {@code target} was added to {@code leader}'s party (invite bypassed). */
    boolean addMember(UUID leader, Player target);

    /** @return {@code true} when the built-in party system is enabled in config. */
    boolean enabled();
}
