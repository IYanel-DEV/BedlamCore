# Shopkeeper Skins + Look-At Fix — Design

Date: 2026-08-27
Status: Approved (pending spec review)

## Summary

Two pieces of work:

1. **Feature — Shopkeeper Skins**: a new cosmetic category (50 skins, coin cost) that
   changes the appearance of a team's in-match shop NPCs (ITEM SHOP + TEAM UPGRADES villagers)
   to a real player skin. Team-shared, split across the two NPCs by team join order.
2. **Bug fix — look-at**: lobby NPCs and in-match shop villagers do not rotate toward players.

Target: MC 1.8.8–26.2, compiled against Spigot 1.8.8 API. Primary test server: stable-1.12.2
(Citizens disabled → packet NPCs). Packet fake-player skins must be SIGNED to render on 1.12.2.

## Part 1 — Shopkeeper Skins

### 1.1 Cosmetics catalog

- New category constant `CAT_SHOPKEEPER_SKIN = "SHOPKEEPER_SKIN"` in `CosmeticsService`,
  registered in `byCategory` (LinkedHashMap) alongside the others.
- `categoryDisplay(CAT_SHOPKEEPER_SKIN)` → `"Shopkeeper Skins"`.
- 50 cosmetics registered via `add(id, CAT_SHOPKEEPER_SKIN, name, cost, {}, username, particles)`.
  - `effect` field holds a **real signed Mojang username** (the skin source).
  - Mixed Hypixel-style variety (mobs, characters, mascots, seasonal, colorful).
  - Tiered coin cost ~150–400 (cheaper common, pricier flashy), consistent with Wood Skins.
  - IDs stable/prefixed `sk_<name>` so already-owned IDs keep resolving.
- All 50 usernames batch-verified before ship: each must resolve at
  `api.mojang.com/users/profiles/minecraft/<name>` AND return a texture value at
  `sessionserver.mojang.com/session/minecraft/profile/<id>?unsigned=false`. Dead ones swapped.

### 1.2 Cosmetic shop menu (GUI)

- `openCosmetics()` gains a **"Shopkeeper Skins"** home icon (villager spawn egg, or a player
  head), in a free slot, via `cosmeticsHomeIcon(player, "Shopkeeper Skins", …, CAT_SHOPKEEPER_SKIN)`.
- `clickCosmeticsHome()` routes `"Shopkeeper Skins"` → `openCosmeticsCategory(CAT_SHOPKEEPER_SKIN)`.
- `openCosmeticsCategory()` renders each shopkeeper cosmetic as a **preview head**
  `Skins.head(cosmetic.effect)` so the player sees the actual skin face; name + price + owned/equipped
  status via the existing item-render path.
- Dispatcher (`click`) + `isBedlamTitle()` (in `GameListener`) both learn the `"Shopkeeper Skins"`
  title so clicks are cancelled and routed.
- Buy/equip/unequip reuse the existing `CosmeticsService` purchase flow (`equippedCosmetic`,
  owned/equipped in stats.yml). No new persistence.

### 1.3 Team → NPC skin resolution

Computed once when the arena's shop NPCs spawn (match start) and held static for the match.

For a team `T`:
- `members` = `arena.players()` entries with `team == T`, in map (join) order. `arena.players()`
  is a `LinkedHashMap<UUID, TeamColor>` so order = join order.
- `skinFor(member)` = `byId(stats.equippedCosmetic(member, CAT_SHOPKEEPER_SKIN)).effect`, or null
  when nothing equipped.
- **Item-shop NPC skin** = `skinFor(members[0])` (null → plain villager).
- **Upgrades NPC skin**:
  - team size == 1 (solo) → `skinFor(members[0])` (same skin on both NPCs).
  - team size >= 2 (doubles) → `skinFor(members[1])` (null → plain villager).
- Only equipped ("paid") skins skin an NPC; null always means the default villager.

Helper lives in `CosmeticsService` (has `plugin.stats()` + catalog): e.g.
`String shopkeeperSkin(Arena arena, TeamColor team, boolean upgradeNpc)`.

### 1.4 In-match rendering

Owned by `ArenaDisplayService` (already spawns/pins shop villagers and runs a 1-tick displayTask).

