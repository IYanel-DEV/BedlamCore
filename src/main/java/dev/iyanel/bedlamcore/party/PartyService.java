package dev.iyanel.bedlamcore.party;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.party.event.BedlamPartyCreateEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyDisbandEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyJoinEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyKickEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyLeaveEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyPreCreateEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyPreJoinEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyPreLeaveEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyPreQueueEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyPromoteEvent;
import dev.iyanel.bedlamcore.party.event.BedlamPartyQueueEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns BedlamCore's built-in parties and the active {@link PartyProvider}. Every party-aware code path
 * (queueing, team assignment, chat) reads through this service, so built-in and external providers behave
 * identically. All public methods are null-safe and never throw on unknown/offline players, expired or
 * duplicate invites, or disband-while-in-a-match.
 */
public final class PartyService implements BedlamPartyApi {
    private final BedlamCore plugin;
    private final Map<UUID, Party> byId = new LinkedHashMap<UUID, Party>();
    /** player uuid → party id. */
    private final Map<UUID, UUID> memberIndex = new HashMap<UUID, UUID>();
    /** players who routed their normal chat into party chat (/party chat, /pc toggle). */
    private final java.util.Set<UUID> chatToggled = new java.util.HashSet<UUID>();
    /** leaders who typed /party disband once (confirm on the second use). */
    private final java.util.Set<UUID> disbandConfirm = new java.util.HashSet<UUID>();

    private final BedlamProvider builtIn;
    private final Map<String, PartyProvider> registered = new LinkedHashMap<String, PartyProvider>();
    private PartyProvider provider;
    private String providerLog = "bedlam (built-in)";

    private int expiryTask = -1;

    public PartyService(BedlamCore plugin) {
        this.plugin = plugin;
        this.builtIn = new BedlamProvider(this);
        this.provider = builtIn;
        // Pre-register the shipped external adapters so provider selection can find them by plugin name.
        register(new BungeePartiesProvider());
        register(new PartyAndFriendsProvider());
        resolveProvider();
        if (GameRules.PARTY_PERSISTENT) load();
        startExpiryTask();
    }

    private void register(PartyProvider p) {
        if (p != null && p.name() != null) registered.put(p.name().toLowerCase(), p);
    }

    // ------------------------------------------------------------------ provider selection

    /** Register an external provider and re-run selection (used by third-party bridges). */
    public void registerProvider(PartyProvider p) {
        register(p);
        resolveProvider();
    }

    public PartyProvider provider() { return provider; }

    /** @return the human-readable provider-selection line logged at enable/reload. */
    public String providerLog() { return providerLog; }

    private void resolveProvider() {
        List<String> external = plugin.getConfig().getStringList("party.external-provider-names");
        String mode = plugin.getConfig().getString("party.provider", "auto");
        List<String> loaded = new ArrayList<String>();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) if (pl != null) loaded.add(pl.getName());

