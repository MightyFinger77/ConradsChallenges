# Tier-Based Reward System Refactor Plan

## Current System

### Structure
- **Fallback Rewards** (`fallbackRewardCommands`): List of commands that always run on completion
- **Speed Tier Rewards** (`speedTiers`): Only for SPEED challenges, time-based tier rewards
- **Difficulty Multiplier**: Applies to ALL rewards (fallback + speed tier)

### Limitations
- All difficulty tiers get the same rewards (just multiplied)
- No way to give different rewards per difficulty tier
- Speed tier rewards are separate from difficulty tier rewards

## Proposed System

### Concept
Each difficulty tier (easy, medium, hard, extreme) has its own reward table. Higher tiers inherit rewards from lower tiers with multipliers applied.

### Inheritance Logic
- **Easy**: Gets only Easy tier rewards (no multiplier, base 1.0x)
- **Medium**: Gets Easy rewards (×1.5) + Medium rewards (×1.0)
- **Hard**: Gets Easy rewards (×3.0) + Medium rewards (×2.0) + Hard rewards (×1.0)
- **Extreme**: Gets Easy rewards (×9.0) + Medium rewards (×6.0) + Hard rewards (×3.0) + Extreme rewards (×1.0)

**Note**: Multipliers shown are cumulative (Easy=1.0, Medium=1.5, Hard=3.0, Extreme=9.0)

### Reward Types
Each tier reward can be one of:
1. **hand**: Uses item currently in player's hand
2. **item**: Specifies material and amount: `item <material> [amount]`
3. **command**: Custom command (like current `addreward`): `command <command>`

## Implementation Plan

### Phase 1: Data Structure Changes

#### 1.1 Update `ChallengeConfig` class
```java
// Replace:
List<String> fallbackRewardCommands;

// With:
Map<String, List<TierReward>> difficultyTierRewards;
// Key: "easy", "medium", "hard", "extreme"
// Value: List of rewards for that tier
```

#### 1.2 Create `TierReward` class
```java
private static class TierReward {
    enum Type { HAND, ITEM, COMMAND }
    Type type;
    Material material;      // For ITEM type
    int amount;             // For ITEM type
    ItemStack itemStack;    // For HAND type (serialized)
    String command;         // For COMMAND type
}
```

#### 1.3 Config File Structure
```yaml
challenges:
  challenge_id:
    difficulty-tier-rewards:
      easy:
        - type: COMMAND
          command: "eco give %player% 5000"
        - type: ITEM
          material: DIAMOND
          amount: 8
      medium:
        - type: ITEM
          material: BEACON
          amount: 2
      hard:
        - type: ITEM
          material: NETHERITE_PICKAXE
          amount: 1
      extreme:
        - type: COMMAND
          command: "give %player% NETHERITE_BLOCK 1"
```

### Phase 2: Command Implementation

#### 2.1 New Command: `/challenge addtierreward <tier> <type> [args]`
- **tier**: easy, medium, hard, extreme
- **type**: hand, item, command
- **args**: 
  - `hand`: No args, uses item in hand
  - `item <material> [amount]`: Material name, optional amount (default 1)
  - `command <command>`: Full command string

#### 2.2 New Command: `/challenge cleartierrewards <tier>`
- Clears all rewards for a specific tier

#### 2.3 New Command: `/challenge listtierrewards [tier]`
- Lists all tier rewards, or specific tier if provided

#### 2.4 Migration Command: `/challenge migraterewards`
- Converts existing `fallbackRewardCommands` to `easy` tier rewards
- One-time migration helper

### Phase 3: Reward Application Logic

#### 3.1 Update `runRewardCommands()` method
```java
private void runRewardCommands(Player player, ChallengeConfig cfg, String challengeId) {
    String difficultyTier = activeChallengeDifficulties.get(challengeId);
    if (difficultyTier == null) difficultyTier = "easy";
    
    List<TierReward> allRewards = collectTierRewards(cfg, difficultyTier);
    
    // Apply rewards
    for (TierReward reward : allRewards) {
        applyTierReward(player, reward, difficultyTier);
    }
    
    // Still handle SPEED tier rewards separately (if applicable)
    // ...
}
```

#### 3.2 New Method: `collectTierRewards()`
```java
private List<TierReward> collectTierRewards(ChallengeConfig cfg, String tier) {
    List<TierReward> rewards = new ArrayList<>();
    List<String> tierOrder = Arrays.asList("easy", "medium", "hard", "extreme");
    int currentTierIndex = tierOrder.indexOf(tier);
    
    // Collect rewards from all lower tiers (with multipliers)
    for (int i = 0; i <= currentTierIndex; i++) {
        String tierName = tierOrder.get(i);
        List<TierReward> tierRewards = cfg.difficultyTierRewards.get(tierName);
        if (tierRewards != null) {
            // Calculate multiplier for this tier's rewards
            double multiplier = getCumulativeMultiplierForTier(tierName);
            double appliedMultiplier = getCumulativeMultiplierForTier(tier) / multiplier;
            
            // Add rewards with appropriate multiplier
            for (TierReward reward : tierRewards) {
                rewards.add(reward.withMultiplier(appliedMultiplier));
            }
        }
    }
    
    return rewards;
}
```

