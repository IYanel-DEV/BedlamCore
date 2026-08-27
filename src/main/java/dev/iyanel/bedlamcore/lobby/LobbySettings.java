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
    private String cosmeticsSkin;
    private String profileSkin;
    private boolean cosmeticsCape;
    private boolean profileCape;

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
    public String cosmeticsSkin() { return cosmeticsSkin; }
    public void cosmeticsSkin(String value) { cosmeticsSkin = value == null || value.trim().isEmpty() ? null : value.trim(); }
    public String profileSkin() { return profileSkin; }
    public void profileSkin(String value) { profileSkin = value == null || value.trim().isEmpty() ? null : value.trim(); }
    public boolean cosmeticsCape() { return cosmeticsCape; }
    public void cosmeticsCape(boolean value) { cosmeticsCape = value; }
    public boolean profileCape() { return profileCape; }
    public void profileCape(boolean value) { profileCape = value; }
    public boolean complete() { return spawn != null && npc(GameType.SOLO).location() != null && npc(GameType.DOUBLES).location() != null; }

    public LobbySettings copy() {
        LobbySettings copy = new LobbySettings();
        copy.spawn(spawn);
        copy.cosmeticsNpc(cosmeticsNpc);
        copy.profileNpc(profileNpc);
        copy.cosmeticsSkin(cosmeticsSkin);
        copy.profileSkin(profileSkin);
        copy.cosmeticsCape(cosmeticsCape);
        copy.profileCape(profileCape);
        for (GameType type : GameType.values()) {
            copy.npc(type).location(npc(type).location());
            copy.npc(type).entityType(npc(type).entityType());
            copy.npc(type).baby(npc(type).baby());
            copy.npc(type).human(npc(type).human());
            copy.npc(type).skin(npc(type).skin());
            copy.npc(type).lookAtPlayers(npc(type).lookAtPlayers());
            copy.npc(type).cape(npc(type).cape());
        }
        return copy;
    }

    private static Location clone(Location value) { return value == null ? null : value.clone(); }

    public static final class NpcSettings {
        private Location location;
        private EntityType entityType = EntityType.VILLAGER;
        private boolean baby;
        private boolean human = true;
        private String skin;
        private boolean lookAtPlayers;
        private boolean cape;

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
        public boolean cape() { return cape; }
        public void cape(boolean value) { cape = value; }
    }
}
