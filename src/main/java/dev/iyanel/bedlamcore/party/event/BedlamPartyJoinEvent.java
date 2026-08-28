package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Fired after a player joins a party. */
public final class BedlamPartyJoinEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joiner;

    public BedlamPartyJoinEvent(Party party, Player joiner) {
        super(party);
        this.joiner = joiner;
    }

    /** @return the player who joined. */
    public Player joiner() { return joiner; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
