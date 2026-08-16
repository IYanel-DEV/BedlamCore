# BedlamCore v0.10.39 — Release notes

**Author:** IYanel-DEV  
**Artifact:** `BedlamCore-0.10.39.jar`  
**Download:** https://github.com/IYanel-DEV/BedlamCore/releases  
**Compatibility:** Java 8+ · Spigot / Paper **1.8.8 → 26.2** (one jar)

---

## What’s new in 0.10.39

### Admin economy
- `/bc token add <player> <amount>` — grant tokens (online or known offline)
- `/bc xp add <player> <amount>` — grant XP (levels update from the existing stats curve)
- Permissions: `bedlam.token.add`, `bedlam.xp.add` (both default op; also under `bedlam.admin`)
- Console can run `token` / `xp` (same as `reload`)

### Paper 26.2 world border fix
- Hiding the setup border no longer uses diameter `6e7` (Paper rejects it and could crash **Cancel** / **Apply**)
- Diameters are clamped to Paper’s max (`59_999_968`)
- Border restore/hide on cancel and apply is guarded so a bad border size cannot leave orphan setup state

---

## Since v0.10.37 (gap)

If your last jar was **0.10.37**, this update is the two items above — plus version bump to **0.10.39**.

If you are jumping from an older public line (**≤ 0.10.1**), you also pick up everything that landed in the **0.10.37** public gap release:

- Bundled map template **bedwars-e2560** + template / import tooling
- Match **build border**
- Cosmetics shop (token sink)
- Profile NPC + lobby polish
- Win effects / dragon presentation
- Dream Defender, forge/gens polish, soft spectate, team chest, Bridge Egg, match rewards
- Persistence hardening (atomic saves, stats/profile, game-worlds / template checks)

Full feature surface: see `docs/spigotmc/DESCRIPTION.md` or the resource description.

---

## Upgrade

1. Stop the server.
2. Replace `plugins/BedlamCore-*.jar` with `BedlamCore-0.10.39.jar`.
3. Start. No config migration required for the 0.10.37 → 0.10.39 delta.
4. Optional smoke: Lobby Setup cancel on Paper 26.2; `/bc token add` / `/bc xp add` as op.

---

## License reminder

Use the **unmodified** official jar freely on your servers. Modification / rebrand / redistributing modified builds needs permission. See [LICENSE](https://github.com/IYanel-DEV/BedlamCore/blob/main/LICENSE).
