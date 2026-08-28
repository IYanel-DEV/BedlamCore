package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before a player joins a party (via invite or open join). Cancel to veto the join. */
public final class BedlamPartyPreJoinEvent extends PartyEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joiner;
    private final boolean invite;
    private boolean cancelled;

    public BedlamPartyPreJoinEvent(Party party, Player joiner, boolean invite) {
        super(party);
        this.joiner = joiner;
        this.invite = invite;
    }

    /** @return the player joining the party. */
    public Player joiner() { return joiner; }

    /** @return {@code true} when joining from an invite, {@code false} for an open join. */
    public boolean invite() { return invite; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
