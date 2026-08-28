package dev.iyanel.bedlamcore.party.event;

import dev.iyanel.bedlamcore.party.Party;
import org.bukkit.event.Event;

/** Base for all party events. Carries the {@link Party}; concrete subclasses own their HandlerList. */
public abstract class PartyEvent extends Event {
    private final Party party;

    protected PartyEvent(Party party) {
        this.party = party;
    }

    /** @return the party this event concerns (never null for post-events; the draft for pre-create). */
    public Party party() { return party; }
}
