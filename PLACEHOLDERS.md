# ConradChallenges PlaceholderAPI Documentation

This document lists all available PlaceholderAPI placeholders provided by ConradChallenges.

**Prefix:** All placeholders use `%conradchallenges_...%`

**Note:** All placeholders are player-specific and will return data for the player viewing them (in scoreboards, chat, etc.).

---

## Table of Contents

- [Player Status](#player-status)
- [Best Times](#best-times)
- [Completion Status](#completion-status)
- [Cooldowns](#cooldowns)
- [Leaderboards](#leaderboards)
- [Statistics](#statistics)
- [Examples](#examples)

---

## Player Status

### `%conradchallenges_active%`
**Description:** Returns the ID of the challenge the player is currently in.

**Returns:**
- Challenge ID (e.g., `dungeon1`, `parkour_challenge`) if player is in a challenge
- `none` if player is not in any challenge

**Example:**
```
%conradchallenges_active%
→ "dungeon1" (if in challenge)
→ "none" (if not in challenge)
```

---

### `%conradchallenges_active_name%`
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

## Best Times

### `%conradchallenges_besttime_<challengeid>%`
**Description:** Returns the player's best completion time for a specific challenge in seconds.

**Parameters:**
- `<challengeid>` - The ID of the challenge (e.g., `dungeon1`, `parkour_challenge`)

**Returns:**
- Time in seconds (e.g., `90`, `125`) if the player has completed the challenge
- `N/A` if the player has not completed the challenge

**Example:**
```
%conradchallenges_besttime_dungeon1%
→ "90" (90 seconds)
→ "N/A" (not completed)
```

---

### `%conradchallenges_besttime_formatted_<challengeid>%`
**Description:** Returns the player's best completion time for a specific challenge in a human-readable format.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Formatted time (e.g., `1m 30s`, `45s`, `2m`) if the player has completed the challenge
- `N/A` if the player has not completed the challenge

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
```

---

## Completion Status

### `%conradchallenges_completed_<challengeid>%`
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

### `%conradchallenges_completions_<challengeid>%`
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

## Cooldowns

### `%conradchallenges_cooldown_<challengeid>%`
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

### `%conradchallenges_cooldown_formatted_<challengeid>%`
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

## Leaderboards

### `%conradchallenges_top_<challengeid>_<position>%`
**Description:** Returns the player name at a specific position in the challenge leaderboard.

**Parameters:**
- `<challengeid>` - The ID of the challenge
- `<position>` - The leaderboard position (1 = first place, 2 = second place, etc.)

**Returns:**
- Player name at the specified position
- `N/A` if the position doesn't exist or challenge has no completions

**Example:**
```
%conradchallenges_top_dungeon1_1%
→ "PlayerName" (first place)
→ "N/A" (no leaderboard entries)
```

---

### `%conradchallenges_top_time_<challengeid>_<position>%`
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

### `%conradchallenges_top_time_formatted_<challengeid>_<position>%`
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

### `%conradchallenges_rank_<challengeid>%`
**Description:** Returns the player's current rank in a challenge leaderboard.

**Parameters:**
- `<challengeid>` - The ID of the challenge

**Returns:**
- Player's rank (1-based, where 1 = first place) if the player has completed the challenge
- `N/A` if the player has not completed the challenge

**Example:**
```
%conradchallenges_rank_dungeon1%
→ "3" (player is in 3rd place)
→ "N/A" (player hasn't completed)
```

---

### `%conradchallenges_leaderboard_count_<challengeid>%`
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

## Statistics

### `%conradchallenges_total_completions%`
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

### `%conradchallenges_challenges_completed%`
**Description:** Returns the number of unique challenges the player has completed.

**Returns:**
- Number of unique challenges completed

**Note:** This is currently the same as `total_completions` since we track one completion per challenge.

**Example:**
```
%conradchallenges_challenges_completed%
→ "5" (player has completed 5 different challenges)
→ "0" (player hasn't completed any)
```

---

## Examples

### Scoreboard Example
```
Challenge: %conradchallenges_active_name%
Best Time: %conradchallenges_besttime_formatted_dungeon1%
Rank: #%conradchallenges_rank_dungeon1%
Cooldown: %conradchallenges_cooldown_formatted_dungeon1%
```

### Leaderboard Display
```
#1: %conradchallenges_top_dungeon1_1% - %conradchallenges_top_time_formatted_dungeon1_1%
#2: %conradchallenges_top_dungeon1_2% - %conradchallenges_top_time_formatted_dungeon1_2%
#3: %conradchallenges_top_dungeon1_3% - %conradchallenges_top_time_formatted_dungeon1_3%
```

### Player Stats
```
Challenges Completed: %conradchallenges_challenges_completed%
Current Challenge: %conradchallenges_active_name%
```

---

## Notes

- All placeholders are **player-specific** - they return data for the player viewing them
- Placeholders return `N/A` when data is not available
- Time formatting uses `m` for minutes and `s` for seconds
- Leaderboard positions are **1-based** (1 = first place, 2 = second place, etc.)
- Cooldowns return `0` or `Ready` when expired or not set
- Challenge IDs are case-sensitive and must match exactly

## Compatibility

These placeholders work with:
- PlaceholderAPI
- AJSL Leaderboards
- Any plugin that supports PlaceholderAPI placeholders
- Scoreboards, chat, holograms, and other PlaceholderAPI-compatible displays

---

**Plugin:** ConradChallenges v2.0.3  
**PlaceholderAPI Expansion:** Automatically registered when PlaceholderAPI is installed

