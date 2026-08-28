package dev.iyanel.bedlamcore.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Default provider backed by BedlamCore's own in-memory parties in {@link PartyService}. */
public final class BedlamProvider implements PartyProvider {
    private final PartyService service;

    public BedlamProvider(PartyService service) {
        this.service = service;
    }

    @Override public String name() { return "bedlam"; }

    @Override public boolean active() { return true; }

    @Override public List<UUID> members(UUID player) {
        Party party = service.partyOf(player);
        return party == null ? new ArrayList<UUID>() : new ArrayList<UUID>(party.members());
    }

    @Override public UUID leader(UUID player) {
        Party party = service.partyOf(player);
        return party == null ? null : party.leader();
    }

    @Override public boolean isInParty(UUID player) {
        Party party = service.partyOf(player);
        return party != null && party.size() > 1;
    }

    @Override public int size(UUID player) {
        Party party = service.partyOf(player);
        return party == null ? 0 : party.size();
    }

    @Override public boolean canQueueAsUnit() { return true; }
}
