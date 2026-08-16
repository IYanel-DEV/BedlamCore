# BedlamCore

**Bed Wars that feels finished — GUI-first setup, one jar from Spigot 1.8.8 to Paper 26.2.**

Author: **IYanel-DEV** · Current line: **0.10.39**  
Independent project. Not affiliated with Hypixel, Mojang, or Microsoft.

---

## Upload these images (SpigotMC gallery / description)

Paste into the resource description after uploading. Suggested order:

| # | File | Role |
|---|------|------|
| 1 | `docs/brand/banner-bedlamcore.png` | Hero banner |
| 2 | `docs/brand/logo-bedlamcore.png` | Logo / icon |
| 3 | `docs/brand/logo-bedlamcore-purple-bed.png` | Purple-bed mark |
| 4 | `docs/showcase/banner-bedlamcore-features.png` | Features strip |
| 5 | `docs/showcase/lobby.png` | Lobby + profile hologram |
| 6 | `docs/showcase/queue-npcs.png` | Solo / Doubles queue NPCs |
| 7 | `docs/showcase/cosmetics.png` | Cosmetics NPC |
| 8 | `docs/showcase/profile.png` | Profile NPC hologram |
| 9 | `docs/showcase/setup.png` | Bedlam Setup compass GUI |
| 10 | `docs/showcase/game-worlds.png` | Game Worlds + bedwars-e2560 |
| 11 | `docs/showcase/templates.png` | Bundled map templates |
| 12 | `docs/showcase/waiting.png` | Waiting structure |
| 13 | `docs/showcase/team-island.png` | Team island |
| 14 | `docs/showcase/forge.png` | Team forge |
| 15 | `docs/showcase/diamond-gen.png` | Diamond generator |
| 16 | `docs/showcase/emerald-gen.png` | Emerald generator |
| 17 | `docs/showcase/quick-buy.png` | Item Shop Quick Buy |
| 18 | `docs/showcase/upgrades.png` | Upgrades & Traps |
| 19 | `docs/showcase/punch-deposit.png` | Punch-to-deposit |
| 20 | `docs/showcase/victory.png` | Victory / Play Again |
| 21 | `docs/showcase/cosmetics-shop.png` | Cosmetics shop (optional) |
| 22 | `docs/showcase/stats.png` | Stats GUI (optional) |
| 23 | `docs/showcase/play-menu.png` | Play menu (optional) |
| 24 | `docs/showcase/shops.png` | Shop NPCs (optional) |

---

## Tagline

Purple-bed Bed Wars. Compass setup. Lobby NPCs. Real matches. One jar.

---

## Overview

BedlamCore is a **GUI-first Bed Wars engine** for Spigot and Paper.

You set up the lobby and arenas with a **hotbar compass** — drafts, Apply, Cancel — not a command maze. Players queue through **Solo / Doubles NPCs**, earn **tokens / XP / levels**, buy **cosmetics**, and play a full loop: waiting structure → forge & gens → Quick Buy shop → upgrades & traps → beds → **soft spectate** → victory rewards → Play Again.

Bundled map template: **bedwars-e2560**. Import your own folder worlds too.

**One jar.** Spigot **1.8.8** through Paper **26.2**.

---

## Features

### Lobby
- Solo & Doubles **queue NPCs** (Citizens, or built-in armor-stand / mob fallback)
- **Profile NPC** + statistics GUI
- **Cosmetics NPC** (token shop — kill-message packs and more)
- Lobby **scoreboard** (level / tokens / kills / wins)
- Configurable scoreboard footer (`play.bedlam`)

### Setup
- Compass-driven **Lobby Setup** and **Game World Setup**
- Transactional drafts — **Apply** saves, **Cancel** discards
- Waiting structure via `/bc spawnbuild` (two-click golden axe)
- Spectator spawn, teams, beds, forges, shops, chests, diamond/emerald gens
- **Build border** radius from waiting + spectator midpoint (outline in setup; match builds stay inside)

### Arenas & templates
- Multi-world **Solo** and **Doubles**
- Bundled template **bedwars-e2560**
- **Import Maps** for folder worlds
- Pristine world snapshots between matches; crash-safe YAML / world replace

