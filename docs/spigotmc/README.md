# SpigotMC listing kit

Paste-ready copy for [SpigotMC.org](https://www.spigotmc.org/) resource pages. Version target: **0.10.39** (`build.gradle.kts`).

## Files

| File | Use |
|------|-----|
| `DESCRIPTION.md` | Human-readable source / mirror |
| `DESCRIPTION.bbcode` | Paste into the resource **Description** field |
| `RELEASE_NOTES.md` | Human-readable update post |
| `RELEASE_NOTES.bbcode` | Paste into the update / version notes field |
| `images/` | Spigot-safe compressed assets (GitHub raw URLs in BBCode) |
| `icon-96.png` | Resource icon only (exact 96×96) |

## Publish in 3 steps

1. **Upload resource icon** — Spigot requires exactly **96×96**. Use `docs/spigotmc/icon-96.png` (same file: `docs/brand/spigotmc-icon-96.png`). Do **not** upload the full purple-bed logo as the resource icon (Spigot rejects oversized icons).
2. **Paste BBCode as-is** — open `DESCRIPTION.bbcode` and paste into the Description field. All `[IMG]` tags already use **GitHub raw** URLs under `docs/spigotmc/images/` — no Spigot “Upload Images” step is required for the gallery. Preview before saving.
3. **Attach the jar** — `BedlamCore-0.10.39.jar` from [GitHub Releases](https://github.com/IYanel-DEV/BedlamCore/releases) (or your tested local build). Set version **0.10.39**, link the GitHub repo, and paste `RELEASE_NOTES.bbcode` into the update changelog.

## BBCode tips

- Spigot XenForo does **not** render `[HR]` — this kit uses a muted unicode line separator instead.
- `[IMG]` must be `[IMG]https://full-url[/IMG]` (empty / placeholder tags show as literal text).
- Image pattern: `https://raw.githubusercontent.com/IYanel-DEV/BedlamCore/main/docs/spigotmc/images/<file>`
- Spigot supports `[SPOILER="title"]…[/SPOILER]`, `[LIST]`, `[IMG]`, `[URL]`, `[COLOR]`, `[SIZE]`, `[CENTER]`, `[ICODE]`.
- Keep the license line: use freely **unmodified**; modification needs permission.

## Brand assets

| File | Use on Spigot |
|------|----------------|
| `docs/spigotmc/icon-96.png` | **Upload resource icon only** (exact 96×96) |
| `docs/brand/spigotmc-icon-96.png` | Same icon (brand folder copy) |
| `docs/spigotmc/images/*` | Description images via GitHub raw (compressed) |
| `docs/brand/logo-bedlamcore*.png` | Full-res source art — not for Spigot icon |

- Full-res gameplay stills remain in `docs/showcase/` for GitHub / other hosts.
