# ConradChallenges - Challenge Setup Guide

A comprehensive guide for setting up and managing challenges in ConradChallenges.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Creating a Challenge](#creating-a-challenge)
- [Basic Challenge Setup](#basic-challenge-setup)
- [Completion Types](#completion-types)
- [Rewards System](#rewards-system)
- [Regeneration System](#regeneration-system)
- [Edit Mode](#edit-mode)
- [Difficulty Tiers](#difficulty-tiers)
- [Challenge Management](#challenge-management)
- [Quick Reference](#quick-reference)

---

## Prerequisites

- **Permission**: You need `conradchallenges.admin` permission
- **WorldEdit/FastAsyncWorldEdit** (optional): For easier regeneration area setup
- **MythicMobs** (optional): For difficulty tier mob level modifications

---

## Creating a Challenge

### Step 1: Create the Challenge
```
/conradchallenges challenge create <id>
```
Example: `/conradchallenges challenge create dungeon1`

This creates a new challenge with the specified ID. The ID is used in all commands to reference this challenge.

---

## Basic Challenge Setup

### Step 2: Set the Challenge Book
1. Write a book in-game with a title
2. Hold the book in your main hand
3. Run:
```
/conradchallenges challenge setbook [id]
```
Example: `/conradchallenges challenge setbook dungeon1`

**Note**: In edit mode, you can omit the `[id]` - it will use the challenge you're editing.

### Step 3: Set the Destination
1. Stand at the location where players should be teleported when they start the challenge
2. Run:
```
/conradchallenges challenge setdestination [id]
```
Example: `/conradchallenges challenge setdestination dungeon1`

This sets the spawn point inside the challenge area.

### Step 4: Set Completion Type
Choose how players complete the challenge:
```
/conradchallenges challenge settype [id] <BOSS|TIMER|ITEM|SPEED|NONE>
```

**Completion Types:**
- **BOSS**: Kill a specific boss entity
- **TIMER**: Survive for a set amount of time
- **ITEM**: Bring a specific item to the GateKeeper
- **SPEED**: Complete as fast as possible (with tiered rewards)
- **NONE**: Just return to GateKeeper anytime

Example: `/conradchallenges challenge settype dungeon1 BOSS`

### Step 5: Configure Completion Requirements

#### For BOSS Type:
```
/conradchallenges challenge setboss [id] <EntityType> [Name]
```
Example: `/conradchallenges challenge setboss dungeon1 WITHER "The Dark Lord"`

#### For TIMER Type:
```
/conradchallenges challenge settimer [id] <seconds>
```
Example: `/conradchallenges challenge settimer dungeon1 300` (5 minutes)

#### For ITEM Type:
```
/conradchallenges challenge setitem [id] <Material> <amount> <consume:true|false>
```
Example: `/conradchallenges challenge setitem dungeon1 DIAMOND 10 true`

#### For SPEED Type:
```
/conradchallenges challenge setspeed [id] <maxSeconds>
```
Example: `/conradchallenges challenge setspeed dungeon1 300`

---

## Rewards System

### Fallback Rewards (Always Given)
These rewards are given to all players who complete the challenge, regardless of completion type.

#### Add a Reward Command:
```
/conradchallenges challenge addreward [id] <command>
```
Examples:
- `/conradchallenges challenge addreward dungeon1 eco give %player% 1000`
- `/conradchallenges challenge addreward dungeon1 give %player% diamond 5`
- `/conradchallenges challenge addreward dungeon1 give %player% iron_sword 1`

**Note**: Use `%player%` as a placeholder - it will be replaced with the player's name.

#### Clear All Rewards:
```
/conradchallenges challenge clearrewards [id]
```

### Speed Tier Rewards (SPEED Challenges Only)
For SPEED challenges, you can set tiered rewards based on completion time. These are configured in `config.yml`:

```yaml
challenges:
  dungeon1:
    completion:
      type: SPEED
      max-seconds: 300
      tiers:
        - name: "Gold"
          max-seconds: 60
          reward-commands:
            - "eco give %player% 5000"
            - "give %player% diamond 10"
        - name: "Silver"
          max-seconds: 120
          reward-commands:
            - "eco give %player% 2000"
        - name: "Bronze"
          max-seconds: 180
          reward-commands:
            - "eco give %player% 1000"
```

**How it works:**
- Players get rewards from the best tier they qualify for
- If a player finishes in 45 seconds, they get Gold tier rewards
- If they finish in 100 seconds, they get Silver tier rewards
- All players also get fallback rewards

---

## Regeneration System

The regeneration system automatically resets the challenge area when new players enter, restoring broken blocks, refilled chests, and other changes.

### Setting Up Regeneration Area

**Important**: Regeneration area commands require edit mode. Enter edit mode first:
```
/conradchallenges challenge edit <id>
```

#### Option 1: Using WorldEdit (Recommended)
1. While in edit mode, use WorldEdit to set two positions:
   - `//pos1` at one corner of the challenge area
   - `//pos2` at the opposite corner
2. Run (no ID needed in edit mode):
```
/conradchallenges challenge setregenerationarea
```
This will automatically use your WorldEdit selection.

#### Option 2: Manual Setup
1. While in edit mode, stand at one corner of the challenge area
2. Run: `/conradchallenges challenge setregenerationarea` (no ID needed)
3. Stand at the opposite corner
4. Run the command again

The plugin will capture the initial state of all blocks in the area.

### Capturing Initial State
After setting the regeneration area, capture the initial state (while in edit mode):
```
/conradchallenges challenge captureregeneration
```

**Note**: This is usually done automatically, but you can manually trigger it if needed.

### Auto-Extend Y Range

By default, regeneration uses the Y coordinates from your two corners. However, you can enable auto-extend mode to regenerate from bedrock to build height, ignoring the corner Y values.

**While in edit mode:**
```
/conradchallenges challenge setautoregenerationy [true|false]
```

- **`true`** (or `on`, `enable`, `yes`): Enables auto-extend - regeneration spans from bedrock to build height
- **`false`** (or `off`, `disable`, `no`): Uses manual Y range from corner coordinates
- **No argument**: Toggles the current setting

**Example:**
- Set two corners at ground level (Y=64) with auto-extend enabled
- Regeneration will restore blocks from Y=-64 (bedrock) to Y=319 (build height)
- Perfect for full-height dungeons or challenges that span multiple levels

**Note**: When you change this setting, the initial state is automatically re-captured with the new Y range.

### Clearing Regeneration Area
While in edit mode:
```
/conradchallenges challenge clearregenerationarea
```

---

## Edit Mode

Edit mode allows you to modify challenges while preventing player access. All changes are backed up and can be saved or cancelled.

### Entering Edit Mode
```
/conradchallenges challenge edit <id>
```

This will:
- Teleport you to the challenge start location
- Regenerate the challenge area
- Lock the challenge from players (shows "maintenance" message)
- Create backups of the challenge config and block states

### Making Changes
While in edit mode, you can use all configuration commands **without** specifying the challenge ID:

- `/conradchallenges challenge setbook` (no ID needed)
- `/conradchallenges challenge setdestination` (no ID needed)
- `/conradchallenges challenge addreward <command>` (no ID needed)
- etc.

### Saving Changes
```
/conradchallenges challenge save
```

This will:
- Save all your changes
- Recapture the regeneration state (if regeneration area is set)
- Teleport you back to your original location
- Unlock the challenge for players

### Cancelling Changes
```
/conradchallenges challenge cancel
```

This will:
- Restore the challenge to its state before edit mode
- Restore the regeneration state
- Teleport you back to your original location
- Unlock the challenge for players

**Important**: You cannot teleport out of edit mode. You must use `/conradchallenges challenge save` or `/conradchallenges challenge cancel` to exit.

---

## Difficulty Tiers

Players can select difficulty tiers when accepting challenges, which affect:
1. **Mob Levels**: MythicMobs spawn levels are increased based on tier
2. **Rewards**: All rewards are multiplied based on tier

### How Players Select Difficulty
When accepting a challenge:
- `/accept` - Defaults to Easy (no multiplier)
- `/accept easy` - Easy difficulty
- `/accept medium` - Medium difficulty
- `/accept hard` - Hard difficulty
- `/accept extreme` - Extreme difficulty

### Configuring Difficulty Multipliers

Edit `config.yml`:

```yaml
# Difficulty tier level modifiers (adds to spawner's base mob_level)
difficulty-tiers:
  easy: 0      # No level increase
  medium: 10   # Adds 10 levels
  hard: 35     # Adds 35 levels
  extreme: 100  # Adds 100 levels

# Difficulty tier reward multipliers (multiplies rewards from previous tier)
difficulty-reward-multipliers:
  easy: 1.0      # Base multiplier (no change)
  medium: 1.5    # Medium = Easy * 1.5
  hard: 2.0      # Hard = Medium * 2.0 (3.0x Easy total)
  extreme: 3.0   # Extreme = Hard * 3.0 (9.0x Easy total)
```

**Example:**
- Base reward: `eco give %player% 1000`
- Easy: 1000 money (1.0x)
- Medium: 1500 money (1.5x)
- Hard: 3000 money (3.0x)
- Extreme: 9000 money (9.0x)

---

## Challenge Management

### View Challenge Info
```
/conradchallenges challenge info [id]
```
Shows all challenge configuration details.

### List All Challenges
```
/conradchallenges challenge list
```

### Set Cooldown
```
/conradchallenges challenge setcooldown [id] <seconds>
```
Example: `/conradchallenges challenge setcooldown dungeon1 3600` (1 hour cooldown)

### Set Time Limit
```
/conradchallenges challenge settimelimit [id] <seconds>
```
Example: `/conradchallenges challenge settimelimit dungeon1 600` (10 minute time limit)

### Set Max Party Size
```
/conradchallenges challenge setmaxparty [id] <size|-1>
```
Example: `/conradchallenges challenge setmaxparty dungeon1 4` (max 4 players)
Use `-1` for unlimited party size.

### Lockdown (Disable Challenge)
```
/conradchallenges challenge lockdown <id>
```
Completely disables a challenge. Players cannot start it or be queued.

### Unlock Challenge
```
/conradchallenges challenge unlockdown <id>
```

### Delete Challenge
```
/conradchallenges challenge delete <id>
```
**Warning**: Cannot delete challenges that are currently active or reserved.

---

## Quick Reference

### Complete Setup Example

1. **Create challenge:**
   ```
   /conradchallenges challenge create dungeon1
   ```

2. **Set book** (hold book in hand):
   ```
   /conradchallenges challenge setbook dungeon1
   ```

3. **Set destination** (stand at spawn point):
   ```
   /conradchallenges challenge setdestination dungeon1
   ```

4. **Set completion type:**
   ```
   /conradchallenges challenge settype dungeon1 BOSS
   /conradchallenges challenge setboss dungeon1 WITHER "The Dark Lord"
   ```

5. **Add rewards:**
   ```
   /conradchallenges challenge addreward dungeon1 eco give %player% 1000
   /conradchallenges challenge addreward dungeon1 give %player% diamond 5
   ```

6. **Set regeneration area** (using WorldEdit):
   ```
   //pos1
   //pos2
   /conradchallenges challenge setregenerationarea dungeon1
   ```

7. **Set cooldown:**
   ```
   /conradchallenges challenge setcooldown dungeon1 3600
   ```

### Edit Mode Workflow

1. **Enter edit mode:**
   ```
   /conradchallenges challenge edit dungeon1
   ```

2. **Make changes** (no ID needed):
   ```
   /conradchallenges challenge addreward eco give %player% 500
   /conradchallenges challenge setcooldown 7200
   ```

3. **Save or cancel:**
   ```
   /conradchallenges challenge save
   ```
   OR
   ```
   /conradchallenges challenge cancel
   ```

### Common Commands (Edit Mode)

When in edit mode, omit the challenge ID:
- `setbook` - Set challenge book
- `setdestination` - Set spawn location
- `settype` - Change completion type
- `setboss`, `settimer`, `setitem`, `setspeed` - Configure completion
- `setcooldown` - Set cooldown time
- `settimelimit` - Set time limit
- `setmaxparty` - Set party size limit
- `addreward` - Add reward command
- `clearrewards` - Clear all rewards
- `setregenerationarea` - Set regeneration area
- `clearregenerationarea` - Clear regeneration area
- `captureregeneration` - Capture initial state

---

## Tips & Best Practices

1. **Always test challenges** before making them available to players
2. **Use edit mode** for making changes - it prevents player access and backs up your work
3. **Set regeneration areas** for challenges with destructible environments
4. **Configure speed tier rewards** in `config.yml` for SPEED challenges
5. **Use WorldEdit** for easier regeneration area setup
6. **Set appropriate cooldowns** to prevent challenge farming
7. **Test difficulty tiers** to ensure rewards are balanced

---

## Troubleshooting

### Challenge not regenerating?
- Make sure regeneration area is set (both corners)
- Run `/conradchallenges challenge captureregeneration <id>` manually
- Check that both corners are in the same world

### Players can't start challenge?
- Check if challenge is locked down: `/conradchallenges challenge info <id>`
- Check if challenge is in edit mode
- Verify challenge has a destination set
- Check if player has the required challenge book

### Rewards not working?
- Verify reward commands are correct (use `%player%` placeholder)
- Check economy plugin is installed (for `eco give` commands)
- Test commands manually in console first

### Edit mode stuck?
- Use `/conradchallenges challenge save` or `/conradchallenges challenge cancel`
- If you disconnect, edit mode is automatically cancelled and challenge is restored

---

For more information, see:
- `PLACEHOLDERS.md` - PlaceholderAPI placeholders
- `CHANGELOG.md` - Version history and changes
- `config.yml` - Configuration options
- `messages.yml` - Customizable messages