        String pick = GameRules.selectProvider(mode, external, loaded);
        if (pick != null) {
            PartyProvider adapter = adapterFor(pick);
            if (adapter != null && adapter.active()) {
                provider = adapter;
                providerLog = adapter.name() + " (external: " + pick + ")";
                plugin.getLogger().info("BedlamCore party provider: " + providerLog);
                return;
            }
            // Plugin present but its Bukkit-side bridge is unreachable — never fabricate, fall back.
            plugin.getLogger().info("party provider " + pick + " not reachable on this server — party info "
                + "must be bridged via Bungee messaging; falling back to provider bedlam");
        }
        // A programmatically registered custom provider that is active wins over built-in in auto mode.
        if (!"bedlam".equalsIgnoreCase(mode)) {
            for (PartyProvider p : registered.values()) {
                if (p == null || !p.active()) continue;
                if (p instanceof BungeePartiesProvider || p instanceof PartyAndFriendsProvider) continue;
                provider = p;
                providerLog = p.name() + " (registered)";
                plugin.getLogger().info("BedlamCore party provider: " + providerLog);
                return;
            }
        }
        provider = builtIn;
        providerLog = "bedlam (built-in)";
        plugin.getLogger().info("BedlamCore party provider: " + providerLog);
    }

    private PartyProvider adapterFor(String pluginName) {
        if (pluginName == null) return null;
        String n = pluginName.toLowerCase();
        if (n.equals("bungeeparties")) return registered.get("bungeeparties");
        if (n.equals("partyandfriends")) return registered.get("partyandfriends");
        // Also allow a custom provider whose name() matches the plugin name.
        return registered.get(n);
    }

    // ------------------------------------------------------------------ lifecycle

    public void reload() {
        cancelExpiryTask();
        resolveProvider();
        if (GameRules.PARTY_PERSISTENT) load();
        startExpiryTask();
    }

    public void shutdown() {
        cancelExpiryTask();
        if (GameRules.PARTY_PERSISTENT) save();
    }

    private void startExpiryTask() {
        if (!GameRules.PARTY_ENABLED) return;
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { pruneInvites(); }
        }, 20L, 20L).getTaskId();
    }

    private void cancelExpiryTask() {
        if (expiryTask != -1) { Bukkit.getScheduler().cancelTask(expiryTask); expiryTask = -1; }
    }

    private void pruneInvites() {
        long now = System.currentTimeMillis();
        for (Party party : byId.values()) {
            Iterator<Map.Entry<UUID, Long>> it = party.invited().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                if (GameRules.inviteExpired(now, entry.getValue() == null ? 0L : entry.getValue())) it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ queries

    @Override public Party partyOf(UUID player) {
        if (player == null) return null;
        UUID id = memberIndex.get(player);
        return id == null ? null : byId.get(id);
    }

    @Override public boolean isInParty(UUID player) {
        Party party = partyOf(player);
        return party != null && party.size() > 1;
    }

    @Override public boolean enabled() { return GameRules.PARTY_ENABLED; }

    /**
     * Group key for team assignment: the party leader shared by all members, via the active provider, or
     * {@code null} when the player is effectively solo. Members of one party share this key.
     */
    public UUID partyKey(UUID player) {
        if (player == null || provider == null || !provider.active()) return null;
        if (!provider.isInParty(player)) return null;
        return provider.leader(player);
    }

    // ------------------------------------------------------------------ create / invite / join

    @Override public Party create(Player leader) {
        if (leader == null || !GameRules.PARTY_ENABLED) return null;
        Party existing = partyOf(leader.getUniqueId());
        if (existing != null) return existing;
        BedlamPartyPreCreateEvent pre = new BedlamPartyPreCreateEvent(leader);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return null;
        Party party = new Party(UUID.randomUUID(), leader.getUniqueId());
        byId.put(party.id(), party);
        memberIndex.put(leader.getUniqueId(), party.id());
        Bukkit.getPluginManager().callEvent(new BedlamPartyCreateEvent(party));
        return party;
    }

    public void invite(Player target, Player leader) {
        if (leader == null) return;
        if (!GameRules.PARTY_ENABLED) { msg(leader, ChatColor.RED + "The party system is disabled."); return; }
        if (target == null) { msg(leader, ChatColor.RED + "That player is not online."); return; }
        if (target.getUniqueId().equals(leader.getUniqueId())) { msg(leader, ChatColor.RED + "You cannot invite yourself."); return; }
        Party party = partyOf(leader.getUniqueId());
        if (party == null) party = create(leader);
        if (party == null) return;
        if (!party.isLeader(leader.getUniqueId())) { msg(leader, ChatColor.RED + "Only the party leader can invite."); return; }
        if (party.isMember(target.getUniqueId())) { msg(leader, ChatColor.RED + target.getName() + " is already in your party."); return; }
        if (partyOf(target.getUniqueId()) != null) { msg(leader, ChatColor.RED + target.getName() + " is already in a party."); return; }
        int cap = maxSize(leader);
        if (party.size() >= cap) { msg(leader, ChatColor.RED + "Your party is full (" + cap + ")."); return; }
        long expiry = System.currentTimeMillis() + GameRules.PARTY_INVITE_TIMEOUT * 1000L;
        party.invite(target.getUniqueId(), expiry); // duplicate invite just refreshes the timer, never errors
        msg(leader, ChatColor.GREEN + "Invited " + target.getName() + " to the party.");
        Sounds.play(leader, "ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_TOUCH", "ORB_PICKUP");
        msg(target, ChatColor.AQUA + leader.getName() + ChatColor.YELLOW + " invited you to their party. "
            + ChatColor.GREEN + "/party accept " + leader.getName() + ChatColor.YELLOW + " (expires in "
            + GameRules.PARTY_INVITE_TIMEOUT + "s)");
    }

    public void accept(Player who, String inviterName) {
        if (who == null) return;
        if (partyOf(who.getUniqueId()) != null) { msg(who, ChatColor.RED + "You are already in a party. Leave it first."); return; }
        Party party = findInvitingParty(who.getUniqueId(), inviterName);
        if (party == null) { msg(who, ChatColor.RED + "You have no pending party invite" + (inviterName != null ? " from " + inviterName : "") + "."); return; }
        Long expiry = party.inviteExpiry(who.getUniqueId());
        if (expiry == null || GameRules.inviteExpired(System.currentTimeMillis(), expiry)) {
            party.invited().remove(who.getUniqueId());
            msg(who, ChatColor.RED + "That invite has expired.");
            return;
        }
        int cap = capFor(party);
        if (party.size() >= cap) { msg(who, ChatColor.RED + "That party is now full."); party.invited().remove(who.getUniqueId()); return; }
        addToParty(party, who, true);
    }

    public void decline(Player who, String inviterName) {
        if (who == null) return;
        Party party = findInvitingParty(who.getUniqueId(), inviterName);
        if (party == null) { msg(who, ChatColor.RED + "You have no pending party invite."); return; }
        party.invited().remove(who.getUniqueId());
        msg(who, ChatColor.YELLOW + "Declined the party invite.");
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader != null) msg(leader, ChatColor.YELLOW + who.getName() + " declined your party invite.");
    }

    /** Open join (no invite) — used by GUI open-party listings. */
    public void joinOpen(Player who, Party party) {
        if (who == null || party == null) return;
        if (!party.open()) { msg(who, ChatColor.RED + "That party is invite-only."); return; }
        if (partyOf(who.getUniqueId()) != null) { msg(who, ChatColor.RED + "You are already in a party."); return; }
        if (party.size() >= capFor(party)) { msg(who, ChatColor.RED + "That party is full."); return; }
        addToParty(party, who, false);
    }

    private void addToParty(Party party, Player who, boolean invite) {
        BedlamPartyPreJoinEvent pre = new BedlamPartyPreJoinEvent(party, who, invite);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) { msg(who, ChatColor.RED + "You cannot join that party right now."); return; }
        party.addMember(who.getUniqueId());
        memberIndex.put(who.getUniqueId(), party.id());
        Bukkit.getPluginManager().callEvent(new BedlamPartyJoinEvent(party, who));
        announce(party, ChatColor.AQUA + who.getName() + ChatColor.YELLOW + " joined the party. "
            + ChatColor.GRAY + "(" + party.size() + "/" + capFor(party) + ")");
        Sounds.play(who, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
    }

    private Party findInvitingParty(UUID target, String inviterName) {
        // Explicit inviter: match their party. Otherwise any party that has invited this player.
        for (Party party : byId.values()) {
            if (!party.hasInvite(target)) continue;
            if (inviterName == null) return party;
            String leaderName = party.leaderName();
            if (leaderName != null && leaderName.equalsIgnoreCase(inviterName)) return party;
        }
        return null;
    }

    // ------------------------------------------------------------------ kick / promote / leave / disband

    public void kick(Player target, UUID leaderUuid) {
        if (target == null || leaderUuid == null) return;
        Party party = partyOf(leaderUuid);
        Player leader = Bukkit.getPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) { if (leader != null) msg(leader, ChatColor.RED + "You are not a party leader."); return; }
        if (!party.isMember(target.getUniqueId())) { if (leader != null) msg(leader, ChatColor.RED + target.getName() + " is not in your party."); return; }
        if (target.getUniqueId().equals(leaderUuid)) { if (leader != null) msg(leader, ChatColor.RED + "You cannot kick yourself. Use /party disband."); return; }
        BedlamPartyKickEvent pre = new BedlamPartyKickEvent(party, target.getUniqueId(), leaderUuid);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return;
        removeMember(party, target.getUniqueId(), ChatColor.YELLOW + "You were removed from the party.");
        announce(party, ChatColor.YELLOW + target.getName() + " was removed from the party.");
    }

    public void promote(Player target, UUID leaderUuid) {
        if (target == null || leaderUuid == null) return;
        Party party = partyOf(leaderUuid);
        Player leader = Bukkit.getPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) { if (leader != null) msg(leader, ChatColor.RED + "You are not a party leader."); return; }
        if (!party.isMember(target.getUniqueId())) { if (leader != null) msg(leader, ChatColor.RED + target.getName() + " is not in your party."); return; }
        if (target.getUniqueId().equals(leaderUuid)) { if (leader != null) msg(leader, ChatColor.RED + "You are already the leader."); return; }
        party.leader(target.getUniqueId());
        // Keep join order sensible: move the new leader to the front.
        party.members().remove(target.getUniqueId());
        party.members().add(0, target.getUniqueId());
        Bukkit.getPluginManager().callEvent(new BedlamPartyPromoteEvent(party, target.getUniqueId(), leaderUuid));
        announce(party, ChatColor.YELLOW + target.getName() + " is now the party leader.");
    }

    public void leave(Player who) {
        if (who == null) return;
        Party party = partyOf(who.getUniqueId());
        if (party == null) { msg(who, ChatColor.RED + "You are not in a party."); return; }
        BedlamPartyPreLeaveEvent pre = new BedlamPartyPreLeaveEvent(party, who.getUniqueId());
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return;
        boolean wasLeader = party.isLeader(who.getUniqueId());
        removeMember(party, who.getUniqueId(), ChatColor.YELLOW + "You left the party.");
        if (byId.containsKey(party.id())) {
            announce(party, ChatColor.YELLOW + who.getName() + " left the party.");
            if (wasLeader) announce(party, ChatColor.YELLOW + party.leaderName() + " is now the party leader.");
        }
    }

    /** Internal removal shared by kick/leave/disconnect: updates index, fires leave, auto-disbands on shrink. */
    private void removeMember(Party party, UUID uuid, String messageToRemoved) {
        boolean wasLeader = party.isLeader(uuid);
        party.removeMember(uuid);
        memberIndex.remove(uuid);
        chatToggled.remove(uuid);
        Bukkit.getPluginManager().callEvent(new BedlamPartyLeaveEvent(party, uuid));
        Player removed = Bukkit.getPlayer(uuid);
        if (removed != null && messageToRemoved != null) msg(removed, messageToRemoved);
        if (wasLeader && party.size() >= 1) {
            Bukkit.getPluginManager().callEvent(new BedlamPartyPromoteEvent(party, party.leader(), uuid));
        }
        if (GameRules.partyDisbandsOnShrink(party.size())) disbandInternal(party, true);
    }

    @Override public boolean disband(UUID leaderUuid) {
        if (leaderUuid == null) return false;
        Party party = partyOf(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) return false;
        disbandInternal(party, false);
        return true;
    }

    /** Leader-initiated disband via command (confirm on the second use within the session). */
    public void disbandCommand(Player leader) {
        if (leader == null) return;
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { msg(leader, ChatColor.RED + "You are not a party leader."); return; }
        if (!disbandConfirm.contains(leader.getUniqueId())) {
            disbandConfirm.add(leader.getUniqueId());
            msg(leader, ChatColor.YELLOW + "Run " + ChatColor.RED + "/party disband" + ChatColor.YELLOW + " again to confirm.");
            return;
        }
        disbandConfirm.remove(leader.getUniqueId());
        disbandInternal(party, false);
    }

    private void disbandInternal(Party party, boolean fromShrink) {
        if (party == null || !byId.containsKey(party.id())) return;
        announce(party, ChatColor.RED + "The party has been disbanded.");
        for (UUID uuid : new ArrayList<UUID>(party.members())) {
            memberIndex.remove(uuid);
            chatToggled.remove(uuid);
            disbandConfirm.remove(uuid);
        }
        party.members().clear();
        byId.remove(party.id());
        Bukkit.getPluginManager().callEvent(new BedlamPartyDisbandEvent(party));
    }

    /** Match-drop hook: release a dropped member's party slot (no messaging) so a teammate can re-invite. */
    public void releaseMembership(UUID uuid) {
        if (uuid == null) return;
        Party party = partyOf(uuid);
        if (party == null) return;
        boolean wasLeader = party.isLeader(uuid);
        party.removeMember(uuid);
        memberIndex.remove(uuid);
        chatToggled.remove(uuid);
        if (wasLeader && party.size() >= 1) {
            Bukkit.getPluginManager().callEvent(new BedlamPartyPromoteEvent(party, party.leader(), uuid));
        }
        if (GameRules.partyDisbandsOnShrink(party.size())) disbandInternal(party, true);
    }

    @Override public boolean addMember(UUID leaderUuid, Player target) {
        if (leaderUuid == null || target == null) return false;
        Party party = partyOf(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) return false;
        if (partyOf(target.getUniqueId()) != null) return false;
        addToParty(party, target, true);
        return party.isMember(target.getUniqueId());
    }

    // ------------------------------------------------------------------ list / chat

    public void list(Player who) {
        if (who == null) return;
        Party party = partyOf(who.getUniqueId());
        if (party == null) { msg(who, ChatColor.RED + "You are not in a party."); return; }
        msg(who, ChatColor.AQUA + "" + ChatColor.STRIKETHROUGH + "----------------------------");
        msg(who, ChatColor.YELLOW + "Party (" + party.size() + "/" + capFor(party) + ")  "
            + (party.open() ? ChatColor.GREEN + "[OPEN]" : ChatColor.GRAY + "[INVITE-ONLY]"));
        for (UUID uuid : party.members()) {
            Player online = Bukkit.getPlayer(uuid);
            String name = online != null ? online.getName() : offlineName(uuid);
            String star = party.isLeader(uuid) ? ChatColor.GOLD + "★ " : ChatColor.GRAY + "  ";
            String status = online != null ? ChatColor.GREEN + "online" : ChatColor.DARK_GRAY + "offline";
            msg(who, star + ChatColor.WHITE + name + " " + ChatColor.DARK_GRAY + "(" + status + ChatColor.DARK_GRAY + ")");
        }
        msg(who, ChatColor.AQUA + "" + ChatColor.STRIKETHROUGH + "----------------------------");
    }

    public void toggleChat(Player who) {
        if (who == null) return;
        if (partyOf(who.getUniqueId()) == null) { msg(who, ChatColor.RED + "You are not in a party."); return; }
        if (chatToggled.remove(who.getUniqueId())) msg(who, ChatColor.YELLOW + "Party chat " + ChatColor.RED + "off" + ChatColor.YELLOW + ".");
        else { chatToggled.add(who.getUniqueId()); msg(who, ChatColor.YELLOW + "Party chat " + ChatColor.GREEN + "on" + ChatColor.YELLOW + "."); }
    }

    public boolean isChatToggled(UUID uuid) { return uuid != null && chatToggled.contains(uuid); }

    /** Send a message to every online member of the sender's party. No-op when the sender has no party. */
    public void sendPartyChat(Player sender, String message) {
        if (sender == null || message == null) return;
        Party party = partyOf(sender.getUniqueId());
        if (party == null) { msg(sender, ChatColor.RED + "You are not in a party."); return; }
        String prefix = colors(plugin.getConfig().getString("party.chat-prefix", "&b[PARTY] &f"));
        String line = prefix + ChatColor.WHITE + sender.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
        // Party chat reaches all online members regardless of world (its own channel; isolation does not apply).
        for (Player member : party.onlineMembers()) member.sendMessage(line);
    }

    public void warp(Player leader) {
        if (leader == null) return;
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { msg(leader, ChatColor.RED + "You are not a party leader."); return; }
        if (plugin.games().arena(leader) != null) { msg(leader, ChatColor.RED + "You can only warp from the lobby."); return; }
        int moved = 0;
        for (Player member : party.onlineMembers()) {
            if (member.getUniqueId().equals(leader.getUniqueId())) continue;
            if (plugin.games().arena(member) != null) continue; // don't yank a member out of a match
            member.teleport(leader.getLocation());
            msg(member, ChatColor.YELLOW + "Warped to your party leader.");
            moved++;
        }
        msg(leader, ChatColor.GREEN + "Warped " + moved + " member(s) to you.");
    }

    public void setOpen(Player leader, boolean open) {
        if (leader == null) return;
        Party party = partyOf(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { msg(leader, ChatColor.RED + "You are not a party leader."); return; }
        party.open(open);
        announce(party, ChatColor.YELLOW + "Party is now " + (open ? ChatColor.GREEN + "open" : ChatColor.GRAY + "invite-only") + ChatColor.YELLOW + ".");
    }

    // ------------------------------------------------------------------ queue event hooks (called by GameService)

    /** @return {@code false} when a listener cancelled the pre-queue event. */
    public boolean callPreQueue(Party party, GameType type) {
        BedlamPartyPreQueueEvent pre = new BedlamPartyPreQueueEvent(party, type);
        Bukkit.getPluginManager().callEvent(pre);
        return !pre.isCancelled();
    }

    public void callQueued(Party party, GameType type) {
        Bukkit.getPluginManager().callEvent(new BedlamPartyQueueEvent(party, type));
    }

    // ------------------------------------------------------------------ helpers

    private int maxSize(Player leader) {
        int cap = GameRules.PARTY_MAX_SIZE;
        if (leader != null && leader.hasPermission("bedlam.party.bypass-limit")) return Math.max(cap, 64);
        return cap;
    }

    private int capFor(Party party) {
        if (party == null) return GameRules.PARTY_MAX_SIZE;
        Player leader = Bukkit.getPlayer(party.leader());
        return maxSize(leader);
    }

    private void announce(Party party, String message) {
        if (party == null) return;
        for (Player member : party.onlineMembers()) member.sendMessage(message);
    }

    private void msg(Player player, String message) { if (player != null) player.sendMessage(message); }

    private static String colors(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    @SuppressWarnings("deprecation")
    private static String offlineName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline != null && offline.getName() != null ? offline.getName() : uuid.toString();
    }

    // ------------------------------------------------------------------ optional party.yml persistence

    private File file() { return new File(plugin.getDataFolder(), "party.yml"); }

    private void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            int i = 0;
            for (Party party : byId.values()) {
                if (party.size() <= 1) continue; // lone parties are not worth persisting
                String path = "parties." + (i++);
                yaml.set(path + ".leader", offlineName(party.leader()));
                List<String> names = new ArrayList<String>();
                for (UUID uuid : party.members()) names.add(offlineName(uuid));
                yaml.set(path + ".members", names);
                yaml.set(path + ".open", party.open());
            }
            dev.iyanel.bedlamcore.util.AtomicFiles.writeUtf8(file().toPath(), yaml.saveToString());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not save party.yml: " + t.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void load() {
        File f = file();
        if (!f.isFile()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection root = yaml.getConfigurationSection("parties");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                String path = "parties." + key;
                String leaderName = yaml.getString(path + ".leader");
                List<String> members = yaml.getStringList(path + ".members");
                if (leaderName == null || members == null || members.isEmpty()) continue;
                UUID leaderId = Bukkit.getOfflinePlayer(leaderName).getUniqueId();
                if (leaderId == null || memberIndex.containsKey(leaderId)) continue;
                Party party = new Party(UUID.randomUUID(), leaderId);
                memberIndex.put(leaderId, party.id());
                for (String name : members) {
                    if (name == null || name.equalsIgnoreCase(leaderName)) continue;
                    UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
                    if (uuid == null || memberIndex.containsKey(uuid)) continue;
                    party.addMember(uuid);
                    memberIndex.put(uuid, party.id());
                }
                party.open(yaml.getBoolean(path + ".open", false));
                if (party.size() > 1) byId.put(party.id(), party);
                else memberIndex.remove(leaderId);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not load party.yml: " + t.getMessage());
        }
    }
}
