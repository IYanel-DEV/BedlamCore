package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.HandlerList;

/** Fired when a party disbands (leader disband, or the last member left). */
public final class BedlamPartyDisbandEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public BedlamPartyDisbandEvent(Party party) { super(party); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
