# Changelog

## Version 3.0.5

### Features
- **Lives System**: Players now have a limited number of lives based on difficulty tier. When dying with lives remaining, players keep inventory and respawn in the challenge. When lives run out, players are removed and drop inventory normally.
- **Buy-Back System**: New `/buyback` command allows players who ran out of lives to pay a fee and re-enter the challenge with lives reset. Fee is configurable in `config.yml`.
- **Dead Party Member Rewards**: Party members who die (run out of lives) but whose party completes the challenge receive Easy tier rewards with 0.5x multiplier, regardless of the actual difficulty tier selected.
- **MythicMobs Spawner Cooldown Reset**: All MythicMobs spawner cooldowns within a challenge's regeneration area are automatically reset when players complete or exit a challenge. This ensures teams don't have to wait on spawner cooldowns from previous teams.
- **Lives Placeholder**: New PlaceholderAPI placeholder `%conradchallenges_lives%` displays remaining lives for a player in their current challenge.

### Improvements
- **Regeneration Performance Optimization**: Chunk collection phase during regeneration is now async and time-budgeted, preventing TPS drops on large challenge areas. The initial chunk collection that previously blocked the main thread when iterating through millions of blocks is now spread across multiple ticks.
- **Completion Key Consumption**: `/cc` command now consumes completion keys from ALL party members who have them, not just one key from one player.
- **Item Reward Display**: Item rewards are now properly displayed in completion messages along with money rewards.
- **Custom Item Serialization**: HAND rewards now fully preserve custom items including NBT data, CustomModelData (resource pack models/textures), custom names, lore, and all other item properties.

### Fixes
- Fixed missing message keys `admin.reward-money-format` and `admin.reward-money-format-decimal` that caused "Message not found" errors.
- Fixed custom items (with NBT/CustomModelData) not being saved properly - now uses full ItemStack serialization instead of just Material and amount.

### Commands
- `/buyback` - Buy back into a challenge after running out of lives (requires economy plugin)

### Configuration
- `lives-per-tier` - Configure lives per difficulty tier (easy, medium, hard, extreme)
- `buy-back-fee` - Configure the fee amount for buying back into challenges

### Technical Changes
- Added `scheduleChunkCollection()` method to process chunk collection in async batches (10ms per tick)
- Added `resetMythicMobsSpawnersInArea()` method that uses reflection to reset spawner cooldowns and warmup timers
- Added `runRewardCommandsForDeadPartyMember()` method to give dead party members Easy tier rewards with 0.5x multiplier
- ItemStack serialization now uses `ItemStack.serialize()` and `ItemStack.deserialize()` for full NBT preservation
- Lives tracking system with per-player and per-tier configuration
- Dead party member tracking for reward distribution on party completion
- Spawner cooldown reset runs on challenge completion (`/cc`) and exit (`/exits`) commands
- MythicMobs integration uses reflection to maintain compatibility across different API versions

## Version 3.0.4

### Features
- **Area Synchronization Command**: New `/challenge areasync [id]` command that automatically sets the regeneration area to match the challenge area's coordinates and size. Useful for quickly syncing regeneration areas when challenge areas are already set.

### Improvements
- **Set Destination Command**: `/challenge setdestination` can now be run outside of edit mode. This fixes the issue where admins couldn't set the destination before entering edit mode, since edit mode teleports players to the challenge area using the destination.
- **Movement and Teleportation Restrictions**: Enhanced teleportation and movement blocking for players in challenges:
  - Players cannot teleport outside the regeneration area while in a challenge
  - Players cannot walk outside the regeneration area (movement is blocked and they are teleported back)
  - Non-admin players cannot teleport into challenge areas (must use challenge books at GateKeeper)
  - Admins can bypass teleportation restrictions to enter challenge areas

### Commands
- `/challenge areasync [id]` - Synchronize regeneration area with challenge area (copies challenge area coordinates to regeneration area)

### Technical Changes
- Removed command-based teleport blocking in favor of event-based location checks
- Added `onPlayerMove` event handler to prevent players from leaving regeneration areas
- Enhanced `onPlayerTeleport` event handler with more specific blocking logic

## Version 3.0.3

### Features
- **Difficulty Tier Reward System**: Complete reward system overhaul with tier-based rewards and inheritance:
  - Each difficulty tier (easy, medium, hard, extreme) has its own reward table
  - Higher tiers inherit rewards from lower tiers with multipliers applied
  - Easy: Gets only Easy tier rewards (1.0x)
  - Medium: Gets Easy rewards (×1.5) + Medium rewards (×1.0)
  - Hard: Gets Easy (×3.0) + Medium (×2.0) + Hard (×1.0)
  - Extreme: Gets all previous (×9.0, ×6.0, ×3.0) + Extreme (×1.0)
