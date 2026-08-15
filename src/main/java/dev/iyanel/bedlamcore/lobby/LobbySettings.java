package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

public final class LobbySettings {
    private Location spawn;
    private final Map<GameType, NpcSettings> npcs = new EnumMap<GameType, NpcSettings>(GameType.class);
    private Location cosmeticsNpc;
    private Location profileNpc;

    public LobbySettings() {
        for (GameType type : GameType.values()) npcs.put(type, new NpcSettings());
    }

    public Location spawn() { return clone(spawn); }
    public void spawn(Location value) { spawn = clone(value); }
    public NpcSettings npc(GameType type) { return npcs.get(type); }
    public Location cosmeticsNpc() { return clone(cosmeticsNpc); }
    public void cosmeticsNpc(Location value) { cosmeticsNpc = clone(value); }
    public Location profileNpc() { return clone(profileNpc); }
    public void profileNpc(Location value) { profileNpc = clone(value); }
    public boolean complete() { return spawn != null && npc(GameType.SOLO).location() != null && npc(GameType.DOUBLES).location() != null; }

    public LobbySettings copy() {
        LobbySettings copy = new LobbySettings();
        copy.spawn(spawn);
        copy.cosmeticsNpc(cosmeticsNpc);
        copy.profileNpc(profileNpc);
        for (GameType type : GameType.values()) {
            copy.npc(type).location(npc(type).location());
            copy.npc(type).entityType(npc(type).entityType());
            copy.npc(type).baby(npc(type).baby());
            copy.npc(type).human(npc(type).human());
            copy.npc(type).skin(npc(type).skin());
            copy.npc(type).lookAtPlayers(npc(type).lookAtPlayers());
        }
        return copy;
    }

    private static Location clone(Location value) { return value == null ? null : value.clone(); }

    public static final class NpcSettings {
        private Location location;
        private EntityType entityType = EntityType.VILLAGER;
        private boolean baby;
        private boolean human;
        private String skin;
        private boolean lookAtPlayers;

        public Location location() { return LobbySettings.clone(location); }
        public void location(Location value) { location = LobbySettings.clone(value); }
        public EntityType entityType() { return entityType; }
        public void entityType(EntityType value) { entityType = value == null ? EntityType.VILLAGER : value; }
        public boolean baby() { return baby; }
        public void baby(boolean value) { baby = value; }
        public boolean human() { return human; }
        public void human(boolean value) { human = value; }
        public String skin() { return skin; }
        public void skin(String value) { skin = value == null || value.trim().isEmpty() ? null : value.trim(); }
        public boolean lookAtPlayers() { return lookAtPlayers; }
        public void lookAtPlayers(boolean value) { lookAtPlayers = value; }
    }
}
