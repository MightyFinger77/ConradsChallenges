# ConradChallenges - Challenge Setup Guide

A comprehensive guide for setting up and managing challenges in ConradChallenges.

## Table of Contents
- [Configuration files](#configuration-files)
- [Prerequisites](#prerequisites)
- [Creating a Challenge](#creating-a-challenge)
- [Basic Challenge Setup](#basic-challenge-setup)
- [Completion Types](#completion-types)
- [Rewards System](#rewards-system)
- [Challenge Area System](#challenge-area-system)
- [Regeneration System](#regeneration-system)
- [Block breaking in challenges](#block-breaking-in-challenges)
- [Edit Mode](#edit-mode)
- [Difficulty Tiers](#difficulty-tiers)
- [Configuration Options](#configuration-options)
- [Loot table manager](#loot-table-manager)
- [Challenge Management](#challenge-management)
- [Player Guide - How to Do Challenges](#player-guide---how-to-do-challenges)
- [Quick Reference](#quick-reference)

---

## Configuration files

Before you start, it helps to know where settings are stored:

- **`config.yml`** – General plugin settings: spawn world, party, difficulty tiers, lives, anti-forgery, edit mode, world/challenge aliases, and so on. Edit this file or use in-game commands where applicable.
- **`challenges.yml`** – All challenge definitions (destination, completion type, rewards, areas, block break settings, etc.). This file is created automatically. Use the in-game challenge commands to create and edit challenges; the plugin writes to `challenges.yml` for you.
- **`messages.yml`** – All player-facing messages. The plugin merges in new keys on version upgrade and backs up to `messages.yml.bak` before updating.

If you are upgrading from an older version and your challenges were previously in `config.yml`, the plugin will migrate them into `challenges.yml` on first load and create a backup at `config.yml.old`. When config or messages are updated during migration, backups are also written to `config.yml.bak` and `messages.yml.bak` before the merge.

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

**Note**: This command can be run outside of edit mode (unlike most other commands). This is necessary because edit mode teleports you to the challenge area using the destination, so you need to set the destination before entering edit mode.

### Step 4: Set Completion Type
Choose how players complete the challenge:
```
/challenge settype [id] <BOSS|TIMER|ITEM|SPEED|NONE>
```

**Completion Types:**
- **BOSS**: Kill a specific boss entity
- **TIMER**: Survive for a set amount of time
- **ITEM**: Bring a specific item to the GateKeeper
- **SPEED**: Complete as fast as possible (speed affects reward multipliers)
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

**Important for SPEED challenges:**
- Speed tiers (Gold/Silver/Bronze) are set via the `setspeed` command and determine multipliers, NOT rewards
- Rewards come from difficulty tier rewards (use `addtierreward` commands)
- Speed tier `reward-commands` in config are **ignored** - they're kept for backward compatibility only
- See [Speed Multipliers](#speed-multipliers-speed-challenges) section for details

---

## Rewards System

The reward system uses difficulty tiers with inheritance, speed multipliers, and RNG chance. Higher difficulty tiers inherit rewards from lower tiers with multipliers applied.

### Difficulty Tier Rewards

Each difficulty tier (easy, medium, hard, extreme) has its own reward table. Higher tiers inherit all lower tier rewards with multipliers.

**Inheritance Example:**
- **Easy**: Gets only Easy tier rewards (1.0x difficulty multiplier)
- **Medium**: Gets Easy rewards (×1.5) + Medium rewards (×1.0)
- **Hard**: Gets Easy (×3.0) + Medium (×2.0) + Hard (×1.0)
- **Extreme**: Gets all previous (×9.0, ×6.0, ×3.0) + Extreme (×1.0)

#### Add Tier Reward:
```
/challenge addtierreward [id] <tier> <type> [args] [chance]
```

**Tiers:** `easy`, `medium`, `hard`, `extreme`

**Types:**
- `hand` - Uses item in your hand
  ```
  /challenge addtierreward dungeon1 easy hand [chance]
  ```
- `item <material> [amount]` - Specifies item type
  ```
  /challenge addtierreward dungeon1 easy item DIAMOND 8 [chance]
  ```
- `command <command>` - Custom command
  ```
  /challenge addtierreward dungeon1 easy command eco give %player% 5000 [chance]
  ```

**Chance:** Optional, 0.0-1.0 (default: 1.0 = 100%). Lower values mean rewards have a random chance to be given.

**Examples:**
```
/challenge addtierreward dungeon1 easy item DIAMOND 8 1.0
/challenge addtierreward dungeon1 medium item BEACON 2 0.8
/challenge addtierreward dungeon1 hard command eco give %player% 10000
```

#### Clear Tier Rewards:
```
/challenge cleartierrewards [id] <tier>
```

#### List Tier Rewards:
```
/challenge listtierrewards [id] [tier]
```

### Speed Multipliers (SPEED Challenges)

For SPEED challenges, completion time affects reward multipliers:
- **Gold tier** (fast time): 1.5x multiplier
- **Silver tier** (average time): 1.0x multiplier (normal)
- **Bronze tier** (slow time): 0.5x multiplier (half rewards)
- **New record**: 3x multiplier (replaces tier multiplier)

**How Speed Tiers Work:**
- Speed tiers are configured via `/challenge setspeed <id> <maxSeconds>` and stored in `completion.tiers`
- Each tier has a `name` (GOLD, SILVER, BRONZE) and `max-seconds` (time limit)
- The `reward-commands` field in speed tiers is **completely ignored** - kept only for backward compatibility
- Speed tiers are used **only** to determine which multiplier applies based on completion time
- Actual rewards come from `difficulty-tier-rewards` (see Difficulty Tier Rewards section)

**Setting Speed Tiers:**
Speed tiers are automatically created when you use `/challenge setspeed`. The plugin creates default tiers (GOLD, SILVER, BRONZE) based on the max time. You can customize them in the config file, but remember: `reward-commands` in tiers are ignored.

### Top Time Rewards

Special rewards only given when a player sets a new top time (SPEED challenges only). These get 3x multiplier on top of difficulty multiplier.

#### Add Top Time Reward:
```
/challenge addtoptimereward [id] <hand|item|command> [args] [chance]
```

**Examples:**
```
/challenge addtoptimereward dungeon1 item NETHERITE_BLOCK 1 0.5
/challenge addtoptimereward dungeon1 command eco give %player% 5000
```

### Fallback Rewards

Fallback rewards are used if no difficulty tier rewards are set. They work with all completion types and support chance values. These are the "old" reward system - if you set tier rewards, fallback rewards are ignored.

**When to use:**
- Simple challenges that don't need tier-based rewards
- Quick setup without complexity
- Backward compatibility with existing challenges

**When NOT to use:**
- If you want different rewards per difficulty tier
- If you want inheritance (higher tiers get lower tier rewards)
- For SPEED challenges (use tier rewards with speed multipliers instead)

#### Add Fallback Reward:
```
/challenge addreward [id] <command> [chance]
```

**Examples:**
```
/challenge addreward dungeon1 eco give %player% 1000 1.0
/challenge addreward dungeon1 give %player% diamond 5 0.8
```

**Note:** 
- Use `%player%` as a placeholder - it will be replaced with the player's name
- Chance defaults to 1.0 (100%) if not specified
- All fallback rewards get difficulty multipliers applied (Easy=1.0x, Medium=1.5x, Hard=3.0x, Extreme=9.0x)
- For SPEED challenges, speed multipliers also apply (Gold=1.5x, Silver=1.0x, Bronze=0.5x, New Record=3.0x)
- **Each player gets their own separate roll** on the loot table - rewards are not shared between party members

#### Clear All Fallback Rewards:
```
/challenge clearrewards [id]
```

### Reward Calculation Examples

**Example 1: Hard Difficulty, Gold Time (60s), New Record**

Player gets:
- **Easy tier rewards**: 5000 money, 8 diamonds
  - Multiplier: ×3.0 (Hard difficulty) × 3.0 (new record) = **9.0x**
  - Result: 45,000 money, 72 diamonds
- **Medium tier rewards**: 2 beacons
  - Multiplier: ×2.0 (Hard difficulty) × 3.0 (new record) = **6.0x**
  - Result: 12 beacons
- **Hard tier rewards**: 1 netherite pickaxe
  - Multiplier: ×1.0 (Hard difficulty) × 3.0 (new record) = **3.0x**
  - Result: 3 netherite pickaxes
- **Top time rewards**: 5000 money (50% chance), 1 netherite block (50% chance)
  - Multiplier: ×1.0 (Hard difficulty) × 3.0 (new record) = **3.0x**
  - Result: 15,000 money (if rolled), 3 netherite blocks (if rolled)

**Example 2: Hard Difficulty, Gold Time (60s), NOT New Record**

Player gets:
- **Easy tier rewards**: 5000 money, 8 diamonds
  - Multiplier: ×3.0 (Hard difficulty) × 1.5 (Gold tier) = **4.5x**
  - Result: 22,500 money, 36 diamonds
- **Medium tier rewards**: 2 beacons
  - Multiplier: ×2.0 (Hard difficulty) × 1.5 (Gold tier) = **3.0x**
  - Result: 6 beacons
- **Hard tier rewards**: 1 netherite pickaxe
  - Multiplier: ×1.0 (Hard difficulty) × 1.5 (Gold tier) = **1.5x**
  - Result: 1 netherite pickaxe (rounded down from 1.5)
- **Top time rewards**: None (not a new record)

**Example 3: Easy Difficulty, Bronze Time (250s)**

Player gets:
- **Easy tier rewards**: 5000 money, 8 diamonds
  - Multiplier: ×1.0 (Easy difficulty) × 0.5 (Bronze tier) = **0.5x**
  - Result: 2,500 money, 4 diamonds
- **Medium/Hard/Extreme rewards**: None (only Easy tier selected)
- **Top time rewards**: None (not a new record)

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

#### Option 3: Sync with Challenge Area
If you've already set the challenge area and want the regeneration area to match it exactly:
1. While in edit mode, run:
```
/challenge areasync
```
This will copy the challenge area's coordinates to the regeneration area and automatically capture the initial state.

**Note**: This requires the challenge area to be set first using `/challenge setchallengearea`.

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

## Block breaking in challenges

You can control whether and what challengers are allowed to break inside a challenge.

### Behavior

- **Block break OFF** (default): Challengers can break any block in the challenge area (normal gameplay).
- **Block break ON**: Challengers can only break:
  - Blocks **they placed** during that run, or
  - Blocks whose **material** is in the challenge’s **allow list** (e.g. `stone_slab`, `oak_planks`).
- **Regeneration area**: Breaking blocks inside a challenge’s **regen area** is only allowed if you are in **edit mode** for that challenge. Anyone else (including admins not in edit mode) gets: *"Enter edit mode to make changes to this area."*
- **Admins in edit mode**: Can always break blocks in that challenge’s area, regardless of the block-break setting.

Placed blocks are tracked in memory only and are cleared when the challenge area is regenerated.

### Commands (edit mode required)

**Toggle block breaking for the challenge you’re editing:**
```
/challenge blockbreak
```
- **ON** = restriction enabled (challengers only break placed + allow list).
- **OFF** = no restriction (challengers can break any block).

**Allow a block type to be broken when block break is ON:**
```
/challenge allowbreak <material>
```
Example: `/challenge allowbreak stone_slab`  
Only block materials are valid (e.g. `oak_planks`, `stone_slab`). Tab completion suggests block names.

**Remove a material from the allow list:**
```
/challenge disallowbreak <material>
```

### Per-challenge config (challenges.yml)

When block break is enabled, each challenge stores:
- `block-break-enabled: true`
- `break-allowed-blocks: [ stone_slab, oak_planks, ... ]`

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

**How it works:**
- These multipliers apply to **all** tier rewards (difficulty tier rewards, fallback rewards, top time rewards)
- Speed multipliers (for SPEED challenges) are applied **on top** of difficulty multipliers
- Example with Easy tier reward of 1000 money:
  - Easy: 1000 money (1.0x difficulty)
  - Medium: 1500 money (1.5x difficulty)
  - Hard: 3000 money (3.0x difficulty)
  - Extreme: 9000 money (9.0x difficulty)
  - If Gold time (SPEED): Multiply by 1.5x
  - If new record (SPEED): Multiply by 3.0x

### Lives System

Players receive a limited number of lives when entering a challenge, based on the difficulty tier selected. Lives determine what happens when a player dies:

**Lives Per Tier (configurable in `config.yml`):**
- **Easy**: 3 lives (default)
- **Medium**: 2 lives (default)
- **Hard**: 1 life (default)
- **Extreme**: 1 life (default)

**When a player dies:**
- **With lives remaining**: Player keeps inventory, respawns in challenge destination, lives decrement
- **No lives remaining**: Player is removed from challenge, drops inventory (normal death), teleported to spawn

**Buy-Back System:**
- Players who run out of lives can use `/buyback` command to pay a fee (configurable, default $6000)
- Paying the fee resets lives and teleports player back to challenge destination
- Requires economy plugin (Vault) to be installed

**Dead Party Member Rewards:**
- If a party member dies (runs out of lives) but the party completes the challenge:
  - Dead members receive Easy tier rewards with 0.5x multiplier
  - This applies regardless of the actual difficulty tier the party selected
  - Rewards are automatically given when the party completes

---

## Configuration Options

General plugin options are in **`config.yml`**. Challenge definitions (destination, completion, rewards, areas, block break, etc.) are in **`challenges.yml`**. The following covers both where relevant.

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

### Lives System Settings

```yaml
# Lives system: Number of lives per difficulty tier
# When a player dies, they keep inventory and are teleported back to challenge destination
# When lives run out, they're removed from challenge and drop inventory
lives-per-tier:
  easy: 3        # Easy tier gets 3 lives
  medium: 2      # Medium tier gets 2 lives
  hard: 1        # Hard tier gets 1 life
  extreme: 1     # Extreme tier gets 1 life

# Buy-back fee: Amount to pay to get back into challenge after running out of lives
# Players can use /buyback command to pay this fee and re-enter with lives reset
buy-back-fee: 6000.0  # Default: $6000
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
  disconnect-action: save
```

**Options:**
- `cancel`: If you disconnect while in edit mode, all changes are discarded and the challenge is restored from backup
- `save`: If you disconnect while in edit mode, all changes are saved automatically

**Disconnect Behavior:**
- When you disconnect in edit mode, your original location (before entering edit mode) is saved
- When you reconnect, you are automatically teleported back to that original location
- This matches the behavior of `/challenge save` or `/challenge cancel` - you return to where you were before entering edit mode

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

Challenge data is stored in **`challenges.yml`**. Here's a complete example of one challenge's structure:

```yaml
# In challenges.yml
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
      tiers:                       # Speed tiers (used for multipliers only, reward-commands are ignored)
        - name: GOLD
          max-seconds: 90
          reward-commands: []       # IGNORED - kept for backward compatibility
        - name: SILVER
          max-seconds: 180
          reward-commands: []       # IGNORED
        - name: BRONZE
          max-seconds: 300
          reward-commands: []       # IGNORED
    
    # Difficulty tier rewards (this is where rewards come from now)
    # Higher tiers inherit lower tier rewards with multipliers
    difficulty-tier-rewards:
      easy:
        rewards:
          - type: COMMAND
            command: "eco give %player% 5000"
            chance: 1.0
          - type: ITEM
            material: DIAMOND
            amount: 8
            chance: 1.0
      medium:
        rewards:
          # Medium gets: Easy rewards (×1.5) + Medium rewards (×1.0)
          - type: ITEM
            material: BEACON
            amount: 2
            chance: 1.0
      hard:
        rewards:
          # Hard gets: Easy (×3.0) + Medium (×2.0) + Hard (×1.0)
          - type: ITEM
            material: NETHERITE_PICKAXE
            amount: 1
            chance: 1.0
      extreme:
        rewards:
          # Extreme gets: Easy (×9.0) + Medium (×6.0) + Hard (×3.0) + Extreme (×1.0)
          - type: COMMAND
            command: "eco give %player% 10000"
            chance: 1.0
    
    # Top time rewards (NEW - only given for new records)
    top-time-rewards:
      rewards:
        - type: COMMAND
          command: "eco give %player% 5000"
          chance: 1.0
        - type: ITEM
          material: NETHERITE_BLOCK
          amount: 1
          chance: 0.5  # 50% chance
    
    # Fallback rewards (used if no difficulty-tier-rewards are set)
    # Format: List of maps with "command" and "chance" (0.0-1.0)
    # Old format (just strings like ['7000']) is automatically converted to chance 1.0
    # This example shows empty fallback rewards since tier rewards are set above
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

**Note**: All fields are always saved, even if empty/unset, to maintain consistent structure. You don't need to manually edit `challenges.yml` — use the in-game commands instead. Block break settings (`block-break-enabled`, `break-allowed-blocks`) are also stored per challenge when you use `/challenge blockbreak`, `allowbreak`, and `disallowbreak`.

---

## Loot table manager

Admins can manage custom chest loot per challenge via **Loot table** in the challenge menu (`/conradchallenges` → Edit loottable).

- **List GUI**: Shows all loot tables in a **54-slot** inventory. The **bottom row is a fixed nav bar**: Back (return to main menu), empty slot, **Previous page**, empty slot, **Next page**. Loot table icons appear only in the **top 45 slots** (5 rows); when there are more than 45 tables, use the arrows to change pages. Your current page is remembered when you open a loot table and then go Back.
- **Options GUI**: For each loot table you can set size (single/double chest), edit contents, rename, assign to challenges, change icon (type name or use item in hand), **loot type** (Normal or Legendary), and delete. Icon options are in the second row (staggered slots).

### Loot type and chest type

Each loot table has a **loot type**: **Normal** or **Legendary** (default: Normal). When a chest is opened in a challenge, the plugin uses only loot tables whose type matches the chest’s type.

- **Normal chests** use loot tables with type Normal (assigned to that challenge).
- **Legendary chests** use loot tables with type Legendary (assigned to that challenge).

All chests in a challenge are Normal by default. In **edit mode**, look at a chest in the regeneration area and run **`/challenge loottype`** to toggle that chest between Normal and Legendary. Use **`/challenge chestignore`** to exclude a chest from loot entirely (no table applied).

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
4. **Wait for Countdown**: A countdown will begin (default 30 seconds for solo, 60 seconds for party)
   - If regeneration is needed, you may wait longer while the area regenerates
   - You'll see a message if regeneration is in progress
5. **Enter the Challenge**: After countdown, you'll be teleported into the challenge

### During the Challenge

- **Teleportation**: You can teleport within the regeneration area, but cannot teleport outside of it. Non-admin players cannot teleport into challenge areas (must use challenge books at GateKeeper).
- **Explosion Protection**: Blocks in the challenge area are protected from explosions (creepers, TNT, etc.)
- **Block Breaking**: Depends on the challenge’s block-break setting. If **off**, you can break any block. If **on**, you can only break blocks you placed or block types the admin added to the allow list (e.g. certain slabs or planks). Breaking inside the regen area is only allowed for admins in edit mode.
- **Movement**: You cannot walk outside the regeneration area - movement is blocked and you'll be teleported back if you try to leave
- **Lives System**: You have a limited number of lives based on difficulty tier:
  - **Easy**: 3 lives (default)
  - **Medium**: 2 lives (default)
  - **Hard**: 1 life (default)
  - **Extreme**: 1 life (default)
  - When you die with lives remaining: You keep your inventory and respawn in the challenge
  - When you run out of lives: You're removed from the challenge and drop inventory (normal death)
  - Use `/buyback` to pay a fee and re-enter the challenge with lives reset

### Completing the Challenge

#### For BOSS, TIMER, or SPEED Challenges:
1. Complete the challenge requirements (kill boss, survive timer, complete speed challenge, etc.)
2. Return to the GateKeeper NPC
3. Right-click the GateKeeper to receive rewards
   - Rewards are based on your selected difficulty tier (easy/medium/hard/extreme)
   - For SPEED challenges, your completion time affects multipliers (Gold=1.5x, Silver=1.0x, Bronze=0.5x)
   - If you set a new record, you get 3x multiplier and top time rewards

#### For ITEM Challenges:
1. Collect the required item(s)
2. Return to the GateKeeper NPC
3. Right-click the GateKeeper while holding the item(s)

#### For NONE Challenges:
1. Return to the GateKeeper NPC anytime
2. Right-click to receive rewards

### Exiting a Challenge

- **Complete**: Use `/challengecomplete` or `/cc` after meeting requirements, then right-click GateKeeper
  - **Completion Keys**: All party members' completion keys will be consumed when using `/cc`
  - One completion key from any party member is required to complete
- **Exit Early**: Use `/exit` to leave without completing (no rewards, no cooldown)
- **Buy Back**: If you ran out of lives, use `/buyback` to pay a fee and re-enter the challenge with lives reset

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
6. **Completion Keys**: When using `/cc`, all completion keys from all party members are consumed
7. **Dead Party Members**: If a party member dies (runs out of lives) but the party completes:
   - Dead members receive Easy tier rewards with 0.5x multiplier
   - This applies even if the party was on a higher difficulty tier

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

5. **Enter edit mode to set areas and rewards:**
   ```
   /challenge edit dungeon1
   ```

6. **Add tier rewards** (while in edit mode, no ID needed):
   ```
   /challenge addtierreward easy item DIAMOND 8
   /challenge addtierreward easy command eco give %player% 5000
   /challenge addtierreward medium item BEACON 2
   /challenge addtierreward hard item NETHERITE_PICKAXE 1
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

**Note**: If you prefer fallback rewards instead of tier rewards, you can use `/challenge addreward <command> [chance]` instead of tier rewards. Fallback rewards are used if no tier rewards are set.

### Edit Mode Workflow

1. **Enter edit mode:**
   ```
   /challenge edit dungeon1
   ```

2. **Make changes** (no ID needed):
   ```
   # Add tier rewards
   /challenge addtierreward easy item DIAMOND 8
   /challenge addtierreward medium item BEACON 2
   
   # Or use fallback rewards (if no tier rewards set)
   /challenge addreward eco give %player% 500
   
   # Configure challenge settings
   /challenge setcooldown 7200
   /challenge setchallengearea  (if using WorldEdit: //1 and //2 first)
   /challenge setregenerationarea  (if using WorldEdit: //1 and //2 first)
   # Or sync regeneration area with challenge area:
   /challenge areasync
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
- `addreward` - Add fallback reward command (with chance)
- `clearrewards` - Clear all fallback rewards
- `addtierreward` - Add reward to difficulty tier (easy/medium/hard/extreme)
- `cleartierrewards` - Clear rewards for a specific tier
- `listtierrewards` - List tier rewards
- `addtoptimereward` - Add top time reward (SPEED challenges only)
- `setchallengearea` - Set challenge area (captures state when set)
- `clearchallengearea` - Clear challenge area
- `setregenerationarea` - Set regeneration area (captures state automatically)
- `areasync` - Sync regeneration area with challenge area (copies challenge area coordinates)
- `clearregenerationarea` - Clear regeneration area
- `captureregeneration` - Manually capture regeneration state
- `setautoregenerationy` - Toggle auto-extend Y range
- `blockbreak` - Toggle block breaking (on = restricted to placed + allow list; off = break any block)
- `allowbreak <material>` - Add a block type to the break allow list (e.g. `stone_slab`)
- `disallowbreak <material>` - Remove a block type from the allow list
- `blockitem <material>` - Block an item from being taken into the challenge (moved to ender chest on entry)
- `unblockitem <material>` - Remove an item from the block list

---

## Tips & Best Practices

1. **Always test challenges** before making them available to players
2. **Use edit mode** for making changes - it prevents player access and backs up your work
3. **Set challenge area first**, then regeneration area - challenge area defines playable boundaries
4. **Set regeneration areas** for challenges with destructible environments - this is what gets reset
5. **Challenge area and regeneration area can overlap** - regeneration area should typically be larger or equal
6. **Challenge area is only captured when set** - save command only recaptures regeneration area
7. **Use difficulty tier rewards** for better reward control - higher tiers inherit lower tier rewards
8. **Set speed tiers** for SPEED challenges (used for multipliers, not rewards)
9. **Use RNG chance** (0.0-1.0) to add grind elements - lower chance = rarer rewards
10. **Use WorldEdit** (`//1` and `//2`) for easier area setup
11. **Set appropriate cooldowns** to prevent challenge farming
12. **Test difficulty tiers** to ensure rewards are balanced
13. **Barrier boxing** (if enabled) only replaces air blocks, so it won't destroy existing structures

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
- Players can only teleport within the regeneration area (cannot teleport outside)
- Non-admin players cannot teleport into challenge areas (must use challenge books at GateKeeper)
- If no regeneration area is set, the system falls back to challenge area or destination world restrictions
- Admins can bypass teleportation restrictions to enter challenge areas
- Players cannot walk outside the regeneration area - movement is blocked and they'll be teleported back

### Rewards not working?
- Verify reward commands are correct (use `%player%` placeholder)
- Check economy plugin is installed (for `eco give` commands)
- Test commands manually in console first

### Edit mode stuck?
- Use `/challenge save` or `/challenge cancel`
- If you disconnect, edit mode is automatically handled based on `edit-mode.disconnect-action` setting:
  - `cancel`: Changes are discarded and challenge is restored from backup
  - `save`: Changes are saved automatically
- When you reconnect after disconnecting in edit mode, you are automatically returned to your original location (before entering edit mode)

### Challenge area not saving?
- You must set challenge area before using `/challenge save`
- Challenge area is only captured when you set it, not on every save
- Use `/challenge setchallengearea` in edit mode (with WorldEdit `//1` and `//2`)

### Upgraded to 4.0 and challenges missing?
- Challenge data is now in **`challenges.yml`**, not `config.yml`. On first load, the plugin migrates from `config.yml` and creates a backup at `config.yml.old`. If something went wrong, you can copy the `challenges` section from `config.yml.old` into a new `challenges.yml` (under a top-level `challenges:` key).

---

For more information, see:
- `PLACEHOLDERS.md` - PlaceholderAPI placeholders
- `CHANGELOG.md` - Version history and changes
- `config.yml` - General configuration options
- `challenges.yml` - Challenge definitions (created and migrated automatically)
- `messages.yml` - Customizable messages