- **RNG Chance System**: All rewards now support chance values (0.0-1.0). Rewards with chance < 1.0 have a random chance to be given, adding grind elements
- **Speed Multiplier Integration**: Speed challenges now use the tier reward system with time-based multipliers:
  - Gold tier (fast): 1.5x multiplier
  - Silver tier (average): 1.0x multiplier (normal)
  - Bronze tier (slow): 0.5x multiplier (half rewards)
  - New record: 3x multiplier (replaces tier multiplier)
- **Top Time Rewards**: New reward type exclusively for players who set new top times. Gets 3x multiplier on top of difficulty multiplier
- **Reward Types**: Three reward types supported:
  - `hand`: Uses item currently in player's hand
  - `item`: Specifies material and amount
  - `command`: Custom command (like old addreward)

### Improvements
- **Speed Tier Rewards Removed**: Old speed tier rewards (from `completion.tiers.reward-commands`) are no longer used. Speed tiers now only determine multipliers, not rewards
- **Fallback Rewards Enhanced**: Fallback rewards now support chance values and work alongside tier rewards (used if no tier rewards are set)
- **Tab Completion**: All new reward commands have full tab completion for tiers, types, and materials
- **Edit Mode Disconnect Restoration**: Players who disconnect while in edit mode are automatically returned to their original location (before entering edit mode) when they reconnect, matching the behavior of `/challenge save` or `/challenge cancel`

### Commands
- `/challenge addtierreward [id] <tier> <hand|item|command> [args] [chance]` - Add reward to a difficulty tier
  - `tier`: easy, medium, hard, or extreme
  - `hand`: Uses item in hand
  - `item <material> [amount]`: Specifies item type
  - `command <command>`: Custom command
  - `chance`: 0.0-1.0 (default: 1.0 = 100%)
- `/challenge cleartierrewards [id] <tier>` - Clear all rewards for a specific tier
- `/challenge listtierrewards [id] [tier]` - List tier rewards (all tiers or specific tier)
- `/challenge addtoptimereward [id] <hand|item|command> [args] [chance]` - Add reward for new top times only
- `/challenge addreward [id] <command> [chance]` - Updated to support chance parameter (default: 1.0)

### Technical Changes
- New `TierReward` class with type (HAND/ITEM/COMMAND), chance, and reward data
- New `RewardWithChance` class for fallback rewards with chance support
- Config structure updated to support tier rewards and chance values
- Backward compatible: Old configs still work, automatically migrates reward-commands format

## Version 3.0.2

### Features
- **Challenge Area System**: New challenge area feature separate from regeneration area. Challenge areas define the playable area for challenges and can be set using WorldEdit selections (`//1` and `//2`)
- **Barrier Boxing**: Optional barrier boxing around challenge areas (configurable in config.yml, disabled by default). Only replaces air blocks to create invisible walls
- **Disconnect/Reconnect System**: Players who disconnect while in challenges get a 30-second grace period to reconnect:
  - **Solo Challenges**: Player has 30s to reconnect or challenge is cancelled and queue processes next player
  - **Party Challenges**: Disconnected players have 30s to reconnect. Challenge only cancels if entire party disconnects and doesn't reconnect within 30s
  - Players reconnecting within grace period are restored to challenge. Players reconnecting after grace period are teleported to spawn

### Improvements
- **Save Command Optimization**: Save command now only recaptures regeneration area state, not challenge area. Challenge area is only captured when initially set, reducing unnecessary server load
- **Teleport Logic**: 
  - Edit mode: Players can teleport anywhere within the challenge world
  - In-challenge: Players can teleport within challenge area (both from and to must be in area to prevent `/tpask` abuse)
- **Area Overlap Protection**: Prevents challenge areas and regeneration areas from overlapping with OTHER challenges' areas. Same challenge's areas can overlap (challenge area can overlap with its own regen area)
- **Explosion Protection**: Blocks within challenge areas are protected from explosions (creepers, TNT, etc.). Only blocks are protected, not entities
- **Command Blocking Fix**: Fixed `/spawnentity` and other commands starting with `/spawn` being incorrectly blocked. Now only `/spawn` and `/claimspawn` are blocked

### Fixes
- Fixed players disconnecting in challenges not being cleaned up properly - now properly removes from active challenges and processes queue
- Fixed challenge area being recaptured on every save - now only captured when set via `setchallengearea` command
- Fixed regeneration area not being recaptured on save - save command now properly recaptures regeneration area state

### Commands
- `/conradchallenges challenge setchallengearea <id>` - Set challenge area using WorldEdit selection (`//1` and `//2`)
- `/conradchallenges challenge clearchallengearea <id>` - Clear challenge area
- Added shortened alias via `/challenge <command>`

### Technical Changes
- Challenge area state stored separately and only captured when area is set
- Disconnect tracking system with 30-second grace period and automatic cleanup
- Improved party challenge handling for disconnects - only cancels if all members disconnect

