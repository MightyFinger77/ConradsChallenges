# Changelog

## Version 2.0.3

### Features
- **Challenge Regeneration System**: Challenges can now automatically regenerate their area when new players enter. Blocks, chests, and other changes are restored to their original state
- **WorldEdit Integration**: Regeneration areas can be set using WorldEdit/FastAsyncWorldEdit selections (`//pos1` and `//pos2`) or manually by setting two corner coordinates
- **Edit Mode**: Admins can enter edit mode for challenges to make modifications. Changes can be saved or cancelled, with automatic backup/restore functionality
- **Challenge Lockdown**: Challenges can be locked down to completely disable them. Locked challenges reject all player attempts with a message instead of queuing

### Improvements
- **Regeneration Handling**: Regeneration correctly handles all block types including directional blocks (signs, fences), containers (chests), and special blocks (spawners, command blocks)
- **Edit Mode Safety**: Challenges in edit mode are locked from players and show a maintenance message
- **Queue Management**: Players attempting to start locked-down challenges are not added to the queue

### Commands
- `/conradchallenges challenge setregenerationarea <id>` - Set regeneration area using WorldEdit selection or manual corners
- `/conradchallenges challenge clearregenerationarea <id>` - Clear regeneration area
- `/conradchallenges challenge captureregeneration <id>` - Manually capture initial state
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

