package dev.iyanel.bedlamcore.party;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A group of players who queue together. Pure data + membership helpers — it holds no schedulers and
 * runs no tasks (the owning {@link PartyService} drives invite expiry). Java 8 compatible.
 *
 * <p>{@link #members} is ordered by join time so the leader is index 0 and auto-promotion picks the
 * next-joined member. All mutators are null-safe.
 */
public final class Party {
    private final UUID id;
    private UUID leader;
    private final List<UUID> members = new ArrayList<UUID>();
    /** Pending invites: target uuid → expiry epoch-millis. */
    private final Map<UUID, Long> invited = new LinkedHashMap<UUID, Long>();
    private boolean open;
    private boolean chatEnabled;
    private final long createdAt;

    public Party(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.createdAt = System.currentTimeMillis();
        if (leader != null) members.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public void leader(UUID value) { this.leader = value; }
    public boolean open() { return open; }
    public void open(boolean value) { this.open = value; }
    public boolean chatEnabled() { return chatEnabled; }
    public void chatEnabled(boolean value) { this.chatEnabled = value; }
    public long createdAt() { return createdAt; }

    /** Live view of members in join order (index 0 = leader). */
    public List<UUID> members() { return members; }
    public Map<UUID, Long> invited() { return invited; }

    public int size() { return members.size(); }

    public boolean isMember(UUID uuid) { return uuid != null && members.contains(uuid); }

    public boolean isLeader(UUID uuid) { return uuid != null && uuid.equals(leader); }

    public void addMember(UUID uuid) {
        if (uuid != null && !members.contains(uuid)) members.add(uuid);
        if (uuid != null) invited.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        if (uuid == null) return;
        members.remove(uuid);
        if (uuid.equals(leader) && !members.isEmpty()) leader = members.get(0); // auto-promote next-joined
    }

    /** Online members only (offline / unknown skipped). Never null. */
    public List<Player> onlineMembers() {
        List<Player> online = new ArrayList<Player>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) online.add(player);
        }
        return online;
    }

    public String leaderName() {
        if (leader == null) return "?";
        Player online = Bukkit.getPlayer(leader);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(leader);
        return offline != null && offline.getName() != null ? offline.getName() : leader.toString();
    }

    public void invite(UUID target, long expireMillis) {
        if (target != null) invited.put(target, expireMillis);
    }

    public boolean hasInvite(UUID target) {
        return target != null && invited.containsKey(target);
    }

    public Long inviteExpiry(UUID target) {
        return target == null ? null : invited.get(target);
    }
}
