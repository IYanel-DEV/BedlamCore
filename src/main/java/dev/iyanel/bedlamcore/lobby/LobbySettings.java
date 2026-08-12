package dev.iyanel.bedlamcore.lobby;

import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

public final class LobbySettings {
    private Location spawn;
    private final Map<GameType, NpcSettings> npcs = new EnumMap<GameType, NpcSettings>(GameType.class);

    public LobbySettings() {
        for (GameType type : GameType.values()) npcs.put(type, new NpcSettings());
    }

    public Location spawn() { return clone(spawn); }
    public void spawn(Location value) { spawn = clone(value); }
    public NpcSettings npc(GameType type) { return npcs.get(type); }
    public boolean complete() { return spawn != null && npc(GameType.SOLO).location() != null && npc(GameType.DOUBLES).location() != null; }

    public LobbySettings copy() {
        LobbySettings copy = new LobbySettings();
        copy.spawn(spawn);
        for (GameType type : GameType.values()) {
            copy.npc(type).location(npc(type).location());
            copy.npc(type).entityType(npc(type).entityType());
        }
        return copy;
    }

    private static Location clone(Location value) { return value == null ? null : value.clone(); }

    public static final class NpcSettings {
        private Location location;
        private EntityType entityType = EntityType.VILLAGER;

        public Location location() { return LobbySettings.clone(location); }
        public void location(Location value) { location = LobbySettings.clone(value); }
        public EntityType entityType() { return entityType; }
        public void entityType(EntityType value) { entityType = value == null ? EntityType.VILLAGER : value; }
    }
}
