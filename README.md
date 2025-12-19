# ConradChallenges - Challenge Setup Guide

A comprehensive guide for setting up and managing challenges in ConradChallenges.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Creating a Challenge](#creating-a-challenge)
- [Basic Challenge Setup](#basic-challenge-setup)
- [Completion Types](#completion-types)
- [Rewards System](#rewards-system)
- [Challenge Area System](#challenge-area-system)
- [Regeneration System](#regeneration-system)
- [Edit Mode](#edit-mode)
- [Difficulty Tiers](#difficulty-tiers)
- [Challenge Management](#challenge-management)
- [Player Guide - How to Do Challenges](#player-guide---how-to-do-challenges)
- [Quick Reference](#quick-reference)

---

## Prerequisites

- **Permission**: You need `conradchallenges.admin` permission, OP: Default
- **WorldEdit/FastAsyncWorldEdit** (optional but recommended): For easier area setup using `//1` and `//2`
- **MythicMobs** (optional): For difficulty tier mob level modifications

**Note**: All commands can use either `/conradchallenges challenge <command>` or the shorter alias `/challenge <command>`

---

## Creating a Challenge

### Step 1: Create the Challenge
```
/challenge create <id>
```
or
```
/conradchallenges challenge create <id>
```
Example: `/challenge create dungeon1`

This creates a new challenge with the specified ID. The ID is used in all commands to reference this challenge.

---

## Basic Challenge Setup

### Step 2: Set the Challenge Book
1. Write a book in-game with a title
2. Hold the book in your main hand
3. Run:
```
/challenge setbook [id]
```
Example: `/challenge setbook dungeon1`

**Note**: In edit mode, you can omit the `[id]` - it will use the challenge you're editing.

### Step 3: Set the Destination
1. Stand at the location where players should be teleported when they start the challenge
2. Run:
```
/challenge setdestination [id]
```
Example: `/challenge setdestination dungeon1`

This sets the spawn point inside the challenge area. This is where players will be teleported when they enter the challenge.

### Step 4: Set Completion Type
Choose how players complete the challenge:
```
/challenge settype [id] <BOSS|TIMER|ITEM|SPEED|NONE>
```

**Completion Types:**
- **BOSS**: Kill a specific boss entity
- **TIMER**: Survive for a set amount of time
- **ITEM**: Bring a specific item to the GateKeeper
- **SPEED**: Complete as fast as possible (with tiered rewards)
- **NONE**: Just return to GateKeeper anytime

Example: `/challenge settype dungeon1 BOSS`

### Step 5: Configure Completion Requirements

#### For BOSS Type:
```
/challenge setboss [id] <EntityType> [Name]
```
Example: `/challenge setboss dungeon1 WITHER "The Dark Lord"`

#### For TIMER Type:
```
/challenge settimer [id] <seconds>
```
Example: `/challenge settimer dungeon1 300` (5 minutes)

#### For ITEM Type:
```
/challenge setitem [id] <Material> <amount> <consume:true|false>
```
Example: `/challenge setitem dungeon1 DIAMOND 10 true`

#### For SPEED Type:
```
/challenge setspeed [id] <maxSeconds>
```
Example: `/challenge setspeed dungeon1 300`

---

## Rewards System

### Fallback Rewards (Always Given)
These rewards are given to all players who complete the challenge, regardless of completion type.

#### Add a Reward Command:
```
/challenge addreward [id] <command>
```
Examples:
- `/challenge addreward dungeon1 eco give %player% 1000`
- `/challenge addreward dungeon1 give %player% diamond 5`
- `/challenge addreward dungeon1 give %player% iron_sword 1`

**Note**: Use `%player%` as a placeholder - it will be replaced with the player's name.

#### Clear All Rewards:
```
/challenge clearrewards [id]
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

## Challenge Area System

The challenge area defines the playable boundaries for the challenge. This is separate from the regeneration area and is used for:
- Teleport restrictions (players can only teleport within the challenge area)
- Explosion protection (blocks in challenge area are protected from creepers, TNT, etc.)
- Area validation

### Setting Up Challenge Area

**Important**: Challenge area commands require edit mode. Enter edit mode first:
```
/challenge edit <id>
```

#### Using WorldEdit (Recommended)
1. While in edit mode, use WorldEdit to set two positions:
   - `//1` at one corner of the challenge area
   - `//2` at the opposite corner
2. Run (no ID needed in edit mode):
```
/challenge setchallengearea
```
This will automatically use your WorldEdit selection and capture the challenge area state.

**Note**: Challenge area state is only captured when you set the area. It is NOT recaptured on save (only regeneration area is recaptured on save).

#### Clearing Challenge Area
While in edit mode:
```
/challenge clearchallengearea
```

### Barrier Boxing (Optional)

You can enable barrier boxing in `config.yml` to automatically create invisible barrier walls around the challenge area:
```yaml
challenge-area:
  barrier-boxing: true  # Set to true to enable
```

When enabled, barrier blocks are placed around the challenge area (only replacing air blocks) when you set the challenge area.

---

## Regeneration System

The regeneration system automatically resets the challenge area when new players enter, restoring broken blocks, refilled chests, and other changes.

**Important**: Regeneration area and challenge area are separate:
- **Challenge Area**: Defines playable boundaries (teleport restrictions, explosion protection)
- **Regeneration Area**: Defines what gets reset when players enter

They can overlap or be different sizes. The regeneration area should typically be larger than or equal to the challenge area.

### Setting Up Regeneration Area

**Important**: Regeneration area commands require edit mode. Enter edit mode first:
```
/challenge edit <id>
```

#### Option 1: Using WorldEdit (Recommended)
1. While in edit mode, use WorldEdit to set two positions:
   - `//1` at one corner of the regeneration area
   - `//2` at the opposite corner
2. Run (no ID needed in edit mode):
```
/challenge setregenerationarea
```
This will automatically use your WorldEdit selection and capture the initial state.

#### Option 2: Manual Setup
1. While in edit mode, stand at one corner of the regeneration area
2. Run: `/challenge setregenerationarea` (no ID needed)
3. Stand at the opposite corner
4. Run the command again

The plugin will automatically capture the initial state of all blocks in the area.

### Capturing Initial State
After setting the regeneration area, the initial state is captured automatically. You can manually trigger it if needed (while in edit mode):
```
/challenge captureregeneration
```

**Note**: The save command recaptures the regeneration area state (not challenge area). Challenge area is only captured when you set it.

### Auto-Extend Y Range

By default, regeneration uses the Y coordinates from your two corners. However, you can enable auto-extend mode to regenerate from bedrock to build height, ignoring the corner Y values.

**While in edit mode:**
```
/challenge setautoregenerationy [true|false]
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
/challenge clearregenerationarea
```

---

## Edit Mode

Edit mode allows you to modify challenges while preventing player access. All changes are backed up and can be saved or cancelled.

### Entering Edit Mode
```
/challenge edit <id>
```

This will:
- Teleport you to the challenge start location
- Regenerate the challenge area (if regeneration area is set)
- Lock the challenge from players (shows "maintenance" message)
- Create backups of the challenge config and block states

### Making Changes
While in edit mode, you can use all configuration commands **without** specifying the challenge ID:

- `/challenge setbook` (no ID needed)
- `/challenge setdestination` (no ID needed)
- `/challenge addreward <command>` (no ID needed)
- `/challenge setchallengearea` (no ID needed)
- `/challenge setregenerationarea` (no ID needed)
- etc.

**Note**: In edit mode, you can teleport anywhere within the challenge world to make changes.

### Saving Changes
```
/challenge save
```

**Important**: You must set a challenge area before saving!

This will:
- Recapture the regeneration area state (if regeneration area is set)
- Save all your changes
- Teleport you back to your original location
- Unlock the challenge for players

**Note**: Challenge area state is NOT recaptured on save - it's only captured when you set it via `setchallengearea`.

### Cancelling Changes
```
/challenge cancel
```

This will:
- Restore the challenge to its state before edit mode
- Restore the regeneration state
- Teleport you back to your original location
- Unlock the challenge for players

**Important**: You cannot teleport out of edit mode using normal teleport commands. You must use `/challenge save` or `/challenge cancel` to exit. However, you CAN teleport within the challenge world while in edit mode.

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

## Configuration Options

All configuration options are in `config.yml`. Here's a complete reference:

### Basic Settings

```yaml
# World players are sent to when using /challengecomplete or when time limit expires
spawn-world: world
```

### Party System Settings

```yaml
# Radius in blocks for party members to be detected
party-radius: 10

# Seconds before a pending challenge is automatically cancelled
pending-challenge-timeout-seconds: 15

# Countdown time in seconds before solo players are teleported to challenge
solo-countdown-seconds: 30

# Countdown time in seconds before party members are teleported to challenge
party-countdown-seconds: 45
```

### Anti-Forgery System

Prevents players from using fake challenge books:

```yaml
anti-forgery:
  enabled: true                    # Enable/disable anti-forgery checks
  fine-amount: 50.0                # Amount to charge for a fake book
  drain-if-less: true              # If player has less than fine-amount, drain entire balance
  broadcast: true                   # Announce to the server when detected
  required-author: OfficerConradd  # Books must be signed by this author name (leave empty to disable author check)
```

**How it works:**
- When a player tries to use a challenge book, the plugin checks if it matches the registered book for that challenge
- If the book is fake (wrong title, wrong author, or not signed by the required author), the player is fined
- If `broadcast: true`, a message is sent to all players announcing the forgery attempt
- If `drain-if-less: true` and the player has less than `fine-amount`, their entire balance is drained

### Anger System

Controls the GateKeeper's angry responses when players try to use fake books:

```yaml
anger:
  enabled: true                    # Enable/disable anger system
  duration-seconds: 300            # How long the GateKeeper stays angry (5 minutes)
```

**Note**: Insult messages are configured in `messages.yml` under `anger.insults`. Edit that file to customize the GateKeeper's responses.

### Edit Mode Settings

```yaml
edit-mode:
  # What to do when a player disconnects while in edit mode
  # Options: "cancel" (restore from backup) or "save" (keep changes)
  disconnect-action: cancel
```

**Options:**
- `cancel`: If you disconnect while in edit mode, all changes are discarded and the challenge is restored from backup
- `save`: If you disconnect while in edit mode, all changes are saved automatically

### Challenge Area Settings

```yaml
challenge-area:
  # Whether to automatically box in challenge areas with barrier blocks
  # Set challenge area using /challenge setchallengearea <id>
  # Use WorldEdit selection (//1 and //2) to select the area
  barrier-boxing: false  # Default: false (off)
```

When enabled, barrier blocks are automatically placed around the challenge area (only replacing air blocks) when you set the challenge area.

### GateKeeper NPCs

```yaml
# GateKeeper NPC UUID list (set via /conradchallenges setnpc)
npcs:
  - 12fb852c-0d06-4d9f-b343-f0d12a4b102c
```

Add NPC UUIDs here. Players right-click these NPCs with challenge books to start challenges.

### Example Challenge Configuration

Here's a complete example of a challenge configuration:

```yaml
challenges:
  fear_hole:
    book-title: fear_hole
    
    # Destination (where players spawn when entering the challenge)
    world: ConradsChallenges
    x: 4792.614971999264
    y: 27.0
    z: 1390.7076254479057
    yaw: -89.24994
    pitch: -18.449978
    
    # Challenge settings
    cooldown-seconds: 3600        # 1 hour cooldown between attempts
    max-party-size: 1              # Max players per party; -1 = unlimited
    time-limit-seconds: 600        # 10 minutes max time in challenge
    time-limit-warnings:           # Warnings at these remaining seconds
      - 60
      - 10
    
    # Completion type and requirements
    completion:
      type: SPEED                  # SPEED, BOSS, TIMER, ITEM, or NONE
      max-seconds: 300             # Max time for SPEED challenges
      tiers:                       # Speed tier rewards
        - name: GOLD
          max-seconds: 90
          reward-commands:
            - eco give %player% 5000
        - name: SILVER
          max-seconds: 180
          reward-commands:
            - eco give %player% 2500
        - name: BRONZE
          max-seconds: 300
          reward-commands:
            - eco give %player% 1000
    
    # Fallback rewards (always given on completion)
    reward-commands: []
    
    # Challenge state
    locked-down: false             # true = challenge disabled
    
    # Challenge area (playable boundaries)
    challenge-area:
      world: ConradsChallenges
      corner1:
        x: 4767.0
        y: -53.0
        z: 1369.0
      corner2:
        x: 4799.0
        y: 43.0
        z: 1407.0
    
    # Regeneration area (what gets reset)
    regeneration:
      world: ConradsChallenges
      corner1:
        x: 4765.0
        y: -50.0
        z: 1369.0
      corner2:
        x: 4800.0
        y: 43.0
        z: 1414.0
      auto-extend-y: false        # true = regenerate from bedrock to build height
```

**Note**: All fields are always saved, even if empty/unset, to maintain consistent structure. You don't need to manually edit this file - use the in-game commands instead.

---

## Challenge Management

### View Challenge Info
```
/challenge info [id]
```
Shows all challenge configuration details.

### List All Challenges
```
/challenge list
```

### Set Cooldown
```
/challenge setcooldown [id] <seconds>
```
Example: `/challenge setcooldown dungeon1 3600` (1 hour cooldown)

### Set Time Limit
```
/challenge settimelimit [id] <seconds>
```
Example: `/challenge settimelimit dungeon1 600` (10 minute time limit)

### Set Max Party Size
```
/challenge setmaxparty [id] <size|-1>
```
Example: `/challenge setmaxparty dungeon1 4` (max 4 players)
Use `-1` for unlimited party size.

### Lockdown (Disable Challenge)
```
/challenge lockdown <id>
```
Completely disables a challenge. Players cannot start it or be queued.

### Unlock Challenge
```
/challenge unlockdown <id>
```

### Delete Challenge
```
/challenge delete <id>
```
**Warning**: Cannot delete challenges that are currently active or reserved.

---

## Player Guide - How to Do Challenges

### Starting a Challenge

1. **Get the Challenge Book**: Obtain the challenge book from an NPC or admin
2. **Right-click the GateKeeper NPC** with the book in your hand
3. **Accept the Challenge**: Type `/accept` (or `/accept <difficulty>` for difficulty tiers)
   - `/accept` - Easy difficulty (default)
   - `/accept easy` - Easy difficulty
   - `/accept medium` - Medium difficulty
   - `/accept hard` - Hard difficulty
   - `/accept extreme` - Extreme difficulty
4. **Wait for Countdown**: A countdown will begin (default 10 seconds)
   - If regeneration is needed, you may wait longer while the area regenerates
   - You'll see a message if regeneration is in progress
5. **Enter the Challenge**: After countdown, you'll be teleported into the challenge

### During the Challenge

- **Teleportation**: You can teleport within the challenge area (both from and to must be in the area)
- **Explosion Protection**: Blocks in the challenge area are protected from explosions (creepers, TNT, etc.)
- **Block Breaking**: You can break blocks normally (no protection)
- **Commands**: Some teleport commands are blocked (`/spawn`, `/home`, `/warp`, etc.)

### Completing the Challenge

#### For BOSS, TIMER, or SPEED Challenges:
1. Complete the challenge requirements (kill boss, survive timer, etc.)
2. Return to the GateKeeper NPC
3. Right-click the GateKeeper to receive rewards

#### For ITEM Challenges:
1. Collect the required item(s)
2. Return to the GateKeeper NPC
3. Right-click the GateKeeper while holding the item(s)

#### For NONE Challenges:
1. Return to the GateKeeper NPC anytime
2. Right-click to receive rewards

### Exiting a Challenge

- **Complete**: Use `/challengecomplete` or `/cc` after meeting requirements, then right-click GateKeeper
- **Exit Early**: Use `/exit` to leave without completing (no rewards, no cooldown)

### Disconnect/Reconnect

If you disconnect while in a challenge:
- **30 Second Grace Period**: You have 30 seconds to reconnect
- **Solo Challenge**: If you don't reconnect within 30s, the challenge is cancelled
- **Party Challenge**: If you don't reconnect within 30s, you'll be teleported to spawn when you reconnect. Other party members continue playing.
- **Reconnecting**: If you reconnect within 30s, you'll be restored to the challenge automatically

### Party Challenges

1. **Start a Party**: One player right-clicks GateKeeper with challenge book
2. **Invite Members**: Nearby players (within party radius) with the challenge book are eligible
3. **Accept Invites**: Each player types `/accept` to join
4. **Start Together**: Party leader types `/accept` again or uses `/party confirm` to start
5. **Complete Together**: All party members must complete requirements, then one member right-clicks GateKeeper

---

## Quick Reference

### Complete Setup Example

1. **Create challenge:**
   ```
   /challenge create dungeon1
   ```

2. **Set book** (hold book in hand):
   ```
   /challenge setbook dungeon1
   ```

3. **Set destination** (stand at spawn point):
   ```
   /challenge setdestination dungeon1
   ```

4. **Set completion type:**
   ```
   /challenge settype dungeon1 BOSS
   /challenge setboss dungeon1 WITHER "The Dark Lord"
   ```

5. **Add rewards:**
   ```
   /challenge addreward dungeon1 eco give %player% 1000
   /challenge addreward dungeon1 give %player% diamond 5
   ```

6. **Enter edit mode to set areas:**
   ```
   /challenge edit dungeon1
   ```

7. **Set challenge area** (using WorldEdit):
   ```
   //1  (at one corner of playable area)
   //2  (at opposite corner)
   /challenge setchallengearea
   ```

8. **Set regeneration area** (using WorldEdit):
   ```
   //1  (at one corner of area to regenerate)
   //2  (at opposite corner)
   /challenge setregenerationarea
   ```

9. **Save changes:**
   ```
   /challenge save
   ```

10. **Set cooldown** (optional, outside edit mode):
    ```
    /challenge setcooldown dungeon1 3600
    ```

### Edit Mode Workflow

1. **Enter edit mode:**
   ```
   /challenge edit dungeon1
   ```

2. **Make changes** (no ID needed):
   ```
   /challenge addreward eco give %player% 500
   /challenge setcooldown 7200
   /challenge setchallengearea  (if using WorldEdit: //1 and //2 first)
   /challenge setregenerationarea  (if using WorldEdit: //1 and //2 first)
   ```

3. **Save or cancel:**
   ```
   /challenge save
   ```
   OR
   ```
   /challenge cancel
   ```

**Important**: You must set a challenge area before saving!

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
- `setchallengearea` - Set challenge area (captures state when set)
- `clearchallengearea` - Clear challenge area
- `setregenerationarea` - Set regeneration area (captures state automatically)
- `clearregenerationarea` - Clear regeneration area
- `captureregeneration` - Manually capture regeneration state
- `setautoregenerationy` - Toggle auto-extend Y range

---

## Tips & Best Practices

1. **Always test challenges** before making them available to players
2. **Use edit mode** for making changes - it prevents player access and backs up your work
3. **Set challenge area first**, then regeneration area - challenge area defines playable boundaries
4. **Set regeneration areas** for challenges with destructible environments - this is what gets reset
5. **Challenge area and regeneration area can overlap** - regeneration area should typically be larger or equal
6. **Challenge area is only captured when set** - save command only recaptures regeneration area
7. **Configure speed tier rewards** in `config.yml` for SPEED challenges
8. **Use WorldEdit** (`//1` and `//2`) for easier area setup
9. **Set appropriate cooldowns** to prevent challenge farming
10. **Test difficulty tiers** to ensure rewards are balanced
11. **Barrier boxing** (if enabled) only replaces air blocks, so it won't destroy existing structures

---

## Troubleshooting

### Challenge not regenerating?
- Make sure regeneration area is set (both corners)
- Run `/challenge captureregeneration <id>` manually (or enter edit mode and use without ID)
- Check that both corners are in the same world
- Verify initial state was captured (check challenge info)

### Players can't start challenge?
- Check if challenge is locked down: `/challenge info <id>`
- Check if challenge is in edit mode
- Verify challenge has a destination set
- Check if player has the required challenge book
- Verify challenge area is set (required for saving)

### Players can't teleport in challenge?
- Check if challenge area is set
- Both teleport locations (from and to) must be within challenge area
- If no challenge area is set, players can teleport anywhere in the destination world

### Rewards not working?
- Verify reward commands are correct (use `%player%` placeholder)
- Check economy plugin is installed (for `eco give` commands)
- Test commands manually in console first

### Edit mode stuck?
- Use `/challenge save` or `/challenge cancel`
- If you disconnect, edit mode is automatically cancelled and challenge is restored (configurable in config.yml)

### Challenge area not saving?
- You must set challenge area before using `/challenge save`
- Challenge area is only captured when you set it, not on every save
- Use `/challenge setchallengearea` in edit mode (with WorldEdit `//1` and `//2`)

---

For more information, see:
- `PLACEHOLDERS.md` - PlaceholderAPI placeholders
- `CHANGELOG.md` - Version history and changes
- `config.yml` - Configuration options
- `messages.yml` - Customizable messages

