package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before a party queues for a mode. Cancel to veto the queue. */
public final class BedlamPartyPreQueueEvent extends PartyEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameType type;
    private boolean cancelled;

    public BedlamPartyPreQueueEvent(Party party, GameType type) {
        super(party);
        this.type = type;
    }

    /** @return the game mode the party is queuing for. */
    public GameType type() { return type; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
