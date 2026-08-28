package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired before a member leaves a party. Cancel to keep them in. */
public final class BedlamPartyPreLeaveEvent extends PartyEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID member;
    private boolean cancelled;

    public BedlamPartyPreLeaveEvent(Party party, UUID member) {
        super(party);
        this.member = member;
    }

    /** @return the member about to leave. */
    public UUID member() { return member; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
