package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.HandlerList;

/** Fired after a party has been queued for a mode. */
public final class BedlamPartyQueueEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameType type;

    public BedlamPartyQueueEvent(Party party, GameType type) {
        super(party);
        this.type = type;
    }

    /** @return the game mode the party queued for. */
    public GameType type() { return type; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
