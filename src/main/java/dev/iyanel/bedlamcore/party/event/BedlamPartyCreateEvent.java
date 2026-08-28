package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.HandlerList;

/** Fired after a party is created. */
public final class BedlamPartyCreateEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public BedlamPartyCreateEvent(Party party) { super(party); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