### Match
- Item Shop **Quick Buy** (Blocks / Melee / Armor / Tools / Ranged / Potions / Utility)
- **Upgrades & Traps** GUI
- Team forge share, diamond & emerald **tier** gens
- Bridge Egg, Dream Defender
- Punch-to-deposit **team chests**
- **Soft spectate** — adventure + flight + invis (not `SPECTATOR` gamemode)
- Match rewards, win effects / dragon presentation, Play Again

### Economy & persistence
- Tokens / XP / levels in `stats.yml`
- Admin: `/bc token add`, `/bc xp add`
- Inventory cleared on leave; ender chests do not persist across matches

---

## Installation

1. Download `BedlamCore-0.10.39.jar` from [GitHub Releases](https://github.com/IYanel-DEV/BedlamCore/releases).
2. Drop it in `plugins/` and restart.
3. Join as an operator — hotbar **Bedlam Setup** compass opens Lobby Setup (lobby) or that world’s arena draft (game world).
4. Finish Lobby Setup (spawn + NPCs), then create or import game worlds. **Apply** saves; **Cancel** discards.
5. Players join via queue NPCs (or `/bedlam` fallbacks). Ops: `/bedlam forcestart` in a waiting arena for solo testing.

---

## Basic setup tutorial

### Lobby
1. Op in the lobby → open the **compass**.
2. **Lobby Setup** → set network spawn.
3. Place **Solo** and **Doubles** queue NPCs (placer item → place; shift-click edits mob/skin/look-at).
4. Optional: **Profile** and **Cosmetics** NPCs.
5. **Apply**.

### Game world / template
1. Compass → **Game World Setup** → create Solo or Doubles void world, or open **Templates** / **Import Map**.
2. Teleport into the arena. Set waiting spawn, spectator, teams, beds, forges, shops, chests, gens.
3. `/bc spawnbuild` — waiting cuboid (glass is not a valid corner; one diamond block = relative spawn anchor).
4. Set **build border** radius once waiting + spectator exist (default **64**).
5. **Apply** — waiting paste stripped, world saved, pristine snapshot taken, back to lobby.

Showcase waiting / island shots use the bundled **bedwars-e2560** template.

### Match flow
Queue NPC → waiting + countdown → team spawn → forge / shop / upgrades → beds → soft spectate on final death → victory → Play Again / lobby.

---

## Commands

| Command | Purpose |
|---------|---------|
| `/bedlam` (`/bc`) | Fallback hub: `menu`, `solo`, `doubles`, `leave`, `spawnbuild`, `forcestart`, `reload`, `token`, `xp` |
| `/leave` | Leave match / return to lobby |

Primary UX is the setup compass and lobby NPCs. Commands are fallbacks and admin tools.

**Economy (admin):**
- `/bc token add <player> <amount>`
- `/bc xp add <player> <amount>`  
Console may use `reload`, `token`, and `xp`.

---

## Permissions

| Permission | Default | Purpose |
|------------|---------|---------|
| `bedlam.admin` | op | Configure arenas, force-start, setup (includes token/xp add) |
| `bedlam.token.add` | op | `/bc token add` |
| `bedlam.xp.add` | op | `/bc xp add` |
| `bedlam.play` | true | Join games |
| `bedlam.lobby.build` | false | Break/place in the lobby world |

---

## Requirements

- **Java 8+** runtime (plugin bytecode is Java 8)
- **Spigot / Paper 1.8.8 → 26.2** (one jar)
- Optional: **Citizens** for real fake-player queue NPCs

---

## License

**Proprietary — All Rights Reserved** (© 2026 IYanel-DEV).

- **Use freely unmodified** — download, install, and run the official jar on your servers without asking.
- Public mirrors of the **unmodified** official jar are allowed with attribution and a link to the repository.
- **Modification**, derivative works, redistributing modified builds, and rebranding require **prior written permission**.

Full text: [LICENSE](https://github.com/IYanel-DEV/BedlamCore/blob/main/LICENSE) on GitHub.

---

## Links

- **GitHub:** https://github.com/IYanel-DEV/BedlamCore  
- **Releases (jar):** https://github.com/IYanel-DEV/BedlamCore/releases  
- **Author:** https://github.com/IYanel-DEV  

Not affiliated with Hypixel, Mojang, or Microsoft. Ships no third-party server code, maps, or branding.