- `spawnShop(...)` gains a `TeamColor team` parameter (callers in `spawnAll` already loop per team).
  It spawns the villager with `bedlamShop` metadata; after spawn, resolve the skin for
  `(team, kind == "UPGRADE")`.
  - skin != null: add infinite invisibility to the villager (hide the real body, keep the click
    hitbox — same trick as lobby NPCs), attach a **shared** `PacketNpcs` model with that skin at the
    pin. Track the model per shop-body UUID.
  - skin == null: leave the plain villager (today's behaviour).
- displayTask loop: for each skinned shop body, `PacketNpcs.ensureViewers(model, range)` (re-show to
  joiners) and `PacketNpcs.look(model, yaw, pitch)` toward the nearest player (shared orientation).
  Spectators get the body packet-hidden (villager is invisible to them via potion see-through) — reuse
  the lobby pattern.
- `clear()` destroys all shop models (`PacketNpcs.destroy`) alongside the entities.
- Skin fetch is async (`PacketNpcs.fetchSkin` + `cachedProfile`); if the profile isn't cached yet at
  spawn, retry attach from the displayTask loop once cached (mirror the lobby pin-retry).
- Team-shared: one model per NPC, shown to everyone → no per-viewer models.

### 1.5 PacketNpcs additions

- Reuse existing `create` / `show` / `ensureViewers` / `look` / `destroy` (shared model). No
  per-viewer skin path needed given the team-shared decision.
- Add `lookEntity(Entity entity, float yaw, float pitch)`: send `PacketPlayOutEntityLook` +
  `PacketPlayOutEntityHeadRotation` for a REAL entity's id to nearby players, so a plain (unskinned)
  shop villager visibly turns (its `setRotation` isn't relayed by the tracker with AI off).

## Part 2 — Look-at fix

- **Lobby NPCs don't turn**: `LobbyNpcService.spawn()` hardcodes `lookAtPlayers → FALSE` (an old
  "faces backward" guard). Fix: `lookAtPlayers.put(id, settings.lookAtPlayers())`. Rotation already
  routes through `applyLook` (body) + `PacketNpcs.look` (visible model) in `pinEntities`, so honoring
  the toggle makes the skin turn. The earlier "faces backward" was the yaw-convention bug, already
  fixed — safe to re-enable.
- **Game shop villagers don't turn (plain)**: `lookAtNearestPlayer` → `applyLook` → `setRotation`
  isn't relayed to clients. Fix: also call `PacketNpcs.lookEntity(entity, yaw, pitch)` for plain shop
  villagers.
- **Skinned shopkeepers**: turn via their model's `PacketNpcs.look` (§1.4).

## Non-goals / limits

- Not per-viewer (team-shared, per decision).
- Cape OFF default for shopkeeper skins (signed-skin cape limit: stripping a cape invalidates the
  Mojang signature → default skin; so shopkeeper skins keep whatever cape the account owns).
- Raw texture-URL skins excluded from the 50 (unsigned → won't render on 1.12.2); usernames only.
- Skin assignment is static at match start; a DC'd/eliminated teammate's NPC keeps its skin.

## Testing

- `coreCheck` stays green (dependency-free rule checks).
- Batch-verify all 50 usernames resolve + return textures (script, pre-ship).
- Manual on stable-1.12.2: buy/equip a shopkeeper skin in the lobby; start a doubles match with a
  teammate holding a different skin → item-shop NPC = your skin, upgrades NPC = teammate's skin;
  solo → both NPCs your skin; no skin → plain villager. Lobby NPC "Look at Players" ON → NPC turns;
  shop villagers turn toward players.

## Files touched

- `cosmetics/CosmeticsService.java` — category, 50 skins, categoryDisplay, `shopkeeperSkin(...)` helper.
- `gui/GuiController.java` — cosmetics home icon, category route, head-preview render, dispatcher.
- `game/GameListener.java` — `isBedlamTitle` "Shopkeeper Skins".
- `arena/ArenaDisplayService.java` — resolve + attach/maintain/destroy shop skin models; plain look-at.
- `compat/PacketNpcs.java` — `lookEntity(...)`.
- `lobby/LobbyNpcService.java` — honor `settings.lookAtPlayers()` in `spawn()`.
- `resources/config.yml` — optional catalog entries (if catalog is config-driven; else defaults only).
