# ConradChallenges PlaceholderAPI Documentation

This document lists all available PlaceholderAPI placeholders provided by ConradChallenges.

**Prefix:** All placeholders use `%conradchallenges_...%`

**Note:** All placeholders are player-specific and will return data for the player viewing them (in scoreboards, chat, etc.).

**Special Keyword:** You can use `active` as a challenge ID in any placeholder that requires a challenge ID. This will automatically use the player's current active challenge. If the player is not in a challenge, it will return `N/A` or `none` (depending on the placeholder).

---

## Table of Contents

- [Placeholder Reference Table](#placeholder-reference-table)
- [Detailed Descriptions](#detailed-descriptions)
- [Examples](#examples)
- [Compatibility](#compatibility)

---

## Placeholder Reference Table

| Placeholder | Description | Returns | Parameters |
|------------|-------------|---------|------------|
| `%conradchallenges_active%` | Current challenge alias/ID the player is in | Challenge alias (if configured) or challenge ID, or `none` | None |
| `%conradchallenges_active_name%` | Display name of current challenge | Challenge name or `none` | None |
| `%conradchallenges_besttime_<id>%` | Player's best completion time (seconds) | Time in seconds or `N/A` | `<id>` = challenge ID or `active` |
| `%conradchallenges_besttime_formatted_<id>%` | Player's best completion time (formatted) | Formatted time (e.g., `1m 30s`) or `N/A` | `<id>` = challenge ID or `active` |
| `%conradchallenges_completed_<id>%` | Whether player has completed challenge | `true` or `false` | `<id>` = challenge ID or `active` |
| `%conradchallenges_completions_<id>%` | Number of times player completed challenge | Number (0 or 1) | `<id>` = challenge ID or `active` |
| `%conradchallenges_cooldown_<id>%` | Remaining cooldown time (seconds) | Seconds remaining or `0` | `<id>` = challenge ID or `active` |
| `%conradchallenges_cooldown_formatted_<id>%` | Remaining cooldown time (formatted) | Formatted time or `Ready` | `<id>` = challenge ID or `active` |
| `%conradchallenges_top_<id>_<pos>%` | Player name at leaderboard position | Player name or `N/A` | `<id>` = challenge ID or `active`, `<pos>` = position (1-based) |
| `%conradchallenges_top_time_<id>_<pos>%` | Completion time at leaderboard position (seconds) | Time in seconds or `N/A` | `<id>` = challenge ID or `active`, `<pos>` = position (1-based) |
| `%conradchallenges_top_time_formatted_<id>_<pos>%` | Completion time at leaderboard position (formatted) | Formatted time or `N/A` | `<id>` = challenge ID or `active`, `<pos>` = position (1-based) |
| `%conradchallenges_rank_<id>%` | Player's rank in challenge leaderboard | Rank number or `N/A` | `<id>` = challenge ID or `active` |
| `%conradchallenges_leaderboard_count_<id>%` | Total entries in challenge leaderboard | Number of entries | `<id>` = challenge ID or `active` |
| `%conradchallenges_total_completions%` | Total unique challenges completed | Number of challenges | None |
| `%conradchallenges_world_alias%` | World alias for player's current world | World alias (with color codes) or world name | None |
| `%conradchallenges_challenge_alias_<id>%` | Challenge alias for a specific challenge ID | Challenge alias (with color codes) or challenge ID | `<id>` = challenge ID or `active` |
| `%conradchallenges_lives%` | Remaining lives for player in their current challenge | Number of lives or `0` | None |
| `%conradchallenges_besttime_active%` | Player's best completion time for active challenge (seconds) | Time in seconds or `N/A` | None |
| `%conradchallenges_besttime_formatted_active%` | Player's best completion time for active challenge (formatted) | Formatted time or `N/A` | None |
| `%conradchallenges_completed_active%` | Whether player has completed active challenge | `true` or `false` | None |
| `%conradchallenges_completions_active%` | Number of times player completed active challenge | Number (0 or 1) | None |
| `%conradchallenges_cooldown_active%` | Remaining cooldown time for active challenge (seconds) | Seconds remaining or `0` | None |
| `%conradchallenges_cooldown_formatted_active%` | Remaining cooldown time for active challenge (formatted) | Formatted time or `Ready` | None |
| `%conradchallenges_top_active_<pos>%` | Player name at leaderboard position for active challenge | Player name or `N/A` | `<pos>` = position (1-based) |
| `%conradchallenges_top_time_active_<pos>%` | Completion time at leaderboard position for active challenge (seconds) | Time in seconds or `N/A` | `<pos>` = position (1-based) |
| `%conradchallenges_top_time_formatted_active_<pos>%` | Completion time at leaderboard position for active challenge (formatted) | Formatted time or `N/A` | `<pos>` = position (1-based) |
| `%conradchallenges_rank_active%` | Player's rank in active challenge leaderboard | Rank number or `N/A` | None |
| `%conradchallenges_leaderboard_count_active%` | Total entries in active challenge leaderboard | Number of entries | None |

---

## Detailed Descriptions

### Player Status

#### `%conradchallenges_active%`
**Description:** Returns the challenge alias (if configured) or challenge ID of the challenge the player is currently in.

**Returns:**
- Challenge alias (with color codes) if configured in `config.yml` under `challenge-aliases`
- Challenge ID (e.g., `dungeon1`, `parkour_challenge`) if player is in a challenge but no alias is configured
- `none` if player is not in any challenge

**Note:** This placeholder now returns the challenge alias instead of the raw challenge ID if an alias is configured. Use challenge aliases to display custom names with color codes in scoreboards and other displays.

**Example:**
```
%conradchallenges_active%
→ "Dungeon Challenge" (if alias is configured as "&6&lDungeon Challenge")
→ "dungeon1" (if in challenge but no alias configured)
→ "none" (if not in challenge)
```

---

#### `%conradchallenges_active_name%`
**Description:** Returns the display name (book title) of the challenge the player is currently in.

**Returns:**
- Challenge display name (book title) if player is in a challenge
- `none` if player is not in any challenge

**Example:**
```
%conradchallenges_active_name%
→ "Dungeon Challenge" (if in challenge)
→ "none" (if not in challenge)
```

---

### Best Times

#### `%conradchallenges_besttime_<challengeid>%`
**Description:** Returns the player's best completion time for a specific challenge in seconds.

**Parameters:**
- `<challengeid>` - The ID of the challenge (e.g., `dungeon1`, `parkour_challenge`) or `active` to use the player's current active challenge

**Returns:**
- Time in seconds (e.g., `90`, `125`) if the player has completed the challenge
- `N/A` if the player has not completed the challenge or is not in a challenge (when using `active`)

**Example:**
```
%conradchallenges_besttime_dungeon1%
→ "90" (90 seconds)
→ "N/A" (not completed)

%conradchallenges_besttime_active%
→ "90" (best time in current challenge)
→ "N/A" (not in a challenge or not completed)
```

---

#### `%conradchallenges_besttime_formatted_<challengeid>%`
**Description:** Returns the player's best completion time for a specific challenge in a human-readable format.

**Parameters:**
- `<challengeid>` - The ID of the challenge or `active` to use the player's current active challenge

**Returns:**
- Formatted time (e.g., `1m 30s`, `45s`, `2m`) if the player has completed the challenge
- `N/A` if the player has not completed the challenge or is not in a challenge (when using `active`)

**Format:**
- Less than 60 seconds: `45s`
- Exactly minutes: `2m`
- Minutes and seconds: `1m 30s`

**Example:**
```
%conradchallenges_besttime_formatted_dungeon1%
→ "1m 30s"
→ "45s"
→ "N/A" (not completed)

%conradchallenges_besttime_formatted_active%
→ "1m 30s" (best time in current challenge)
→ "N/A" (not in a challenge or not completed)
```

---

### Completion Status

#### `%conradchallenges_completed_<challengeid>%`
**Description:** Returns whether the player has ever completed a specific challenge.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- `true` if the player has completed the challenge
- `false` if the player has not completed the challenge

**Example:**
```
%conradchallenges_completed_dungeon1%
→ "true"
→ "false"
```

---

#### `%conradchallenges_completions_<challengeid>%`
**Description:** Returns the number of times the player has completed a specific challenge.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Number of completions (currently `0` or `1`)

**Note:** Currently tracks only if a player has ever completed a challenge. Future versions may track multiple completions.

**Example:**
```
%conradchallenges_completions_dungeon1%
→ "1" (completed)
→ "0" (not completed)
```

---

### Cooldowns

#### `%conradchallenges_cooldown_<challengeid>%`
**Description:** Returns the remaining cooldown time in seconds for a specific challenge.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Remaining cooldown in seconds (e.g., `300`, `45`) if cooldown is active
- `0` if cooldown has expired or challenge has no cooldown

**Example:**
```
%conradchallenges_cooldown_dungeon1%
→ "300" (5 minutes remaining)
→ "0" (cooldown expired or no cooldown)
```

---

#### `%conradchallenges_cooldown_formatted_<challengeid>%`
**Description:** Returns the remaining cooldown time in a human-readable format.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Formatted time (e.g., `5m 30s`, `45s`) if cooldown is active
- `Ready` if cooldown has expired or challenge has no cooldown

**Format:**
- Less than 60 seconds: `45s`
- Exactly minutes: `5m`
- Minutes and seconds: `5m 30s`

**Example:**
```
%conradchallenges_cooldown_formatted_dungeon1%
→ "5m 30s" (cooldown active)
→ "Ready" (cooldown expired)
```

---

### Leaderboards

#### `%conradchallenges_top_<challengeid>_<position>%`
**Description:** Returns the player name at a specific position in the challenge leaderboard.

**Parameters:**
- `<challengeid>` - The ID of the challenge or `active` to use the player's current active challenge
- `<position>` - The leaderboard position (1 = first place, 2 = second place, etc.)

**Returns:**
- Player name at the specified position
- `N/A` if the position doesn't exist, challenge has no completions, or player is not in a challenge (when using `active`)

**Example:**
```
%conradchallenges_top_dungeon1_1%
→ "PlayerName" (first place)
→ "N/A" (no leaderboard entries)

%conradchallenges_top_active_1%
→ "PlayerName" (first place in current challenge)
→ "N/A" (not in a challenge or no leaderboard entries)
```

---

#### `%conradchallenges_top_time_<challengeid>_<position>%`
**Description:** Returns the completion time at a specific position in the challenge leaderboard (in seconds).

**Parameters:**
- `<challengeid>` - The ID of the challenge
- `<position>` - The leaderboard position

**Returns:**
- Time in seconds (e.g., `90`, `125`) at the specified position
- `N/A` if the position doesn't exist

**Example:**
```
%conradchallenges_top_time_dungeon1_1%
→ "90" (90 seconds - first place time)
→ "N/A" (position doesn't exist)
```

---

#### `%conradchallenges_top_time_formatted_<challengeid>_<position>%`
**Description:** Returns the completion time at a specific position in the challenge leaderboard in a human-readable format.

**Parameters:**
- `<challengeid>` - The ID of the challenge
- `<position>` - The leaderboard position

**Returns:**
- Formatted time (e.g., `1m 30s`, `45s`) at the specified position
- `N/A` if the position doesn't exist

**Example:**
```
%conradchallenges_top_time_formatted_dungeon1_1%
→ "1m 30s" (first place time)
→ "N/A" (position doesn't exist)
```

---

#### `%conradchallenges_rank_<challengeid>%`
**Description:** Returns the player's current rank in a challenge leaderboard.

**Parameters:**
- `<challengeid>` - The ID of the challenge or `active` to use the player's current active challenge

**Returns:**
- Player's rank (1-based, where 1 = first place) if the player has completed the challenge
- `N/A` if the player has not completed the challenge or is not in a challenge (when using `active`)

**Example:**
```
%conradchallenges_rank_dungeon1%
→ "3" (player is in 3rd place)
→ "N/A" (player hasn't completed)

%conradchallenges_rank_active%
→ "3" (player is in 3rd place in current challenge)
→ "N/A" (not in a challenge or hasn't completed)
```

---

#### `%conradchallenges_leaderboard_count_<challengeid>%`
**Description:** Returns the total number of entries in a challenge leaderboard.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Number of players who have completed the challenge (e.g., `15`, `0`)

**Example:**
```
%conradchallenges_leaderboard_count_dungeon1%
→ "15" (15 players have completed)
→ "0" (no completions yet)
```

---

### Statistics

#### `%conradchallenges_total_completions%`
**Description:** Returns the total number of challenge completions across all challenges.

**Returns:**
- Total number of unique challenges the player has completed

**Note:** Currently counts unique challenges completed (one per challenge). Future versions may track multiple completions per challenge.

**Example:**
```
%conradchallenges_total_completions%
→ "5" (player has completed 5 different challenges)
→ "0" (player hasn't completed any)
```

---

### World Information

#### `%conradchallenges_world_alias%`
**Description:** Returns the world alias for the player's current world. World aliases are configured in `config.yml` under the `world-aliases` section and allow you to replace world names with custom display names (with color codes).

**Returns:**
- World alias (with color codes applied) if configured in `config.yml`
- World name if no alias is configured
- `Unknown` if player is offline or world cannot be determined

**Configuration:**
World aliases are configured in `config.yml`:
```yaml
world-aliases:
  world: "&aSurvival"
  world_nether: "&aSurvival"
  world_the_end: "&aSurvival"
  hub_world: "&cAdmin Hub"
```

**Example:**
```
%conradchallenges_world_alias%
→ "Survival" (if world alias is configured as "&aSurvival")
→ "world" (if no alias is configured)
→ "Unknown" (if player is offline)
```

**Note:** Color codes use `&` format (e.g., `&a` for green, `&c` for red). The placeholder automatically translates these to Minecraft color codes.

---

### Challenge Information

#### `%conradchallenges_challenge_alias_<challengeid>%`
**Description:** Returns the challenge alias for a specific challenge ID. Challenge aliases are configured in `config.yml` under the `challenge-aliases` section and allow you to replace challenge IDs with custom display names (with color codes).

**Parameters:**
- `<challengeid>` - The ID of the challenge or `active` to use the player's current active challenge

**Returns:**
- Challenge alias (with color codes applied) if configured in `config.yml`
- Challenge ID if no alias is configured
- `N/A` if challenge doesn't exist or player is not in a challenge (when using `active`)

**Configuration:**
Challenge aliases are configured in `config.yml`:
```yaml
challenge-aliases:
  dungeon1: "&6&lDungeon Challenge"
  dungeon2: "&c&lBoss Arena"
  parkour_easy: "&aEasy Parkour"
```

**Example:**
```
%conradchallenges_challenge_alias_dungeon1%
→ "Dungeon Challenge" (if alias is configured as "&6&lDungeon Challenge")
→ "dungeon1" (if no alias is configured)

%conradchallenges_challenge_alias_active%
→ "Dungeon Challenge" (alias for current active challenge)
→ "N/A" (not in a challenge)
```

**Note:** 
- Color codes use `&` format (e.g., `&6` for gold, `&c` for red, `&l` for bold). The placeholder automatically translates these to Minecraft color codes.
- The `%conradchallenges_active%` placeholder now returns the challenge alias (if configured) instead of the raw challenge ID.

---

### Lives System

#### `%conradchallenges_lives%`
**Description:** Returns the remaining lives for the player in their current challenge. Lives are based on the difficulty tier selected when entering the challenge.

**Returns:**
- Number of remaining lives (e.g., `3`, `2`, `1`) if the player is in a challenge
- `0` if the player is not in a challenge or has no lives remaining

**Note:** Lives are configured per difficulty tier in `config.yml` under `lives-per-tier`. When a player dies with lives remaining, they keep their inventory and respawn in the challenge. When lives run out, they're removed from the challenge and drop inventory.

**Example:**
```
%conradchallenges_lives%
→ "3" (3 lives remaining in Easy tier challenge)
→ "1" (1 life remaining in Hard tier challenge)
→ "0" (not in a challenge or no lives remaining)
```

---

## Examples

### Scoreboard Example

**Using specific challenge ID:**
```
Challenge: %conradchallenges_active_name%
Best Time: %conradchallenges_besttime_formatted_dungeon1%
Rank: #%conradchallenges_rank_dungeon1%
Cooldown: %conradchallenges_cooldown_formatted_dungeon1%
```

**Using `active` keyword (automatically uses current challenge):**
```
Challenge: %conradchallenges_active_name%
Best Time: %conradchallenges_besttime_formatted_active%
Rank: #%conradchallenges_rank_active%
Cooldown: %conradchallenges_cooldown_formatted_active%
Top Leaderboard:
#1: %conradchallenges_top_active_1% - %conradchallenges_top_time_formatted_active_1%
#2: %conradchallenges_top_active_2% - %conradchallenges_top_time_formatted_active_2%
#3: %conradchallenges_top_active_3% - %conradchallenges_top_time_formatted_active_3%
```

### Leaderboard Display
```
#1: %conradchallenges_top_dungeon1_1% - %conradchallenges_top_time_formatted_dungeon1_1%
#2: %conradchallenges_top_dungeon1_2% - %conradchallenges_top_time_formatted_dungeon1_2%
#3: %conradchallenges_top_dungeon1_3% - %conradchallenges_top_time_formatted_dungeon1_3%
```

### Player Stats
```
Challenges Completed: %conradchallenges_total_completions%
Current Challenge: %conradchallenges_active_name%
World: %conradchallenges_world_alias%
```

---

## Notes

- All placeholders are **player-specific** - they return data for the player viewing them
- Placeholders return `N/A` when data is not available
- Time formatting uses `m` for minutes and `s` for seconds
- Leaderboard positions are **1-based** (1 = first place, 2 = second place, etc.)
- Cooldowns return `0` or `Ready` when expired or not set
- Challenge IDs are case-sensitive and must match exactly
- **Special `active` keyword**: Use `active` as the challenge ID in any placeholder to automatically use the player's current active challenge. If the player is not in a challenge, placeholders will return `N/A` or `none` (depending on the placeholder type)

## Compatibility

These placeholders work with:
- PlaceholderAPI
- AJSL Leaderboards
- Any plugin that supports PlaceholderAPI placeholders
- Scoreboards, chat, holograms, and other PlaceholderAPI-compatible displays

---

**Plugin:** ConradChallenges v3.0.5  
**PlaceholderAPI Expansion:** Automatically registered when PlaceholderAPI is installed

---

## Using the `active` Keyword

Instead of hardcoding a specific challenge ID, you can use `active` as a special keyword that automatically uses the player's current active challenge. This is especially useful for scoreboards that should display information about whatever challenge the player is currently in.

### When to Use `active`

- **Dynamic scoreboards** that show data for the current challenge
- **Leaderboards** that automatically switch based on the active challenge
- **Player stats** that update based on the current challenge

### Examples with `active`

```
# Leaderboard for current challenge
%conradchallenges_top_active_1%
%conradchallenges_top_time_formatted_active_1%

# Player stats for current challenge
%conradchallenges_rank_active%
%conradchallenges_besttime_formatted_active%

# Cooldown for current challenge
%conradchallenges_cooldown_formatted_active%
```

### Behavior

- If the player **is in a challenge**: `active` resolves to that challenge's ID and returns data for it
- If the player **is not in a challenge**: Returns `N/A` or `none` (depending on the placeholder type)

This allows you to create a single scoreboard configuration that works for all challenges without needing separate scoreboards for each challenge ID.
