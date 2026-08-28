package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a member leaves a party. */
public final class BedlamPartyLeaveEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID member;

    public BedlamPartyLeaveEvent(Party party, UUID member) {
        super(party);
        this.member = member;
    }

    /** @return the member who left. */
    public UUID member() { return member; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
