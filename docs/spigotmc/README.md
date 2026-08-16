# SpigotMC listing kit

Paste-ready copy for [SpigotMC.org](https://www.spigotmc.org/) resource pages. Version target: **0.10.39** (`build.gradle.kts`).

## Files

| File | Use |
|------|-----|
| `DESCRIPTION.md` | Human-readable source / mirror |
| `DESCRIPTION.bbcode` | Paste into the resource **Description** field |
| `RELEASE_NOTES.md` | Human-readable update post |
| `RELEASE_NOTES.bbcode` | Paste into the update / version notes field |

## Publish in 3 steps

1. **Upload images** on the Spigot resource (gallery + description). Use the order in `DESCRIPTION.md` (banner → logo → showcase stills). Prefer `docs/brand/banner-bedlamcore.png` as the hero and `docs/brand/logo-bedlamcore.png` as the resource icon if Spigot asks for one.
2. **Paste BBCode** — open `DESCRIPTION.bbcode`, replace every `UPLOAD: path` inside `[IMG]…[/IMG]` with the CDN URL Spigot gives you after upload (or attach images via the editor and let Spigot insert `[IMG]` tags). Same idea for `RELEASE_NOTES.bbcode` when posting an update.
3. **Attach the jar** — `BedlamCore-0.10.39.jar` from [GitHub Releases](https://github.com/IYanel-DEV/BedlamCore/releases) (or your tested local build). Set version **0.10.39**, link the GitHub repo, and paste release notes into the update changelog.

## BBCode tips

- Spigot supports `[SPOILER="title"]…[/SPOILER]`, `[LIST]`, `[IMG]`, `[URL]`, `[COLOR]`, `[SIZE]`, `[CENTER]`, `[ICODE]`.
- Do not leave `UPLOAD:` placeholders live — they are markers for you.
- Keep the license line: use freely **unmodified**; modification needs permission.

## Brand assets

- Banners / logos: `docs/brand/`
- Gameplay stills: `docs/showcase/`
