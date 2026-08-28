package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired before a member is kicked. Cancel to veto the kick. */
public final class BedlamPartyKickEvent extends PartyEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID target;
    private final UUID by;
    private boolean cancelled;

    public BedlamPartyKickEvent(Party party, UUID target, UUID by) {
        super(party);
        this.target = target;
        this.by = by;
    }

    /** @return the member being kicked. */
    public UUID target() { return target; }

    /** @return the leader performing the kick. */
    public UUID by() { return by; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
