package dev.iyanel.bedlamcore.party.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before a party is created. Cancel to veto creation. {@link #party()} is null (no party yet). */
public final class BedlamPartyPreCreateEvent extends PartyEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player creator;
    private boolean cancelled;

    public BedlamPartyPreCreateEvent(Player creator) {
        super(null);
        this.creator = creator;
    }

    /** @return the player attempting to create the party. */
    public Player creator() { return creator; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