#### 3.3 New Method: `applyTierReward()`
```java
private void applyTierReward(Player player, TierReward reward, String difficultyTier) {
    switch (reward.type) {
        case HAND:
            // Give item from hand (with multiplier if it's a quantity item)
            ItemStack item = reward.itemStack.clone();
            // Apply multiplier to amount if applicable
            item.setAmount((int) Math.max(1, item.getAmount() * getMultiplierForTier(difficultyTier)));
            player.getInventory().addItem(item);
            break;
        case ITEM:
            // Give material with amount (multiplier already applied in collectTierRewards)
            ItemStack item = new ItemStack(reward.material, reward.amount);
            player.getInventory().addItem(item);
            break;
        case COMMAND:
            // Execute command (multiplier applied via applyRewardMultiplier)
            String cmd = reward.command.replace("%player%", player.getName());
            String multipliedCmd = applyRewardMultiplier(cmd, getMultiplierForTier(difficultyTier));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), multipliedCmd);
            break;
    }
}
```

### Phase 4: Backward Compatibility

#### 4.1 Migration Strategy
- Keep `fallbackRewardCommands` field temporarily
- On load, if `difficulty-tier-rewards` doesn't exist but `reward-commands` does, migrate automatically
- Add migration command for manual conversion

#### 4.2 Config Loading
```java
// In loadChallengesFromConfig():
if (config.contains(path + ".difficulty-tier-rewards")) {
    // Load new system
    loadDifficultyTierRewards(cfg, config.getConfigurationSection(path + ".difficulty-tier-rewards"));
} else if (config.contains(path + ".reward-commands")) {
    // Migrate old system
    migrateFallbackRewardsToTierRewards(cfg, config.getStringList(path + ".reward-commands"));
}
```

### Phase 5: UI/UX Improvements

#### 5.1 Update `/challenge info` command
- Show tier rewards organized by tier
- Show inheritance (e.g., "Hard gets: Easy (×3.0) + Medium (×2.0) + Hard (×1.0)")

#### 5.2 Update README
- Document new tier reward system
- Show examples
- Explain inheritance

## Migration Path

### Step 1: Add new data structures (non-breaking)
- Add `difficultyTierRewards` to `ChallengeConfig`
- Keep `fallbackRewardCommands` for now

### Step 2: Implement new commands
- Add `addtierreward`, `cleartierrewards`, `listtierrewards`
- Keep `addreward` working (adds to easy tier)

### Step 3: Update reward application
- Modify `runRewardCommands()` to use new system
- Keep fallback to old system if new system is empty

### Step 4: Migration tool
- Add `/challenge migraterewards` command
- Auto-migrate on config load if needed

### Step 5: Deprecation (future)
- Mark `addreward` as deprecated
- Eventually remove `fallbackRewardCommands`

## Updated Requirements

### Speed Rewards Integration
- Speed rewards are integrated into tier rewards
- Time-based logic: Faster completion = multipliers applied
- New record time = extra boost multiplier
- Example: If player finishes in 60s (Gold tier), apply speed multiplier to tier rewards

### Fallback Rewards
- Keep `addreward` command and `fallbackRewardCommands`
- If no tier rewards are set, use fallback rewards with difficulty multipliers
- No migration needed - both systems coexist

### RNG Chance System
- Each reward (tier or fallback) has a chance value (0.0-1.0)
- 1.0 = always given, 0.5 = 50% chance, etc.
- Syntax: `/challenge addtierreward <tier> <type> [args] <chance>`
- Syntax: `/challenge addreward <command> <chance>`
- Adds grind element - players may need multiple runs to get rare rewards

### Tab Completion
- All new commands must have proper tab completion
- Tier names: easy, medium, hard, extreme
- Reward types: hand, item, command
- Material names for item type

## Questions to Consider

1. **Item Serialization**: How to store HAND type rewards?
   - **Recommendation**: Serialize ItemStack to base64 or use Material + NBT data

2. **Multiplier Application**: When to apply multipliers?
   - **Recommendation**: Apply when collecting rewards (in `collectTierRewards`), so each tier's rewards get the correct multiplier

3. **Command Parsing**: How to handle complex commands in `applyRewardMultiplier`?
   - **Recommendation**: Keep existing `applyRewardMultiplier` logic, it already handles eco/give commands well

4. **Edit Mode**: Should tier rewards require edit mode?
   - **Recommendation**: Yes, same as current `addreward` command

5. **Speed Multiplier Calculation**: How to calculate speed-based multipliers?
   - **Recommendation**: Based on speed tier (Gold/Silver/Bronze) - maybe 1.5x, 1.25x, 1.0x
   - New record gets additional 1.5x boost on top of speed tier multiplier

## Testing Checklist

- [ ] Easy tier gets only easy rewards
- [ ] Medium tier gets easy (×1.5) + medium rewards
- [ ] Hard tier gets easy (×3.0) + medium (×2.0) + hard rewards
- [ ] Extreme tier gets all previous (with correct multipliers) + extreme rewards
- [ ] HAND type rewards work correctly
- [ ] ITEM type rewards work correctly
- [ ] COMMAND type rewards work correctly (with multipliers)
- [ ] Migration from old system works
- [ ] Speed tier rewards still work for SPEED challenges
- [ ] Edit mode requirements work
- [ ] Config saves/loads correctly

