# Changelog

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

