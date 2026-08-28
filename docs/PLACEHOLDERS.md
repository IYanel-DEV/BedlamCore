# PlaceholderAPI — `%bedlamcore_*%`

Install [PlaceholderAPI](https://www.spigotmc.org/resources/6245/); BedlamCore auto-registers the
`bedlamcore` expansion on enable (soft dependency — nothing breaks without PAPI). All values read the
in-memory stats cache, so they are async-safe. Unknown placeholders return an empty string.

## Player stats
| Placeholder | Meaning |
|---|---|
| `%bedlamcore_tokens%` | Cosmetic tokens |
| `%bedlamcore_xp%` / `%bedlamcore_level%` | Total XP / derived level |
| `%bedlamcore_level_progress%` | Progress into the current level, e.g. `68%` |
| `%bedlamcore_kills%` `%bedlamcore_deaths%` | Kills / deaths |
| `%bedlamcore_final_kills%` `%bedlamcore_final_deaths%` | Final kills / final deaths |
| `%bedlamcore_wins%` `%bedlamcore_losses%` | Wins / losses |
| `%bedlamcore_beds%` `%bedlamcore_beds_lost%` | Beds broken / lost |
| `%bedlamcore_games%` | Games played |
| `%bedlamcore_winstreak%` `%bedlamcore_best_winstreak%` | Current / best winstreak |
| `%bedlamcore_kdr%` `%bedlamcore_fkdr%` | Ratios (`0.00` when no deaths) |
| `%bedlamcore_prestige_color%` | Equipped prestige colour as an `&`-code, or blank |
| `%bedlamcore_prestige_name%` | Equipped prestige name (e.g. `Gold Prestige`), or blank |

## Per-mode (`<mode>` = `solo` `doubles` `trios` `quads`)
`%bedlamcore_wins_<mode>%` `%bedlamcore_kills_<mode>%` `%bedlamcore_games_<mode>%` `%bedlamcore_beds_<mode>%`

## Live server counts
`%bedlamcore_player_count%` — players online ·
`%bedlamcore_waiting_<mode>%` — players in a waiting/countdown lobby for that mode