## Version 3.0.0

### Major Improvements
- **Thread-Safe Regeneration**: Complete rewrite of regeneration system to be fully thread-safe. All world access now happens on the main thread with time-budgeted batching to prevent lag
- **Hybrid Block Comparison**: Optimized regeneration comparison phase - uses fast `getType()`/`getBlockData()` for regular blocks, only calls `getState()` for tile entities when needed
- **No Async Warnings**: Eliminated all "Block entity is null, asynchronous access?" warnings from Paper/Purpur by ensuring all block access is on the main thread
- **Improved Capture Performance**: Block state capture now uses time-budgeted batching (3ms per tick) to prevent server lag while maintaining accuracy

### Fixes
- Fixed "Already scheduled" error when capturing initial state for large areas
- Fixed regeneration area not clearing properly - now clears initial states and regeneration status
- Fixed edit mode allowing admins to edit locked-down challenges (now explicitly allowed)
- Fixed completion messages not showing after capturing initial state
- Fixed teleport blocking preventing players from entering challenges

### Technical Changes
- Regeneration comparison phase moved from async to main thread with time budgeting
- Block capture uses recursive helper method to avoid BukkitRunnable scheduling conflicts
- All tile entity access (chests, spawners, signs) now safely handled on main thread
- Improved error handling for block entity access during async operations

## Version 2.0.3

### Features
- **Challenge Regeneration System**: Challenges can now automatically regenerate their area when new players enter. Blocks, chests, and other changes are restored to their original state
- **WorldEdit Integration**: Regeneration areas can be set using WorldEdit/FastAsyncWorldEdit selections (`//pos1` and `//pos2`) or manually by setting two corner coordinates
- **Auto-Extend Y Range**: Regeneration can automatically extend from bedrock to build height, ignoring corner Y coordinates. Perfect for full-height challenge areas
- **Asynchronous Regeneration**: Regeneration runs asynchronously with batched block updates to prevent server lag, even for large areas
- **Edit Mode**: Admins can enter edit mode for challenges to make modifications. Changes can be saved or cancelled, with automatic backup/restore functionality
- **Challenge Lockdown**: Challenges can be locked down to completely disable them. Locked challenges reject all player attempts with a message instead of queuing
- **Difficulty Tiers**: Players can select difficulty tiers (Easy/Medium/Hard/Extreme) when accepting challenges, which modify MythicMobs spawn levels
- **Difficulty Reward Multipliers**: Reward amounts are multiplied based on selected difficulty tier, with configurable multipliers that stack (e.g., Medium = Easy × 1.5, Hard = Medium × 2.0)
- **PlaceholderAPI Integration**: Custom placeholders for challenge data, times, completions, cooldowns, and leaderboards
- **New Record Announcements**: Server-wide announcements when players achieve new fastest times

### Improvements
- **Regeneration Handling**: Regeneration correctly handles all block types including directional blocks (signs, fences), containers (chests), and special blocks (spawners, command blocks)
- **Edit Mode Safety**: Challenges in edit mode are locked from players and show a maintenance message
- **Queue Management**: Players attempting to start locked-down challenges are not added to the queue

### Commands
- `/conradchallenges challenge setregenerationarea <id>` - Set regeneration area using WorldEdit selection or manual corners
- `/conradchallenges challenge clearregenerationarea <id>` - Clear regeneration area
- `/conradchallenges challenge captureregeneration <id>` - Manually capture initial state
- `/conradchallenges challenge setautoregenerationy <id> [true|false]` - Toggle auto-extend Y range (bedrock to build height)
- `/conradchallenges challenge edit <id>` - Enter edit mode for a challenge
- `/conradchallenges challenge save` - Save changes and exit edit mode
- `/conradchallenges challenge cancel` - Cancel edits and restore previous state
- `/conradchallenges challenge lockdown <id>` - Lock down a challenge
- `/conradchallenges challenge unlockdown <id>` - Unlock a challenge

## Version 2.0.1

### Features
- **Customizable Messages**: All plugin messages moved to `messages.yml` for easy customization
- **Countdown Display Options**: Added `/countdown` command to choose display method (title, actionbar, bossbar, chat)
- **Player Preferences**: Countdown display preferences persist across sessions

### Improvements
- **Config Migration**: Automatic config migration preserves user settings when updating
- **Config Structure**: All challenge fields now saved consistently, preventing malformed configs
- **Party Size Limits**: Party size limits now enforced when accepting party invites
- **Teleport Blocking**: Players in challenges cannot teleport out (must use `/exit` or `/cc`)
- **Death Handling**: Players who die are immediately removed from challenges and returned to spawn

### Fixes
- Fixed config migration duplicating example challenges
- Fixed inconsistent challenge config structure
- Fixed party size limit not being enforced during `/accept`

