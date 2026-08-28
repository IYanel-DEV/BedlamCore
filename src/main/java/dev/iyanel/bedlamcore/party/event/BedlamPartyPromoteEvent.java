package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after party leadership transfers. */
public final class BedlamPartyPromoteEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID newLeader;
    private final UUID oldLeader;

    public BedlamPartyPromoteEvent(Party party, UUID newLeader, UUID oldLeader) {
        super(party);
        this.newLeader = newLeader;
        this.oldLeader = oldLeader;
    }

    /** @return the new leader. */
    public UUID newLeader() { return newLeader; }

    /** @return the previous leader. */
    public UUID oldLeader() { return oldLeader; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
