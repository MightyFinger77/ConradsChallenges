package com.gix.conradchallenges;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

// FancyNPCs API (optional soft dependency)
// Note: Using try-catch for ClassNotFoundException since API may not be available at compile time

public class ConradChallengesPlugin extends JavaPlugin implements Listener {

    // Available subcommands for /conradchallenges
    private static final List<String> CONRAD_SUBCOMMANDS = Arrays.asList(
        "setnpc", "removenpc", "removeallnpcs", "reload", "challenge", "party", "top"
    );
    
    // Available subcommands for /conradchallenges challenge
    private static final List<String> CHALLENGE_SUBCOMMANDS = Arrays.asList(
        "create", "delete", "setbook", "clearbook", "setdestination", "settype", "setboss", 
        "settimer", "setitem", "setspeed", "setcooldown", "settimelimit", 
        "setmaxparty", "addreward", "clearrewards", "addtierreward", "cleartierrewards", "listtierrewards", "removereward", "addtoptimereward",
        "list", "info",
        "setregenerationarea", "clearregenerationarea", "captureregeneration", "setautoregenerationy",
        "setchallengearea", "clearchallengearea", "areasync",
        "edit", "save", "cancel", "lockdown", "unlockdown"
    );
    
    // Available subcommands for /conradchallenges party
    private static final List<String> PARTY_SUBCOMMANDS = Arrays.asList(
        "start", "confirm"
    );

    // ====== TYPES ======

    private enum CompletionType {
        BOSS,
        TIMER,
        ITEM,
        SPEED,
        NONE
    }

    private static class SpeedTier {
        final String name;
        final int maxSeconds;
        final List<String> rewardCommands;

        SpeedTier(String name, int maxSeconds, List<String> rewardCommands) {
            this.name = name;
            this.maxSeconds = maxSeconds;
            this.rewardCommands = rewardCommands;
        }
    }

    private static class TierReward {
        enum Type { HAND, ITEM, COMMAND }
        final Type type;
        final double chance; // 0.0 to 1.0
        Material material;      // For ITEM type
        int amount;             // For ITEM type
        ItemStack itemStack;    // For HAND type
        String command;         // For COMMAND type

        TierReward(Type type, double chance) {
            this.type = type;
            this.chance = Math.max(0.0, Math.min(1.0, chance)); // Clamp to 0.0-1.0
        }
    }

    private static class RewardWithChance {
        final String command;
        final double chance; // 0.0 to 1.0

        RewardWithChance(String command, double chance) {
            this.command = command;
            this.chance = Math.max(0.0, Math.min(1.0, chance)); // Clamp to 0.0-1.0
        }
    }

    private static class BossRequirement {
        final EntityType type;
        final String name; // optional

        BossRequirement(EntityType type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private static class ItemRequirement {
        final Material material;
        final int amount;
        final boolean consume;

        ItemRequirement(Material material, int amount, boolean consume) {
            this.material = material;
            this.amount = amount;
            this.consume = consume;
        }
    }

    private static class ChallengeConfig {
        final String id;
        String bookTitle;

        Location destination;
        long cooldownSeconds;

        CompletionType completionType;
        BossRequirement bossRequirement;
        int timerSeconds;
        ItemRequirement itemRequirement;
        List<SpeedTier> speedTiers;

        List<RewardWithChance> fallbackRewardCommands;
        Map<String, List<TierReward>> difficultyTierRewards; // Key: "easy", "medium", "hard", "extreme"
        List<TierReward> topTimeRewards; // Rewards only given for new top times (SPEED challenges)

        // -1 = unlimited party size
        int maxPartySize;

        // 0 = no time limit
        int timeLimitSeconds;
        List<Integer> timeLimitWarnings;

        // Regeneration area (two corners defining a bounding box)
        Location regenerationCorner1;
        Location regenerationCorner2;
        // If true, Y range extends from bedrock to build height (ignores corner Y values)
        boolean regenerationAutoExtendY;

        // Challenge area (two corners defining the playable area - used for teleport, protection, etc.)
        Location challengeCorner1;
        Location challengeCorner2;

        // Lockdown: if true, challenge is disabled and players cannot start it
        boolean lockedDown;

        ChallengeConfig(String id) {
            this.id = id;
            this.cooldownSeconds = 0;
            this.lockedDown = false;
            this.regenerationAutoExtendY = false; // Default to manual Y range
            this.completionType = CompletionType.NONE;
            this.fallbackRewardCommands = new ArrayList<>();
            this.difficultyTierRewards = new HashMap<>();
            this.topTimeRewards = new ArrayList<>();
            this.speedTiers = new ArrayList<>();
            this.maxPartySize = -1;
            this.timeLimitSeconds = 0;
            this.timeLimitWarnings = new ArrayList<>();
        }
    }

    private static class PendingChallenge {
        final String challengeId;
        String difficultyTier; // "easy", "medium", "hard", "extreme"

        PendingChallenge(String challengeId) {
            this.challengeId = challengeId;
            this.difficultyTier = "easy"; // Default
        }
    }

    private static class PartySession {
        final UUID leader;
        final String challengeId;
        final long createdAt;
        final Set<UUID> eligiblePlayers;
        final Set<UUID> acceptedPlayers;
        String difficultyTier; // "easy", "medium", "hard", "extreme"

        PartySession(UUID leader, String challengeId, Set<UUID> eligiblePlayers) {
            this.leader = leader;
            this.challengeId = challengeId;
            this.createdAt = System.currentTimeMillis();
            this.eligiblePlayers = eligiblePlayers;
            this.acceptedPlayers = new HashSet<>();
            this.difficultyTier = "easy"; // Default
            // Leader is automatically accepted
            this.acceptedPlayers.add(leader);
        }
    }

    private static class QueueEntry {
        final UUID playerUuid;  // For solo: player UUID, for party: leader UUID
        final String challengeId;
        final boolean isParty;
        final long queuedAt;

        QueueEntry(UUID playerUuid, String challengeId, boolean isParty) {
            this.playerUuid = playerUuid;
            this.challengeId = challengeId;
            this.isParty = isParty;
            this.queuedAt = System.currentTimeMillis();
        }
    }

    // Wrapper class to allow dynamic countdown adjustment
    private static class CountdownTaskWrapper {
        volatile int seconds;
        final BukkitRunnable task;
        final String challengeId;
        final Set<UUID> acceptedPlayers; // Only these players will teleport (modifiable)

        CountdownTaskWrapper(int seconds, BukkitRunnable task, String challengeId, Set<UUID> acceptedPlayers) {
            this.seconds = seconds;
            this.task = task;
            this.challengeId = challengeId;
            this.acceptedPlayers = acceptedPlayers != null ? new HashSet<>(acceptedPlayers) : null;
        }

        void setSeconds(int newSeconds) {
            this.seconds = newSeconds;
        }
    }

    // ====== STATE ======

    private final Set<UUID> npcIds = new HashSet<>();

    private final Map<String, ChallengeConfig> challenges = new HashMap<>();
    private final Map<String, String> bookTitleToChallengeId = new HashMap<>();

    private final Map<UUID, PendingChallenge> pendingChallenges = new HashMap<>();
    private final Map<UUID, String> activeChallenges = new HashMap<>();
    private final Map<UUID, String> completedChallenges = new HashMap<>();
    private final Map<UUID, Long> challengeStartTimes = new HashMap<>();

    // speed best times per challenge
    private final Map<String, Map<UUID, Long>> bestTimes = new HashMap<>();
    // cooldowns: last completion
    private final Map<String, Map<UUID, Long>> lastCompletionTimes = new HashMap<>();
    // first-time completions (per player per challenge)
    private final Map<String, Set<UUID>> everCompleted = new HashMap<>();

    // party: leader uuid -> session
    private final Map<UUID, PartySession> partySessions = new HashMap<>();

    // challenges that are in countdown before teleport
    private final Set<String> reservedChallenges = new HashSet<>();

    // Queue system: challenge ID -> queue of entries (FIFO)
    private final Map<String, Queue<QueueEntry>> challengeQueues = new HashMap<>();
    // Track which players/parties are in queue (for quick lookup when denying)
    private final Map<UUID, QueueEntry> playerQueueEntries = new HashMap<>();

    // Track active countdown tasks (challenge ID -> countdown task wrapper)
    private final Map<String, CountdownTaskWrapper> activeCountdownTasks = new HashMap<>();
    // Track which challenge a player is in countdown for (player UUID -> challenge ID)
    private final Map<UUID, String> playerCountdownChallenges = new HashMap<>();
    
    // Track regeneration status per challenge (challenge ID -> true if regeneration is complete)
    private final Map<String, Boolean> challengeRegenerationStatus = new HashMap<>();
    // Track which players are in which countdown (challenge ID -> list of players)
    private final Map<String, List<Player>> countdownPlayers = new HashMap<>();
    // Track bossbars for countdown display (player UUID -> bossbar)
    private final Map<UUID, BossBar> playerCountdownBossBars = new HashMap<>();

    // Anger system: when GateKeeper is mad at a player
    private final Map<UUID, Long> angryUntil = new HashMap<>();
    private final Set<UUID> requiresApology = new HashSet<>();

    private Location spawnLocation;
    private File dataFile;
    private FileConfiguration dataConfig;
    private FileConfiguration messagesConfig;

    private Economy economy;
    private NamespacedKey challengeKey;
    private int partyRadius = 10;  // Default 10 blocks
    private int pendingChallengeTimeoutSeconds = 15;  // Default 15 seconds
    private int soloCountdownSeconds = 10;  // Default 10 seconds
    private int partyCountdownSeconds = 10;  // Default 10 seconds
    private String editModeDisconnectAction = "cancel";  // Default: cancel (restore from backup)
    private boolean barrierBoxingEnabled = false;  // Default: false (barrier boxing disabled)
    private final Map<UUID, BukkitTask> pendingChallengeTimeouts = new HashMap<>();
    
    // Track players who died in challenges and need to be teleported on respawn
    private final Map<UUID, String> pendingDeathTeleports = new HashMap<>();
    
    // Track disconnected players: UUID -> DisconnectInfo
    private static class DisconnectInfo {
        final String challengeId;
        final long disconnectTime;
        final BukkitTask timeoutTask;
        
        DisconnectInfo(String challengeId, long disconnectTime, BukkitTask timeoutTask) {
            this.challengeId = challengeId;
            this.disconnectTime = disconnectTime;
            this.timeoutTask = timeoutTask;
        }
    }
    private final Map<UUID, DisconnectInfo> disconnectedPlayers = new HashMap<>();
    
    // Track player countdown display preferences (title, actionbar, bossbar, chat)
    private final Map<UUID, String> countdownDisplayPreferences = new HashMap<>();
    
    // Regeneration: Store initial block states for each challenge (challenge ID -> list of block states)
    private final Map<String, List<BlockState>> challengeInitialStates = new HashMap<>();
    
    // Edit mode: Track which challenges are being edited (challenge ID -> editor UUID)
    private final Map<String, UUID> challengeEditors = new HashMap<>();
    
    // World aliases for display names
    private final Map<String, String> worldAliases = new HashMap<>();
    private final Map<String, String> challengeAliases = new HashMap<>();
    // Edit mode: Store original locations before edit (editor UUID -> original location)
    private final Map<UUID, Location> editorOriginalLocations = new HashMap<>();
    // Edit mode: Store original locations for disconnected players (editor UUID -> original location)
    // Used to restore location on reconnect
    private final Map<UUID, Location> editorDisconnectLocations = new HashMap<>();
    // Edit mode: Store original challenge configs before edit (challenge ID -> cloned config)
    private final Map<String, ChallengeConfig> challengeEditBackups = new HashMap<>();
    // Edit mode: Store original block states before edit (challenge ID -> list of block states)
    private final Map<String, List<BlockState>> challengeEditStateBackups = new HashMap<>();
    
    // Difficulty tiers: Track active challenge difficulty (challenge ID -> tier name)
    private final Map<String, String> activeChallengeDifficulties = new HashMap<>();
    // Difficulty tier modifiers (tier name -> level modifier)
    private final Map<String, Integer> difficultyTierModifiers = new HashMap<>();
    // Difficulty tier reward multipliers (tier name -> multiplier)
    private final Map<String, Double> difficultyRewardMultipliers = new HashMap<>();
    // Cumulative multipliers (tier name -> total multiplier including all previous tiers)
    private final Map<String, Double> cumulativeRewardMultipliers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.challengeKey = new NamespacedKey(this, "conrad_challenge_id");

        // Migrate config to add any missing new options
        migrateConfig();

        setupEconomy();
        loadMessages();
        loadNpcIds();
        loadSpawnLocation();
        loadWorldAliases();
        loadChallengeAliases();
        loadDifficultyTiers();
        loadDifficultyRewardMultipliers();
        loadChallengesFromConfig();
        setupDataFile();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        setupPlaceholderAPI();
        setupMythicMobsListener();

        getLogger().info("ConradChallenges v2.0 enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("ConradChallenges v2.0 disabled.");
    }

    // ====== ECONOMY ======

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found; economy-based fines and rewards that use eco commands may not work.");
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("No Economy provider found via Vault.");
            economy = null;
            return;
        }
        economy = rsp.getProvider();
        if (economy != null) {
            getLogger().info("Hooked into economy provider: " + economy.getName());
        } else {
            getLogger().warning("Could not hook Economy provider via Vault.");
        }
    }

    // ====== PLACEHOLDERAPI ======

    private void setupPlaceholderAPI() {
        // Use a delayed task to ensure PlaceholderAPI is fully loaded
        getServer().getScheduler().runTaskLater(this, () -> {
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && 
                getServer().getPluginManager().isPluginEnabled(getServer().getPluginManager().getPlugin("PlaceholderAPI"))) {
                boolean registered = new ConradChallengesPlaceholders(this).register();
                if (registered) {
                    getLogger().info("PlaceholderAPI expansion registered successfully!");
                } else {
                    getLogger().warning("Failed to register PlaceholderAPI expansion. Check console for errors.");
                }
            } else {
                getLogger().info("PlaceholderAPI not found or not enabled. Placeholders will not be available.");
            }
        }, 20L); // 1 second delay to ensure PlaceholderAPI is ready
    }

    // ====== DATA.YML ======

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadData() {
        // bestTimes
        ConfigurationSection bestTimesSec = dataConfig.getConfigurationSection("bestTimes");
        if (bestTimesSec != null) {
            for (String challengeId : bestTimesSec.getKeys(false)) {
                ConfigurationSection challSec = bestTimesSec.getConfigurationSection(challengeId);
                if (challSec == null) continue;
                Map<UUID, Long> map = new HashMap<>();
                for (String uuidStr : challSec.getKeys(false)) {
                    long val = challSec.getLong(uuidStr);
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        map.put(uuid, val);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                bestTimes.put(challengeId, map);
            }
        }

        // lastCompletion
        ConfigurationSection lastCompSec = dataConfig.getConfigurationSection("lastCompletion");
        if (lastCompSec != null) {
            for (String challengeId : lastCompSec.getKeys(false)) {
                ConfigurationSection challSec = lastCompSec.getConfigurationSection(challengeId);
                if (challSec == null) continue;
                Map<UUID, Long> map = new HashMap<>();
                for (String uuidStr : challSec.getKeys(false)) {
                    long val = challSec.getLong(uuidStr);
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        map.put(uuid, val);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                lastCompletionTimes.put(challengeId, map);
            }
        }

        // everCompleted
        ConfigurationSection everSec = dataConfig.getConfigurationSection("everCompleted");
        if (everSec != null) {
            for (String challengeId : everSec.getKeys(false)) {
                List<String> uuids = everSec.getStringList(challengeId);
                Set<UUID> set = new HashSet<>();
                for (String uuidStr : uuids) {
                    try {
                        set.add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                everCompleted.put(challengeId, set);
            }
        }

        // Load countdown preferences from individual player files
        loadCountdownPreferences();
    }
    
    private void loadCountdownPreferences() {
        File playerDataFolder = new File(getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        if (!playerDataFolder.isDirectory()) {
            return;
        }
        
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        
        for (File file : files) {
            try {
                String fileName = file.getName();
                String uuidStr = fileName.substring(0, fileName.length() - 4); // Remove .yml extension
                UUID uuid = UUID.fromString(uuidStr);
                
                FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(file);
                String displayType = playerConfig.getString("countdown-display");
                if (displayType != null && (displayType.equals("title") || displayType.equals("actionbar") || 
                    displayType.equals("bossbar") || displayType.equals("chat"))) {
                    countdownDisplayPreferences.put(uuid, displayType);
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip this file
            }
        }
    }
    
    private void savePlayerCountdownPreference(UUID uuid, String displayType) {
        File playerDataFolder = new File(getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        
        File playerFile = new File(playerDataFolder, uuid.toString() + ".yml");
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        
        playerConfig.set("countdown-display", displayType);
        
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            getLogger().severe("Could not save player countdown preference for " + uuid + ": " + e.getMessage());
        }
    }

    private void saveData() {
        dataConfig.set("bestTimes", null);
        dataConfig.set("lastCompletion", null);
        dataConfig.set("everCompleted", null);

        ConfigurationSection bestTimesSec = dataConfig.createSection("bestTimes");
        for (Map.Entry<String, Map<UUID, Long>> entry : bestTimes.entrySet()) {
            ConfigurationSection challSec = bestTimesSec.createSection(entry.getKey());
            for (Map.Entry<UUID, Long> e : entry.getValue().entrySet()) {
                challSec.set(e.getKey().toString(), e.getValue());
            }
        }

        ConfigurationSection lastCompSec = dataConfig.createSection("lastCompletion");
        for (Map.Entry<String, Map<UUID, Long>> entry : lastCompletionTimes.entrySet()) {
            ConfigurationSection challSec = lastCompSec.createSection(entry.getKey());
            for (Map.Entry<UUID, Long> e : entry.getValue().entrySet()) {
                challSec.set(e.getKey().toString(), e.getValue());
            }
        }

        ConfigurationSection everSec = dataConfig.createSection("everCompleted");
        for (Map.Entry<String, Set<UUID>> entry : everCompleted.entrySet()) {
            List<String> list = entry.getValue().stream().map(UUID::toString).collect(Collectors.toList());
            everSec.set(entry.getKey(), list);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    // ====== CONFIG LOADING ======

    private void loadSpawnLocation() {
        FileConfiguration config = getConfig();
        String worldName = config.getString("spawn-world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world != null) {
            spawnLocation = world.getSpawnLocation();
            getLogger().info("Return spawn set to world: " + world.getName());
        } else {
            getLogger().warning("Could not determine spawn world. /challengecomplete may not work.");
        }
        
        // Load party radius
        partyRadius = config.getInt("party-radius", 10);
        if (partyRadius < 1) {
            partyRadius = 10;
            getLogger().warning("Invalid party-radius in config, using default: 10");
        }
        getLogger().info("Party radius set to: " + partyRadius + " blocks");
        
        // Load pending challenge timeout
        pendingChallengeTimeoutSeconds = config.getInt("pending-challenge-timeout-seconds", 15);
        if (pendingChallengeTimeoutSeconds < 1) {
            pendingChallengeTimeoutSeconds = 15;
            getLogger().warning("Invalid pending-challenge-timeout-seconds in config, using default: 15");
        }
        getLogger().info("Pending challenge timeout set to: " + pendingChallengeTimeoutSeconds + " seconds");
        
        // Load countdown times
        soloCountdownSeconds = config.getInt("solo-countdown-seconds", 10);
        if (soloCountdownSeconds < 1) {
            soloCountdownSeconds = 10;
            getLogger().warning("Invalid solo-countdown-seconds in config, using default: 10");
        }
        getLogger().info("Solo countdown set to: " + soloCountdownSeconds + " seconds");
        
        partyCountdownSeconds = config.getInt("party-countdown-seconds", 10);
        if (partyCountdownSeconds < 1) {
            partyCountdownSeconds = 10;
            getLogger().warning("Invalid party-countdown-seconds in config, using default: 10");
        }
        getLogger().info("Party countdown set to: " + partyCountdownSeconds + " seconds");
        
        // Load edit mode disconnect action
        String disconnectAction = config.getString("edit-mode.disconnect-action", "cancel");
        if (disconnectAction != null && (disconnectAction.equalsIgnoreCase("cancel") || disconnectAction.equalsIgnoreCase("save"))) {
            editModeDisconnectAction = disconnectAction.toLowerCase();
        } else {
            editModeDisconnectAction = "cancel";
            getLogger().warning("Invalid edit-mode.disconnect-action in config (must be 'cancel' or 'save'), using default: cancel");
        }
        getLogger().info("Edit mode disconnect action set to: " + editModeDisconnectAction);
        
        // Load barrier boxing setting
        barrierBoxingEnabled = config.getBoolean("challenge-area.barrier-boxing", false);
        getLogger().info("Barrier boxing for challenge areas: " + (barrierBoxingEnabled ? "enabled" : "disabled"));
    }
    
    private void loadWorldAliases() {
        FileConfiguration config = getConfig();
        ConfigurationSection aliasesSection = config.getConfigurationSection("world-aliases");
        worldAliases.clear();
        
        if (aliasesSection != null) {
            for (String worldName : aliasesSection.getKeys(false)) {
                String alias = aliasesSection.getString(worldName);
                if (alias != null && !alias.isEmpty()) {
                    worldAliases.put(worldName, ChatColor.translateAlternateColorCodes('&', alias));
                }
            }
            getLogger().info("Loaded " + worldAliases.size() + " world aliases");
        } else {
            getLogger().info("No world aliases configured");
        }
    }
    
    /**
     * Loads challenge aliases from config.yml
     */
    private void loadChallengeAliases() {
        FileConfiguration config = getConfig();
        ConfigurationSection aliasesSection = config.getConfigurationSection("challenge-aliases");
        challengeAliases.clear();
        
        if (aliasesSection != null) {
            for (String challengeId : aliasesSection.getKeys(false)) {
                String alias = aliasesSection.getString(challengeId);
                if (alias != null && !alias.isEmpty()) {
                    challengeAliases.put(challengeId, ChatColor.translateAlternateColorCodes('&', alias));
                }
            }
            getLogger().info("Loaded " + challengeAliases.size() + " challenge aliases");
        } else {
            getLogger().info("No challenge aliases configured");
        }
    }
    
    /**
     * Gets the challenge alias for a challenge ID, or returns the challenge ID if no alias is set.
     * @param challengeId The challenge ID to get the alias for
     * @return The challenge alias (with color codes) or the challenge ID if no alias exists
     */
    public String getChallengeAlias(String challengeId) {
        if (challengeId == null) return "Unknown";
        return challengeAliases.getOrDefault(challengeId, challengeId);
    }
    
    /**
     * Gets the world alias for a world name, or returns the world name if no alias is set.
     * @param worldName The world name to get the alias for
     * @return The world alias (with color codes) or the world name if no alias exists
     */
    public String getWorldAlias(String worldName) {
        if (worldName == null) return "Unknown";
        return worldAliases.getOrDefault(worldName, worldName);
    }
    
    /**
     * Gets the world alias for a player's current world.
     * @param player The player to get the world alias for
     * @return The world alias (with color codes) or the world name if no alias exists
     */
    public String getWorldAlias(Player player) {
        if (player == null || player.getWorld() == null) return "Unknown";
        return getWorldAlias(player.getWorld().getName());
    }
    
    /**
     * Gets the world alias for an offline player's last known world, or current world if online.
     * @param player The offline player to get the world alias for
     * @return The world alias (with color codes) or the world name if no alias exists
     */
    public String getWorldAlias(OfflinePlayer player) {
        if (player == null) return "Unknown";
        if (player.isOnline() && player.getPlayer() != null) {
            return getWorldAlias(player.getPlayer());
        }
        // For offline players, we can't determine their world, so return "Unknown"
        return "Unknown";
    }

    private void loadMessages() {
        // Migrate messages.yml first
        migrateMessages();
        
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        getLogger().info("Messages loaded from messages.yml");
    }

    /**
     * Migrate messages.yml to add missing options from newer versions
     * Preserves user values while adding new options and comments
     */
    private void migrateMessages() {
        try {
            // Get the actual messages file
            File messagesFile = new File(getDataFolder(), "messages.yml");
            
            if (!messagesFile.exists()) {
                return; // No migration needed if file doesn't exist
            }
            
            // Load the default messages from the jar resource
            InputStream defaultMessagesTextStream = getResource("messages.yml");
            if (defaultMessagesTextStream == null) {
                getLogger().warning("Could not load default messages.yml from jar for migration");
                return;
            }
            
            // Read default messages as text to preserve formatting
            List<String> currentLines = new ArrayList<>(java.nio.file.Files.readAllLines(messagesFile.toPath()));
            List<String> defaultLines = new ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(defaultMessagesTextStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            // Also load as YAML to check what's missing
            InputStream defaultMessagesYamlStream = getResource("messages.yml");
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultMessagesYamlStream));
            YamlConfiguration currentMessages = YamlConfiguration.loadConfiguration(messagesFile);
            
            // Check messages version
            int defaultVersion = defaultMessages.getInt("messages_version", 1);
            int currentVersion = currentMessages.getInt("messages_version", 0); // 0 means old messages without version
            
            // If versions match and messages has version field, no migration needed
            if (currentVersion == defaultVersion && currentMessages.contains("messages_version")) {
                return; // Messages are up to date
            }
            
            // Merge messages: Use default structure/comments, replace values with user's where they exist
            List<String> mergedLines = mergeConfigs(defaultLines, currentMessages, defaultMessages, currentLines);
            
            // Check for and log deprecated keys that were removed
            Set<String> deprecatedKeys = findDeprecatedKeys(currentMessages, defaultMessages);
            if (!deprecatedKeys.isEmpty()) {
                getLogger().info("Removed deprecated message keys: " + String.join(", ", deprecatedKeys));
            }
            
            // Update messages version
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, "messages_version");
            
            // Write merged messages
            java.nio.file.Files.write(messagesFile.toPath(), mergedLines, 
                java.nio.charset.StandardCharsets.UTF_8);
            
            getLogger().info("Messages migration completed - merged with default messages, preserving user values and all comments");
            
        } catch (Exception e) {
            getLogger().warning("Error during messages migration: " + e.getMessage());
            // Don't fail plugin startup if migration has issues
            e.printStackTrace();
        }
    }

    // Helper method to get a message from messages.yml with color code support and placeholders
    private String getMessage(String path, String... replacements) {
        String message = messagesConfig.getString(path, "&cMessage not found: " + path);
        // Replace color codes (&a, &c, etc. to §a, §c, etc.)
        message = message.replace('&', '§');
        // Replace placeholders
        if (replacements.length > 0 && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        return message;
    }

    private void loadNpcIds() {
        FileConfiguration config = getConfig();
        List<String> list = config.getStringList("npcs");
        npcIds.clear();
        for (String s : list) {
            try {
                npcIds.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
    
    private void loadDifficultyTiers() {
        FileConfiguration config = getConfig();
        ConfigurationSection tierSection = config.getConfigurationSection("difficulty-tiers");
        if (tierSection == null) {
            getLogger().warning("No difficulty-tiers section found in config, using defaults");
            difficultyTierModifiers.put("easy", 0);
            difficultyTierModifiers.put("medium", 10);
            difficultyTierModifiers.put("hard", 35);
            difficultyTierModifiers.put("extreme", 100);
            return;
        }
        
        // Load each tier with defaults
        difficultyTierModifiers.put("easy", tierSection.getInt("easy", 0));
        difficultyTierModifiers.put("medium", tierSection.getInt("medium", 10));
        difficultyTierModifiers.put("hard", tierSection.getInt("hard", 35));
        difficultyTierModifiers.put("extreme", tierSection.getInt("extreme", 100));
        
        getLogger().info("Loaded difficulty tiers: Easy=" + difficultyTierModifiers.get("easy") + 
                        ", Medium=" + difficultyTierModifiers.get("medium") + 
                        ", Hard=" + difficultyTierModifiers.get("hard") + 
                        ", Extreme=" + difficultyTierModifiers.get("extreme"));
    }
    
    private void loadDifficultyRewardMultipliers() {
        FileConfiguration config = getConfig();
        ConfigurationSection multiplierSection = config.getConfigurationSection("difficulty-reward-multipliers");
        if (multiplierSection == null) {
            getLogger().warning("No difficulty-reward-multipliers section found in config, using defaults");
            difficultyRewardMultipliers.put("easy", 1.0);
            difficultyRewardMultipliers.put("medium", 1.5);
            difficultyRewardMultipliers.put("hard", 2.0);
            difficultyRewardMultipliers.put("extreme", 3.0);
        } else {
            // Load each tier multiplier with defaults
            difficultyRewardMultipliers.put("easy", multiplierSection.getDouble("easy", 1.0));
            difficultyRewardMultipliers.put("medium", multiplierSection.getDouble("medium", 1.5));
            difficultyRewardMultipliers.put("hard", multiplierSection.getDouble("hard", 2.0));
            difficultyRewardMultipliers.put("extreme", multiplierSection.getDouble("extreme", 3.0));
        }
        
        // Calculate cumulative multipliers (each tier multiplies from previous)
        cumulativeRewardMultipliers.put("easy", difficultyRewardMultipliers.get("easy"));
        cumulativeRewardMultipliers.put("medium", cumulativeRewardMultipliers.get("easy") * difficultyRewardMultipliers.get("medium"));
        cumulativeRewardMultipliers.put("hard", cumulativeRewardMultipliers.get("medium") * difficultyRewardMultipliers.get("hard"));
        cumulativeRewardMultipliers.put("extreme", cumulativeRewardMultipliers.get("hard") * difficultyRewardMultipliers.get("extreme"));
        
        getLogger().info("Loaded difficulty reward multipliers: Easy=" + cumulativeRewardMultipliers.get("easy") + 
                        "x, Medium=" + cumulativeRewardMultipliers.get("medium") + 
                        "x, Hard=" + cumulativeRewardMultipliers.get("hard") + 
                        "x, Extreme=" + cumulativeRewardMultipliers.get("extreme") + "x");
    }
    
    private void saveNpcIds() {
        List<String> list = npcIds.stream().map(UUID::toString).collect(Collectors.toList());
        getConfig().set("npcs", list);
        saveConfig();
    }

    private void loadChallengesFromConfig() {
        challenges.clear();
        bookTitleToChallengeId.clear();

        FileConfiguration config = getConfig();
        ConfigurationSection root = config.getConfigurationSection("challenges");
        if (root == null) {
            getLogger().warning("No 'challenges' section in config.yml");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;

            ChallengeConfig cfg = new ChallengeConfig(id);

            cfg.bookTitle = cs.getString("book-title");

            String worldName = cs.getString("world");
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    double x = cs.getDouble("x");
                    double y = cs.getDouble("y");
                    double z = cs.getDouble("z");
                    float yaw = (float) cs.getDouble("yaw");
                    float pitch = (float) cs.getDouble("pitch");
                    cfg.destination = new Location(w, x, y, z, yaw, pitch);
                } else {
                    getLogger().warning("Challenge '" + id + "' has invalid world: " + worldName);
                }
            }

            cfg.cooldownSeconds = cs.getLong("cooldown-seconds", 0L);
            cfg.maxPartySize = cs.getInt("max-party-size", -1);
            cfg.timeLimitSeconds = cs.getInt("time-limit-seconds", 0);
            cfg.timeLimitWarnings = cs.getIntegerList("time-limit-warnings");
            if (cfg.timeLimitSeconds > 0 && cfg.timeLimitWarnings.isEmpty()) {
                cfg.timeLimitWarnings = Arrays.asList(60, 10);
            }
            cfg.lockedDown = cs.getBoolean("locked-down", false);

            ConfigurationSection compSec = cs.getConfigurationSection("completion");
            cfg.completionType = CompletionType.NONE;
            if (compSec != null) {
                String typeStr = compSec.getString("type", "NONE").toUpperCase(Locale.ROOT);
                try {
                    cfg.completionType = CompletionType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid completion type '" + typeStr + "' for challenge '" + id + "'. Using NONE.");
                    cfg.completionType = CompletionType.NONE;
                }

                switch (cfg.completionType) {
                    case BOSS -> {
                        String bossTypeName = compSec.getString("boss-type");
                        String bossName = compSec.getString("boss-name");
                        if (bossTypeName != null) {
                            try {
                                EntityType et = EntityType.valueOf(bossTypeName.toUpperCase(Locale.ROOT));
                                cfg.bossRequirement = new BossRequirement(et, bossName);
                            } catch (IllegalArgumentException ex) {
                                getLogger().warning("Invalid boss-type '" + bossTypeName + "' for challenge '" + id + "'.");
                            }
                        }
                    }
                    case TIMER -> cfg.timerSeconds = compSec.getInt("seconds", 60);
                    case ITEM -> {
                        String matName = compSec.getString("item");
                        int amount = compSec.getInt("amount", 1);
                        boolean consume = compSec.getBoolean("consume-on-complete", true);
                        if (matName != null) {
                            try {
                                Material mat = Material.valueOf(matName.toUpperCase(Locale.ROOT));
                                cfg.itemRequirement = new ItemRequirement(mat, Math.max(1, amount), consume);
                            } catch (IllegalArgumentException ex) {
                                getLogger().warning("Invalid material '" + matName + "' for challenge '" + id + "'.");
                            }
                        }
                    }
                    case SPEED -> {
                        cfg.timerSeconds = compSec.getInt("max-seconds", 300);
                        cfg.speedTiers.clear();
                        List<Map<?, ?>> tierMaps = compSec.getMapList("tiers");
                        for (Map<?, ?> map : tierMaps) {
                            Object nameObj = map.get("name");
                            String tName = nameObj != null ? String.valueOf(nameObj) : "TIER";
                            Object maxSecObj = map.get("max-seconds");
                            int maxSec = Integer.parseInt(String.valueOf(maxSecObj != null ? maxSecObj : 300));
                            Object rcObj = map.get("reward-commands");
                            List<String> rcs = new ArrayList<>();
                            if (rcObj instanceof List<?> list) {
                                for (Object o : list) {
                                    rcs.add(String.valueOf(o));
                                }
                            }
                            cfg.speedTiers.add(new SpeedTier(tName, maxSec, rcs));
                        }
                    }
                    case NONE -> {
                    }
                }
            }

            // Load fallback rewards (with chance support)
            cfg.fallbackRewardCommands.clear();
            if (cs.contains("reward-commands")) {
                Object rcObj = cs.get("reward-commands");
                if (rcObj instanceof List<?>) {
                    List<?> rcList = (List<?>) rcObj;
                    for (Object item : rcList) {
                        if (item instanceof Map<?, ?>) {
                            // New format: {command: "...", chance: 1.0}
                            Map<?, ?> map = (Map<?, ?>) item;
                            String cmd = String.valueOf(map.get("command"));
                            double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 1.0;
                            cfg.fallbackRewardCommands.add(new RewardWithChance(cmd, chance));
                        } else {
                            // Old format: just a string (backward compatibility)
                            String cmd = String.valueOf(item);
                            cfg.fallbackRewardCommands.add(new RewardWithChance(cmd, 1.0));
                        }
                    }
                }
            }
            
            // Load difficulty tier rewards
            cfg.difficultyTierRewards.clear();
            ConfigurationSection tierRewardsSec = cs.getConfigurationSection("difficulty-tier-rewards");
            if (tierRewardsSec != null) {
                for (String tier : Arrays.asList("easy", "medium", "hard", "extreme")) {
                    ConfigurationSection tierSec = tierRewardsSec.getConfigurationSection(tier);
                    if (tierSec != null) {
                        List<TierReward> tierRewards = new ArrayList<>();
                        List<Map<?, ?>> rewardMaps = tierSec.getMapList("rewards");
                        for (Map<?, ?> map : rewardMaps) {
                            String typeStr = String.valueOf(map.get("type")).toUpperCase();
                            double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 1.0;
                            try {
                                TierReward.Type type = TierReward.Type.valueOf(typeStr);
                                TierReward reward = new TierReward(type, chance);
                                switch (type) {
                                    case HAND -> {
                                        String itemData = String.valueOf(map.get("item-data"));
                                        if (itemData != null && !itemData.isEmpty()) {
                                            try {
                                                // Deserialize ItemStack from base64 or use Material
                                                if (itemData.contains(";")) {
                                                    String[] parts = itemData.split(";");
                                                    Material mat = Material.valueOf(parts[0]);
                                                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                                                    reward.itemStack = new ItemStack(mat, amount);
                                                } else {
                                                    Material mat = Material.valueOf(itemData);
                                                    reward.itemStack = new ItemStack(mat, 1);
                                                }
                                            } catch (Exception e) {
                                                getLogger().warning("Failed to load HAND reward item for challenge " + id + ": " + e.getMessage());
                                            }
                                        }
                                    }
                                    case ITEM -> {
                                        String matStr = String.valueOf(map.get("material"));
                                        reward.material = Material.valueOf(matStr);
                                        reward.amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
                                    }
                                    case COMMAND -> {
                                        reward.command = String.valueOf(map.get("command"));
                                    }
                                }
                                tierRewards.add(reward);
                            } catch (IllegalArgumentException e) {
                                getLogger().warning("Invalid reward type '" + typeStr + "' for challenge " + id);
                            }
                        }
                        if (!tierRewards.isEmpty()) {
                            cfg.difficultyTierRewards.put(tier, tierRewards);
                        }
                    }
                }
            }
            
            // Load top time rewards
            cfg.topTimeRewards.clear();
            ConfigurationSection topTimeSec = cs.getConfigurationSection("top-time-rewards");
            if (topTimeSec != null) {
                List<Map<?, ?>> rewardMaps = topTimeSec.getMapList("rewards");
                for (Map<?, ?> map : rewardMaps) {
                    String typeStr = String.valueOf(map.get("type")).toUpperCase();
                    double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 1.0;
                    try {
                        TierReward.Type type = TierReward.Type.valueOf(typeStr);
                        TierReward reward = new TierReward(type, chance);
                        switch (type) {
                            case HAND -> {
                                String itemData = String.valueOf(map.get("item-data"));
                                if (itemData != null && !itemData.isEmpty()) {
                                    try {
                                        if (itemData.contains(";")) {
                                            String[] parts = itemData.split(";");
                                            Material mat = Material.valueOf(parts[0]);
                                            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                                            reward.itemStack = new ItemStack(mat, amount);
                                        } else {
                                            Material mat = Material.valueOf(itemData);
                                            reward.itemStack = new ItemStack(mat, 1);
                                        }
                                    } catch (Exception e) {
                                        getLogger().warning("Failed to load HAND reward item for challenge " + id + ": " + e.getMessage());
                                    }
                                }
                            }
                            case ITEM -> {
                                String matStr = String.valueOf(map.get("material"));
                                reward.material = Material.valueOf(matStr);
                                reward.amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
                            }
                            case COMMAND -> {
                                reward.command = String.valueOf(map.get("command"));
                            }
                        }
                        cfg.topTimeRewards.add(reward);
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid reward type '" + typeStr + "' for challenge " + id);
                    }
                }
            }

            // Load regeneration area
            ConfigurationSection regenSec = cs.getConfigurationSection("regeneration");
            if (regenSec != null) {
                String regenWorldName = regenSec.getString("world");
                if (regenWorldName != null) {
                    World regenWorld = Bukkit.getWorld(regenWorldName);
                    if (regenWorld != null) {
                        ConfigurationSection corner1Sec = regenSec.getConfigurationSection("corner1");
                        ConfigurationSection corner2Sec = regenSec.getConfigurationSection("corner2");
                        if (corner1Sec != null && corner2Sec != null) {
                            double x1 = corner1Sec.getDouble("x");
                            double y1 = corner1Sec.getDouble("y");
                            double z1 = corner1Sec.getDouble("z");
                            double x2 = corner2Sec.getDouble("x");
                            double y2 = corner2Sec.getDouble("y");
                            double z2 = corner2Sec.getDouble("z");
                            cfg.regenerationCorner1 = new Location(regenWorld, x1, y1, z1);
                            cfg.regenerationCorner2 = new Location(regenWorld, x2, y2, z2);
                        }
                        // Load auto-extend Y option (defaults to false if not set)
                        cfg.regenerationAutoExtendY = regenSec.getBoolean("auto-extend-y", false);
                        // Try to capture initial state if not already captured
                        if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null && 
                            !challengeInitialStates.containsKey(id)) {
                            // Capture initial state asynchronously during config load
                            captureInitialState(cfg, success -> {
                                if (success) {
                                    getLogger().info("Captured initial state for challenge '" + id + "' during config load");
                                } else {
                                    getLogger().warning("Failed to capture initial state for challenge '" + id + "' during config load");
                                }
                            });
                        }
                    } else {
                        getLogger().warning("Challenge '" + id + "' has invalid regeneration world: " + regenWorldName);
                    }
                }
            }

            // Load challenge area
            ConfigurationSection challengeAreaSec = cs.getConfigurationSection("challenge-area");
            if (challengeAreaSec != null) {
                String challengeWorldName = challengeAreaSec.getString("world");
                if (challengeWorldName != null) {
                    World challengeWorld = Bukkit.getWorld(challengeWorldName);
                    if (challengeWorld != null) {
                        ConfigurationSection corner1Sec = challengeAreaSec.getConfigurationSection("corner1");
                        ConfigurationSection corner2Sec = challengeAreaSec.getConfigurationSection("corner2");
                        if (corner1Sec != null && corner2Sec != null) {
                            double x1 = corner1Sec.getDouble("x");
                            double y1 = corner1Sec.getDouble("y");
                            double z1 = corner1Sec.getDouble("z");
                            double x2 = corner2Sec.getDouble("x");
                            double y2 = corner2Sec.getDouble("y");
                            double z2 = corner2Sec.getDouble("z");
                            cfg.challengeCorner1 = new Location(challengeWorld, x1, y1, z1);
                            cfg.challengeCorner2 = new Location(challengeWorld, x2, y2, z2);
                        }
                    } else {
                        getLogger().warning("Challenge '" + id + "' has invalid challenge area world: " + challengeWorldName);
                    }
                }
            }

            challenges.put(id, cfg);
            if (cfg.bookTitle != null) {
                bookTitleToChallengeId.put(cfg.bookTitle, id);
            }

            getLogger().info("Loaded challenge '" + id + "' (type " + cfg.completionType + ")");
        }
    }

    // ====== CONFIG MIGRATION ======

    /**
     * Migrate config.yml to add missing options from newer versions
     * Preserves user values while adding new options and comments
     */
    private void migrateConfig() {
        try {
            // Get the actual config file
            File configFile = new File(getDataFolder(), "config.yml");
            
            if (!configFile.exists()) {
                return; // No migration needed if file doesn't exist
            }
            
            // Load the default config from the jar resource
            InputStream defaultConfigTextStream = getResource("config.yml");
            if (defaultConfigTextStream == null) {
                getLogger().warning("Could not load default config.yml from jar for migration");
                return;
            }
            
            // Read default config as text to preserve formatting
            List<String> currentLines = new ArrayList<>(java.nio.file.Files.readAllLines(configFile.toPath()));
            List<String> defaultLines = new ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(defaultConfigTextStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            // Also load as YAML to check what's missing (get resource again - getResource returns new stream)
            InputStream defaultConfigYamlStream = getResource("config.yml");
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigYamlStream));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Check config version
            int defaultVersion = defaultConfig.getInt("config_version", 1);
            int currentVersion = currentConfig.getInt("config_version", 0); // 0 means old config without version
            
            getLogger().info("Config migration check: current version=" + currentVersion + ", default version=" + defaultVersion);
            
            // Check if there are missing keys in current config that exist in default
            boolean hasMissingKeys = false;
            List<String> missingKeys = new ArrayList<>();
            for (String key : defaultConfig.getKeys(true)) {
                // Skip version key and challenges section (challenges is preserved separately)
                if (key.equals("config_version") || key.equals("challenges") || key.startsWith("challenges.")) {
                    continue;
                }
                if (!currentConfig.contains(key)) {
                    hasMissingKeys = true;
                    missingKeys.add(key);
                }
            }
            
            if (!missingKeys.isEmpty()) {
                getLogger().info("Found missing config keys: " + String.join(", ", missingKeys));
            }
            
            // If versions match, config has version field, AND no missing keys, no migration needed
            if (currentVersion == defaultVersion && currentConfig.contains("config_version") && !hasMissingKeys) {
                getLogger().info("Config is up to date, skipping migration");
                return; // Config is up to date
            }
            
            getLogger().info("Config migration needed - proceeding with migration...");
            
            // Simple merge approach: Use default config structure/comments, replace values with user's where they exist
            // This preserves all comments and formatting from default, while keeping user's custom values
            // Deprecated keys (in user config but not in default) are automatically removed
            // The 'challenges' section is completely ignored during migration - preserved as-is from original
            List<String> mergedLines = mergeConfigs(defaultLines, currentConfig, defaultConfig, currentLines);
            
            // Safety check: if challenges section was lost, abort migration
            YamlConfiguration mergedConfig = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(String.join("\n", mergedLines)));
            if (currentConfig.contains("challenges") && currentConfig.getConfigurationSection("challenges") != null) {
                ConfigurationSection originalChallenges = currentConfig.getConfigurationSection("challenges");
                ConfigurationSection mergedChallenges = mergedConfig.getConfigurationSection("challenges");
                if (mergedChallenges == null || mergedChallenges.getKeys(false).isEmpty()) {
                    // Challenges were lost! Abort migration and keep original
                    getLogger().severe("Config migration would have wiped challenges section! Aborting migration to preserve data.");
                    getLogger().severe("Please report this issue. Your config has been preserved.");
                    return; // Don't write the migrated config
                }
            }
            
            // Check for and log deprecated keys that were removed
            Set<String> deprecatedKeys = findDeprecatedKeys(currentConfig, defaultConfig);
            if (!deprecatedKeys.isEmpty()) {
                getLogger().info("Removed deprecated config keys: " + String.join(", ", deprecatedKeys));
            }
            
            // Update config version
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, "config_version");
            
            // Write merged config
            java.nio.file.Files.write(configFile.toPath(), mergedLines, 
                java.nio.charset.StandardCharsets.UTF_8);
            
            getLogger().info("Config migration completed - merged with default config, preserving user values and all comments");
            
            // Reload config
            reloadConfig();
        } catch (Exception e) {
            getLogger().warning("Error during config migration: " + e.getMessage());
            // Don't fail plugin startup if migration has issues
            e.printStackTrace();
        }
    }
    
    /**
     * Merge default config (with comments) with user config (with values)
     * Simple approach: Use default structure/comments, replace values with user's where they exist
     */
    private List<String> mergeConfigs(List<String> defaultLines, YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        return mergeConfigs(defaultLines, userConfig, defaultConfig, new ArrayList<>());
    }
    
    private List<String> mergeConfigs(List<String> defaultLines, YamlConfiguration userConfig, YamlConfiguration defaultConfig, List<String> originalUserLines) {
        List<String> merged = new ArrayList<>();
        
        // Track current path for nested keys - store both name and indent level
        Stack<Pair<String, Integer>> pathStack = new Stack<>();
        
        for (int i = 0; i < defaultLines.size(); i++) {
            String line = defaultLines.get(i);
            String trimmed = line.trim();
            int currentIndent = line.length() - trimmed.length();
            
            // Always preserve comments and blank lines
            if (trimmed.isEmpty() || line.startsWith("#")) {
                merged.add(line);
                continue;
            }
            
            // Pop sections we've left (based on indent level)
            while (!pathStack.isEmpty() && currentIndent <= pathStack.peek().getValue()) {
                pathStack.pop();
            }
            
            // Check if this is a list item (starts with -)
            if (trimmed.startsWith("-")) {
                // This is a list item - preserve as-is (lists are handled at the parent key level)
                merged.add(line);
                continue;
            }
            
            // Check if this is a key=value line
            if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                int colonIndex = trimmed.indexOf(':');
                String keyPart = trimmed.substring(0, colonIndex).trim();
                String valuePart = trimmed.substring(colonIndex + 1).trim();
                
                // Build full path for nested keys
                StringBuilder fullPathBuilder = new StringBuilder();
                for (Pair<String, Integer> pathEntry : pathStack) {
                    if (fullPathBuilder.length() > 0) {
                        fullPathBuilder.append(".");
                    }
                    fullPathBuilder.append(pathEntry.getKey());
                }
                if (fullPathBuilder.length() > 0) {
                    fullPathBuilder.append(".");
                }
                fullPathBuilder.append(keyPart);
                String fullPath = fullPathBuilder.toString();
                
                // Check if this is a section (value is empty and next line is indented or is a list)
                boolean isSection = valuePart.isEmpty();
                boolean isList = false;
                if (isSection && i + 1 < defaultLines.size()) {
                    // Look ahead to see if next non-comment line is indented or is a list item
                    for (int j = i + 1; j < defaultLines.size() && j < i + 10; j++) {
                        String nextLine = defaultLines.get(j);
                        String nextTrimmed = nextLine.trim();
                        if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                            continue;
                        }
                        int nextIndent = nextLine.length() - nextTrimmed.length();
                        if (nextTrimmed.startsWith("-")) {
                            // This is a list
                            isList = true;
                            isSection = true;
                            break;
                        } else if (nextIndent > currentIndent) {
                            isSection = true;
                            break;
                        } else {
                            // Next line is at same or less indent - not a section
                            break;
                        }
                    }
                }
                
                if (isSection) {
                    // Special handling: Skip challenges section entirely - preserve it from original
                    if (fullPath.equals("challenges")) {
                        // Extract challenges section from original user config
                        List<String> userSectionLines = extractSectionFromOriginalLines(originalUserLines, fullPath, currentIndent);
                        
                        if (!userSectionLines.isEmpty()) {
                            // Add section header (with default's comment if any)
                            merged.add(line);
                            // Add user's challenges section content (preserving their formatting)
                            merged.addAll(userSectionLines);
                            
                            // Skip the default section content
                            int sectionStartIndent = currentIndent;
                            while (i + 1 < defaultLines.size()) {
                                String nextLine = defaultLines.get(i + 1);
                                String nextTrimmed = nextLine.trim();
                                if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                    int nextIndent = nextLine.length() - nextTrimmed.length();
                                    if (nextIndent <= sectionStartIndent) {
                                        break; // Comment at section level or above - end of section
                                    }
                                    i++; // Skip comment/blank within section
                                } else {
                                    int nextIndent = nextLine.length() - nextTrimmed.length();
                                    if (nextIndent <= sectionStartIndent) {
                                        break; // End of section
                                    }
                                    i++; // Skip this line (it's part of default section)
                                }
                            }
                            
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                            continue;
                        }
                    }
                    
                    // This is a section - check if user has values for it
                    if (userConfig.contains(fullPath)) {
                        Object userValue = userConfig.get(fullPath);
                        
                        // If it's a list, we need to handle it specially
                        if (isList && userValue instanceof List) {
                            // Add the key line
                            merged.add(line);
                            // Add list items from user config
                            List<?> userList = (List<?>) userValue;
                            for (Object item : userList) {
                                String itemStr = formatYamlValue(item);
                                // Remove quotes if they were added (lists often don't need them)
                                if (itemStr.startsWith("\"") && itemStr.endsWith("\"")) {
                                    itemStr = itemStr.substring(1, itemStr.length() - 1);
                                }
                                merged.add(" ".repeat(currentIndent + 2) + "- " + itemStr);
                            }
                            // Skip the default list items and their comments - we've already added user's
                            // Skip until we're out of the list (next line at same or less indent)
                            while (i + 1 < defaultLines.size()) {
                                String nextLine = defaultLines.get(i + 1);
                                String nextTrimmed = nextLine.trim();
                                int nextIndent = nextLine.length() - nextTrimmed.length();
                                
                                // If it's a comment or blank line within the list, skip it
                                if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                    if (nextIndent > currentIndent) {
                                        i++; // Skip comment/blank within list
                                    } else {
                                        break; // Comment at section level or above - end of list
                                    }
                                } else if (nextTrimmed.startsWith("-") && nextIndent > currentIndent) {
                                    i++; // Skip this list item
                                } else {
                                    break; // End of list
                                }
                            }
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                            continue;
                        } else if (userValue instanceof ConfigurationSection) {
                            // Complex nested section (like challenges) - extract from original user file
                            // Preserve any comment on the section header line from default
                            String sectionHeader = line;
                            
                            // Extract this section from the original user config file
                            List<String> userSectionLines = extractSectionFromOriginalLines(originalUserLines, fullPath, currentIndent);
                            
                            if (!userSectionLines.isEmpty()) {
                                // Successfully extracted - use user's section
                                // Add section header (with default's comment if any)
                                merged.add(sectionHeader);
                                // Add user's section content (preserving their formatting)
                                merged.addAll(userSectionLines);
                                
                                // Skip the default section content
                                int sectionStartIndent = currentIndent;
                                while (i + 1 < defaultLines.size()) {
                                    String nextLine = defaultLines.get(i + 1);
                                    String nextTrimmed = nextLine.trim();
                                    if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                        // Keep comments/blank lines that are at section level (for spacing)
                                        int nextIndent = nextLine.length() - nextTrimmed.length();
                                        if (nextIndent <= sectionStartIndent) {
                                            break; // Comment at section level or above - end of section
                                        }
                                        i++; // Skip comment/blank within section
                                    } else {
                                        int nextIndent = nextLine.length() - nextTrimmed.length();
                                        if (nextIndent <= sectionStartIndent) {
                                            break; // End of section
                                        }
                                        i++; // Skip this line (it's part of default section)
                                    }
                                }
                                
                                pathStack.push(new Pair<>(keyPart, currentIndent));
                                continue;
                            } else {
                                // Couldn't extract from original - this is dangerous!
                                // For safety, preserve the original section by not migrating it
                                // Log a warning and use default (which might be empty, but won't wipe user data)
                                getLogger().warning("Could not extract section '" + fullPath + "' from original config during migration. Using default structure.");
                                // Use default but this might lose user data - better than crashing
                                merged.add(line);
                                pathStack.push(new Pair<>(keyPart, currentIndent));
                            }
                        } else {
                            // Regular section - add it and push to path stack
                            merged.add(line);
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                        }
                    } else {
                        // User doesn't have this section - use default (with all comments and list items)
                        merged.add(line);
                        pathStack.push(new Pair<>(keyPart, currentIndent));
                    }
                } else {
                    // This is a key=value line
                    // Skip version keys - they're handled separately by updateConfigVersion
                    if (keyPart.equals("config_version")) {
                        // Use default version line - it will be updated by updateConfigVersion
                        merged.add(line);
                    } else if (userConfig.contains(fullPath)) {
                        // User has this key - use their value but keep default's formatting
                        Object userValue = userConfig.get(fullPath);
                        String userValueStr = formatYamlValue(userValue);
                        
                        // Preserve inline comment if present
                        String inlineComment = "";
                        int commentIndex = valuePart.indexOf('#');
                        if (commentIndex >= 0) {
                            inlineComment = " " + valuePart.substring(commentIndex);
                        }
                        
                        // Replace value while preserving indentation and inline comment
                        merged.add(" ".repeat(currentIndent) + keyPart + ": " + userValueStr + inlineComment);
                    } else {
                        // User doesn't have this key - use default (with default value and comments)
                        merged.add(line);
                    }
                }
            } else {
                // Not a key=value line - preserve as-is
                merged.add(line);
            }
        }
        
        return merged;
    }
    
    /**
     * Extract a section from the original user config file lines
     * This preserves the exact formatting and comments from the user's file
     */
    private List<String> extractSectionFromOriginalLines(List<String> originalLines, String sectionPath, int targetIndent) {
        List<String> sectionLines = new ArrayList<>();
        
        // Find the section in the original file
        String[] pathParts = sectionPath.split("\\.");
        String sectionKey = pathParts[pathParts.length - 1];
        
        // Track path stack to find the right section
        Stack<Pair<String, Integer>> pathStack = new Stack<>();
        boolean foundSection = false;
        int sectionStartLine = -1;
        
        for (int i = 0; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            String trimmed = line.trim();
            
            // Skip comments and blank lines for path tracking, but we'll include them in extraction
            if (trimmed.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int currentIndent = line.length() - trimmed.length();
            
            // Pop sections we've left (based on indent level)
            while (!pathStack.isEmpty() && currentIndent <= pathStack.peek().getValue()) {
                pathStack.pop();
            }
            
            if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                int colonIndex = trimmed.indexOf(':');
                String keyPart = trimmed.substring(0, colonIndex).trim();
                String valuePart = trimmed.substring(colonIndex + 1).trim();
                
                // Build current path
                StringBuilder currentPath = new StringBuilder();
                for (Pair<String, Integer> entry : pathStack) {
                    if (currentPath.length() > 0) currentPath.append(".");
                    currentPath.append(entry.getKey());
                }
                if (currentPath.length() > 0) currentPath.append(".");
                currentPath.append(keyPart);
                
                // Check if this matches our target section
                if (currentPath.toString().equals(sectionPath)) {
                    foundSection = true;
                    sectionStartLine = i;
                    break;
                }
                
                // Check if it's a section (empty value or next line is indented)
                boolean isSection = valuePart.isEmpty();
                if (isSection && i + 1 < originalLines.size()) {
                    // Look ahead to see if next non-comment line is indented
                    for (int j = i + 1; j < originalLines.size() && j < i + 10; j++) {
                        String nextLine = originalLines.get(j);
                        String nextTrimmed = nextLine.trim();
                        if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                            continue; // Skip comments/blanks when checking
                        }
                        int nextIndent = nextLine.length() - nextTrimmed.length();
                        if (nextIndent > currentIndent) {
                            isSection = true;
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                            break;
                        } else {
                            isSection = false;
                            break;
                        }
                    }
                }
            }
        }
        
        if (!foundSection || sectionStartLine == -1) {
            return sectionLines; // Section not found in original
        }
        
        // Extract all lines belonging to this section
        int sectionIndent = originalLines.get(sectionStartLine).length() - originalLines.get(sectionStartLine).trim().length();
        
        // Start from the line after the section header
        for (int i = sectionStartLine + 1; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            String trimmed = line.trim();
            
            if (trimmed.isEmpty() || line.startsWith("#")) {
                // Include comments and blank lines that are part of the section
                int lineIndent = line.length() - trimmed.length();
                if (lineIndent > sectionIndent || trimmed.isEmpty()) {
                    sectionLines.add(line);
                    continue;
                } else {
                    // Comment at section level or above - end of section
                    break;
                }
            }
            
            int lineIndent = line.length() - trimmed.length();
            if (lineIndent > sectionIndent) {
                // This line belongs to the section
                sectionLines.add(line);
            } else {
                // We've reached the end of the section
                break;
            }
        }
        
        return sectionLines;
    }
    
    /**
     * Find deprecated keys that exist in user config but not in default config
     * These keys will be removed during migration
     */
    private Set<String> findDeprecatedKeys(YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        Set<String> deprecated = new HashSet<>();
        findDeprecatedKeysRecursive(userConfig, defaultConfig, "", deprecated);
        return deprecated;
    }
    
    /**
     * Recursively find deprecated keys
     */
    private void findDeprecatedKeysRecursive(YamlConfiguration userConfig, YamlConfiguration defaultConfig, 
                                             String basePath, Set<String> deprecated) {
        for (String key : userConfig.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            
            // Skip version keys - they're handled separately
            if (key.equals("config_version")) {
                continue;
            }
            
            if (!defaultConfig.contains(fullPath)) {
                // This key doesn't exist in default config - it's deprecated
                deprecated.add(fullPath);
            } else if (userConfig.isConfigurationSection(key) && defaultConfig.isConfigurationSection(fullPath)) {
                // Both are sections - recursively check nested keys
                findDeprecatedKeysRecursive(
                    userConfig.getConfigurationSection(key),
                    defaultConfig.getConfigurationSection(fullPath),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    /**
     * Recursive helper for configuration sections
     */
    private void findDeprecatedKeysRecursive(org.bukkit.configuration.ConfigurationSection userSection,
                                            org.bukkit.configuration.ConfigurationSection defaultSection,
                                            String basePath, Set<String> deprecated) {
        for (String key : userSection.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            
            // Skip version keys - they're handled separately
            if (key.equals("config_version")) {
                continue;
            }
            
            if (!defaultSection.contains(key)) {
                // This key doesn't exist in default config - it's deprecated
                deprecated.add(fullPath);
            } else if (userSection.isConfigurationSection(key) && defaultSection.isConfigurationSection(key)) {
                // Both are sections - recursively check nested keys
                findDeprecatedKeysRecursive(
                    userSection.getConfigurationSection(key),
                    defaultSection.getConfigurationSection(key),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    // Simple Pair class for path tracking
    private static class Pair<K, V> {
        private final K key;
        private final V value;
        
        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
        
        public K getKey() { return key; }
        public V getValue() { return value; }
    }
    
    /**
     * Format a YAML value as a string
     */
    private String formatYamlValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            // Check if it needs quotes
            String str = (String) value;
            if (str.contains(":") || str.contains("#") || str.trim().isEmpty() || 
                str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false") || 
                str.equalsIgnoreCase("null") || str.matches("^-?\\d+$")) {
                return "\"" + str.replace("\"", "\\\"") + "\"";
            }
            return str;
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof List) {
            // Format list
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            // For lists, we'll just return the first approach - inline if simple
            if (list.size() == 1 && (list.get(0) instanceof String || list.get(0) instanceof Number)) {
                return "[" + formatYamlValue(list.get(0)) + "]";
            }
            // Multi-line list - return as inline for now, could be improved
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatYamlValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return value.toString();
        }
    }
    
    /**
     * Update version field in the config file lines
     * Preserves comments and formatting
     * Extracts comment from default config if adding new
     */
    private void updateConfigVersion(List<String> lines, int newVersion, List<String> defaultLines, String versionKey) {
        // Look for version line and update it, or add it if missing
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // Check if this is the version line
            if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                // Update the version value, preserving indentation and any inline comments
                int indent = line.length() - trimmed.length();
                String restOfLine = "";
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex >= 0 && colonIndex + 1 < trimmed.length()) {
                    String afterColon = trimmed.substring(colonIndex + 1).trim();
                    // Check if there's an inline comment
                    int commentIndex = afterColon.indexOf('#');
                    if (commentIndex >= 0) {
                        restOfLine = " #" + afterColon.substring(commentIndex + 1);
                    }
                }
                lines.set(i, " ".repeat(indent) + versionKey + ": " + newVersion + restOfLine);
                found = true;
                break;
            }
        }
        
        // If not found, add it after the header comment (usually line 2-3)
        if (!found) {
            // Extract comment from default config
            String commentLine = "# Config version - do not modify";
            for (int i = 0; i < defaultLines.size(); i++) {
                String line = defaultLines.get(i);
                String trimmed = line.trim();
                // Look for version key in default config
                if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                    // Check if there's a comment line before it
                    if (i > 0) {
                        String prevLine = defaultLines.get(i - 1);
                        if (prevLine.trim().startsWith("#")) {
                            commentLine = prevLine;
                        }
                    }
                    break;
                }
            }
            
            int insertIndex = 0;
            // Find a good place to insert - after first comment block, before first section
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                // Stop at first non-comment, non-blank line that's not the version key
                if (!trimmed.isEmpty() && !line.startsWith("#") && !trimmed.startsWith(versionKey)) {
                    insertIndex = i;
                    break;
                }
            }
            // Insert version line with comment from default config
            lines.add(insertIndex, commentLine);
            lines.add(insertIndex + 1, versionKey + ": " + newVersion);
            // Add blank line after if needed
            if (insertIndex + 2 < lines.size() && !lines.get(insertIndex + 2).trim().isEmpty()) {
                lines.add(insertIndex + 2, "");
            }
        }
    }

    private void saveChallengeToConfig(ChallengeConfig cfg) {
        FileConfiguration config = getConfig();
        String path = "challenges." + cfg.id;

        // Always save all fields with their current values (or defaults if null) to maintain consistent structure
        config.set(path + ".book-title", cfg.bookTitle != null ? cfg.bookTitle : "");

        // Always save destination fields - use actual values if set, otherwise use defaults
        if (cfg.destination != null) {
            config.set(path + ".world", cfg.destination.getWorld().getName());
            config.set(path + ".x", cfg.destination.getX());
            config.set(path + ".y", cfg.destination.getY());
            config.set(path + ".z", cfg.destination.getZ());
            config.set(path + ".yaw", cfg.destination.getYaw());
            config.set(path + ".pitch", cfg.destination.getPitch());
        } else {
            // Set to defaults so they appear in config
            config.set(path + ".world", "");
            config.set(path + ".x", 0.0);
            config.set(path + ".y", 0.0);
            config.set(path + ".z", 0.0);
            config.set(path + ".yaw", 0.0);
            config.set(path + ".pitch", 0.0);
        }

        // Always save these fields with their current values
        config.set(path + ".cooldown-seconds", cfg.cooldownSeconds);
        config.set(path + ".max-party-size", cfg.maxPartySize);
        config.set(path + ".time-limit-seconds", cfg.timeLimitSeconds);
        config.set(path + ".time-limit-warnings", cfg.timeLimitWarnings != null ? cfg.timeLimitWarnings : new ArrayList<>());
        config.set(path + ".locked-down", cfg.lockedDown);

        // Always create completion section with all fields
        ConfigurationSection compSec = config.createSection(path + ".completion");
        CompletionType type = cfg.completionType != null ? cfg.completionType : CompletionType.NONE;
        compSec.set("type", type.name());

        // Save completion-specific fields based on type, but always set all fields to ensure structure
        switch (type) {
            case BOSS -> {
                if (cfg.bossRequirement != null) {
                    compSec.set("boss-type", cfg.bossRequirement.type.name());
                    compSec.set("boss-name", cfg.bossRequirement.name != null ? cfg.bossRequirement.name : "");
                } else {
                    compSec.set("boss-type", "");
                    compSec.set("boss-name", "");
                }
                compSec.set("seconds", 0);
                compSec.set("item", "");
                compSec.set("amount", 0);
                compSec.set("consume-on-complete", false);
                compSec.set("max-seconds", 0);
                compSec.set("tiers", new ArrayList<>());
            }
            case TIMER -> {
                compSec.set("seconds", cfg.timerSeconds);
                compSec.set("boss-type", "");
                compSec.set("boss-name", "");
                compSec.set("item", "");
                compSec.set("amount", 0);
                compSec.set("consume-on-complete", false);
                compSec.set("max-seconds", 0);
                compSec.set("tiers", new ArrayList<>());
            }
            case ITEM -> {
                if (cfg.itemRequirement != null) {
                    compSec.set("item", cfg.itemRequirement.material.name());
                    compSec.set("amount", cfg.itemRequirement.amount);
                    compSec.set("consume-on-complete", cfg.itemRequirement.consume);
                } else {
                    compSec.set("item", "");
                    compSec.set("amount", 0);
                    compSec.set("consume-on-complete", false);
                }
                compSec.set("boss-type", "");
                compSec.set("boss-name", "");
                compSec.set("seconds", 0);
                compSec.set("max-seconds", 0);
                compSec.set("tiers", new ArrayList<>());
            }
            case SPEED -> {
                compSec.set("boss-type", "");
                compSec.set("boss-name", "");
                compSec.set("seconds", 0);
                compSec.set("item", "");
                compSec.set("amount", 0);
                compSec.set("consume-on-complete", false);
                compSec.set("max-seconds", cfg.timerSeconds);
                List<Map<String, Object>> tierList = new ArrayList<>();
                if (cfg.speedTiers != null && !cfg.speedTiers.isEmpty()) {
                    for (SpeedTier tier : cfg.speedTiers) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", tier.name);
                        m.put("max-seconds", tier.maxSeconds);
                        m.put("reward-commands", tier.rewardCommands != null ? tier.rewardCommands : new ArrayList<>());
                        tierList.add(m);
                    }
                }
                compSec.set("tiers", tierList);
            }
            case NONE -> {
                compSec.set("boss-type", "");
                compSec.set("boss-name", "");
                compSec.set("seconds", 0);
                compSec.set("item", "");
                compSec.set("amount", 0);
                compSec.set("consume-on-complete", false);
                compSec.set("max-seconds", 0);
                compSec.set("tiers", new ArrayList<>());
            }
        }

        // Always save reward-commands (even if empty list) - new format with chance
        List<Map<String, Object>> rewardCommandsList = new ArrayList<>();
        if (cfg.fallbackRewardCommands != null) {
            for (RewardWithChance rwc : cfg.fallbackRewardCommands) {
                Map<String, Object> rewardMap = new HashMap<>();
                rewardMap.put("command", rwc.command);
                rewardMap.put("chance", rwc.chance);
                rewardCommandsList.add(rewardMap);
            }
        }
        config.set(path + ".reward-commands", rewardCommandsList);
        
        // Save difficulty tier rewards
        if (cfg.difficultyTierRewards != null && !cfg.difficultyTierRewards.isEmpty()) {
            ConfigurationSection tierRewardsSec = config.createSection(path + ".difficulty-tier-rewards");
            for (Map.Entry<String, List<TierReward>> entry : cfg.difficultyTierRewards.entrySet()) {
                String tier = entry.getKey();
                List<TierReward> rewards = entry.getValue();
                ConfigurationSection tierSec = tierRewardsSec.createSection(tier);
                List<Map<String, Object>> rewardMaps = new ArrayList<>();
                for (TierReward reward : rewards) {
                    Map<String, Object> rewardMap = new HashMap<>();
                    rewardMap.put("type", reward.type.name());
                    rewardMap.put("chance", reward.chance);
                    switch (reward.type) {
                        case HAND -> {
                            if (reward.itemStack != null) {
                                rewardMap.put("item-data", reward.itemStack.getType().name() + ";" + reward.itemStack.getAmount());
                            }
                        }
                        case ITEM -> {
                            rewardMap.put("material", reward.material.name());
                            rewardMap.put("amount", reward.amount);
                        }
                        case COMMAND -> {
                            rewardMap.put("command", reward.command);
                        }
                    }
                    rewardMaps.add(rewardMap);
                }
                tierSec.set("rewards", rewardMaps);
            }
        }

        // Save regeneration area
        if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
            config.set(path + ".regeneration.world", cfg.regenerationCorner1.getWorld().getName());
            config.set(path + ".regeneration.corner1.x", cfg.regenerationCorner1.getX());
            config.set(path + ".regeneration.corner1.y", cfg.regenerationCorner1.getY());
            config.set(path + ".regeneration.corner1.z", cfg.regenerationCorner1.getZ());
            config.set(path + ".regeneration.corner2.x", cfg.regenerationCorner2.getX());
            config.set(path + ".regeneration.corner2.y", cfg.regenerationCorner2.getY());
            config.set(path + ".regeneration.corner2.z", cfg.regenerationCorner2.getZ());
            config.set(path + ".regeneration.auto-extend-y", cfg.regenerationAutoExtendY);
        } else {
            config.set(path + ".regeneration", null);
        }

        // Save challenge area
        if (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null) {
            config.set(path + ".challenge-area.world", cfg.challengeCorner1.getWorld().getName());
            config.set(path + ".challenge-area.corner1.x", cfg.challengeCorner1.getX());
            config.set(path + ".challenge-area.corner1.y", cfg.challengeCorner1.getY());
            config.set(path + ".challenge-area.corner1.z", cfg.challengeCorner1.getZ());
            config.set(path + ".challenge-area.corner2.x", cfg.challengeCorner2.getX());
            config.set(path + ".challenge-area.corner2.y", cfg.challengeCorner2.getY());
            config.set(path + ".challenge-area.corner2.z", cfg.challengeCorner2.getZ());
        } else {
            config.set(path + ".challenge-area", null);
        }

        saveConfig();

        // rebuild lookup
        bookTitleToChallengeId.clear();
        for (ChallengeConfig c : challenges.values()) {
            if (c.bookTitle != null) {
                bookTitleToChallengeId.put(c.bookTitle, c.id);
            }
        }
    }

    // ====== UTILS ======

    private boolean isChallengeLocked(String challengeId) {
        if (challengeId == null) return false;
        if (reservedChallenges.contains(challengeId)) return true;
        if (activeChallenges.containsValue(challengeId)) return true;
        if (challengeEditors.containsKey(challengeId)) return true; // Challenge is being edited
        return false;
    }
    
    private boolean isChallengeLockedDown(String challengeId) {
        if (challengeId == null) return false;
        ChallengeConfig cfg = challenges.get(challengeId);
        return cfg != null && cfg.lockedDown;
    }
    
    /**
     * Handles tab completion for commands when player is in edit mode.
     * Returns parameter completions directly (skipping challenge ID).
     */
    private List<String> handleEditModeTabCompletion(String subcommand, String partial, List<String> completions) {
        switch (subcommand.toLowerCase(Locale.ROOT)) {
            case "settype":
                completions.add("BOSS");
                completions.add("TIMER");
                completions.add("ITEM");
                completions.add("SPEED");
                completions.add("NONE");
                return filterCompletions(completions, partial);
                
            case "setboss":
                for (EntityType type : EntityType.values()) {
                    if (type.isSpawnable() && type.isAlive()) {
                        completions.add(type.name());
                    }
                }
                return filterCompletions(completions, partial);
                
            case "setitem":
                // First parameter: material
                for (Material mat : Material.values()) {
                    if (mat.isItem()) {
                        completions.add(mat.name());
                    }
                }
                return filterCompletions(completions, partial);
                
            case "settimer":
            case "setspeed":
            case "setcooldown":
            case "settimelimit":
            case "setmaxparty":
                // These commands just need a number - no completion
                return Collections.emptyList();
                
            case "addreward":
                // Command text - no completion
                return Collections.emptyList();
                
            case "addtierreward":
            case "cleartierrewards":
            case "listtierrewards":
            case "removereward":
                // First parameter: tier (easy, medium, hard, extreme)
                completions.add("easy");
                completions.add("medium");
                completions.add("hard");
                completions.add("extreme");
                return filterCompletions(completions, partial);
                
            case "addtoptimereward":
                // First parameter: type (hand, item, command)
                completions.add("hand");
                completions.add("item");
                completions.add("command");
                return filterCompletions(completions, partial);
                
            case "setautoregenerationy":
                completions.add("true");
                completions.add("false");
                completions.add("on");
                completions.add("off");
                completions.add("enable");
                completions.add("disable");
                completions.add("yes");
                completions.add("no");
                return filterCompletions(completions, partial);
                
            case "lockdown":
            case "unlockdown":
            case "delete":
            case "setbook":
            case "clearbook":
            case "setdestination":
            case "setregenerationarea":
            case "clearregenerationarea":
            case "captureregeneration":
            case "setchallengearea":
            case "clearchallengearea":
            case "areasync":
            case "info":
            case "edit":
                // These commands need challenge ID (handled elsewhere)
                return Collections.emptyList();
                
            default:
                // Commands that don't need parameters
                return Collections.emptyList();
        }
    }
    
    private boolean isChallengeBeingEdited(String challengeId) {
        return challengeId != null && challengeEditors.containsKey(challengeId);
    }
    
    private UUID getChallengeEditor(String challengeId) {
        return challengeEditors.get(challengeId);
    }
    
    /**
     * Gets the challenge ID that a player is currently editing, or null if not in edit mode.
     */
    private String getPlayerEditingChallenge(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Checks if a player is currently in edit mode.
     */
    private boolean isPlayerInEditMode(Player player) {
        return getPlayerEditingChallenge(player) != null;
    }

    private String displayName(ChallengeConfig cfg) {
        return cfg.bookTitle != null ? cfg.bookTitle : cfg.id;
    }

    private void recordCompletionTimeForCooldown(String challengeId, UUID uuid) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> map = lastCompletionTimes.computeIfAbsent(challengeId, k -> new HashMap<>());
        map.put(uuid, now);
    }

    /**
     * Updates the best time for a player and checks if it's a new overall record.
     * @param challengeId The challenge ID
     * @param uuid The player UUID
     * @param seconds The completion time in seconds
     * @return true if this is a new overall record (fastest time across all players), false otherwise
     */
    private boolean updateBestTime(String challengeId, UUID uuid, long seconds) {
        Map<UUID, Long> map = bestTimes.computeIfAbsent(challengeId, k -> new HashMap<>());
        Long prev = map.get(uuid);
        boolean isPersonalBest = (prev == null || seconds < prev);
        
        if (isPersonalBest) {
            // Get the previous overall best time (before updating)
            long previousOverallBest = Long.MAX_VALUE;
            for (Long time : map.values()) {
                if (time < previousOverallBest) {
                    previousOverallBest = time;
                }
            }
            if (previousOverallBest == Long.MAX_VALUE) {
                previousOverallBest = -1; // No previous times
            }
            
            // Update the player's best time
            map.put(uuid, seconds);
            
            // Check if this is now the overall fastest time
            long currentOverallBest = Long.MAX_VALUE;
            for (Long time : map.values()) {
                if (time < currentOverallBest) {
                    currentOverallBest = time;
                }
            }
            
            // If this time is the new overall best, it's a new record
            if (seconds == currentOverallBest && (previousOverallBest == -1 || seconds < previousOverallBest)) {
                return true;
            }
        }
        
        return false;
    }

    private SpeedTier findBestSpeedTier(ChallengeConfig cfg, int elapsedSeconds) {
        SpeedTier best = null;
        for (SpeedTier tier : cfg.speedTiers) {
            if (elapsedSeconds <= tier.maxSeconds) {
                if (best == null || tier.maxSeconds < best.maxSeconds) {
                    best = tier;
                }
            }
        }
        return best;
    }
    
    /**
     * Formats time in seconds to a readable string (e.g., "1m 30s", "45s")
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (secs == 0) {
            return minutes + "m";
        }
        return minutes + "m " + secs + "s";
    }

    private void showTopTimes(org.bukkit.command.CommandSender sender, String challengeId, ChallengeConfig cfg) {
        Map<UUID, Long> map = bestTimes.get(challengeId);
        if (map == null || map.isEmpty()) {
            sender.sendMessage(getMessage("top.no-times"));
            return;
        }
        sender.sendMessage(getMessage("top.header", "challenge", displayName(cfg)));
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(10)
                .forEach(e -> {
                    UUID uuid = e.getKey();
                    long secs = e.getValue();
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    sender.sendMessage(getMessage("top.entry", "player", op.getName(), "time", String.valueOf(secs)));
                });
    }

    // ====== ANGER SYSTEM ======

    private boolean angerEnabled() {
        return getConfig().getBoolean("anger.enabled", true);
    }

    private long angerDurationMillis() {
        int sec = getConfig().getInt("anger.duration-seconds", 300);
        return sec * 1000L;
    }

    private boolean isAngryAt(Player player) {
        if (!angerEnabled()) return false;
        UUID uuid = player.getUniqueId();
        Long until = angryUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            angryUntil.remove(uuid);
            requiresApology.remove(uuid);
            return false;
        }
        return requiresApology.contains(uuid);
    }

    private void setAngryAt(Player player) {
        if (!angerEnabled()) return;
        UUID uuid = player.getUniqueId();
        angryUntil.put(uuid, System.currentTimeMillis() + angerDurationMillis());
        requiresApology.add(uuid);
    }

    private void clearAnger(Player player) {
        UUID uuid = player.getUniqueId();
        angryUntil.remove(uuid);
        requiresApology.remove(uuid);
    }

    private void sendAngryResponse(Player player) {
        List<String> insults = messagesConfig.getStringList("anger.insults");
        String chosen;
        if (insults == null || insults.isEmpty()) {
            chosen = getMessage("admin.anger-fallback");
        } else {
            String raw = insults.get(new Random().nextInt(insults.size()));
            chosen = ChatColor.translateAlternateColorCodes('&', raw);
        }
        player.sendMessage(chosen);
        player.sendMessage(getMessage("anger.use-sorry"));
    }

    // ====== PENDING CHALLENGE TIMEOUT ======
    
    /**
     * Start a timeout timer for a pending challenge
     * If the player doesn't respond within the timeout, silently cancel their pending challenge
     */
    private void startPendingChallengeTimeout(UUID playerUuid) {
        // Cancel any existing timeout for this player
        cancelPendingChallengeTimeout(playerUuid);
        
        // Start new timeout task
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // Silently remove pending challenge and from queue
                pendingChallenges.remove(playerUuid);
                removeFromQueue(playerUuid);
                pendingChallengeTimeouts.remove(playerUuid);
            }
        }.runTaskLater(this, pendingChallengeTimeoutSeconds * 20L); // Convert seconds to ticks (20 ticks = 1 second)
        
        pendingChallengeTimeouts.put(playerUuid, task);
    }
    
    /**
     * Cancel the timeout timer for a pending challenge
     */
    private void cancelPendingChallengeTimeout(UUID playerUuid) {
        BukkitTask task = pendingChallengeTimeouts.remove(playerUuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    // ====== ANTI-FORGERY ======

    private boolean antiForgeryEnabled() {
        return getConfig().getBoolean("anti-forgery.enabled", true);
    }

    private double antiForgeryFine() {
        return getConfig().getDouble("anti-forgery.fine-amount", 50.0);
    }

    private boolean antiForgeryDrainIfLess() {
        return getConfig().getBoolean("anti-forgery.drain-if-less", true);
    }

    private boolean antiForgeryBroadcast() {
        return getConfig().getBoolean("anti-forgery.broadcast", true);
    }

    private String antiForgeryRequiredAuthor() {
        return getConfig().getString("anti-forgery.required-author", "");
    }

    private void punishFakeBook(Player player, ItemStack inHand, String challengeId) {
        // take 1 book from hand
        ItemStack item = inHand.clone();
        int amt = item.getAmount();
        if (amt > 1) {
            item.setAmount(amt - 1);
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();

        // messages + fine
        double fine = antiForgeryFine();
        if (economy != null && antiForgeryEnabled()) {
            double bal = economy.getBalance(player);
            if (bal >= fine) {
                economy.withdrawPlayer(player, fine);
                player.sendMessage(getMessage("forgery.detected"));
                player.sendMessage(getMessage("forgery.fake-book"));
                player.sendMessage(getMessage("forgery.fine-charged", "amount", String.valueOf((int) fine)));
            } else {
                double taken = bal;
                if (antiForgeryDrainIfLess() && bal > 0) {
                    economy.withdrawPlayer(player, bal);
                }
                player.sendMessage(getMessage("forgery.no-money", "amount", String.valueOf((int) fine)));
                if (taken > 0) {
                    player.sendMessage(getMessage("forgery.drained", "amount", String.valueOf((int) taken)));
                } else {
                    player.sendMessage(getMessage("forgery.nothing-taken"));
                }
            }
        } else {
            player.sendMessage(getMessage("forgery.no-economy"));
            player.sendMessage(getMessage("forgery.no-economy-kept"));
        }

        // broadcast + console log
        if (antiForgeryBroadcast()) {
            String challengePart = challengeId != null ? getMessage("forgery.broadcast-challenge-part", "challenge", challengeId) : "";
            Bukkit.broadcastMessage(getMessage("forgery.broadcast-detected", "player", player.getName(), "challenge", challengePart));
            Bukkit.broadcastMessage(getMessage("forgery.broadcast-punished"));
        }
        getLogger().warning("Player " + player.getName() + " attempted to use a fake challenge book" +
                (challengeId != null ? " for challenge '" + challengeId + "'" : "") + ".");

        // anger
        setAngryAt(player);
    }

    // ====== COMMANDS ======

    private void registerCommands() {
        // /conradchallenges
        PluginCommand cmdConrad = Objects.requireNonNull(getCommand("conradchallenges"));
        cmdConrad.setExecutor((sender, command, label, args) -> {
            if (args.length == 0) {
                sender.sendMessage(getMessage("commands.setnpc"));
                sender.sendMessage(getMessage("commands.removenpc"));
                sender.sendMessage(getMessage("commands.removeallnpcs"));
                sender.sendMessage(getMessage("commands.reload"));
                sender.sendMessage(getMessage("commands.challenge"));
                sender.sendMessage(getMessage("commands.party"));
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.command-players-only"));
                return true;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("setnpc")) {
                if (!player.hasPermission("conradchallenges.admin")) {
                    player.sendMessage(getMessage("general.no-permission"));
                    return true;
                }
                Entity target = getTargetEntity(player, 10.0);
                if (target == null) {
                    player.sendMessage(getMessage("npc.setnpc-look"));
                    player.sendMessage(getMessage("npc.setnpc-tip"));
                    return true;
                }
                UUID id = target.getUniqueId();
                if (npcIds.add(id)) {
                    saveNpcIds();
                    setEntityDisplayName(target, getMessage("npc.npc-display-name"));
                    player.sendMessage(getMessage("npc.setnpc-success"));
                } else {
                    player.sendMessage(getMessage("npc.setnpc-already"));
                }
                return true;
            }

            if (sub.equals("removenpc")) {
                if (!player.hasPermission("conradchallenges.admin")) {
                    player.sendMessage(getMessage("general.no-permission"));
                    return true;
                }
                Entity target = getTargetEntity(player, 10.0);
                if (target == null) {
                    player.sendMessage(getMessage("npc.removenpc-look"));
                    player.sendMessage(getMessage("npc.removenpc-tip"));
                    return true;
                }
                UUID id = target.getUniqueId();
                if (npcIds.remove(id)) {
                    saveNpcIds();
                    clearEntityDisplayName(target);
                    player.sendMessage(getMessage("npc.removenpc-success"));
                } else {
                    player.sendMessage(getMessage("npc.removenpc-not"));
                }
                return true;
            }

            if (sub.equals("removeallnpcs")) {
                if (!player.hasPermission("conradchallenges.admin")) {
                    player.sendMessage(getMessage("general.no-permission"));
                    return true;
                }
                if (npcIds.isEmpty()) {
                    player.sendMessage(getMessage("npc.removeall-none"));
                    return true;
                }
                
                int count = npcIds.size();
                
                // Clear display names for all registered NPCs
                for (UUID npcId : new HashSet<>(npcIds)) {
                    // Try to find the entity in all loaded worlds
                    for (World world : Bukkit.getWorlds()) {
                        for (Entity entity : world.getEntities()) {
                            if (entity.getUniqueId().equals(npcId)) {
                                clearEntityDisplayName(entity);
                                break;
                            }
                        }
                    }
                }
                
                npcIds.clear();
                saveNpcIds();
                player.sendMessage(getMessage("npc.removeall-success", "count", String.valueOf(count)));
                return true;
            }

            if (sub.equals("reload")) {
                if (!player.hasPermission("conradchallenges.admin")) {
                    player.sendMessage(getMessage("general.no-permission"));
                    return true;
                }
                reloadConfig();
                loadMessages();
                loadNpcIds();
                loadSpawnLocation();
                loadChallengesFromConfig();
                player.sendMessage(getMessage("general.reloaded"));
                return true;
            }

            if (sub.equals("challenge")) {
                return handleAdminChallengeCommands(player, Arrays.copyOfRange(args, 1, args.length));
            }

            if (sub.equals("party")) {
                return handlePartyCommands(player, Arrays.copyOfRange(args, 1, args.length));
            }

            if (sub.equals("top")) {
                if (!player.hasPermission("conradchallenges.challenge")) {
                    player.sendMessage(getMessage("general.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(getMessage("admin.top-usage"));
                    return true;
                }
                String id = args[1];
                ChallengeConfig cfg = challenges.get(id);
                if (cfg == null) {
                    player.sendMessage(getMessage("admin.challenge-unknown", "id", id));
                    return true;
                }
                showTopTimes(player, id, cfg);
                return true;
            }

            player.sendMessage(getMessage("admin.unknown-subcommand"));
            return true;
        });
        cmdConrad.setTabCompleter((sender, command, alias, args) -> {
            if (!(sender instanceof Player player)) {
                return Collections.emptyList();
            }
            
            if (!player.hasPermission("conradchallenges.use")) {
                return Collections.emptyList();
            }
            
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                // First level subcommands - return ALL that match what they typed
                String partial = args[0].toLowerCase(Locale.ROOT);
                
                for (String subcommand : CONRAD_SUBCOMMANDS) {
                    if (subcommand.toLowerCase(Locale.ROOT).startsWith(partial)) {
                        // Check permissions
                        if (subcommand.equals("setnpc") || subcommand.equals("removenpc") || 
                            subcommand.equals("removeallnpcs") || subcommand.equals("reload") || 
                            subcommand.equals("challenge")) {
                            if (player.hasPermission("conradchallenges.admin")) {
                                completions.add(subcommand);
                            }
                        } else if (subcommand.equals("party")) {
                            if (player.hasPermission("conradchallenges.party")) {
                                completions.add(subcommand);
                            }
                        } else if (subcommand.equals("top")) {
                            if (player.hasPermission("conradchallenges.challenge")) {
                                completions.add(subcommand);
                            }
                        }
                    }
                }
                
                return completions;
            }
            
            if (args.length == 2) {
                String first = args[0].toLowerCase(Locale.ROOT);
                String partial = args[1].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    for (String subcommand : CHALLENGE_SUBCOMMANDS) {
                        if (subcommand.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            completions.add(subcommand);
                        }
                    }
                    return completions;
                }
                
                if (first.equals("party") && player.hasPermission("conradchallenges.party")) {
                    for (String subcommand : PARTY_SUBCOMMANDS) {
                        if (subcommand.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            completions.add(subcommand);
                        }
                    }
                    return completions;
                }
                
                if (first.equals("top") && player.hasPermission("conradchallenges.challenge")) {
                    return filterCompletions(new ArrayList<>(challenges.keySet()), args[1]);
                }
            }
            
            if (args.length == 3) {
                String first = args[0].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    
                    // Commands that don't need an ID
                    if (second.equals("create") || second.equals("list") || 
                        second.equals("save") || second.equals("cancel")) {
                        return Collections.emptyList();
                    }
                    
                    // If player is in edit mode, skip ID completion and go to parameter completion
                    if (isPlayerInEditMode(player)) {
                        // For addtierreward in edit mode, check if we're at tier or type position
                        if (second.equals("addtierreward")) {
                            // args[2] is tier position, args[3] would be type position
                            // If args.length == 3, we're typing the tier
                            if (args.length == 3) {
                                List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                                return filterCompletions(tiers, args[2]);
                            }
                            // If args.length == 4, we're typing the type
                            if (args.length == 4) {
                                List<String> types = Arrays.asList("hand", "item", "command");
                                return filterCompletions(types, args[3]);
                            }
                        }
                        // For cleartierrewards, listtierrewards in edit mode, complete tiers
                        if (second.equals("cleartierrewards") || second.equals("listtierrewards")) {
                            List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                            return filterCompletions(tiers, args[2]);
                        }
                        // For addtoptimereward in edit mode, complete types
                        if (second.equals("addtoptimereward")) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[2]);
                        }
                        // In edit mode - complete parameters directly
                        return handleEditModeTabCompletion(second, args[2], completions);
                    }
                    
                    // Not in edit mode - need challenge ID first
                    return filterCompletions(new ArrayList<>(challenges.keySet()), args[2]);
                }
                
                if (first.equals("party") && args[1].equalsIgnoreCase("start") && player.hasPermission("conradchallenges.party")) {
                    return filterCompletions(new ArrayList<>(challenges.keySet()), args[2]);
                }
            }
            
            // Handle edit mode parameter completion when args.length == 2 (after subcommand)
            if (args.length == 2) {
                String first = args[0].toLowerCase(Locale.ROOT);
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    // If in edit mode and this is a modification command, complete parameters
                    if (isPlayerInEditMode(player)) {
                        // For addtierreward, cleartierrewards, listtierrewards in edit mode, complete tiers
                        if (second.equals("addtierreward") || second.equals("cleartierrewards") || second.equals("listtierrewards")) {
                            List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                            return filterCompletions(tiers, "");
                        }
                        return handleEditModeTabCompletion(second, "", completions);
                    }
                }
            }
            
            if (args.length == 4) {
                String first = args[0].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    boolean inEditMode = isPlayerInEditMode(player);
                    
                    // Adjust index based on edit mode
                    int paramIndex = inEditMode ? 2 : 3;
                    
                    if (second.equals("settype")) {
                        if (inEditMode) {
                            completions.add("BOSS");
                            completions.add("TIMER");
                            completions.add("ITEM");
                            completions.add("SPEED");
                            completions.add("NONE");
                            return filterCompletions(completions, args[paramIndex]);
                        }
                        // Not in edit mode - args[3] is the type parameter
                        completions.add("BOSS");
                        completions.add("TIMER");
                        completions.add("ITEM");
                        completions.add("SPEED");
                        completions.add("NONE");
                        return filterCompletions(completions, args[3]);
                    }
                    
                    if (second.equals("setboss")) {
                        if (inEditMode) {
                            for (EntityType type : EntityType.values()) {
                                if (type.isSpawnable() && type.isAlive()) {
                                    completions.add(type.name());
                                }
                            }
                            return filterCompletions(completions, args[paramIndex]);
                        }
                        // Not in edit mode
                        for (EntityType type : EntityType.values()) {
                            if (type.isSpawnable() && type.isAlive()) {
                                completions.add(type.name());
                            }
                        }
                        return filterCompletions(completions, args[3]);
                    }
                    
                    if (second.equals("setitem")) {
                        if (inEditMode) {
                            for (Material mat : Material.values()) {
                                if (mat.isItem()) {
                                    completions.add(mat.name());
                                }
                            }
                            return filterCompletions(completions, args[paramIndex]);
                        }
                        // Not in edit mode
                        for (Material mat : Material.values()) {
                            if (mat.isItem()) {
                                completions.add(mat.name());
                            }
                        }
                        return filterCompletions(completions, args[3]);
                    }
                    
                    // Tab completion for addtierreward - check if tier is already provided
                    if (second.equals("addtierreward")) {
                        int tierIndex = inEditMode ? 2 : 3;
                        int typeIndex = inEditMode ? 3 : 4;
                        
                        // If we're at the tier position (args.length == tierIndex + 1), complete tiers
                        if (args.length == tierIndex + 1) {
                            List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                            return filterCompletions(tiers, args[tierIndex]);
                        }
                        // If we're at the type position (args.length == typeIndex + 1), complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                    }
                    
                    // Tab completion for addreward
                    if (second.equals("addreward")) {
                        int typeIndex = inEditMode ? 2 : 3;
                        int materialIndex = inEditMode ? 3 : 4;
                        
                        // If we're at the type position, complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If item type is selected and we're at the material position, complete materials
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for cleartierrewards, listtierrewards, removereward
                    if (second.equals("cleartierrewards") || second.equals("listtierrewards") || second.equals("removereward")) {
                        List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                        return filterCompletions(tiers, args[paramIndex]);
                    }
                    
                    // Tab completion for addtoptimereward
                    if (second.equals("addtoptimereward")) {
                        int typeIndex = inEditMode ? 2 : 3;
                        int materialIndex = inEditMode ? 3 : 4;
                        
                        // If we're at the type position, complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If item type is selected and we're at the material position, complete materials
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            }
                        }
                    }
                }
            }
            
            if (args.length == 5 || args.length == 6 || args.length == 7 || args.length == 8) {
                String first = args[0].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    boolean inEditMode = isPlayerInEditMode(player);
                    
                    // Tab completion for removereward <tier> <reward number>
                    if (second.equals("removereward")) {
                        int tierIndex = inEditMode ? 2 : 3;
                        int numberIndex = inEditMode ? 3 : 4;
                        
                        // If we're at the tier position, complete tiers
                        if (args.length == tierIndex + 1) {
                            List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                            return filterCompletions(tiers, args[tierIndex]);
                        }
                        
                        // If we're at the reward number position, complete reward numbers
                        if (args.length == numberIndex + 1) {
                            String id = getChallengeIdForCommand(player, args, 1);
                            if (id != null) {
                                ChallengeConfig cfg = challenges.get(id);
                                if (cfg != null && args.length > tierIndex) {
                                    String tier = args[tierIndex].toLowerCase();
                                    if (Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                                        List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
                                        if (rewards != null && !rewards.isEmpty()) {
                                            for (int i = 1; i <= rewards.size(); i++) {
                                                completions.add(String.valueOf(i));
                                            }
                                            return filterCompletions(completions, args[numberIndex]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Tab completion for addtierreward <tier> <type>
                    if (second.equals("addtierreward")) {
                        int tierIndex = inEditMode ? 2 : 3;
                        int typeIndex = inEditMode ? 3 : 4;
                        int materialIndex = inEditMode ? 4 : 5;
                        int chanceIndex = inEditMode ? 4 : 5;
                        
                        // If we're at the type position, complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If item type is selected and we're at the material position, complete materials
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            }
                        }
                        
                        // If hand type is selected and we're at the chance position, complete chance
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for addreward <type> (for args.length == 5)
                    if (second.equals("addreward")) {
                        int typeIndex = inEditMode ? 1 : 2;
                        int materialIndex = inEditMode ? 2 : 3;
                        int amountIndex = inEditMode ? 3 : 4;
                        int chanceIndex = inEditMode ? 4 : 5;
                        
                        // If we're at the type position, complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If item type is selected
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            } else if (args.length == amountIndex + 1) {
                                // Complete common amounts
                                completions.add("1");
                                completions.add("8");
                                completions.add("16");
                                completions.add("32");
                                completions.add("64");
                                return filterCompletions(completions, args[amountIndex]);
                            } else if (args.length == chanceIndex + 1) {
                                // Complete chance hint
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                        
                        // If hand type is selected and we're at the chance position, complete chance
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            int handChanceIndex = inEditMode ? 2 : 3;
                            if (args.length == handChanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[handChanceIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for addtoptimereward <type> (for args.length == 5)
                    if (second.equals("addtoptimereward")) {
                        int typeIndex = inEditMode ? 2 : 3;
                        int materialIndex = inEditMode ? 3 : 4;
                        int amountIndex = inEditMode ? 4 : 5;
                        int chanceIndex = inEditMode ? 3 : 4;
                        
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If item type is selected
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            } else if (args.length == amountIndex + 1) {
                                // Complete common amounts
                                completions.add("1");
                                completions.add("8");
                                completions.add("16");
                                completions.add("32");
                                completions.add("64");
                                return filterCompletions(completions, args[amountIndex]);
                            }
                        }
                        
                        // If hand type is selected and we're at the chance position, complete chance
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for setautoregenerationy <true|false>
                    if (second.equals("setautoregenerationy")) {
                        int boolIndex = inEditMode ? 2 : 3;
                        if (args.length == boolIndex + 1) {
                            completions.add("true");
                            completions.add("false");
                            completions.add("on");
                            completions.add("off");
                            completions.add("enable");
                            completions.add("disable");
                            completions.add("yes");
                            completions.add("no");
                            return filterCompletions(completions, args[boolIndex]);
                        }
                    }
                    
                    // Tab completion for addreward <type> [args] [chance]
                    // Similar to addtierreward but without difficulty tier
                    if (second.equals("addreward")) {
                        int typeIndex = inEditMode ? 1 : 2;
                        int chanceIndex = inEditMode ? 2 : 3;
                        
                        // If we're at the type position, complete types
                        if (args.length == typeIndex + 1) {
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                        
                        // If hand type is selected and we're at the chance position, complete chance
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                    }
                }
            }
            
            if (args.length >= 6) {
                String first = args[0].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    boolean inEditMode = isPlayerInEditMode(player);
                    
                    // Tab completion for addreward <type> [args] [chance] (for longer commands)
                    // Similar to addtierreward but without difficulty tier
                    if (second.equals("addreward")) {
                        int typeIndex = inEditMode ? 1 : 2;
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            int materialIndex = inEditMode ? 2 : 3;
                            int amountIndex = inEditMode ? 3 : 4;
                            int chanceIndex = inEditMode ? 4 : 5;
                            
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            } else if (args.length == amountIndex + 1) {
                                // Complete common amounts
                                completions.add("1");
                                completions.add("8");
                                completions.add("16");
                                completions.add("32");
                                completions.add("64");
                                return filterCompletions(completions, args[amountIndex]);
                            } else if (args.length == chanceIndex + 1) {
                                // Complete chance hint
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("command")) {
                            // Command type - check if we're at the chance position
                            // Minimum args: command <command>, so chance would be after at least 2 args after type
                            int minCommandArgs = inEditMode ? 3 : 4; // challenge addreward [id] command <cmd>
                            int chanceIndex = inEditMode ? 4 : 5; // After command text
                            
                            // If we have at least the minimum args and we're at the chance position, suggest chance
                            if (args.length >= minCommandArgs && args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                            // Otherwise, no completion for command text
                            return Collections.emptyList();
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            // Hand type - check for chance parameter
                            int chanceIndex = inEditMode ? 2 : 3;
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for addtierreward <tier> item <material> [amount] [chance]
                    if (second.equals("addtierreward")) {
                        int tierIndex = inEditMode ? 2 : 3;
                        int typeIndex = inEditMode ? 3 : 4;
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            int materialIndex = inEditMode ? 4 : 5;
                            int amountIndex = inEditMode ? 5 : 6;
                            int chanceIndex = inEditMode ? 6 : 7;
                            
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            } else if (args.length == amountIndex + 1) {
                                // Complete common amounts (optional - user can type their own)
                                completions.add("1");
                                completions.add("8");
                                completions.add("16");
                                completions.add("32");
                                completions.add("64");
                                return filterCompletions(completions, args[amountIndex]);
                            } else if (args.length == chanceIndex + 1) {
                                // Complete chance hint (optional)
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("command")) {
                            // Command type - check if we're at the chance position
                            // Minimum args: tier command <command>, so chance would be after at least 3 args after type
                            int minCommandArgs = inEditMode ? 5 : 6; // challenge addtierreward [id] tier command <cmd>
                            int chanceIndex = inEditMode ? 6 : 7; // After command text
                            
                            // If we have at least the minimum args and we're at the chance position, suggest chance
                            if (args.length >= minCommandArgs && args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                            // Otherwise, no completion for command text
                            return Collections.emptyList();
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            // Hand type - check for chance parameter
                            int chanceIndex = inEditMode ? 4 : 5;
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        }
                    }
                    
                    // Tab completion for addtoptimereward <type> [args] [chance]
                    if (second.equals("addtoptimereward")) {
                        int typeIndex = inEditMode ? 2 : 3;
                        if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("item")) {
                            int materialIndex = inEditMode ? 3 : 4;
                            int amountIndex = inEditMode ? 4 : 5;
                            int chanceIndex = inEditMode ? 5 : 6;
                            
                            if (args.length == materialIndex + 1) {
                                // Complete material names
                                for (Material mat : Material.values()) {
                                    if (mat.isItem()) {
                                        completions.add(mat.name());
                                    }
                                }
                                return filterCompletions(completions, args[materialIndex]);
                            } else if (args.length == amountIndex + 1) {
                                // Complete common amounts
                                completions.add("1");
                                completions.add("8");
                                completions.add("16");
                                completions.add("32");
                                completions.add("64");
                                return filterCompletions(completions, args[amountIndex]);
                            } else if (args.length == chanceIndex + 1) {
                                // Complete chance hint
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("command")) {
                            // Command type - check if we're at the chance position
                            // Minimum args: command <command>, so chance would be after at least 2 args after type
                            int minCommandArgs = inEditMode ? 3 : 4; // challenge addtoptimereward [id] command <cmd>
                            int chanceIndex = inEditMode ? 4 : 5; // After command text
                            
                            // If we have at least the minimum args and we're at the chance position, suggest chance
                            if (args.length >= minCommandArgs && args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                            // Otherwise, no completion for command text
                            return Collections.emptyList();
                        } else if (args.length > typeIndex && args[typeIndex].equalsIgnoreCase("hand")) {
                            // Hand type - check for chance parameter
                            int chanceIndex = inEditMode ? 3 : 4;
                            if (args.length == chanceIndex + 1) {
                                completions.add("<0.00-1.0>");
                                return filterCompletions(completions, args[chanceIndex]);
                            }
                        } else if (args.length == typeIndex + 1) {
                            // Complete type
                            List<String> types = Arrays.asList("hand", "item", "command");
                            return filterCompletions(types, args[typeIndex]);
                        }
                    }
                }
            }
            
            if (args.length == 5 || args.length == 6) {
                String first = args[0].toLowerCase(Locale.ROOT);
                
                if (first.equals("challenge") && player.hasPermission("conradchallenges.admin")) {
                    String second = args[1].toLowerCase(Locale.ROOT);
                    boolean inEditMode = isPlayerInEditMode(player);
                    
                    if (second.equals("setitem")) {
                        int materialIndex = inEditMode ? 2 : 3;
                        int amountIndex = inEditMode ? 3 : 4;
                        int consumeIndex = inEditMode ? 4 : 5;
                        
                        // Amount parameter
                        if (args.length == amountIndex + 1) {
                            completions.add("1");
                            completions.add("8");
                            completions.add("16");
                            completions.add("32");
                            completions.add("64");
                            return filterCompletions(completions, args[amountIndex]);
                        }
                        
                        // Consume parameter: true/false
                        if (args.length == consumeIndex + 1) {
                            completions.add("true");
                            completions.add("false");
                            return filterCompletions(completions, args[consumeIndex]);
                        }
                    }
                }
            }
            
            return Collections.emptyList();
        });
        
        // /challenge - Alias for /conradchallenges challenge
        PluginCommand cmdChallenge = Objects.requireNonNull(getCommand("challenge"));
        cmdChallenge.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.command-players-only"));
                return true;
            }
            
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            
            // Directly call handleAdminChallengeCommands with all args
            // This makes /challenge <subcommand> work like /conradchallenges challenge <subcommand>
            return handleAdminChallengeCommands(player, args);
        });
        cmdChallenge.setTabCompleter((sender, command, alias, args) -> {
            if (!(sender instanceof Player player)) {
                return Collections.emptyList();
            }
            
            if (!player.hasPermission("conradchallenges.admin")) {
                return Collections.emptyList();
            }
            
            // Tab completion for challenge subcommands (same as /conradchallenges challenge)
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                String partial = args[0].toLowerCase(Locale.ROOT);
                for (String subcommand : CHALLENGE_SUBCOMMANDS) {
                    if (subcommand.toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(subcommand);
                    }
                }
                return completions;
            }
            
            // For args.length >= 2, use the same tab completion logic as challenge command
            // We need to prepend "challenge" to args to match the existing logic
            String[] modifiedArgs = new String[args.length + 1];
            modifiedArgs[0] = "challenge";
            System.arraycopy(args, 0, modifiedArgs, 1, args.length);
            
            // Reuse the existing tab completer logic by calling it with modified args
            return cmdConrad.getTabCompleter().onTabComplete(sender, cmdConrad, alias, modifiedArgs);
        });

        // /accept
        Objects.requireNonNull(getCommand("accept")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            
            // Parse difficulty tier argument (if provided)
            String tierArg = args.length > 0 ? args[0].toLowerCase() : null;
            String selectedTier = "easy"; // Default
            if (tierArg != null) {
                if (tierArg.equals("easy") || tierArg.equals("medium") || tierArg.equals("hard") || tierArg.equals("extreme")) {
                    selectedTier = tierArg;
                } else {
                    player.sendMessage(getMessage("accept.invalid-tier", "tier", tierArg));
                    player.sendMessage(getMessage("accept.valid-tiers"));
                    return true;
                }
            }
            
            // First check if player is part of a party session (not the leader)
            PartySession partySession = findPartySessionForPlayer(uuid);
            if (partySession != null && !partySession.leader.equals(uuid)) {
                // Non-leader party members cannot set tier
                // Player is accepting a party invitation
                if (partySession.acceptedPlayers.contains(uuid)) {
                    player.sendMessage(getMessage("accept.already-accepted-party"));
                    return true;
                }
                
                ChallengeConfig cfg = challenges.get(partySession.challengeId);
                if (cfg == null || cfg.destination == null) {
                    player.sendMessage(getMessage("challenge.config-invalid"));
                    return true;
                }
                
                if (!checkAndWarnCooldown(player, cfg)) {
                    return true;
                }
                
                // Check if accepting would exceed max party size
                if (cfg.maxPartySize > 0 && partySession.acceptedPlayers.size() >= cfg.maxPartySize) {
                    player.sendMessage(getMessage("accept.party-full", "limit", String.valueOf(cfg.maxPartySize)));
                    player.sendMessage(getMessage("accept.party-full-hint"));
                    return true;
                }
                
                partySession.acceptedPlayers.add(uuid);
                
                // Update the accepted players set in the countdown wrapper
                CountdownTaskWrapper wrapper = activeCountdownTasks.get(partySession.challengeId);
                if (wrapper != null && wrapper.acceptedPlayers != null) {
                    wrapper.acceptedPlayers.add(uuid);
                }
                
                Player leader = Bukkit.getPlayer(partySession.leader);
                if (leader != null && leader.isOnline()) {
                    leader.sendMessage(getMessage("accept.accepted-party", "player", player.getName()));
                }
                
                // Check if all eligible players have accepted
                if (partySession.acceptedPlayers.size() == partySession.eligiblePlayers.size()) {
                    // All players accepted - reduce countdown to 10s if it's > 10s
                    if (wrapper != null && wrapper.seconds > soloCountdownSeconds) {
                        wrapper.setSeconds(soloCountdownSeconds);
                        // Notify all players
                        List<Player> countdownList = countdownPlayers.get(partySession.challengeId);
                        if (countdownList != null) {
                            for (Player p : countdownList) {
                                if (p.isOnline()) {
                                    p.sendMessage(getMessage("accept.all-accepted-speedup", "seconds", String.valueOf(soloCountdownSeconds)));
                                }
                            }
                        }
                    }
                } else {
                    int remaining = partySession.eligiblePlayers.size() - partySession.acceptedPlayers.size();
                    if (leader != null && leader.isOnline()) {
                        leader.sendMessage(getMessage("accept.waiting-more", "remaining", String.valueOf(remaining)));
                        leader.sendMessage(getMessage("accept.confirm-hint"));
                    }
                }
                return true;
            }
            
            // Check if player is a party leader accepting
            PartySession leaderSession = partySessions.get(uuid);
            if (leaderSession != null) {
                // Party leader is accepting and setting tier
                leaderSession.difficultyTier = selectedTier;
                // Start the challenge with the selected tier
                ChallengeConfig cfg = challenges.get(leaderSession.challengeId);
                if (cfg == null || cfg.destination == null) {
                    player.sendMessage(getMessage("challenge.config-invalid"));
                    return true;
                }
                
                if (isChallengeLocked(cfg.id)) {
                    if (isChallengeBeingEdited(cfg.id)) {
                        UUID editorUuid = getChallengeEditor(cfg.id);
                        Player editor = editorUuid != null ? Bukkit.getPlayer(editorUuid) : null;
                        String editorName = editor != null && editor.isOnline() ? editor.getName() : "an admin";
                        player.sendMessage(getMessage("challenge.maintenance", "admin", editorName));
                        player.sendMessage(getMessage("challenge.locked-queue"));
                    } else {
                        player.sendMessage(getMessage("admin.challenge-locked"));
                    }
                    return true;
                }
                
                // Store tier for this challenge instance
                activeChallengeDifficulties.put(cfg.id, selectedTier);
                
                // Start party challenge
                startPartyChallenge(leaderSession, cfg);
                return true;
            }
            
            // Handle solo challenge acceptance
            PendingChallenge pending = pendingChallenges.get(uuid);
            if (pending == null) {
                player.sendMessage(getMessage("challenge.no-pending"));
                return true;
            }
            // Set tier for solo challenge
            pending.difficultyTier = selectedTier;
            ChallengeConfig cfg = challenges.get(pending.challengeId);
            if (cfg == null || cfg.destination == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }

            // Check if challenge is locked down
            if (isChallengeLockedDown(cfg.id)) {
                player.sendMessage(getMessage("challenge.locked-down"));
                // Remove pending challenge
                cancelPendingChallengeTimeout(uuid);
                pendingChallenges.remove(uuid);
                return true;
            }

            // Check if player is in queue and if it's their turn
            QueueEntry queueEntry = playerQueueEntries.get(uuid);
            if (queueEntry != null && queueEntry.challengeId.equals(cfg.id)) {
                // Player is in queue, check if it's their turn
                Queue<QueueEntry> queue = challengeQueues.get(cfg.id);
                if (queue != null && !queue.isEmpty() && queue.peek() != queueEntry) {
                    int position = 1;
                    for (QueueEntry e : queue) {
                        if (e == queueEntry) break;
                        position++;
                    }
                    player.sendMessage(getMessage("challenge.queue-position", "position", String.valueOf(position)));
                    player.sendMessage(getMessage("challenge.queue-wait"));
                    return true;
                }
            }

            if (isChallengeLocked(cfg.id)) {
                if (isChallengeBeingEdited(cfg.id)) {
                    UUID editorUuid = getChallengeEditor(cfg.id);
                    Player editor = editorUuid != null ? Bukkit.getPlayer(editorUuid) : null;
                    String editorName = editor != null && editor.isOnline() ? editor.getName() : "an admin";
                    player.sendMessage(getMessage("challenge.maintenance", "admin", editorName));
                    // Add to queue
                    if (queueEntry == null || !queueEntry.challengeId.equals(cfg.id) || queueEntry.isParty) {
                        addToQueue(uuid, cfg.id, false);
                    }
                    player.sendMessage(getMessage("challenge.locked-queue"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-locked"));
                }
                return true;
            }

            if (!checkAndWarnCooldown(player, cfg)) {
                return true;
            }

            // Remove from queue if they were in it
            removeFromQueue(uuid);
            cancelPendingChallengeTimeout(uuid);
            pendingChallenges.remove(uuid);

            // Store tier for this challenge instance
            activeChallengeDifficulties.put(cfg.id, pending.difficultyTier);

            reservedChallenges.add(cfg.id);
            startTeleportCountdown(Collections.singletonList(player), cfg);
            return true;
        });
        Objects.requireNonNull(getCommand("accept")).setTabCompleter((sender, command, alias, args) -> {
            if (!(sender instanceof Player)) {
                return Collections.emptyList();
            }
            
            // If player types /accept with a space, complete difficulty tiers
            if (args.length == 1) {
                List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                return filterCompletions(tiers, args[0]);
            }
            
            // No completion for additional arguments
            return Collections.emptyList();
        });

        // /deny
        Objects.requireNonNull(getCommand("deny")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            
            boolean cancelledSomething = false;
            
            // Cancel active countdown if player is in one
            String challengeId = playerCountdownChallenges.remove(uuid);
            if (challengeId != null) {
                List<Player> countdownList = countdownPlayers.get(challengeId);
                if (countdownList != null && countdownList.remove(player)) {
                    cancelledSomething = true;
                    // Clean up bossbar
                    BossBar bossBar = playerCountdownBossBars.remove(uuid);
                    if (bossBar != null) {
                        bossBar.removePlayer(player);
                        bossBar.removeAll();
                    }
                    // If countdown list is now empty, cancel the task and clean up
                    if (countdownList.isEmpty()) {
                        CountdownTaskWrapper wrapper = activeCountdownTasks.remove(challengeId);
                        if (wrapper != null && wrapper.task != null) {
                            wrapper.task.cancel();
                        }
                        countdownPlayers.remove(challengeId);
                        reservedChallenges.remove(challengeId);
                    }
                }
            }
            
            // Remove from queue (works even during countdown)
            boolean wasInQueue = playerQueueEntries.containsKey(uuid);
            removeFromQueue(uuid);
            if (wasInQueue) {
                cancelledSomething = true;
            }
            
            // Remove pending challenge and party session (even if not in queue, if challenge is clear)
            cancelPendingChallengeTimeout(uuid);
            PendingChallenge pending = pendingChallenges.remove(uuid);
            PartySession partySession = partySessions.remove(uuid);
            
            if (pending != null || partySession != null) {
                cancelledSomething = true;
            }
            
            if (!cancelledSomething) {
                player.sendMessage(getMessage("deny.nothing-pending"));
            } else {
                player.sendMessage(getMessage("deny.cancelled"));
                if (wasInQueue) {
                    player.sendMessage(getMessage("deny.removed-queue"));
                }
                if (challengeId != null) {
                    player.sendMessage(getMessage("deny.countdown-cancelled"));
                }
            }
            return true;
        });

        // /startparty
        Objects.requireNonNull(getCommand("startparty")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            PendingChallenge pending = pendingChallenges.get(uuid);
            if (pending == null) {
                player.sendMessage(getMessage("challenge.no-pending-click"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(pending.challengeId);
            if (cfg == null || cfg.destination == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }

            // Check if player is in queue and if it's their turn
            QueueEntry queueEntry = playerQueueEntries.get(uuid);
            if (queueEntry != null && queueEntry.challengeId.equals(cfg.id) && queueEntry.isParty) {
                // Player is in queue, check if it's their turn
                Queue<QueueEntry> queue = challengeQueues.get(cfg.id);
                if (queue != null && !queue.isEmpty() && queue.peek() != queueEntry) {
                    int position = 1;
                    for (QueueEntry e : queue) {
                        if (e == queueEntry) break;
                        position++;
                    }
                    player.sendMessage(getMessage("challenge.queue-position", "position", String.valueOf(position)));
                    player.sendMessage(getMessage("challenge.queue-wait"));
                    return true;
                }
            }

            if (isChallengeLocked(cfg.id)) {
                // Challenge is locked, add to queue if not already in it
                if (queueEntry == null || !queueEntry.challengeId.equals(cfg.id) || !queueEntry.isParty) {
                    addToQueue(uuid, cfg.id, true);
                }
                player.sendMessage(getMessage("challenge.locked-queue"));
                return true;
            }

            int radiusSquared = partyRadius * partyRadius;
            List<Player> nearby = player.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= radiusSquared)
                    .collect(Collectors.toList());

            List<Player> eligible = new ArrayList<>();
            List<Player> ineligible = new ArrayList<>();

            for (Player p : nearby) {
                if (hasChallengeBook(p, cfg.bookTitle)) {
                    eligible.add(p);
                } else {
                    ineligible.add(p);
                }
            }

            // Create set of eligible player UUIDs (including leader)
            Set<UUID> eligibleUuids = eligible.stream()
                    .map(Player::getUniqueId)
                    .collect(Collectors.toSet());

            PartySession session = new PartySession(player.getUniqueId(), cfg.id, eligibleUuids);
            partySessions.put(player.getUniqueId(), session);

            // Reserve the challenge
            reservedChallenges.add(cfg.id);
            
            // Remove pending challenge
            cancelPendingChallengeTimeout(uuid);
            pendingChallenges.remove(uuid);
            
            // Remove from queue if they were in it
            removeFromQueue(uuid);

            player.sendMessage(getMessage("party.gatekeeper-book-required", "challenge", displayName(cfg)));
            player.sendMessage(getMessage("party.eligible-members"));
            if (eligible.isEmpty()) {
                player.sendMessage(getMessage("party-additional.eligible-none-display"));
            } else {
                for (Player p : eligible) {
                    player.sendMessage(getMessage("party.eligible-player", "player", p.getName()));
                    if (!p.getUniqueId().equals(uuid)) {
                        p.sendMessage(getMessage("party.eligible-notify", "challenge", displayName(cfg)));
                        p.sendMessage(getMessage("party.eligible-accept"));
                    }
                }
            }
            player.sendMessage(getMessage("party.ineligible"));
            if (ineligible.isEmpty()) {
                player.sendMessage(getMessage("party-additional.ineligible-none-display"));
            } else {
                for (Player p : ineligible) {
                    player.sendMessage(getMessage("party.ineligible-player", "player", p.getName()));
                    p.sendMessage(getMessage("party.ineligible-notify", "challenge", displayName(cfg)));
                }
            }
            
            // Start countdown immediately with all eligible players
            List<Player> eligiblePlayersList = eligible.stream()
                    .filter(p -> p.isOnline())
                    .collect(Collectors.toList());
            
            for (Player p : eligiblePlayersList) {
                p.sendMessage(getMessage("party.entering", "challenge", displayName(cfg)));
                p.sendMessage(getMessage("party.accept-prompt"));
            }
            
            startTeleportCountdown(eligiblePlayersList, cfg, true, session.acceptedPlayers);
            return true;
        });

        // /confirm - Leader can manually start the challenge with only accepted players
        Objects.requireNonNull(getCommand("confirm")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            PartySession session = partySessions.get(player.getUniqueId());
            if (session == null) {
                player.sendMessage(getMessage("party.no-session"));
                return true;
            }
            
            // Only the leader can confirm
            if (!session.leader.equals(player.getUniqueId())) {
                player.sendMessage(getMessage("party.leader-only"));
                return true;
            }
            
            ChallengeConfig cfg = challenges.get(session.challengeId);
            if (cfg == null || cfg.destination == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                partySessions.remove(player.getUniqueId());
                cancelPendingChallengeTimeout(player.getUniqueId());
                pendingChallenges.remove(player.getUniqueId());
                return true;
            }
            
            // Check if at least the leader has accepted (should always be true, but check anyway)
            if (session.acceptedPlayers.isEmpty()) {
                player.sendMessage(getMessage("party.no-accepted"));
                return true;
            }
            
            // Update the accepted players set in the countdown wrapper
            CountdownTaskWrapper wrapper = activeCountdownTasks.get(session.challengeId);
            if (wrapper != null && wrapper.acceptedPlayers != null) {
                wrapper.acceptedPlayers.clear();
                wrapper.acceptedPlayers.addAll(session.acceptedPlayers);
            }
            
            // Start with only the players who have accepted
            int notAccepted = session.eligiblePlayers.size() - session.acceptedPlayers.size();
            if (notAccepted > 0) {
                player.sendMessage(getMessage("party-additional.confirm-starting", "count", String.valueOf(session.acceptedPlayers.size())));
                player.sendMessage(getMessage("party-additional.confirm-not-accepted", "count", String.valueOf(notAccepted)));
            } else {
                player.sendMessage(getMessage("party.all-accepted"));
            }
            return true;
        });

        // /challengecomplete (and /cc alias)
        Objects.requireNonNull(getCommand("challengecomplete")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            String challengeId = activeChallenges.get(uuid);
            if (challengeId == null) {
                player.sendMessage(getMessage("challenge.no-active"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }
            if (spawnLocation == null) {
                player.sendMessage(getMessage("challenge.spawn-not-configured"));
                return true;
            }

            // Find all party members (players with the same active challenge)
            List<Player> partyMembers = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : activeChallenges.entrySet()) {
                if (entry.getValue().equals(challengeId)) {
                    Player member = Bukkit.getPlayer(entry.getKey());
                    if (member != null && member.isOnline()) {
                        partyMembers.add(member);
                    }
                }
            }

            // Check if any party member has a Completion Key
            Player keyHolder = null;
            ItemStack completionKey = null;
            
            // First check the player who used the command
            completionKey = findCompletionKey(player);
            if (completionKey != null) {
                keyHolder = player;
            } else {
                // Check other party members
                for (Player member : partyMembers) {
                    if (member.equals(player)) continue; // Already checked
                    ItemStack key = findCompletionKey(member);
                    if (key != null) {
                        completionKey = key;
                        keyHolder = member;
                        break;
                    }
                }
            }

            if (completionKey == null || keyHolder == null) {
                if (partyMembers.size() > 1) {
                    player.sendMessage(getMessage("completion.no-key-party"));
                    player.sendMessage(getMessage("completion.key-required"));
                    player.sendMessage(getMessage("completion.key-required-party"));
                } else {
                    player.sendMessage(getMessage("completion.no-key"));
                    player.sendMessage(getMessage("completion.key-required"));
                }
                return true;
            }
            
            // Consume the key from whoever has it
            consumeCompletionKey(keyHolder, completionKey);
            
            // If party member other than command user has the key, notify them
            if (!keyHolder.equals(player)) {
                keyHolder.sendMessage(getMessage("completion.key-consumed", "player", player.getName()));
            }

            // Complete challenge for all party members
            for (Player member : partyMembers) {
                UUID memberUuid = member.getUniqueId();
                
                if (cfg.completionType == CompletionType.SPEED) {
                    Long start = challengeStartTimes.get(memberUuid);
                    if (start != null) {
                        long elapsed = (System.currentTimeMillis() - start) / 1000L;
                        SpeedTier tier = findBestSpeedTier(cfg, (int) elapsed);
                        if (tier != null) {
                            completedChallenges.put(memberUuid, challengeId);
                            boolean isNewRecord = updateBestTime(challengeId, memberUuid, elapsed);
                            member.sendMessage(getMessage("completion.completed-speed", "challenge", displayName(cfg), "seconds", String.valueOf(elapsed)));
                            member.sendMessage(getMessage("completion.completed-speed-tier", "tier", tier.name));
                            markEverCompleted(cfg, memberUuid);
                            
                            // Announce new record to server
                            if (isNewRecord) {
                                String challengeName = displayName(cfg);
                                String timeFormatted = formatTime(elapsed);
                                String message = getMessage("completion.new-record", "player", member.getName(), "challenge", challengeName, "time", timeFormatted);
                                Bukkit.broadcastMessage(message);
                            }
                        } else {
                            member.sendMessage(getMessage("completion.completed-speed-failed", "elapsed", String.valueOf(elapsed), "max", String.valueOf(cfg.timerSeconds)));
                            member.sendMessage(getMessage("completion.completed-speed-retry"));
                        }
                    } else {
                        member.sendMessage(getMessage("completion.completed-speed-no-time"));
                    }
                } else if (cfg.completionType == CompletionType.NONE) {
                    completedChallenges.put(memberUuid, challengeId);
                }

                // Teleport all party members
                member.teleport(spawnLocation);
                if (partyMembers.size() > 1) {
                    member.sendMessage(getMessage("completion.party-returned"));
                } else {
                    member.sendMessage(getMessage("completion.solo-returned"));
                }
                member.sendMessage(getMessage("completion.speak-gatekeeper"));
                
                // Clean up challenge state
                activeChallenges.remove(memberUuid);
                challengeStartTimes.remove(memberUuid);
            }
            
            // Clean up difficulty tier if no players are active in this challenge
            cleanupChallengeDifficultyIfEmpty(cfg.id);
            
            return true;
        });

        // /countdown - Set countdown display preference
        Objects.requireNonNull(getCommand("countdown")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            
            if (args.length == 0) {
                String current = countdownDisplayPreferences.getOrDefault(player.getUniqueId(), "title");
                player.sendMessage(getMessage("countdown.current", "type", current));
                player.sendMessage(getMessage("countdown.usage"));
                return true;
            }
            
            String type = args[0].toLowerCase();
            if (!type.equals("title") && !type.equals("actionbar") && !type.equals("bossbar") && !type.equals("chat")) {
                player.sendMessage(getMessage("countdown.invalid-type"));
                player.sendMessage(getMessage("countdown.usage"));
                return true;
            }
            
            countdownDisplayPreferences.put(player.getUniqueId(), type);
            // Save immediately to player's individual file so preference persists
            savePlayerCountdownPreference(player.getUniqueId(), type);
            player.sendMessage(getMessage("countdown.set", "type", type));
            return true;
        });
        
        Objects.requireNonNull(getCommand("countdown")).setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1) {
                return Arrays.asList("title", "actionbar", "bossbar", "chat");
            }
            return new ArrayList<>();
        });

        // /exit - Exit challenge without completing (no rewards)
        Objects.requireNonNull(getCommand("exit")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            String challengeId = activeChallenges.get(uuid);
            if (challengeId == null) {
                player.sendMessage(getMessage("challenge.no-active"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }
            if (spawnLocation == null) {
                player.sendMessage(getMessage("challenge.spawn-not-configured"));
                return true;
            }

            // Clear challenge state (no completion, no rewards)
            String exitedChallengeId = activeChallenges.remove(uuid);
            completedChallenges.remove(uuid);
            challengeStartTimes.remove(uuid);
            
            // Clean up difficulty tier if no players are active in this challenge
            if (exitedChallengeId != null) {
                cleanupChallengeDifficultyIfEmpty(exitedChallengeId);
            }
            
            // Check if challenge is now available and process queue
            if (exitedChallengeId != null && !isChallengeLocked(exitedChallengeId)) {
                processQueue(exitedChallengeId);
            }

            // Teleport back
            player.teleport(spawnLocation);
            player.sendMessage(getMessage("exit.exited-returned"));
            player.sendMessage(getMessage("exit.exited-no-rewards"));
            return true;
        });


        // /sorry
        Objects.requireNonNull(getCommand("sorry")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            if (!isAngryAt(player)) {
                player.sendMessage(getMessage("sorry.what-apologizing"));
                return true;
            }
            clearAnger(player);
            player.sendMessage(getMessage("sorry.forgiven-message"));
            player.sendMessage(getMessage("sorry.forgiven-hint"));
            return true;
        });
    }
    
    // ====== TAB COMPLETION HELPER ======
    
    private List<String> filterCompletions(List<String> completions, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                filtered.add(completion);
            }
        }
        Collections.sort(filtered);
        return filtered;
    }
    
    // ====== ENTITY DETECTION ======
    
    /**
     * Gets the entity the player is looking at using raycasting.
     * This method works better with FancyNPCs and custom entities than getTargetEntity().
     * 
     * @param player The player to check
     * @param maxDistance Maximum distance to check
     * @return The entity being looked at, or null if none found
     */
    private Entity getTargetEntity(Player player, double maxDistance) {
        // First try the standard method (works for most entities)
        Entity standardTarget = player.getTargetEntity((int) maxDistance);
        if (standardTarget != null) {
            return standardTarget;
        }
        
        // Use raycasting for better detection (works with FancyNPCs and custom entities)
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        
        // Raycast to find entities
        RayTraceResult result = player.getWorld().rayTraceEntities(
            eyeLocation,
            direction,
            maxDistance,
            entity -> entity != player && !entity.isDead()
        );
        
        if (result != null && result.getHitEntity() != null) {
            return result.getHitEntity();
        }
        
        // Fallback: check nearby entities in the direction the player is looking
        List<Entity> nearby = player.getNearbyEntities(maxDistance, maxDistance, maxDistance);
        Location playerLoc = player.getEyeLocation();
        Vector playerDir = playerLoc.getDirection().normalize();
        
        Entity closest = null;
        double closestDistance = maxDistance;
        
        for (Entity entity : nearby) {
            if (entity == player || entity.isDead()) continue;
            
            Vector toEntity = entity.getLocation().toVector().subtract(playerLoc.toVector());
            double distance = toEntity.length();
            
            if (distance > maxDistance) continue;
            
            // Check if entity is in front of player (within 45 degree cone)
            toEntity.normalize();
            double dot = playerDir.dot(toEntity);
            if (dot > 0.7) { // ~45 degrees
                if (distance < closestDistance) {
                    closest = entity;
                    closestDistance = distance;
                }
            }
        }
        
        return closest;
    }
    
    /**
     * Sets the display name for an entity, using FancyNPCs API if available, otherwise using standard Bukkit method.
     * 
     * @param entity The entity to set the display name for
     * @param displayName The display name (supports color codes with &)
     */
    @SuppressWarnings("deprecation")
    private void setEntityDisplayName(Entity entity, String displayName) {
        String translatedName = ChatColor.translateAlternateColorCodes('&', displayName);
        
        // Always set standard display name first
        entity.setCustomName(translatedName);
        entity.setCustomNameVisible(true);
        
        // Try FancyNPCs API if available (this will override the standard name if it's a FancyNPC)
        // Using reflection to access API (following exact API structure from documentation)
        if (getServer().getPluginManager().getPlugin("FancyNPCs") != null) {
            try {
                // Get FancyNpcsPlugin instance using reflection (FancyNpcsPlugin.get())
                Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.FancyNpcsPlugin");
                Object fancyNpcsPlugin = pluginClass.getMethod("get").invoke(null);
                if (fancyNpcsPlugin == null) {
                    return;
                }
                
                // Get NPC Manager (fancyNpcsPlugin.getNpcManager())
                Method getNpcManagerMethod = pluginClass.getMethod("getNpcManager");
                Object npcManager = getNpcManagerMethod.invoke(fancyNpcsPlugin);
                if (npcManager == null) {
                    return;
                }
                
                // Try to get NPC by UUID (npcManager.getNpc(UUID))
                Method getNpcMethod = npcManager.getClass().getMethod("getNpc", UUID.class);
                Object npc = getNpcMethod.invoke(npcManager, entity.getUniqueId());
                
                // If not found by UUID, try to find by iterating all NPCs
                if (npc == null) {
                    Method getAllNpcsMethod = npcManager.getClass().getMethod("getAllNpcs");
                    @SuppressWarnings("unchecked")
                    Collection<Object> allNpcs = (Collection<Object>) getAllNpcsMethod.invoke(npcManager);
                    
                    for (Object testNpc : allNpcs) {
                        // Get the NPC's entity to compare (npc.getEntity())
                        Method getEntityMethod = testNpc.getClass().getMethod("getEntity");
                        Object npcEntity = getEntityMethod.invoke(testNpc);
                        if (npcEntity != null && npcEntity.equals(entity)) {
                            npc = testNpc;
                            break;
                        }
                    }
                }
                
                if (npc != null) {
                    // Get the NPC's data object (npc.getData())
                    Method getDataMethod = npc.getClass().getMethod("getData");
                    Object npcData = getDataMethod.invoke(npc);
                    
                    // Set display name on the data object (npcData.setDisplayName(String))
                    Method setDisplayNameMethod = npcData.getClass().getMethod("setDisplayName", String.class);
                    setDisplayNameMethod.invoke(npcData, translatedName);
                    
                    // Update the NPC for all players (npc.updateForAll())
                    Method updateForAllMethod = npc.getClass().getMethod("updateForAll");
                    updateForAllMethod.invoke(npc);
                    
                    getLogger().info("Set FancyNPCs display name for entity " + entity.getUniqueId());
                }
            } catch (Exception e) {
                // FancyNPCs API not available or entity is not a FancyNPC - standard method already set
                // (ClassNotFoundException, NoClassDefFoundError, etc. are all caught here)
                getLogger().fine("Could not use FancyNPCs API: " + e.getMessage());
            }
        }
    }
    
    /**
     * Clears the display name from an entity (works for both regular entities and FancyNPCs).
     * 
     * @param entity The entity to clear the display name from
     */
    @SuppressWarnings("deprecation")
    private void clearEntityDisplayName(Entity entity) {
        // Clear standard display name
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        
        // Try FancyNPCs API if available (to clear FancyNPC display name)
        if (getServer().getPluginManager().getPlugin("FancyNPCs") != null) {
            try {
                // Get FancyNpcsPlugin instance using reflection
                Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.FancyNpcsPlugin");
                Object fancyNpcsPlugin = pluginClass.getMethod("get").invoke(null);
                if (fancyNpcsPlugin == null) {
                    return;
                }
                
                // Get NPC Manager
                Method getNpcManagerMethod = pluginClass.getMethod("getNpcManager");
                Object npcManager = getNpcManagerMethod.invoke(fancyNpcsPlugin);
                if (npcManager == null) {
                    return;
                }
                
                // Try to get NPC by UUID
                Method getNpcMethod = npcManager.getClass().getMethod("getNpc", UUID.class);
                Object npc = getNpcMethod.invoke(npcManager, entity.getUniqueId());
                
                // If not found by UUID, try to find by iterating all NPCs
                if (npc == null) {
                    Method getAllNpcsMethod = npcManager.getClass().getMethod("getAllNpcs");
                    @SuppressWarnings("unchecked")
                    Collection<Object> allNpcs = (Collection<Object>) getAllNpcsMethod.invoke(npcManager);
                    
                    for (Object testNpc : allNpcs) {
                        Method getEntityMethod = testNpc.getClass().getMethod("getEntity");
                        Object npcEntity = getEntityMethod.invoke(testNpc);
                        if (npcEntity != null && npcEntity.equals(entity)) {
                            npc = testNpc;
                            break;
                        }
                    }
                }
                
                if (npc != null) {
                    // Get the NPC's data object and clear display name
                    Method getDataMethod = npc.getClass().getMethod("getData");
                    Object npcData = getDataMethod.invoke(npc);
                    
                    // Clear display name (set to null or empty string)
                    Method setDisplayNameMethod = npcData.getClass().getMethod("setDisplayName", String.class);
                    setDisplayNameMethod.invoke(npcData, (String) null);
                    
                    // Update the NPC for all players
                    Method updateForAllMethod = npc.getClass().getMethod("updateForAll");
                    updateForAllMethod.invoke(npc);
                    
                    getLogger().info("Cleared FancyNPCs display name for entity " + entity.getUniqueId());
                }
            } catch (Exception e) {
                // FancyNPCs API not available or entity is not a FancyNPC - standard method already cleared
                getLogger().fine("Could not use FancyNPCs API to clear display name: " + e.getMessage());
            }
        }
    }

    // ====== ADMIN CHALLENGE COMMANDS ======

    /**
     * Gets the challenge ID for a command. If player is in edit mode, uses that challenge.
     * Otherwise, requires the ID to be provided in args.
     * @param player The player executing the command
     * @param args The command arguments
     * @param argIndex The index in args where the challenge ID should be (if not in edit mode)
     * @return The challenge ID, or null if not found/not in edit mode
     */
    private String getChallengeIdForCommand(Player player, String[] args, int argIndex) {
        // Check if player is in edit mode
        String editingChallengeId = getPlayerEditingChallenge(player);
        if (editingChallengeId != null) {
            return editingChallengeId;
        }
        
        // Not in edit mode, require ID in args
        if (args.length <= argIndex) {
            return null;
        }
        return args[argIndex];
    }

    private boolean handleAdminChallengeCommands(Player player, String[] args) {
        if (!player.hasPermission("conradchallenges.admin")) {
            player.sendMessage(getMessage("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(getMessage("admin.challenge-create-command"));
            player.sendMessage(getMessage("admin.challenge-delete-command"));
            player.sendMessage(getMessage("admin.challenge-setbook-command"));
            player.sendMessage(getMessage("admin.challenge-clearbook-command"));
            player.sendMessage(getMessage("admin.challenge-setdestination-command"));
            player.sendMessage(getMessage("admin.challenge-settype-command"));
            player.sendMessage(getMessage("admin.challenge-setboss-command"));
            player.sendMessage(getMessage("admin.challenge-settimer-command"));
            player.sendMessage(getMessage("admin.challenge-setitem-command"));
            player.sendMessage(getMessage("admin.challenge-setspeed-command"));
            player.sendMessage(getMessage("admin.challenge-setcooldown-command"));
            player.sendMessage(getMessage("admin.challenge-settimelimit-command"));
            player.sendMessage(getMessage("admin.challenge-setmaxparty-command"));
            player.sendMessage(getMessage("admin.challenge-addreward-command"));
            player.sendMessage(getMessage("admin.challenge-clearrewards-command"));
            player.sendMessage(getMessage("admin.challenge-list-command"));
            player.sendMessage(getMessage("admin.challenge-info-command"));
            player.sendMessage(getMessage("admin.challenge-setregenerationarea-command"));
            player.sendMessage(getMessage("admin.challenge-clearregenerationarea-command"));
            player.sendMessage(getMessage("admin.challenge-captureregeneration-command"));
            player.sendMessage(getMessage("admin.challenge-setautoregenerationy-command"));
            player.sendMessage(getMessage("admin.challenge-areasync-command"));
            player.sendMessage(getMessage("admin.challenge-edit-command"));
            player.sendMessage(getMessage("admin.challenge-save-command"));
            player.sendMessage(getMessage("admin.challenge-cancel-command"));
            player.sendMessage(getMessage("admin.challenge-lockdown-command"));
            player.sendMessage(getMessage("admin.challenge-unlockdown-command"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-create-usage"));
                return true;
            }
            String id = args[1].toLowerCase(Locale.ROOT);
            if (challenges.containsKey(id)) {
                player.sendMessage(getMessage("admin.challenge-exists", "id", id));
                return true;
            }
            ChallengeConfig cfg = new ChallengeConfig(id);
            challenges.put(id, cfg);
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-created", "id", id));
            return true;
        }

        if (sub.equals("delete")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-delete-usage"));
                return true;
            }
            String id = args[1].toLowerCase(Locale.ROOT);
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Check if challenge is currently active or reserved
            if (isChallengeLocked(id)) {
                player.sendMessage(getMessage("admin.challenge-cannot-delete", "id", id));
                player.sendMessage(getMessage("admin.challenge-cannot-delete-hint"));
                return true;
            }
            
            // Remove from maps
            challenges.remove(id);
            if (cfg.bookTitle != null) {
                bookTitleToChallengeId.remove(cfg.bookTitle);
            }
            
            // Remove from config file
            FileConfiguration config = getConfig();
            config.set("challenges." + id, null);
            saveConfig();
            
            // Clean up data (optional - you might want to keep historical data)
            // bestTimes.remove(id);
            // lastCompletionTimes.remove(id);
            // everCompleted.remove(id);
            
            player.sendMessage(getMessage("admin.challenge-deleted", "id", id));
            return true;
        }

        if (sub.equals("setbook")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setbook-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() != Material.WRITTEN_BOOK || !(hand.getItemMeta() instanceof BookMeta meta)) {
                player.sendMessage(getMessage("admin.challenge-setbook-hold"));
                return true;
            }
            String title = meta.getTitle();
            if (title == null) {
                player.sendMessage(getMessage("admin.challenge-setbook-no-title"));
                return true;
            }
            cfg.bookTitle = title;

            // tag this book as an official challenge book
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(challengeKey, PersistentDataType.STRING, cfg.id);
            hand.setItemMeta(meta);
            player.getInventory().setItemInMainHand(hand);
            player.updateInventory();

            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-setbook-success", "id", id, "title", title));
            return true;
        }

        if (sub.equals("clearbook")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-clearbook-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            String oldTitle = cfg.bookTitle;
            cfg.bookTitle = null;
            
            // Remove from lookup map
            if (oldTitle != null) {
                bookTitleToChallengeId.remove(oldTitle);
            }
            
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-clearbook-success", "id", id, "was", oldTitle != null ? " (was: " + oldTitle + ")" : ""));
            return true;
        }

        if (sub.equals("setdestination")) {
            // setdestination can be run outside edit mode (needed to set destination before entering edit mode)
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setdestination-usage"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            cfg.destination = player.getLocation();
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-setdestination-success", "id", id));
            return true;
        }

        if (sub.equals("settype")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-settype-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get type from args - if in edit mode, it's args[1], otherwise args[2]
            int typeArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= typeArgIndex) {
                player.sendMessage(getMessage("admin.challenge-settype-usage"));
                return true;
            }
            String typeStr = args[typeArgIndex].toUpperCase(Locale.ROOT);
            try {
                cfg.completionType = CompletionType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-settype-invalid", "type", typeStr));
                return true;
            }
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-settype-success", "id", id, "type", String.valueOf(cfg.completionType)));
            return true;
        }

        if (sub.equals("setboss")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setboss-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get entity type from args - if in edit mode, it's args[1], otherwise args[2]
            int typeArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= typeArgIndex) {
                player.sendMessage(getMessage("admin.challenge-setboss-usage"));
                return true;
            }
            String typeStr = args[typeArgIndex].toUpperCase(Locale.ROOT);
            try {
                EntityType et = EntityType.valueOf(typeStr);
                String name = null;
                int nameStartIndex = typeArgIndex + 1;
                if (args.length > nameStartIndex) {
                    String joined = String.join(" ", Arrays.copyOfRange(args, nameStartIndex, args.length));
                    name = joined.replace("_", " ");
                }
                cfg.bossRequirement = new BossRequirement(et, name);
                cfg.completionType = CompletionType.BOSS;
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-setboss-success", "id", id, "entity", et.toString(), "name", name != null ? " (" + name + ")" : ""));
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-setboss-invalid", "type", typeStr));
            }
            return true;
        }

        if (sub.equals("settimer")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-settimer-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get seconds from args - if in edit mode, it's args[1], otherwise args[2]
            int secArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= secArgIndex) {
                player.sendMessage(getMessage("admin.challenge-settimer-usage"));
                return true;
            }
            String secStr = args[secArgIndex];
            try {
                int secs = Integer.parseInt(secStr);
                cfg.completionType = CompletionType.TIMER;
                cfg.timerSeconds = secs;
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-settimer-success", "id", id, "seconds", String.valueOf(secs)));
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.challenge-settimer-invalid", "num", secStr));
            }
            return true;
        }

        if (sub.equals("setitem")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setitem-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get parameters from args - if in edit mode, shift indices by 1
            int baseIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length < baseIndex + 3) {
                player.sendMessage(getMessage("admin.challenge-setitem-usage"));
                return true;
            }
            String matName = args[baseIndex].toUpperCase(Locale.ROOT);
            String amtStr = args[baseIndex + 1];
            String consumeStr = args[baseIndex + 2];
            try {
                Material mat = Material.valueOf(matName);
                int amount = Integer.parseInt(amtStr);
                boolean consume = Boolean.parseBoolean(consumeStr);
                cfg.completionType = CompletionType.ITEM;
                cfg.itemRequirement = new ItemRequirement(mat, Math.max(1, amount), consume);
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-setitem-success", "id", id, "amount", String.valueOf(amount), "material", mat.name(), "consume", String.valueOf(consume)));
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.challenge-setitem-invalid-amount"));
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-setitem-invalid-material"));
            }
            return true;
        }

        if (sub.equals("setspeed")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setspeed-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get seconds from args - if in edit mode, it's args[1], otherwise args[2]
            int secArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= secArgIndex) {
                player.sendMessage(getMessage("admin.challenge-setspeed-usage"));
                return true;
            }
            String secStr = args[secArgIndex];
            try {
                int maxSec = Integer.parseInt(secStr);
                cfg.completionType = CompletionType.SPEED;
                cfg.timerSeconds = maxSec;
                if (cfg.speedTiers.isEmpty()) {
                    cfg.speedTiers.add(new SpeedTier("DEFAULT", maxSec, List.of("eco give %player% 1000")));
                }
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-setspeed-success", "id", id, "seconds", String.valueOf(maxSec)));
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.challenge-settimer-invalid", "num", secStr));
            }
            return true;
        }

        if (sub.equals("setcooldown")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setcooldown-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get seconds from args - if in edit mode, it's args[1], otherwise args[2]
            int secArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= secArgIndex) {
                player.sendMessage(getMessage("admin.challenge-setcooldown-usage"));
                return true;
            }
            String secStr = args[secArgIndex];
            try {
                long secs = Long.parseLong(secStr);
                cfg.cooldownSeconds = secs;
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-setcooldown-success", "id", id, "seconds", String.valueOf(secs)));
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.challenge-settimer-invalid", "num", secStr));
            }
            return true;
        }

        if (sub.equals("settimelimit")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-settimelimit-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get seconds from args - if in edit mode, it's args[1], otherwise args[2]
            int secArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= secArgIndex) {
                player.sendMessage(getMessage("admin.challenge-settimelimit-usage"));
                return true;
            }
            String secStr = args[secArgIndex];
            try {
                int secs = Integer.parseInt(secStr);
                cfg.timeLimitSeconds = Math.max(0, secs);
                if (cfg.timeLimitSeconds > 0 && cfg.timeLimitWarnings.isEmpty()) {
                    cfg.timeLimitWarnings = Arrays.asList(60, 10);
                }
                saveChallengeToConfig(cfg);
                if (cfg.timeLimitSeconds == 0) {
                    player.sendMessage(getMessage("admin.challenge-settimelimit-success", "id", id, "seconds", "0").replace("Set time limit", "Time limit removed (unlimited)"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-settimelimit-success", "id", id, "seconds", String.valueOf(cfg.timeLimitSeconds)));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.challenge-settimer-invalid", "num", secStr));
            }
            return true;
        }

        if (sub.equals("setmaxparty")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setmaxparty-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Get size from args - if in edit mode, it's args[1], otherwise args[2]
            int sizeArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= sizeArgIndex) {
                player.sendMessage(getMessage("admin.challenge-setmaxparty-usage"));
                return true;
            }
            String sizeStr = args[sizeArgIndex];
            try {
                int size = Integer.parseInt(sizeStr);
                cfg.maxPartySize = size;
                saveChallengeToConfig(cfg);
                if (size <= 0) {
                    player.sendMessage(getMessage("admin.challenge-setmaxparty-success", "id", id, "size", "unlimited"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-setmaxparty-success", "id", id, "size", String.valueOf(size)));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("admin.invalid-number", "num", sizeStr));
            }
            return true;
        }

        if (sub.equals("addreward")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-addreward-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argStartIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argStartIndex) {
                player.sendMessage("Usage: /challenge addreward [id] <hand|item|command> [args] [chance]");
                player.sendMessage("Types: hand (uses item in hand), item <material> [amount], command <command>");
                player.sendMessage("Chance: 0.0-1.0 (default: 1.0 = 100%)");
                return true;
            }
            
            String typeStr = args[argStartIndex].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid type. Must be: hand, item, or command");
                return true;
            }
            
            TierReward reward = null;
            double chance = 1.0;
            
            switch (type) {
                case HAND -> {
                    if (args.length < argStartIndex + 1) {
                        player.sendMessage("Usage: /challenge addreward [id] hand [chance]");
                        return true;
                    }
                    // Parse chance (last argument if it's a number between 0.0 and 1.0)
                    if (args.length > argStartIndex + 1) {
                        try {
                            double lastArg = Double.parseDouble(args[args.length - 1]);
                            if (lastArg >= 0.0 && lastArg <= 1.0) {
                                chance = lastArg;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, use default chance of 1.0
                        }
                    }
                    reward = new TierReward(type, chance);
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem == null || handItem.getType().isAir()) {
                        player.sendMessage("You must be holding an item in your hand!");
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage("Usage: /challenge addreward [id] item <material> [amount] [chance]");
                        return true;
                    }
                    try {
                        reward = new TierReward(type, chance);
                        reward.material = Material.valueOf(args[argStartIndex + 1].toUpperCase());
                        if (args.length > argStartIndex + 2) {
                            try {
                                double testChance = Double.parseDouble(args[args.length - 1]);
                                if (testChance >= 0.0 && testChance <= 1.0) {
                                    // Check if this is the chance parameter or amount
                                    if (args.length == argStartIndex + 3) {
                                        // Only 2 args after type, so this must be chance (amount is optional)
                                        chance = testChance;
                                        reward.amount = 1;
                                    } else {
                                        // More args, so last is chance, second-to-last is amount
                                        reward.amount = Integer.parseInt(args[argStartIndex + 2]);
                                        chance = testChance;
                                    }
                                } else {
                                    reward.amount = Integer.parseInt(args[argStartIndex + 2]);
                                }
                            } catch (NumberFormatException e) {
                                reward.amount = Integer.parseInt(args[argStartIndex + 2]);
                            }
                        } else {
                            reward.amount = 1;
                        }
                        // chance is already set in constructor
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("Invalid material: " + args[argStartIndex + 1]);
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage("Usage: /challenge addreward [id] command <command> [chance]");
                        return true;
                    }
                    // Parse chance (last argument if it's a number between 0.0 and 1.0)
                    int argEndIndex = args.length;
                    if (args.length > argStartIndex + 2) {
                        try {
                            double lastArg = Double.parseDouble(args[args.length - 1]);
                            if (lastArg >= 0.0 && lastArg <= 1.0) {
                                chance = lastArg;
                                argEndIndex = args.length - 1;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, use default chance of 1.0
                        }
                    }
                    reward = new TierReward(type, chance);
                    reward.command = String.join(" ", Arrays.copyOfRange(args, argStartIndex + 1, argEndIndex));
                }
            }
            
            if (reward == null) {
                player.sendMessage("Error: Failed to create reward. Please check your command syntax.");
                return true;
            }
            
            // Convert TierReward to RewardWithChance for fallback rewards
            String cmd = null;
            switch (reward.type) {
                case HAND -> {
                    if (reward.itemStack != null) {
                        cmd = "give %player% " + reward.itemStack.getType().name() + " " + reward.itemStack.getAmount();
                    } else {
                        player.sendMessage("Error: No item in hand");
                        return true;
                    }
                }
                case ITEM -> {
                    cmd = "give %player% " + reward.material.name() + " " + reward.amount;
                }
                case COMMAND -> {
                    cmd = reward.command;
                }
            }
            
            if (cmd == null) {
                player.sendMessage("Error: Failed to create command from reward");
                return true;
            }
            
            cfg.fallbackRewardCommands.add(new RewardWithChance(cmd, reward.chance));
            saveChallengeToConfig(cfg);
            String chanceStr = reward.chance == 1.0 ? "100%" : String.format("%.0f%%", reward.chance * 100);
            player.sendMessage(getMessage("admin.challenge-addreward-success", "id", id, "command", cmd + " (chance: " + chanceStr + ")"));
            return true;
        }

        if (sub.equals("clearrewards")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-clearrewards-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            cfg.fallbackRewardCommands.clear();
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-clearrewards-success", "id", id));
            return true;
        }

        if (sub.equals("addtierreward")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argStartIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argStartIndex + 1) {
                player.sendMessage("Usage: /challenge addtierreward [id] <tier> <hand|item|command> [args] [chance]");
                player.sendMessage("Tiers: easy, medium, hard, extreme");
                player.sendMessage("Types: hand (uses item in hand), item <material> [amount], command <command>");
                player.sendMessage("Chance: 0.0-1.0 (default: 1.0 = 100%)");
                return true;
            }
            
            String tier = args[argStartIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage("Invalid tier. Must be: easy, medium, hard, or extreme");
                return true;
            }
            
            String typeStr = args[argStartIndex + 1].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid type. Must be: hand, item, or command");
                return true;
            }
            
            TierReward reward = null;
            double chance = 1.0;
            
            switch (type) {
                case HAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage("Usage: /challenge addtierreward [id] <tier> hand [chance]");
                        return true;
                    }
                    // Parse chance (last argument if it's a number between 0.0 and 1.0)
                    if (args.length > argStartIndex + 2) {
                        try {
                            double lastArg = Double.parseDouble(args[args.length - 1]);
                            if (lastArg >= 0.0 && lastArg <= 1.0) {
                                chance = lastArg;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, use default chance of 1.0
                        }
                    }
                    reward = new TierReward(type, chance);
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem == null || handItem.getType().isAir()) {
                        player.sendMessage("You must be holding an item in your hand!");
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 3) {
                        player.sendMessage("Usage: /challenge addtierreward [id] <tier> item <material> [amount] [chance]");
                        return true;
                    }
                    try {
                        Material material = Material.valueOf(args[argStartIndex + 2].toUpperCase());
                        
                        // Parse amount and chance
                        // Format: item <material> [amount] [chance]
                        // If last arg is 0.0-1.0, it's chance; otherwise it's amount
                        int amount = 1;
                        double itemChance = 1.0;
                        
                        if (args.length > argStartIndex + 3) {
                            // Check if last argument is a chance value (0.0-1.0)
                            try {
                                double lastArg = Double.parseDouble(args[args.length - 1]);
                                if (lastArg >= 0.0 && lastArg <= 1.0) {
                                    // Last arg is chance
                                    itemChance = lastArg;
                                    // Check if there's an amount before it
                                    if (args.length > argStartIndex + 4) {
                                        // Format: item <material> <amount> <chance>
                                        try {
                                            amount = Integer.parseInt(args[argStartIndex + 3]);
                                        } catch (NumberFormatException e) {
                                            player.sendMessage("Invalid amount. Must be a number.");
                                            return true;
                                        }
                                    } else {
                                        // Format: item <material> <chance> (no amount specified)
                                        amount = 1;
                                    }
                                } else {
                                    // Last arg is not a chance, so it must be amount (and no chance specified)
                                    amount = Integer.parseInt(args[argStartIndex + 3]);
                                    itemChance = 1.0;
                                }
                            } catch (NumberFormatException e) {
                                // Last arg is not a number, so it's not chance - must be amount
                                try {
                                    amount = Integer.parseInt(args[argStartIndex + 3]);
                                    itemChance = 1.0;
                                } catch (NumberFormatException e2) {
                                    player.sendMessage("Invalid amount. Must be a number.");
                                    return true;
                                }
                            }
                        } else {
                            // No amount or chance specified
                            amount = 1;
                            itemChance = 1.0;
                        }
                        
                        reward = new TierReward(type, itemChance);
                        reward.material = material;
                        reward.amount = amount;
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("Invalid material: " + args[argStartIndex + 2]);
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 3) {
                        player.sendMessage("Usage: /challenge addtierreward [id] <tier> command <command> [chance]");
                        return true;
                    }
                    // Parse chance (last argument if it's a number between 0.0 and 1.0)
                    int argEndIndex = args.length;
                    if (args.length > argStartIndex + 3) {
                        try {
                            double lastArg = Double.parseDouble(args[args.length - 1]);
                            if (lastArg >= 0.0 && lastArg <= 1.0) {
                                chance = lastArg;
                                argEndIndex = args.length - 1;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, use default chance of 1.0
                        }
                    }
                    reward = new TierReward(type, chance);
                    reward.command = String.join(" ", Arrays.copyOfRange(args, argStartIndex + 2, argEndIndex));
                }
            }
            
            if (reward == null) {
                player.sendMessage("Error: Failed to create reward. Please check your command syntax.");
                return true;
            }
            
            cfg.difficultyTierRewards.computeIfAbsent(tier, k -> new ArrayList<>()).add(reward);
            saveChallengeToConfig(cfg);
            double finalChance = reward.chance;
            String chanceStr = finalChance == 1.0 ? "100%" : String.format("%.0f%%", finalChance * 100);
            player.sendMessage("Added " + typeStr + " reward to " + tier + " tier (chance: " + chanceStr + ")");
            return true;
        }

        if (sub.equals("cleartierrewards")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argIndex) {
                player.sendMessage("Usage: /challenge cleartierrewards [id] <tier>");
                player.sendMessage("Tiers: easy, medium, hard, extreme");
                return true;
            }
            String tier = args[argIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage("Invalid tier. Must be: easy, medium, hard, or extreme");
                return true;
            }
            List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
            if (rewards == null || rewards.isEmpty()) {
                player.sendMessage("No rewards found for " + tier + " tier");
                return true;
            }
            rewards.clear();
            if (rewards.isEmpty()) {
                cfg.difficultyTierRewards.remove(tier);
            }
            saveChallengeToConfig(cfg);
            player.sendMessage("Cleared all rewards for " + tier + " tier");
            return true;
        }

        if (sub.equals("listtierrewards")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argIndex = isPlayerInEditMode(player) ? 1 : 2;
            String filterTier = null;
            if (args.length > argIndex) {
                filterTier = args[argIndex].toLowerCase();
                if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(filterTier)) {
                    player.sendMessage("Invalid tier. Must be: easy, medium, hard, or extreme");
                    return true;
                }
            }
            
            List<String> tiersToShow = filterTier != null ? Arrays.asList(filterTier) : Arrays.asList("easy", "medium", "hard", "extreme");
            boolean foundAny = false;
            for (String tier : tiersToShow) {
                List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
                if (rewards != null && !rewards.isEmpty()) {
                    foundAny = true;
                    player.sendMessage("§6" + tier.toUpperCase() + " Tier Rewards (" + rewards.size() + "):");
                    for (int i = 0; i < rewards.size(); i++) {
                        TierReward reward = rewards.get(i);
                        String chanceStr = reward.chance == 1.0 ? "100%" : String.format("%.0f%%", reward.chance * 100);
                        switch (reward.type) {
                            case HAND -> {
                                if (reward.itemStack != null) {
                                    player.sendMessage("  " + (i + 1) + ". HAND: " + reward.itemStack.getType().name() + " x" + reward.itemStack.getAmount() + " (chance: " + chanceStr + ")");
                                }
                            }
                            case ITEM -> {
                                player.sendMessage("  " + (i + 1) + ". ITEM: " + reward.material.name() + " x" + reward.amount + " (chance: " + chanceStr + ")");
                            }
                            case COMMAND -> {
                                player.sendMessage("  " + (i + 1) + ". COMMAND: " + reward.command + " (chance: " + chanceStr + ")");
                            }
                        }
                    }
                }
            }
            if (!foundAny) {
                player.sendMessage("No tier rewards found" + (filterTier != null ? " for " + filterTier + " tier" : ""));
            }
            return true;
        }

        if (sub.equals("removereward")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argStartIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argStartIndex + 1) {
                player.sendMessage("Usage: /challenge removereward [id] <tier> <reward number>");
                player.sendMessage("Tiers: easy, medium, hard, extreme");
                player.sendMessage("Use /challenge listtierrewards [id] [tier] to see reward numbers");
                return true;
            }
            
            String tier = args[argStartIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage("Invalid tier. Must be: easy, medium, hard, or extreme");
                return true;
            }
            
            List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
            if (rewards == null || rewards.isEmpty()) {
                player.sendMessage("No rewards found for " + tier + " tier");
                return true;
            }
            
            try {
                int rewardNumber = Integer.parseInt(args[argStartIndex + 1]);
                if (rewardNumber < 1 || rewardNumber > rewards.size()) {
                    player.sendMessage("Invalid reward number. Must be between 1 and " + rewards.size());
                    return true;
                }
                
                // Remove the reward (list is 1-indexed, array is 0-indexed)
                TierReward removed = rewards.remove(rewardNumber - 1);
                
                // If the list is now empty, remove the tier entry
                if (rewards.isEmpty()) {
                    cfg.difficultyTierRewards.remove(tier);
                }
                
                saveChallengeToConfig(cfg);
                
                String rewardType = removed.type.name();
                player.sendMessage("Removed reward #" + rewardNumber + " from " + tier + " tier (" + rewardType + ")");
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid reward number: " + args[argStartIndex + 1]);
                player.sendMessage("Use /challenge listtierrewards [id] " + tier + " to see reward numbers");
                return true;
            }
            return true;
        }

        if (sub.equals("addtoptimereward")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            int argStartIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argStartIndex + 1) {
                player.sendMessage("Usage: /challenge addtoptimereward [id] <hand|item|command> [args] [chance]");
                player.sendMessage("Types: hand (uses item in hand), item <material> [amount], command <command>");
                player.sendMessage("Chance: 0.0-1.0 (default: 1.0 = 100%)");
                player.sendMessage("Note: These rewards are only given when a player sets a new top time");
                return true;
            }
            
            String typeStr = args[argStartIndex].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid type. Must be: hand, item, or command");
                return true;
            }
            
            // Parse chance (last argument if it's a number between 0.0 and 1.0)
            double chance = 1.0;
            int argEndIndex = args.length;
            if (args.length > argStartIndex + 1) {
                try {
                    double lastArg = Double.parseDouble(args[args.length - 1]);
                    if (lastArg >= 0.0 && lastArg <= 1.0) {
                        chance = lastArg;
                        argEndIndex = args.length - 1;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a number, use default chance of 1.0
                }
            }
            
            TierReward reward = new TierReward(type, chance);
            
            switch (type) {
                case HAND -> {
                    if (args.length < argStartIndex + 1) {
                        player.sendMessage("Usage: /challenge addtoptimereward [id] hand [chance]");
                        return true;
                    }
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem == null || handItem.getType().isAir()) {
                        player.sendMessage("You must be holding an item in your hand!");
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage("Usage: /challenge addtoptimereward [id] item <material> [amount] [chance]");
                        return true;
                    }
                    try {
                        reward.material = Material.valueOf(args[argStartIndex + 1].toUpperCase());
                        if (args.length > argStartIndex + 2) {
                            try {
                                double testChance = Double.parseDouble(args[argStartIndex + 2]);
                                if (testChance >= 0.0 && testChance <= 1.0 && argStartIndex + 2 == argEndIndex) {
                                    // This is the chance parameter, not amount
                                    reward.amount = 1;
                                } else {
                                    reward.amount = Integer.parseInt(args[argStartIndex + 2]);
                                }
                            } catch (NumberFormatException e) {
                                reward.amount = Integer.parseInt(args[argStartIndex + 2]);
                            }
                        } else {
                            reward.amount = 1;
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("Invalid material: " + args[argStartIndex + 1]);
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage("Usage: /challenge addtoptimereward [id] command <command> [chance]");
                        return true;
                    }
                    reward.command = String.join(" ", Arrays.copyOfRange(args, argStartIndex + 1, argEndIndex));
                }
            }
            
            cfg.topTimeRewards.add(reward);
            saveChallengeToConfig(cfg);
            String chanceStr = chance == 1.0 ? "100%" : String.format("%.0f%%", chance * 100);
            player.sendMessage("Added " + typeStr + " reward to top time rewards (chance: " + chanceStr + ")");
            return true;
        }

        if (sub.equals("setregenerationarea")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Clear any old initial states when setting a new regeneration area
            challengeInitialStates.remove(id);
            challengeRegenerationStatus.remove(id);
            
            // Try to get WorldEdit selection first
            Location[] weSelection = getWorldEditSelection(player);
            Location corner1Loc = null;
            Location corner2Loc = null;
            
            if (weSelection != null && weSelection.length == 2) {
                // WorldEdit selection found - use both corners at once
                corner1Loc = weSelection[0];
                corner2Loc = weSelection[1];
                
                // Check for overlaps with other challenges
                String overlappingChallenge = checkAreaOverlap(corner1Loc, corner2Loc, id, true, true);
                if (overlappingChallenge != null) {
                    player.sendMessage(ChatColor.RED + "Error: Regeneration area overlaps with challenge/regeneration area of challenge '" + overlappingChallenge + "'!");
                    return true;
                }
                
                cfg.regenerationCorner1 = corner1Loc;
                cfg.regenerationCorner2 = corner2Loc;
                
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-corner1", "id", id, 
                    "x", String.valueOf(corner1Loc.getBlockX()), 
                    "y", String.valueOf(corner1Loc.getBlockY()), 
                    "z", String.valueOf(corner1Loc.getBlockZ())));
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-corner2", "id", id, 
                    "x", String.valueOf(corner2Loc.getBlockX()), 
                    "y", String.valueOf(corner2Loc.getBlockY()), 
                    "z", String.valueOf(corner2Loc.getBlockZ())));
                
                // Automatically capture initial state (async)
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing initial state... This may take a moment for large areas."));
                final Player finalPlayer = player; // Capture player reference for callback
                captureInitialState(cfg, success -> {
                    // Callback runs on main thread, so we can safely access player
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) {
                            finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                        } else {
                            finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                        }
                    }
                });
            } else {
                // No WorldEdit selection - fall back to player location method
                Location currentLoc = player.getLocation();
                if (cfg.regenerationCorner1 == null) {
                    cfg.regenerationCorner1 = currentLoc;
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner1", "id", id, 
                        "x", String.valueOf(currentLoc.getBlockX()), 
                        "y", String.valueOf(currentLoc.getBlockY()), 
                        "z", String.valueOf(currentLoc.getBlockZ())));
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-hint"));
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-hint"));
                } else if (cfg.regenerationCorner2 == null) {
                    // Both corners now set - check for overlaps with other challenges
                    String overlappingChallenge = checkAreaOverlap(cfg.regenerationCorner1, currentLoc, id, true, true);
                    if (overlappingChallenge != null) {
                        player.sendMessage(ChatColor.RED + "Error: Regeneration area overlaps with challenge/regeneration area of challenge '" + overlappingChallenge + "'!");
                        return true;
                    }
                    
                    cfg.regenerationCorner2 = currentLoc;
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner2", "id", id, 
                        "x", String.valueOf(currentLoc.getBlockX()), 
                        "y", String.valueOf(currentLoc.getBlockY()), 
                        "z", String.valueOf(currentLoc.getBlockZ())));
                    // Automatically capture initial state when both corners are set (async)
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing initial state... This may take a moment for large areas."));
                    final Player finalPlayer2 = player; // Capture player reference for callback
                    captureInitialState(cfg, success -> {
                        // Callback runs on main thread, so we can safely access player
                        if (finalPlayer2 != null && finalPlayer2.isOnline()) {
                            if (success) {
                                finalPlayer2.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                            } else {
                                finalPlayer2.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                            }
                        }
                    });
                } else {
                    // Both corners already set, replace corner2 - check for overlaps with other challenges
                    String overlappingChallenge = checkAreaOverlap(cfg.regenerationCorner1, currentLoc, id, true, true);
                    if (overlappingChallenge != null) {
                        player.sendMessage(ChatColor.RED + "Error: Regeneration area overlaps with challenge/regeneration area of challenge '" + overlappingChallenge + "'!");
                        return true;
                    }
                    
                    // Both corners already set, replace corner2
                    cfg.regenerationCorner2 = currentLoc;
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner2-updated", "id", id, 
                        "x", String.valueOf(currentLoc.getBlockX()), 
                        "y", String.valueOf(currentLoc.getBlockY()), 
                        "z", String.valueOf(currentLoc.getBlockZ())));
                    // Re-capture initial state (async)
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing initial state... This may take a moment for large areas."));
                    final Player finalPlayer3 = player; // Capture player reference for callback
                    captureInitialState(cfg, success -> {
                        // Callback runs on main thread, so we can safely access player
                        if (finalPlayer3 != null && finalPlayer3.isOnline()) {
                            if (success) {
                                finalPlayer3.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                            } else {
                                finalPlayer3.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                            }
                        }
                    });
                }
            }
            saveChallengeToConfig(cfg);
            return true;
        }

        if (sub.equals("clearregenerationarea")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-clearregenerationarea-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-clearregenerationarea-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            cfg.regenerationCorner1 = null;
            cfg.regenerationCorner2 = null;
            cfg.regenerationAutoExtendY = false; // Reset auto-extend Y as well
            challengeInitialStates.remove(id); // Clear initial states from memory
            challengeRegenerationStatus.remove(id); // Clear regeneration status
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-clearregenerationarea-success", "id", id));
            return true;
        }

        if (sub.equals("setchallengearea")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(ChatColor.RED + "Usage: /conradchallenges challenge setchallengearea <id>");
                player.sendMessage(ChatColor.RED + "You must be in edit mode to set challenge areas.");
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(ChatColor.RED + "Usage: /conradchallenges challenge setchallengearea <id>");
                player.sendMessage(ChatColor.RED + "You must be in edit mode to set challenge areas.");
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Try to get WorldEdit selection first (//1 and //2)
            Location[] weSelection = getWorldEditSelection(player);
            Location corner1Loc = null;
            Location corner2Loc = null;
            
            if (weSelection != null && weSelection.length == 2) {
                // WorldEdit selection found - use both corners at once
                corner1Loc = weSelection[0];
                corner2Loc = weSelection[1];
                
                // Check for overlaps with other challenges
                String overlappingChallenge = checkAreaOverlap(corner1Loc, corner2Loc, id, true, true);
                if (overlappingChallenge != null) {
                    player.sendMessage(ChatColor.RED + "Error: Challenge area overlaps with challenge/regeneration area of challenge '" + overlappingChallenge + "'!");
                    return true;
                }
                
                cfg.challengeCorner1 = corner1Loc;
                cfg.challengeCorner2 = corner2Loc;
                
                player.sendMessage(ChatColor.GREEN + "Challenge area set for '" + id + "' using WorldEdit selection:");
                player.sendMessage(ChatColor.GRAY + "  Corner 1: " + corner1Loc.getBlockX() + ", " + corner1Loc.getBlockY() + ", " + corner1Loc.getBlockZ());
                player.sendMessage(ChatColor.GRAY + "  Corner 2: " + corner2Loc.getBlockX() + ", " + corner2Loc.getBlockY() + ", " + corner2Loc.getBlockZ());
                
                // Capture challenge area state when it's set (only time it should be captured)
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing challenge area state... This may take a moment for large areas."));
                final Player finalPlayer = player; // Capture player reference for callback
                captureChallengeAreaState(cfg, success -> {
                    // Callback runs on main thread, so we can safely access player
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) {
                            finalPlayer.sendMessage(ChatColor.GREEN + "Challenge area state captured successfully!");
                        } else {
                            finalPlayer.sendMessage(ChatColor.RED + "Failed to capture challenge area state!");
                        }
                    }
                });
                
                // Apply barrier boxing if enabled
                if (barrierBoxingEnabled) {
                    player.sendMessage(ChatColor.YELLOW + "Creating barrier box around challenge area...");
                    createBarrierBox(cfg, player);
                }
            } else {
                player.sendMessage(ChatColor.RED + "No WorldEdit selection found! Use //1 and //2 to select the challenge area first.");
                player.sendMessage(ChatColor.GRAY + "Tip: Stand at one corner, use //1, then stand at the opposite corner and use //2");
                return true;
            }
            
            saveChallengeToConfig(cfg);
            return true;
        }

        if (sub.equals("clearchallengearea")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(ChatColor.RED + "Usage: /conradchallenges challenge clearchallengearea <id>");
                player.sendMessage(ChatColor.RED + "You must be in edit mode to clear challenge areas.");
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(ChatColor.RED + "Usage: /conradchallenges challenge clearchallengearea <id>");
                player.sendMessage(ChatColor.RED + "You must be in edit mode to clear challenge areas.");
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Remove barrier box if it exists
            if (barrierBoxingEnabled && cfg.challengeCorner1 != null && cfg.challengeCorner2 != null) {
                removeBarrierBox(cfg, player);
            }
            
            // Clear challenge area state (it will need to be recaptured when area is set again)
            // Note: We don't clear challengeInitialStates here because it's also used for regeneration area
            // The challenge area state will be overwritten when setchallengearea is called again
            
            cfg.challengeCorner1 = null;
            cfg.challengeCorner2 = null;
            saveChallengeToConfig(cfg);
            player.sendMessage(ChatColor.GREEN + "Challenge area cleared for '" + id + "'");
            return true;
        }

        if (sub.equals("areasync")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-areasync-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-areasync-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Check if challenge area is set
            if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
                player.sendMessage(getMessage("admin.challenge-areasync-no-challenge-area", "id", id));
                return true;
            }
            
            // Copy challenge area corners to regeneration area
            cfg.regenerationCorner1 = cfg.challengeCorner1.clone();
            cfg.regenerationCorner2 = cfg.challengeCorner2.clone();
            
            // Clear any old initial states when setting a new regeneration area
            challengeInitialStates.remove(id);
            challengeRegenerationStatus.remove(id);
            
            player.sendMessage(getMessage("admin.challenge-areasync-success", "id", id,
                "x1", String.valueOf(cfg.regenerationCorner1.getBlockX()),
                "y1", String.valueOf(cfg.regenerationCorner1.getBlockY()),
                "z1", String.valueOf(cfg.regenerationCorner1.getBlockZ()),
                "x2", String.valueOf(cfg.regenerationCorner2.getBlockX()),
                "y2", String.valueOf(cfg.regenerationCorner2.getBlockY()),
                "z2", String.valueOf(cfg.regenerationCorner2.getBlockZ())));
            
            // Re-capture initial state (async)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing initial state... This may take a moment for large areas."));
            final Player finalPlayer = player; // Capture player reference for callback
            captureInitialState(cfg, success -> {
                // Callback runs on main thread, so we can safely access player
                if (finalPlayer != null && finalPlayer.isOnline()) {
                    if (success) {
                        finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                    } else {
                        finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                    }
                }
            });
            
            saveChallengeToConfig(cfg);
            return true;
        }

        if (sub.equals("setautoregenerationy")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-setautoregenerationy-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setautoregenerationy-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
                player.sendMessage(getMessage("admin.challenge-setautoregenerationy-no-area", "id", id));
                return true;
            }
            if (args.length < 2) {
                // Toggle current setting
                cfg.regenerationAutoExtendY = !cfg.regenerationAutoExtendY;
            } else {
                String value = args[1].toLowerCase();
                if (value.equals("true") || value.equals("on") || value.equals("enable") || value.equals("yes")) {
                    cfg.regenerationAutoExtendY = true;
                } else if (value.equals("false") || value.equals("off") || value.equals("disable") || value.equals("no")) {
                    cfg.regenerationAutoExtendY = false;
                } else {
                    player.sendMessage(getMessage("admin.challenge-setautoregenerationy-invalid", "value", args[1]));
                    return true;
                }
            }
            saveChallengeToConfig(cfg);
            // Re-capture initial state with new Y range (async)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Capturing initial state with new Y range... This may take a moment for large areas."));
            captureInitialState(cfg, success -> {
                if (success) {
                    player.sendMessage(getMessage("admin.challenge-setautoregenerationy-success", 
                        "id", id, "enabled", cfg.regenerationAutoExtendY ? "enabled" : "disabled"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-setautoregenerationy-failed", "id", id));
                }
            });
            return true;
        }

        if (sub.equals("captureregeneration")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-captureregeneration-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-captureregeneration-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
                player.sendMessage(getMessage("admin.challenge-captureregeneration-no-area"));
                return true;
            }
            player.sendMessage("&7Capturing initial state... This may take a moment for large areas.");
            captureInitialState(cfg, success -> {
                if (success) {
                    player.sendMessage(getMessage("admin.challenge-captureregeneration-success", "id", id));
                } else {
                    player.sendMessage(getMessage("admin.challenge-captureregeneration-failed", "id", id));
                }
            });
            return true;
        }

        if (sub.equals("edit")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-edit-usage"));
                return true;
            }
            String id = args[1];
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            // Note: Admins can enter edit mode even if challenge is locked down
            // (lockdown only prevents players from entering challenges, not admins from editing)
            
            // Check if challenge is already being edited
            UUID currentEditor = challengeEditors.get(id);
            if (currentEditor != null && !currentEditor.equals(player.getUniqueId())) {
                Player currentEditorPlayer = Bukkit.getPlayer(currentEditor);
                String editorName = currentEditorPlayer != null && currentEditorPlayer.isOnline() ? currentEditorPlayer.getName() : "an admin";
                player.sendMessage(getMessage("admin.challenge-edit-already-editing", "id", id, "admin", editorName));
                return true;
            }
            
            // Check if player is already editing another challenge
            for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
                if (entry.getValue().equals(player.getUniqueId()) && !entry.getKey().equals(id)) {
                    player.sendMessage(getMessage("admin.challenge-edit-already-editing-another", "id", entry.getKey()));
                    player.sendMessage(getMessage("admin.challenge-edit-cancel-hint"));
                    return true;
                }
            }
            
            if (cfg.destination == null) {
                player.sendMessage(getMessage("challenge.no-destination"));
                return true;
            }
            
            // Save original location
            editorOriginalLocations.put(player.getUniqueId(), player.getLocation());
            
            // Create backup of challenge config
            ChallengeConfig backup = cloneChallengeConfig(cfg);
            challengeEditBackups.put(id, backup);
            
            // Create backup of block states if regeneration area exists
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                List<BlockState> stateBackup = challengeInitialStates.get(id);
                if (stateBackup != null) {
                    // Deep copy the block states
                    List<BlockState> backupStates = new ArrayList<>();
                    for (BlockState state : stateBackup) {
                        backupStates.add(state);
                    }
                    challengeEditStateBackups.put(id, backupStates);
                }
            }
            
            // Mark challenge as being edited
            challengeEditors.put(id, player.getUniqueId());
            
            // Regenerate the area to clean state
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                regenerateChallengeArea(cfg);
                // Wait a moment for regeneration to complete, then teleport
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Location teleportLoc = getTeleportLocation(cfg);
                        if (teleportLoc != null) {
                            player.teleport(teleportLoc);
                            player.sendMessage(getMessage("admin.challenge-edit-entered", "id", id));
                            player.sendMessage(getMessage("admin.challenge-edit-instructions"));
                        } else {
                            player.sendMessage(ChatColor.RED + "Error: No teleport location set for challenge!");
                        }
                    }
                }.runTaskLater(this, 20L); // 1 second delay
            } else {
                // No regeneration area, teleport immediately
                Location teleportLoc = getTeleportLocation(cfg);
                if (teleportLoc != null) {
                    player.teleport(teleportLoc);
                    player.sendMessage(getMessage("admin.challenge-edit-entered", "id", id));
                    player.sendMessage(getMessage("admin.challenge-edit-instructions"));
                } else {
                    player.sendMessage(ChatColor.RED + "Error: No teleport location set for challenge!");
                }
            }
            
            return true;
        }

        if (sub.equals("save")) {
            // Find which challenge this player is editing
            String editingChallengeId = null;
            for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
                if (entry.getValue().equals(player.getUniqueId())) {
                    editingChallengeId = entry.getKey();
                    break;
                }
            }
            
            if (editingChallengeId == null) {
                player.sendMessage(getMessage("admin.challenge-save-not-editing"));
                return true;
            }
            
            ChallengeConfig cfg = challenges.get(editingChallengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", editingChallengeId));
                exitEditMode(player, editingChallengeId, false);
                return true;
            }
            
            // Check if challenge area is set - required for saving
            if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
                player.sendMessage(ChatColor.RED + "You must set a challenge area before saving!");
                player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/challenge setchallengearea " + editingChallengeId + ChatColor.YELLOW + " after selecting an area with WorldEdit (//1 and //2)");
                return true;
            }
            
            // Recapture regeneration area state (not challenge area - that's only captured when set)
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Recapturing regeneration area state... This may take a moment for large areas."));
                final Player finalPlayer = player; // Capture player reference for callback
                captureInitialState(cfg, success -> {
                    // Callback runs on main thread, so we can safely access player
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) {
                            finalPlayer.sendMessage(ChatColor.GREEN + "Regeneration area state recaptured successfully!");
                        } else {
                            finalPlayer.sendMessage(ChatColor.RED + "Failed to recapture regeneration area state!");
                        }
                    }
                });
            }
            
            // Save challenge config
            saveChallengeToConfig(cfg);
            
            // Exit edit mode
            Location originalLoc = editorOriginalLocations.remove(player.getUniqueId());
            challengeEditors.remove(editingChallengeId);
            challengeEditBackups.remove(editingChallengeId);
            challengeEditStateBackups.remove(editingChallengeId);
            
            // Teleport back
            if (originalLoc != null) {
                player.teleport(originalLoc);
            }
            
            player.sendMessage(getMessage("admin.challenge-save-success", "id", editingChallengeId));
            
            // Process queue if there are players waiting
            if (!isChallengeLocked(editingChallengeId)) {
                processQueue(editingChallengeId);
            }
            
            return true;
        }

        if (sub.equals("cancel")) {
            // Find which challenge this player is editing
            String editingChallengeId = null;
            for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
                if (entry.getValue().equals(player.getUniqueId())) {
                    editingChallengeId = entry.getKey();
                    break;
                }
            }
            
            if (editingChallengeId == null) {
                player.sendMessage(getMessage("admin.challenge-cancel-not-editing"));
                return true;
            }
            
            ChallengeConfig cfg = challenges.get(editingChallengeId);
            ChallengeConfig backup = challengeEditBackups.get(editingChallengeId);
            
            if (backup != null && cfg != null) {
                // Restore challenge config from backup
                restoreChallengeConfig(cfg, backup);
                saveChallengeToConfig(cfg);
                
                // Restore block states if they were backed up
                List<BlockState> stateBackup = challengeEditStateBackups.get(editingChallengeId);
                if (stateBackup != null) {
                    challengeInitialStates.put(editingChallengeId, new ArrayList<>(stateBackup));
                    // Regenerate area to restore original state
                    regenerateChallengeArea(cfg);
                }
                
                player.sendMessage(getMessage("admin.challenge-cancel-restored", "id", editingChallengeId));
            } else {
                player.sendMessage(getMessage("admin.challenge-cancel-no-backup", "id", editingChallengeId));
            }
            
            // Exit edit mode
            Location originalLoc = editorOriginalLocations.remove(player.getUniqueId());
            challengeEditors.remove(editingChallengeId);
            challengeEditBackups.remove(editingChallengeId);
            challengeEditStateBackups.remove(editingChallengeId);
            
            // Teleport back
            if (originalLoc != null) {
                player.teleport(originalLoc);
            }
            
            player.sendMessage(getMessage("admin.challenge-cancel-success", "id", editingChallengeId));
            
            // Process queue if there are players waiting
            if (!isChallengeLocked(editingChallengeId)) {
                processQueue(editingChallengeId);
            }
            
            return true;
        }

        if (sub.equals("lockdown")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-lockdown-usage"));
                return true;
            }
            String id = args[1];
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            // Toggle lockdown state
            if (cfg.lockedDown) {
                // Already locked, unlock it
                cfg.lockedDown = false;
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-unlockdown-success", "id", id));
            } else {
                // Not locked, lock it
                cfg.lockedDown = true;
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-lockdown-success", "id", id));
            }
            return true;
        }

        if (sub.equals("unlockdown")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-unlockdown-usage"));
                return true;
            }
            String id = args[1];
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            if (!cfg.lockedDown) {
                player.sendMessage(getMessage("admin.challenge-unlockdown-not-locked", "id", id));
                return true;
            }
            cfg.lockedDown = false;
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-unlockdown-success", "id", id));
            return true;
        }

        if (sub.equals("list")) {
            if (challenges.isEmpty()) {
                player.sendMessage(getMessage("admin.challenge-list-empty"));
                return true;
            }
            player.sendMessage(getMessage("admin.challenge-list-header"));
            for (ChallengeConfig cfg : challenges.values()) {
                String bookPart = cfg.bookTitle != null ? getMessage("admin.challenge-list-entry-book", "book", cfg.bookTitle) : getMessage("admin.challenge-list-entry-no-book");
                String statusPart = cfg.lockedDown ? " &c[LOCKED DOWN]" : "";
                player.sendMessage(getMessage("admin.challenge-list-entry", "id", cfg.id) + " " + bookPart + statusPart);
            }
            return true;
        }

        if (sub.equals("info")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-info-command"));
                return true;
            }
            String id = args[1];
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            player.sendMessage(getMessage("admin.challenge-info-header", "id", id));
            if (cfg.bookTitle != null) {
                player.sendMessage(getMessage("admin.challenge-info-book", "book", cfg.bookTitle));
            } else {
                player.sendMessage(getMessage("admin.challenge-info-no-book"));
            }
            player.sendMessage(getMessage("admin.challenge-info-type", "type", String.valueOf(cfg.completionType)));
            player.sendMessage(getMessage("admin.challenge-info-cooldown", "cooldown", String.valueOf(cfg.cooldownSeconds)));
            if (cfg.maxPartySize > 0) {
                player.sendMessage(getMessage("admin.challenge-info-max-party", "max", String.valueOf(cfg.maxPartySize)));
            } else {
                player.sendMessage(getMessage("admin.challenge-info-max-party", "max", "unlimited"));
            }
            if (cfg.timeLimitSeconds > 0) {
                player.sendMessage(getMessage("admin.challenge-info-time-limit", "limit", String.valueOf(cfg.timeLimitSeconds)) + getMessage("admin.challenge-info-time-limit-warnings", "warnings", cfg.timeLimitWarnings.toString()));
            } else {
                player.sendMessage(getMessage("admin.challenge-info-time-limit", "limit", "unlimited"));
            }
            if (cfg.destination != null) {
                Location d = cfg.destination;
                player.sendMessage(getMessage("admin.challenge-info-location", "x", String.valueOf(d.getBlockX()), "y", String.valueOf(d.getBlockY()), "z", String.valueOf(d.getBlockZ())));
            } else {
                player.sendMessage(getMessage("admin.challenge-info-no-destination"));
            }
            if (cfg.lockedDown) {
                player.sendMessage(getMessage("admin.challenge-info-locked-down"));
            }
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                player.sendMessage(getMessage("admin.challenge-info-regeneration-area", 
                    "x1", String.valueOf(cfg.regenerationCorner1.getBlockX()),
                    "y1", String.valueOf(cfg.regenerationCorner1.getBlockY()),
                    "z1", String.valueOf(cfg.regenerationCorner1.getBlockZ()),
                    "x2", String.valueOf(cfg.regenerationCorner2.getBlockX()),
                    "y2", String.valueOf(cfg.regenerationCorner2.getBlockY()),
                    "z2", String.valueOf(cfg.regenerationCorner2.getBlockZ())));
                if (cfg.regenerationAutoExtendY) {
                    World world = cfg.regenerationCorner1.getWorld();
                    if (world != null) {
                        player.sendMessage(getMessage("admin.challenge-info-regeneration-auto-extend-y", 
                            "min", String.valueOf(world.getMinHeight()),
                            "max", String.valueOf(world.getMaxHeight() - 1)));
                    }
                } else {
                    int minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
                    int maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
                    player.sendMessage(getMessage("admin.challenge-info-regeneration-manual-y", 
                        "min", String.valueOf(minY),
                        "max", String.valueOf(maxY)));
                }
                int blockCount = challengeInitialStates.containsKey(cfg.id) ? challengeInitialStates.get(cfg.id).size() : 0;
                player.sendMessage(getMessage("admin.challenge-info-regeneration-captured", "count", String.valueOf(blockCount)));
            } else {
                player.sendMessage(getMessage("admin.challenge-info-no-regeneration-area"));
            }
            switch (cfg.completionType) {
                case BOSS -> {
                    if (cfg.bossRequirement != null) {
                        String bossNamePart = cfg.bossRequirement.name != null ? getMessage("admin.challenge-info-boss-name", "name", cfg.bossRequirement.name) : "";
                        player.sendMessage(getMessage("admin.challenge-info-boss", "type", cfg.bossRequirement.type.toString(), "name", bossNamePart));
                    }
                }
                case TIMER -> player.sendMessage(getMessage("admin.challenge-info-timer", "seconds", String.valueOf(cfg.timerSeconds)));
                case ITEM -> {
                    if (cfg.itemRequirement != null) {
                        player.sendMessage(getMessage("admin.challenge-info-item", "amount", String.valueOf(cfg.itemRequirement.amount), "material", cfg.itemRequirement.material.toString(), "consume", String.valueOf(cfg.itemRequirement.consume)));
                    }
                }
                case SPEED -> {
                    player.sendMessage(getMessage("admin.challenge-info-speed", "seconds", String.valueOf(cfg.timerSeconds)));
                    if (!cfg.speedTiers.isEmpty()) {
                        player.sendMessage(getMessage("admin.challenge-info-speed-tiers", "count", String.valueOf(cfg.speedTiers.size())));
                        for (SpeedTier t : cfg.speedTiers) {
                            player.sendMessage(getMessage("admin.challenge-info-speed-tier", "name", t.name, "seconds", String.valueOf(t.maxSeconds), "count", String.valueOf(t.rewardCommands.size())));
                        }
                    } else {
                        player.sendMessage(getMessage("admin.challenge-info-no-speed-tiers"));
                    }
                }
                case NONE -> {
                }
            }
            // Show tier rewards if any exist
            boolean hasTierRewards = cfg.difficultyTierRewards != null && !cfg.difficultyTierRewards.isEmpty();
            if (hasTierRewards) {
                player.sendMessage("§6Difficulty Tier Rewards:");
                for (String tier : Arrays.asList("easy", "medium", "hard", "extreme")) {
                    List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
                    if (rewards != null && !rewards.isEmpty()) {
                        player.sendMessage("  §e" + tier.toUpperCase() + ": §f" + rewards.size() + " reward(s)");
                    }
                }
            }
            
            // Show fallback rewards
            if (cfg.fallbackRewardCommands != null && !cfg.fallbackRewardCommands.isEmpty()) {
                player.sendMessage(getMessage("admin.challenge-info-rewards", "count", String.valueOf(cfg.fallbackRewardCommands.size())));
            } else if (!hasTierRewards) {
                player.sendMessage(getMessage("admin.challenge-info-no-rewards"));
            }
            return true;
        }

        player.sendMessage(getMessage("admin.unknown-subcommand"));
        return true;
    }

    // ====== PARTY COMMANDS ======

    private boolean handlePartyCommands(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(getMessage("admin.party-start-command"));
            player.sendMessage(getMessage("admin.party-confirm-command"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("start")) {
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.party-start-usage"));
                return true;
            }
            String id = args[1].toLowerCase(Locale.ROOT);
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            if (cfg.destination == null) {
                player.sendMessage(getMessage("challenge.no-destination"));
                return true;
            }

            if (isChallengeLocked(cfg.id)) {
                player.sendMessage(getMessage("admin.challenge-locked-party"));
                return true;
            }

            int radiusSquared = partyRadius * partyRadius;
            List<Player> nearby = player.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= radiusSquared)
                    .collect(Collectors.toList());

            List<Player> eligible = new ArrayList<>();
            List<Player> ineligible = new ArrayList<>();

            for (Player p : nearby) {
                if (hasChallengeBook(p, cfg.bookTitle)) {
                    eligible.add(p);
                } else {
                    ineligible.add(p);
                }
            }

            // Create set of eligible player UUIDs (including leader)
            Set<UUID> eligibleUuids = eligible.stream()
                    .map(Player::getUniqueId)
                    .collect(Collectors.toSet());

            partySessions.put(player.getUniqueId(), new PartySession(player.getUniqueId(), id, eligibleUuids));

            player.sendMessage(getMessage("party.gatekeeper-book-required", "challenge", displayName(cfg)));
            player.sendMessage(getMessage("party.eligible-members"));
            if (eligible.isEmpty()) {
                player.sendMessage(getMessage("party-additional.eligible-none-display"));
            } else {
                for (Player p : eligible) {
                    player.sendMessage(getMessage("party.eligible-player", "player", p.getName()));
                    if (!p.getUniqueId().equals(player.getUniqueId())) {
                        p.sendMessage(getMessage("party.eligible-notify", "challenge", displayName(cfg)));
                        p.sendMessage(getMessage("party.eligible-accept"));
                    }
                }
            }
            player.sendMessage(getMessage("party.ineligible"));
            if (ineligible.isEmpty()) {
                player.sendMessage(getMessage("party-additional.ineligible-none-display"));
            } else {
                for (Player p : ineligible) {
                    player.sendMessage(getMessage("party.ineligible-player", "player", p.getName()));
                    p.sendMessage(getMessage("party.ineligible-notify", "challenge", displayName(cfg)));
                }
            }
            player.sendMessage(getMessage("party.waiting-accept"));
            return true;
        }

        if (sub.equals("confirm")) {
            PartySession session = partySessions.get(player.getUniqueId());
            if (session == null) {
                player.sendMessage(getMessage("admin.no-party-confirm"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(session.challengeId);
            if (cfg == null || cfg.destination == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                partySessions.remove(player.getUniqueId());
                return true;
            }

            int radiusSquared = partyRadius * partyRadius;
            List<Player> nearby = player.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= radiusSquared)
                    .collect(Collectors.toList());

            List<Player> entrants = new ArrayList<>();
            for (Player p : nearby) {
                if (hasChallengeBook(p, cfg.bookTitle)) {
                    if (checkAndWarnCooldown(p, cfg)) {
                        entrants.add(p);
                    }
                } else {
                    p.sendMessage(getMessage("party.ineligible-notify", "challenge", displayName(cfg)));
                }
            }

            if (entrants.isEmpty()) {
                player.sendMessage(getMessage("admin.no-book-found"));
                partySessions.remove(player.getUniqueId());
                return true;
            }

            if (cfg.maxPartySize > 0 && entrants.size() > cfg.maxPartySize) {
                player.sendMessage(getMessage("party.max-size", "max", String.valueOf(cfg.maxPartySize)));
                player.sendMessage(getMessage("admin.eligible-detected", "count", String.valueOf(entrants.size())));
                player.sendMessage(getMessage("party.max-size-reduce"));
                partySessions.remove(player.getUniqueId());
                return true;
            }

            if (isChallengeLocked(cfg.id)) {
                player.sendMessage(getMessage("admin.challenge-locked-party"));
                partySessions.remove(player.getUniqueId());
                return true;
            }

            reservedChallenges.add(cfg.id);

            // Remove pending challenge and party session when actually starting
            cancelPendingChallengeTimeout(player.getUniqueId());
            pendingChallenges.remove(player.getUniqueId());
            partySessions.remove(player.getUniqueId());

            player.sendMessage(getMessage("party.confirmed", "count", String.valueOf(entrants.size()), "challenge", displayName(cfg)));
            for (Player p : entrants) {
                p.sendMessage(getMessage("party.entering", "challenge", displayName(cfg)) + getMessage("admin.party-teleporting", "seconds", String.valueOf(partyCountdownSeconds)));
            }

            startTeleportCountdown(entrants, cfg, true); // true = isParty
            return true;
        }

        player.sendMessage(getMessage("admin.party-unknown-subcommand"));
        return true;
    }

    // ====== PARTY HELPERS ======

    private PartySession findPartySessionForPlayer(UUID playerUuid) {
        for (PartySession session : partySessions.values()) {
            if (session.eligiblePlayers.contains(playerUuid)) {
                return session;
            }
        }
        return null;
    }

    private void startPartyChallenge(PartySession session, ChallengeConfig cfg) {
        // Get all accepted players - only these players will enter and have their books consumed
        // Players who were eligible but didn't type /accept will NOT be included here
        List<Player> entrants = new ArrayList<>();
        for (UUID uuid : session.acceptedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (checkAndWarnCooldown(p, cfg)) {
                    entrants.add(p);
                }
            }
        }

        if (entrants.isEmpty()) {
            Player leader = Bukkit.getPlayer(session.leader);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(getMessage("party.no-eligible"));
            }
            partySessions.remove(session.leader);
            cancelPendingChallengeTimeout(session.leader);
            pendingChallenges.remove(session.leader);
            return;
        }

        if (cfg.maxPartySize > 0 && entrants.size() > cfg.maxPartySize) {
            Player leader = Bukkit.getPlayer(session.leader);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(getMessage("party.max-size", "max", String.valueOf(cfg.maxPartySize)));
                leader.sendMessage(getMessage("party.max-size-accepted", "count", String.valueOf(entrants.size())));
                leader.sendMessage(getMessage("party.max-size-reduce"));
            }
            partySessions.remove(session.leader);
            cancelPendingChallengeTimeout(session.leader);
            pendingChallenges.remove(session.leader);
            return;
        }

        if (isChallengeLocked(cfg.id)) {
            Player leader = Bukkit.getPlayer(session.leader);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(getMessage("admin.challenge-locked-party"));
            }
            partySessions.remove(session.leader);
            cancelPendingChallengeTimeout(session.leader);
            pendingChallenges.remove(session.leader);
            return;
        }

        reservedChallenges.add(cfg.id);

        // Remove from queue if they were in it
        removeFromQueue(session.leader);
        
        // Remove pending challenge and party session
        cancelPendingChallengeTimeout(session.leader);
        pendingChallenges.remove(session.leader);
        partySessions.remove(session.leader);

        // Notify all players
        Player leader = Bukkit.getPlayer(session.leader);
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(getMessage("party.confirmed", "count", String.valueOf(entrants.size()), "challenge", displayName(cfg)));
        }
        for (Player p : entrants) {
            p.sendMessage(getMessage("party.entering", "challenge", displayName(cfg)) + getMessage("admin.party-teleporting", "seconds", String.valueOf(partyCountdownSeconds)));
        }

        startTeleportCountdown(entrants, cfg, true, session.acceptedPlayers); // true = isParty
    }

    // ====== QUEUE SYSTEM ======

    private void addToQueue(UUID playerUuid, String challengeId, boolean isParty) {
        QueueEntry entry = new QueueEntry(playerUuid, challengeId, isParty);
        challengeQueues.computeIfAbsent(challengeId, k -> new LinkedList<>()).offer(entry);
        playerQueueEntries.put(playerUuid, entry);
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg != null) {
                int position = challengeQueues.get(challengeId).size();
                player.sendMessage(getMessage("challenge.queue-added"));
                player.sendMessage(getMessage("challenge.queue-your-turn", "position", String.valueOf(position)));
                player.sendMessage(getMessage("challenge.queue-notify"));
            }
        }
    }

    private void removeFromQueue(UUID playerUuid) {
        QueueEntry entry = playerQueueEntries.remove(playerUuid);
        if (entry != null) {
            Queue<QueueEntry> queue = challengeQueues.get(entry.challengeId);
            if (queue != null) {
                queue.remove(entry);
                if (queue.isEmpty()) {
                    challengeQueues.remove(entry.challengeId);
                }
            }
        }
    }

    private void processQueue(String challengeId) {
        Queue<QueueEntry> queue = challengeQueues.get(challengeId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        // Remove entries for offline players or invalid entries
        while (!queue.isEmpty()) {
            QueueEntry entry = queue.peek();
            if (entry == null) {
                queue.poll();
                continue;
            }

            Player player = Bukkit.getPlayer(entry.playerUuid);
            if (player == null || !player.isOnline()) {
                queue.poll();
                playerQueueEntries.remove(entry.playerUuid);
                continue;
            }

            ChallengeConfig cfg = challenges.get(entry.challengeId);
            if (cfg == null || cfg.destination == null) {
                queue.poll();
                playerQueueEntries.remove(entry.playerUuid);
                continue;
            }

            // Found a valid entry, process it
            queue.poll();
            playerQueueEntries.remove(entry.playerUuid);

            if (entry.isParty) {
                // Process party entry - automatically start with only accepted players
                PartySession session = partySessions.get(entry.playerUuid);
                if (session != null && session.challengeId.equals(challengeId)) {
                    // Automatically start with only accepted players (whoever didn't /accept gets left behind)
                    if (!session.acceptedPlayers.isEmpty()) {
                        startPartyChallenge(session, cfg);
                    } else {
                        // No one accepted, remove from queue
                        Player leader = Bukkit.getPlayer(entry.playerUuid);
                        if (leader != null && leader.isOnline()) {
                            leader.sendMessage(getMessage("challenge.queue-no-accept"));
                        }
                        partySessions.remove(entry.playerUuid);
                        cancelPendingChallengeTimeout(entry.playerUuid);
                        pendingChallenges.remove(entry.playerUuid);
                    }
                } else {
                    // Session doesn't exist, remove from queue
                    Player leader = Bukkit.getPlayer(entry.playerUuid);
                    if (leader != null && leader.isOnline()) {
                        leader.sendMessage(getMessage("challenge.queue-expired"));
                    }
                }
            } else {
                // Process solo entry - automatically start if they have a pending challenge
                PendingChallenge pending = pendingChallenges.get(entry.playerUuid);
                if (pending != null && pending.challengeId.equals(challengeId)) {
                    // Automatically start the countdown
                        if (player.isOnline()) {
                            if (isChallengeLocked(cfg.id)) {
                                // Challenge became locked again (might be in edit mode)
                                if (isChallengeBeingEdited(cfg.id)) {
                                    UUID editorUuid = getChallengeEditor(cfg.id);
                                    Player editor = editorUuid != null ? Bukkit.getPlayer(editorUuid) : null;
                                    String editorName = editor != null && editor.isOnline() ? editor.getName() : "an admin";
                                    player.sendMessage(getMessage("challenge.maintenance", "admin", editorName));
                                }
                                // Re-add to queue
                                addToQueue(entry.playerUuid, cfg.id, false);
                                player.sendMessage(getMessage("challenge.queue-unavailable"));
                            } else {
                            if (!checkAndWarnCooldown(player, cfg)) {
                                // Cooldown active, remove from queue
                                cancelPendingChallengeTimeout(entry.playerUuid);
                                pendingChallenges.remove(entry.playerUuid);
                                break; // Exit processing for this entry
                            }
                            
                            // Remove pending challenge
                            cancelPendingChallengeTimeout(entry.playerUuid);
                            pendingChallenges.remove(entry.playerUuid);
                            
                            // Start countdown
                            reservedChallenges.add(cfg.id);
                            player.sendMessage(getMessage("challenge.queue-turn"));
                            startTeleportCountdown(Collections.singletonList(player), cfg);
                        }
                    }
                } else {
                    // No pending challenge (player denied or never accepted), remove from queue
                    if (player.isOnline()) {
                        player.sendMessage(getMessage("challenge.queue-no-pending"));
                    }
                }
            }
            break; // Process one at a time
        }
    }

    // ====== TELEPORT & TIME LIMITS ======

    private boolean checkAndWarnCooldown(Player player, ChallengeConfig cfg) {
        if (cfg.cooldownSeconds <= 0) return true;
        Map<UUID, Long> map = lastCompletionTimes.computeIfAbsent(cfg.id, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = map.get(player.getUniqueId());
        if (last == null) return true;
        long elapsed = (now - last) / 1000L;
        long remaining = cfg.cooldownSeconds - elapsed;
        if (remaining > 0) {
                player.sendMessage(getMessage("challenge.cooldown-active", "seconds", String.valueOf(remaining)));
            return false;
        }
        return true;
    }

    private void startTeleportCountdown(List<Player> players, ChallengeConfig cfg) {
        // Default: assume solo if only 1 player, party if multiple
        startTeleportCountdown(players, cfg, players.size() == 1 ? soloCountdownSeconds : partyCountdownSeconds, false, null);
    }
    
    private void startTeleportCountdown(List<Player> players, ChallengeConfig cfg, boolean isParty) {
        startTeleportCountdown(players, cfg, isParty ? partyCountdownSeconds : soloCountdownSeconds, isParty, null);
    }
    
    private void startTeleportCountdown(List<Player> players, ChallengeConfig cfg, boolean isParty, Set<UUID> acceptedPlayers) {
        startTeleportCountdown(players, cfg, isParty ? partyCountdownSeconds : soloCountdownSeconds, isParty, acceptedPlayers);
    }
    
    private void startTeleportCountdown(List<Player> players, ChallengeConfig cfg, int countdownSeconds, boolean isParty, Set<UUID> acceptedPlayers) {
        // Create a modifiable list to allow removing players during countdown
        List<Player> countdownList = new ArrayList<>(players);
        
        // For parties, get the leader UUID to include them in countdown messages
        UUID partyLeaderUuid = null;
        if (isParty && acceptedPlayers != null && !acceptedPlayers.isEmpty()) {
            // Find the party session by checking if any accepted player is a leader
            for (PartySession session : partySessions.values()) {
                if (session.challengeId.equals(cfg.id) && session.acceptedPlayers.containsAll(acceptedPlayers) && acceptedPlayers.containsAll(session.acceptedPlayers)) {
                    partyLeaderUuid = session.leader;
                    break;
                }
            }
            // If not found, try finding by any accepted player being the leader
            if (partyLeaderUuid == null) {
                for (UUID acceptedUuid : acceptedPlayers) {
                    PartySession session = partySessions.get(acceptedUuid);
                    if (session != null && session.challengeId.equals(cfg.id)) {
                        partyLeaderUuid = session.leader;
                        break;
                    }
                }
            }
        }
        
        final UUID leaderUuid = partyLeaderUuid;
        
        // Cancel any existing countdown for this challenge
        CountdownTaskWrapper existingWrapper = activeCountdownTasks.remove(cfg.id);
        if (existingWrapper != null && existingWrapper.task != null) {
            existingWrapper.task.cancel();
        }
        
        // Regenerate challenge area at the start of countdown (if regeneration is configured)
        // Don't regenerate if challenge is being edited
        if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null && !isChallengeBeingEdited(cfg.id)) {
            // regenerateChallengeArea will set the status itself
            regenerateChallengeArea(cfg);
        } else {
            // No regeneration needed - mark as complete immediately
            challengeRegenerationStatus.put(cfg.id, true);
        }
        
        // Store the task for this challenge
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // Get current seconds from wrapper
                CountdownTaskWrapper wrapper = activeCountdownTasks.get(cfg.id);
                if (wrapper == null) {
                    cancel();
                    return;
                }
                int seconds = wrapper.seconds;
                // Remove offline players from the countdown
                countdownList.removeIf(p -> {
                    if (!p.isOnline()) {
                        playerCountdownChallenges.remove(p.getUniqueId());
                        // Clean up bossbar
                        BossBar bossBar = playerCountdownBossBars.remove(p.getUniqueId());
                        if (bossBar != null) {
                            bossBar.removePlayer(p);
                            bossBar.removeAll();
                        }
                        return true;
                    }
                    return false;
                });
                
                // Check if players still have their challenge book
                countdownList.removeIf(p -> {
                    if (!hasChallengeBook(p, cfg.bookTitle)) {
                        // Player lost their book - remove them from countdown
                        playerCountdownChallenges.remove(p.getUniqueId());
                        p.sendMessage(getMessage("entry.book-lost"));
                        p.sendMessage(getMessage("entry.book-required"));
                        // Clean up their pending challenge and queue entry
                        cancelPendingChallengeTimeout(p.getUniqueId());
                        pendingChallenges.remove(p.getUniqueId());
                        removeFromQueue(p.getUniqueId());
                        // Clean up bossbar
                        BossBar bossBar = playerCountdownBossBars.remove(p.getUniqueId());
                        if (bossBar != null) {
                            bossBar.removePlayer(p);
                            bossBar.removeAll();
                        }
                        return true;
                    }
                    return false;
                });
                
                if (countdownList.isEmpty()) {
                    // Clean up tracking and bossbars
                    activeCountdownTasks.remove(cfg.id);
                    countdownPlayers.remove(cfg.id);
                    reservedChallenges.remove(cfg.id);
                    // Clean up any remaining bossbars for players who left
                    for (Player p : new ArrayList<>(countdownList)) {
                        BossBar bossBar = playerCountdownBossBars.remove(p.getUniqueId());
                        if (bossBar != null) {
                            bossBar.removePlayer(p);
                            bossBar.removeAll();
                        }
                    }
                    cancel();
                    return;
                }

                if (seconds <= 0) {
                    // Check if regeneration is complete before teleporting
                    Boolean regenerationComplete = challengeRegenerationStatus.get(cfg.id);
                    if (regenerationComplete == null || !regenerationComplete) {
                        // Regeneration still in progress - show message and wait
                        for (Player p : countdownList) {
                            if (!p.isOnline()) continue;
                            
                            // For parties: only show to accepted players or the leader
                            if (isParty && acceptedPlayers != null) {
                                if (!acceptedPlayers.contains(p.getUniqueId()) && (leaderUuid == null || !p.getUniqueId().equals(leaderUuid))) {
                                    continue;
                                }
                            }
                            
                            // Show regeneration in progress message
                            String displayType = countdownDisplayPreferences.getOrDefault(p.getUniqueId(), "title");
                            String regenTitle = ChatColor.translateAlternateColorCodes('&', getMessage("countdown.regeneration-in-progress"));
                            String regenSubtitle = ChatColor.translateAlternateColorCodes('&', getMessage("countdown.regeneration-subtitle"));
                            
                            switch (displayType) {
                                case "title" -> p.sendTitle(regenTitle, regenSubtitle, 0, 20, 0);
                                case "actionbar" -> {
                                    try {
                                        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(regenTitle));
                                    } catch (Exception e) {
                                        p.sendTitle(regenTitle, regenSubtitle, 0, 20, 0);
                                    }
                                }
                                case "bossbar" -> {
                                    BossBar bossBar = playerCountdownBossBars.get(p.getUniqueId());
                                    if (bossBar != null) {
                                        bossBar.setTitle(regenTitle);
                                        bossBar.setProgress(1.0); // Full bar while waiting
                                    }
                                }
                                case "chat" -> p.sendMessage(regenTitle);
                            }
                        }
                        // Continue running to check again next tick
                        return;
                    }
                    
                    // Regeneration complete - proceed with teleport
                    // Final check before teleporting - ensure all players still have their books
                    // Only teleport accepted players (if acceptedPlayers set is provided)
                    List<Player> playersToTeleport = new ArrayList<>();
                    for (Player p : countdownList) {
                        if (!p.isOnline()) continue;
                        
                        // If acceptedPlayers is provided, only teleport accepted players
                        if (acceptedPlayers != null && !acceptedPlayers.contains(p.getUniqueId())) {
                            // Player didn't accept, remove them from countdown but don't teleport
                            playerCountdownChallenges.remove(p.getUniqueId());
                            // Clean up bossbar
                            BossBar bossBar = playerCountdownBossBars.remove(p.getUniqueId());
                            if (bossBar != null) {
                                bossBar.removePlayer(p);
                                bossBar.removeAll();
                            }
                            p.sendMessage(getMessage("entry.not-accepted"));
                            continue;
                        }
                        
                        // Final book check before teleport
                        if (!hasChallengeBook(p, cfg.bookTitle)) {
                            // Player lost their book at the last moment
                            playerCountdownChallenges.remove(p.getUniqueId());
                            p.sendMessage(getMessage("entry.book-lost"));
                            p.sendMessage(getMessage("entry.book-required"));
                            // Clean up their pending challenge and queue entry
                            cancelPendingChallengeTimeout(p.getUniqueId());
                            pendingChallenges.remove(p.getUniqueId());
                            removeFromQueue(p.getUniqueId());
                            continue;
                        }
                        
                        playersToTeleport.add(p);
                    }
                    
                    // Teleport only players who still have their books
                    for (Player p : playersToTeleport) {
                        // Remove pending challenge if it exists (should already be removed, but clean up just in case)
                        cancelPendingChallengeTimeout(p.getUniqueId());
                        pendingChallenges.remove(p.getUniqueId());
                        // Remove from queue if they were in it (should already be removed, but clean up)
                        removeFromQueue(p.getUniqueId());
                        // Clean up countdown tracking
                        playerCountdownChallenges.remove(p.getUniqueId());

                        consumeChallengeBook(p, cfg.bookTitle);
                        activeChallenges.put(p.getUniqueId(), cfg.id);
                        challengeStartTimes.put(p.getUniqueId(), System.currentTimeMillis());

                        Location teleportLoc = getTeleportLocation(cfg);
                        if (teleportLoc != null) {
                            p.teleport(teleportLoc);
                            p.sendMessage(getMessage("entry.entered", "challenge", displayName(cfg)));
                        } else {
                            p.sendMessage(ChatColor.RED + "Error: No teleport location set for challenge!");
                            return;
                        }

                        switch (cfg.completionType) {
                            case BOSS -> p.sendMessage(getMessage("entry.boss-instruction"));
                            case TIMER -> {
                                p.sendMessage(getMessage("entry.timer-instruction", "seconds", String.valueOf(cfg.timerSeconds)));
                                startTimerCompletion(p, cfg);
                            }
                            case ITEM -> p.sendMessage(getMessage("entry.item-instruction"));
                            case SPEED -> {
                                p.sendMessage(getMessage("entry.speed-instruction"));
                                p.sendMessage(getMessage("entry.speed-time-limit", "seconds", String.valueOf(cfg.timerSeconds)));
                            }
                            case NONE -> p.sendMessage(getMessage("entry.none-instruction"));
                        }

                        // Start time-limit watcher for this player
                        startTimeLimitWatcher(p, cfg);
                    }
                    // Clean up tracking
                    activeCountdownTasks.remove(cfg.id);
                    countdownPlayers.remove(cfg.id);
                    reservedChallenges.remove(cfg.id);
                    challengeRegenerationStatus.remove(cfg.id); // Clean up regeneration status
                    cancel();
                    return;
                }

                // Only show countdown to accepted players (and leader for parties)
                for (Player p : countdownList) {
                    if (!p.isOnline()) continue;
                    
                    // For parties: only show to accepted players or the leader
                    if (isParty && acceptedPlayers != null) {
                        if (!acceptedPlayers.contains(p.getUniqueId()) && (leaderUuid == null || !p.getUniqueId().equals(leaderUuid))) {
                            continue; // Skip this player - they didn't accept
                        }
                    }
                    // For solo: show to all (acceptedPlayers will be null)
                    
                    // Get player's countdown display preference (default: title)
                    String displayType = countdownDisplayPreferences.getOrDefault(p.getUniqueId(), "title");
                    String countdownText = getMessage("countdown.teleporting", "seconds", String.valueOf(seconds), "plural", seconds == 1 ? "" : "s");
                    
                    // Display countdown based on player preference
                    String formattedText = ChatColor.translateAlternateColorCodes('&', countdownText);
                    String subtitleText = ChatColor.translateAlternateColorCodes('&', getMessage("countdown.subtitle-cancel"));
                    switch (displayType) {
                        case "title" -> p.sendTitle(formattedText, subtitleText, 0, 20, 0);
                        case "actionbar" -> {
                            // Use Spigot API for action bar
                            try {
                                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(formattedText));
                            } catch (Exception e) {
                                // Fallback to title if actionbar fails
                                p.sendTitle(formattedText, subtitleText, 0, 20, 0);
                            }
                        }
                        case "bossbar" -> {
                            // Get or create bossbar for this player
                            BossBar bossBar = playerCountdownBossBars.get(p.getUniqueId());
                            if (bossBar == null) {
                                bossBar = Bukkit.createBossBar(
                                    formattedText,
                                    BarColor.GREEN,
                                    BarStyle.SOLID
                                );
                                bossBar.addPlayer(p);
                                playerCountdownBossBars.put(p.getUniqueId(), bossBar);
                            }
                            bossBar.setTitle(formattedText);
                            double progress = Math.max(0.0, Math.min(1.0, (double) seconds / countdownSeconds));
                            bossBar.setProgress(progress);
                            bossBar.setVisible(true);
                        }
                        case "chat" -> p.sendMessage(formattedText);
                    }
                }
                wrapper.seconds--;
            }
        };
        
        // Create wrapper and store it
        Set<UUID> acceptedSet = acceptedPlayers != null ? new HashSet<>(acceptedPlayers) : null;
        CountdownTaskWrapper wrapper = new CountdownTaskWrapper(countdownSeconds, task, cfg.id, acceptedSet);
        activeCountdownTasks.put(cfg.id, wrapper);
        countdownPlayers.put(cfg.id, countdownList);
        for (Player p : players) {
            playerCountdownChallenges.put(p.getUniqueId(), cfg.id);
        }
        
        task.runTaskTimer(this, 0L, 20L);
    }

    private void startTimerCompletion(Player player, ChallengeConfig cfg) {
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            int remaining = cfg.timerSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                String currentId = activeChallenges.get(uuid);
                if (currentId == null || !currentId.equals(cfg.id)) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    completedChallenges.put(uuid, cfg.id);
                    player.sendMessage(getMessage("admin.timer-survived", "challenge", displayName(cfg)));
                    player.sendMessage(getMessage("entry.none-instruction"));
                    markEverCompleted(cfg, uuid);
                    cancel();
                    return;
                }
                remaining--;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void startTimeLimitWatcher(Player player, ChallengeConfig cfg) {
        if (cfg.timeLimitSeconds <= 0) return;
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                String currentId = activeChallenges.get(uuid);
                if (currentId == null || !currentId.equals(cfg.id)) {
                    cancel();
                    return;
                }
                Long start = challengeStartTimes.get(uuid);
                if (start == null) {
                    cancel();
                    return;
                }
                long elapsed = (System.currentTimeMillis() - start) / 1000L;
                long remaining = cfg.timeLimitSeconds - elapsed;
                if (remaining <= 0) {
                    // time expired
                    handleTimeLimitExpired(player, cfg);
                    cancel();
                    return;
                }
                int remInt = (int) remaining;
                if (cfg.timeLimitWarnings.contains(remInt)) {
                    player.sendMessage(getMessage("admin.timer-remaining", "seconds", String.valueOf(remInt)));
                    if (remInt == 10) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void handleTimeLimitExpired(Player player, ChallengeConfig cfg) {
        UUID uuid = player.getUniqueId();
        activeChallenges.remove(uuid);
        completedChallenges.remove(uuid);
        challengeStartTimes.remove(uuid);
        
        // Clean up difficulty tier if no players are active in this challenge
        cleanupChallengeDifficultyIfEmpty(cfg.id);
        
        // Check if challenge is now available and process queue
        if (!isChallengeLocked(cfg.id)) {
            processQueue(cfg.id);
        }

        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }
        player.sendMessage(getMessage("admin.time-expired", "challenge", displayName(cfg)));
        player.sendMessage(getMessage("admin.time-expired-retry"));
    }

    private void markEverCompleted(ChallengeConfig cfg, UUID uuid) {
        Set<UUID> set = everCompleted.computeIfAbsent(cfg.id, k -> new HashSet<>());
        if (!set.contains(uuid)) {
            set.add(uuid);
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            Bukkit.broadcastMessage(getMessage("admin.first-completion-broadcast", "player", op.getName(), "challenge", displayName(cfg)));
        }
    }

    // ====== EVENTS ======

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player is in edit mode
        String editingChallengeId = null;
        for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                editingChallengeId = entry.getKey();
                break;
            }
        }
        
        if (editingChallengeId != null) {
            // Player is in edit mode - allow teleports anywhere within the challenge world
            Location to = event.getTo();
            Location from = event.getFrom();
            Location originalLoc = editorOriginalLocations.get(uuid);
            
            // Allow save/cancel teleport back to original location
            if (to != null && originalLoc != null && 
                to.getWorld() != null && originalLoc.getWorld() != null &&
                to.getWorld().equals(originalLoc.getWorld()) &&
                to.distanceSquared(originalLoc) < 1.0) {
                // This is our save/cancel teleport back, allow it
                return;
            }
            
            // Allow teleport to challenge teleport location (for entering edit mode)
            ChallengeConfig cfg = challenges.get(editingChallengeId);
            Location teleportLoc = cfg != null ? getTeleportLocation(cfg) : null;
            if (cfg != null && teleportLoc != null && to != null &&
                teleportLoc.getWorld() != null && to.getWorld() != null &&
                to.getWorld().equals(teleportLoc.getWorld()) &&
                to.distanceSquared(teleportLoc) < 4.0) { // Allow within 2 blocks
                // This is the edit mode entry teleport, allow it
                return;
            }
            
            // Allow teleports anywhere within the challenge world
            // Since one world is used for all challenges, just check if both locations are in the same world
            if (cfg != null && to != null && from != null &&
                to.getWorld() != null && from.getWorld() != null &&
                to.getWorld().equals(from.getWorld())) {
                // Both locations are in the same world - allow teleport (one world for all challenges)
                return;
            }
            
            // Block all other teleports during edit mode (cross-world)
            event.setCancelled(true);
            player.sendMessage(getMessage("admin.challenge-edit-teleport-blocked"));
            return;
        }
        
        // Check if player is in an active challenge
        String challengeId = activeChallenges.get(uuid);
        Location to = event.getTo();
        Location from = event.getFrom();
        
        if (challengeId == null) {
            // Player NOT in challenge - check if they're trying to teleport INTO a challenge area
            if (to != null) {
                ChallengeConfig targetChallenge = getChallengeForLocation(to);
                if (targetChallenge != null) {
                    // Player is trying to teleport into a challenge area
                    // Allow if player is admin, otherwise block
                    if (!player.hasPermission("conradchallenges.admin")) {
                        event.setCancelled(true);
                        player.sendMessage(getMessage("challenge.teleport-blocked-into"));
                        return;
                    }
                    // Admin bypass - allow teleport into challenge area
                }
            }
            // Player not in challenge and not teleporting into challenge area - allow all teleports
            return;
        }
        
        // Player IS in a challenge - only allow teleports within the challenge area
        ChallengeConfig cfg = challenges.get(challengeId);
        
        // Allow teleports to spawnLocation (our exit/complete teleports)
        if (to != null && spawnLocation != null && 
            to.getWorld() != null && spawnLocation.getWorld() != null &&
            to.getWorld().equals(spawnLocation.getWorld()) &&
            to.distanceSquared(spawnLocation) < 1.0) {
            // This is our exit/complete teleport, allow it
            return;
        }
        
        // Allow teleports to challenge teleport location (our entry teleport)
        Location teleportLoc = cfg != null ? getTeleportLocation(cfg) : null;
        if (cfg != null && teleportLoc != null && to != null &&
            teleportLoc.getWorld() != null && to.getWorld() != null &&
            to.getWorld().equals(teleportLoc.getWorld()) &&
            to.distanceSquared(teleportLoc) < 4.0) { // Allow within 2 blocks
            // This is our entry teleport, allow it
            return;
        }
        
        // Only allow teleports within the regeneration area (both to and from must be in regeneration area)
        // This prevents players from teleporting outside the regeneration area where they could mess things up
        if (cfg != null && to != null && from != null) {
            // Check if regeneration area is set
            boolean hasRegenerationArea = (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null);
            
            if (hasRegenerationArea) {
                // Regeneration area is set - check if both locations are within it
                boolean toInRegenArea = isLocationInRegenerationArea(cfg, to);
                boolean fromInRegenArea = isLocationInRegenerationArea(cfg, from);
                
                if (toInRegenArea && fromInRegenArea) {
                    // Both locations are within regeneration area, allow teleport
                    return;
                } else if (!toInRegenArea) {
                    // Player is trying to teleport OUT of regeneration area - block it
                    event.setCancelled(true);
                    player.sendMessage(getMessage("challenge.teleport-blocked-regen"));
                    return;
                }
            } else {
                // No regeneration area set - fall back to challenge area or destination world
                boolean hasChallengeArea = (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null);
                
                if (hasChallengeArea) {
                    // Use challenge area as fallback
                    boolean toInArea = isLocationInChallengeArea(cfg, to);
                    boolean fromInArea = isLocationInChallengeArea(cfg, from);
                    
                    if (toInArea && fromInArea) {
                        return;
                    } else if (!toInArea) {
                        event.setCancelled(true);
                        player.sendMessage(getMessage("challenge.teleport-blocked"));
                        return;
                    }
                } else {
                    // No areas set - allow teleports within destination world
                    if (cfg.destination != null && cfg.destination.getWorld() != null &&
                        to.getWorld() != null && from.getWorld() != null &&
                        to.getWorld().equals(cfg.destination.getWorld()) &&
                        from.getWorld().equals(cfg.destination.getWorld())) {
                        // Teleport is within destination world, allow it
                        return;
                    } else {
                        // Player is trying to teleport OUT of destination world - block it
                        event.setCancelled(true);
                        player.sendMessage(getMessage("challenge.teleport-blocked"));
                        return;
                    }
                }
            }
        }
        
        // Block all other teleports (fallback)
        event.setCancelled(true);
        player.sendMessage(getMessage("challenge.teleport-blocked"));
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player is in an active challenge
        String challengeId = activeChallenges.get(uuid);
        if (challengeId == null) {
            // Player not in challenge - allow all movement
            return;
        }
        
        // Check if player is in edit mode (admins can move freely in edit mode)
        String editingChallengeId = null;
        for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                editingChallengeId = entry.getKey();
                break;
            }
        }
        
        if (editingChallengeId != null) {
            // Player is in edit mode - allow all movement
            return;
        }
        
        // Player IS in a challenge - prevent leaving the regeneration area
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) {
            return;
        }
        
        Location to = event.getTo();
        Location from = event.getFrom();
        
        // Allow movement if both locations are the same (just looking around)
        if (to != null && from != null && to.getBlockX() == from.getBlockX() && 
            to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        
        // Check if regeneration area is set
        boolean hasRegenerationArea = (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null);
        
        if (hasRegenerationArea) {
            // Regeneration area is set - check if destination is within it
            if (to != null && !isLocationInRegenerationArea(cfg, to)) {
                // Player is trying to move OUT of regeneration area - block it
                event.setCancelled(true);
                // Teleport them back slightly to prevent them from getting stuck
                Location safeLocation = from.clone();
                if (safeLocation.getWorld() != null) {
                    player.teleport(safeLocation);
                }
                player.sendMessage(getMessage("challenge.movement-blocked-regen"));
                return;
            }
        } else {
            // No regeneration area set - fall back to challenge area or destination world
            boolean hasChallengeArea = (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null);
            
            if (hasChallengeArea) {
                // Use challenge area as fallback
                if (to != null && !isLocationInChallengeArea(cfg, to)) {
                    event.setCancelled(true);
                    Location safeLocation = from.clone();
                    if (safeLocation.getWorld() != null) {
                        player.teleport(safeLocation);
                    }
                    player.sendMessage(getMessage("challenge.movement-blocked"));
                    return;
                }
            } else {
                // No areas set - check destination world
                if (cfg.destination != null && cfg.destination.getWorld() != null &&
                    to != null && to.getWorld() != null &&
                    !to.getWorld().equals(cfg.destination.getWorld())) {
                    event.setCancelled(true);
                    Location safeLocation = from.clone();
                    if (safeLocation.getWorld() != null) {
                        player.teleport(safeLocation);
                    }
                    player.sendMessage(getMessage("challenge.movement-blocked"));
                    return;
                }
            }
        }
    }
    
    /**
     * Checks if a location is within a challenge's regeneration area bounds.
     * 
     * @param cfg The challenge configuration
     * @param loc The location to check
     * @return true if the location is within the regeneration area, false otherwise
     */
    private boolean isLocationInRegenerationArea(ChallengeConfig cfg, Location loc) {
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            // No regeneration area set, fall back to world check
            return cfg.destination != null && cfg.destination.getWorld() != null &&
                   loc.getWorld() != null && loc.getWorld().equals(cfg.destination.getWorld());
        }
        
        if (cfg.regenerationCorner1.getWorld() == null || 
            !cfg.regenerationCorner1.getWorld().equals(cfg.regenerationCorner2.getWorld()) ||
            !cfg.regenerationCorner1.getWorld().equals(loc.getWorld())) {
            return false;
        }
        
        // Calculate bounding box
        int minX = Math.min(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int maxX = Math.max(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            // Auto-extend from bedrock to build height
            minY = loc.getWorld().getMinHeight();
            maxY = loc.getWorld().getMaxHeight() - 1;
        } else {
            // Use Y coordinates from corners
            minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
            maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
        }
        int minZ = Math.min(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        int maxZ = Math.max(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        
        // Check if location is within bounds
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Checks if a location is within a challenge's challenge area bounds.
     * Falls back to regeneration area if challenge area is not set.
     * 
     * @param cfg The challenge configuration
     * @param loc The location to check
     * @return true if the location is within the challenge area (or regen area if challenge area not set), false otherwise
     */
    private boolean isLocationInChallengeArea(ChallengeConfig cfg, Location loc) {
        // Prefer challenge area if set, otherwise fall back to regeneration area
        Location corner1 = cfg.challengeCorner1 != null ? cfg.challengeCorner1 : cfg.regenerationCorner1;
        Location corner2 = cfg.challengeCorner2 != null ? cfg.challengeCorner2 : cfg.regenerationCorner2;
        
        if (corner1 == null || corner2 == null) {
            // No challenge or regeneration area set - location is NOT in challenge area
            return false;
        }
        
        if (corner1.getWorld() == null || 
            !corner1.getWorld().equals(corner2.getWorld()) ||
            !corner1.getWorld().equals(loc.getWorld())) {
            return false;
        }
        
        // Calculate bounding box
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        
        // Check if location is within bounds
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Gets the challenge config for a location if it's within any challenge area.
     * 
     * @param loc The location to check
     * @return The challenge config if location is in a challenge area, null otherwise
     */
    private ChallengeConfig getChallengeForLocation(Location loc) {
        for (ChallengeConfig cfg : challenges.values()) {
            if (isLocationInChallengeArea(cfg, loc)) {
                return cfg;
            }
        }
        return null;
    }
    
    /**
     * Checks if two bounding boxes overlap.
     * 
     * @param minX1, maxX1, minY1, maxY1, minZ1, maxZ1 First bounding box
     * @param minX2, maxX2, minY2, maxY2, minZ2, maxZ2 Second bounding box
     * @return true if the boxes overlap, false otherwise
     */
    private boolean boundingBoxesOverlap(int minX1, int maxX1, int minY1, int maxY1, int minZ1, int maxZ1,
                                        int minX2, int maxX2, int minY2, int maxY2, int minZ2, int maxZ2) {
        // Check if boxes overlap in all three dimensions
        return !(maxX1 < minX2 || maxX2 < minX1 ||
                 maxY1 < minY2 || maxY2 < minY1 ||
                 maxZ1 < minZ2 || maxZ2 < minZ1);
    }
    
    /**
     * Checks if a challenge area or regeneration area overlaps with any other challenge's areas.
     * 
     * @param corner1 First corner of the area to check
     * @param corner2 Second corner of the area to check
     * @param excludeChallengeId Challenge ID to exclude from checking (usually the current challenge)
     * @param checkChallengeAreas Whether to check against challenge areas
     * @param checkRegenerationAreas Whether to check against regeneration areas
     * @return Challenge ID that overlaps, or null if no overlap
     */
    private String checkAreaOverlap(Location corner1, Location corner2, String excludeChallengeId,
                                   boolean checkChallengeAreas, boolean checkRegenerationAreas) {
        if (corner1 == null || corner2 == null || 
            corner1.getWorld() == null || !corner1.getWorld().equals(corner2.getWorld())) {
            return null; // Invalid area
        }
        
        // Calculate bounding box for the area being checked
        int minX1 = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX1 = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY1 = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY1 = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ1 = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ1 = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        
        // Check against all other challenges
        for (Map.Entry<String, ChallengeConfig> entry : challenges.entrySet()) {
            String challengeId = entry.getKey();
            if (challengeId.equals(excludeChallengeId)) {
                continue; // Skip the challenge we're setting the area for
            }
            
            ChallengeConfig otherCfg = entry.getValue();
            
            // Check challenge areas
            if (checkChallengeAreas && otherCfg.challengeCorner1 != null && otherCfg.challengeCorner2 != null) {
                if (otherCfg.challengeCorner1.getWorld() != null && 
                    otherCfg.challengeCorner1.getWorld().equals(corner1.getWorld())) {
                    int minX2 = Math.min(otherCfg.challengeCorner1.getBlockX(), otherCfg.challengeCorner2.getBlockX());
                    int maxX2 = Math.max(otherCfg.challengeCorner1.getBlockX(), otherCfg.challengeCorner2.getBlockX());
                    int minY2 = Math.min(otherCfg.challengeCorner1.getBlockY(), otherCfg.challengeCorner2.getBlockY());
                    int maxY2 = Math.max(otherCfg.challengeCorner1.getBlockY(), otherCfg.challengeCorner2.getBlockY());
                    int minZ2 = Math.min(otherCfg.challengeCorner1.getBlockZ(), otherCfg.challengeCorner2.getBlockZ());
                    int maxZ2 = Math.max(otherCfg.challengeCorner1.getBlockZ(), otherCfg.challengeCorner2.getBlockZ());
                    
                    if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1,
                                            minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) {
                        return challengeId;
                    }
                }
            }
            
            // Check regeneration areas
            if (checkRegenerationAreas && otherCfg.regenerationCorner1 != null && otherCfg.regenerationCorner2 != null) {
                if (otherCfg.regenerationCorner1.getWorld() != null && 
                    otherCfg.regenerationCorner1.getWorld().equals(corner1.getWorld())) {
                    int minX2 = Math.min(otherCfg.regenerationCorner1.getBlockX(), otherCfg.regenerationCorner2.getBlockX());
                    int maxX2 = Math.max(otherCfg.regenerationCorner1.getBlockX(), otherCfg.regenerationCorner2.getBlockX());
                    int minY2 = Math.min(otherCfg.regenerationCorner1.getBlockY(), otherCfg.regenerationCorner2.getBlockY());
                    int maxY2 = Math.max(otherCfg.regenerationCorner1.getBlockY(), otherCfg.regenerationCorner2.getBlockY());
                    int minZ2 = Math.min(otherCfg.regenerationCorner1.getBlockZ(), otherCfg.regenerationCorner2.getBlockZ());
                    int maxZ2 = Math.max(otherCfg.regenerationCorner1.getBlockZ(), otherCfg.regenerationCorner2.getBlockZ());
                    
                    if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1,
                                            minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) {
                        return challengeId;
                    }
                }
            }
        }
        
        return null; // No overlap found
    }
    
    /**
     * Gets the teleport location for a challenge.
     * Always uses the destination - challenge area is only for restrictions/protection, not teleport location.
     * 
     * @param cfg The challenge configuration
     * @return The teleport location (destination)
     */
    private Location getTeleportLocation(ChallengeConfig cfg) {
        // Always use destination for teleporting
        // Challenge area is only used for restrictions/protection, not for determining teleport location
        if (cfg.destination != null && cfg.destination.getWorld() != null) {
            return cfg.destination;
        }
        
        return null;
    }
    
    /**
     * Event handler to protect blocks from explosions in challenge areas.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        
        // Check each block - remove from explosion if it's in a challenge area
        blocks.removeIf(block -> {
            ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
            return cfg != null;
        });
    }
    
    /**
     * Event handler to protect blocks from block explosions in challenge areas.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blocks = event.blockList();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        
        // Check each block - remove from explosion if it's in a challenge area
        blocks.removeIf(block -> {
            ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
            return cfg != null;
        });
    }
    
    /**
     * Event handler to protect blocks from being broken in challenge areas.
     * Only blocks blocks if player is not in the challenge (to allow normal gameplay).
     */
    
    // Command blocking removed - teleportation is now handled via PlayerTeleportEvent
    // This allows all commands to execute, but teleports are blocked at the event level
    // This is more secure and works with any teleport method (commands, plugins, etc.)
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        
        String challengeId = activeChallenges.get(uuid);
        if (challengeId == null) return;
        
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) return;
        
        // Remove player from challenge immediately
        String removedChallengeId = activeChallenges.remove(uuid);
        completedChallenges.remove(uuid);
        challengeStartTimes.remove(uuid);
        
        // Clean up difficulty tier if no players are active in this challenge
        if (removedChallengeId != null) {
            cleanupChallengeDifficultyIfEmpty(removedChallengeId);
        }
        
        // Mark player for teleport on respawn
        pendingDeathTeleports.put(uuid, challengeId);
        
        // Check if challenge is now available and process queue
        if (!isChallengeLocked(challengeId)) {
            processQueue(challengeId);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Clean up edit mode if player was editing
        String editingChallengeId = null;
        for (Map.Entry<String, UUID> entry : challengeEditors.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                editingChallengeId = entry.getKey();
                break;
            }
        }
        
        if (editingChallengeId != null) {
            ChallengeConfig cfg = challenges.get(editingChallengeId);
            
            // Store original location for reconnection (before cleanup)
            Location originalLoc = editorOriginalLocations.get(uuid);
            if (originalLoc != null) {
                editorDisconnectLocations.put(uuid, originalLoc.clone());
            }
            
            if ("save".equalsIgnoreCase(editModeDisconnectAction)) {
                // Save changes on disconnect
                if (cfg != null) {
                    // Recapture initial state if regeneration area exists
                    if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                        captureInitialState(cfg);
                    }
                    
                    // Save challenge config
                    saveChallengeToConfig(cfg);
                }
                
                // Clean up edit mode
                editorOriginalLocations.remove(uuid);
                challengeEditors.remove(editingChallengeId);
                challengeEditBackups.remove(editingChallengeId);
                challengeEditStateBackups.remove(editingChallengeId);
                
                getLogger().info("Player " + player.getName() + " disconnected while editing challenge '" + editingChallengeId + "'. Edit mode saved and changes kept.");
            } else {
                // Cancel edit mode and restore (default behavior)
                ChallengeConfig backup = challengeEditBackups.get(editingChallengeId);
                
                if (backup != null && cfg != null) {
                    // Restore challenge config from backup
                    restoreChallengeConfig(cfg, backup);
                    saveChallengeToConfig(cfg);
                    
                    // Restore block states if they were backed up
                    List<BlockState> stateBackup = challengeEditStateBackups.get(editingChallengeId);
                    if (stateBackup != null) {
                        challengeInitialStates.put(editingChallengeId, new ArrayList<>(stateBackup));
                    }
                }
                
                // Clean up edit mode
                editorOriginalLocations.remove(uuid);
                challengeEditors.remove(editingChallengeId);
                challengeEditBackups.remove(editingChallengeId);
                challengeEditStateBackups.remove(editingChallengeId);
                
                getLogger().info("Player " + player.getName() + " disconnected while editing challenge '" + editingChallengeId + "'. Edit mode cancelled and challenge restored.");
            }
            
            // Process queue if there are players waiting
            if (!isChallengeLocked(editingChallengeId)) {
                processQueue(editingChallengeId);
            }
        }
        
        // Handle active challenge disconnect with 30s grace period
        String challengeId = activeChallenges.get(uuid);
        if (challengeId != null) {
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg != null) {
                // Check if this is a solo or party challenge BEFORE removing player
                // Count active players in this challenge (including this one)
                int activeCount = 0;
                for (String activeChallengeId : activeChallenges.values()) {
                    if (activeChallengeId.equals(challengeId)) {
                        activeCount++;
                    }
                }
                boolean isPartyChallenge = activeCount > 1;
                
                // Don't immediately remove - track for 30s grace period
                long disconnectTime = System.currentTimeMillis();
                
                // Schedule task to check after 30 seconds
                BukkitTask timeoutTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        handleDisconnectTimeout(uuid, challengeId, isPartyChallenge);
                    }
                }.runTaskLater(this, 20L * 30); // 30 seconds
                
                // Store disconnect info
                disconnectedPlayers.put(uuid, new DisconnectInfo(challengeId, disconnectTime, timeoutTask));
                
                // Temporarily remove from active challenges (will be restored on reconnect)
                activeChallenges.remove(uuid);
                completedChallenges.remove(uuid);
                challengeStartTimes.remove(uuid);
                
                // Clean up difficulty tier if no active players remain (but keep challenge reserved for grace period)
                cleanupChallengeDifficultyIfEmpty(challengeId);
                
                getLogger().info("Player " + player.getName() + " disconnected while in challenge '" + challengeId + "' (" + (isPartyChallenge ? "party" : "solo") + "). 30 second grace period started.");
            }
        }
        
        // Clean up any pending challenge timeouts
        cancelPendingChallengeTimeout(uuid);
        
        // Remove from queue if they were in it
        removeFromQueue(uuid);
        
        // Clean up countdown if they were in one
        String countdownChallengeId = playerCountdownChallenges.remove(uuid);
        if (countdownChallengeId != null) {
            List<Player> countdownList = countdownPlayers.get(countdownChallengeId);
            if (countdownList != null && countdownList.remove(player)) {
                // Clean up bossbar
                BossBar bossBar = playerCountdownBossBars.remove(uuid);
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                    bossBar.removeAll();
                }
                // If countdown list is now empty, cancel the task and clean up
                if (countdownList.isEmpty()) {
                    CountdownTaskWrapper wrapper = activeCountdownTasks.remove(countdownChallengeId);
                    if (wrapper != null && wrapper.task != null) {
                        wrapper.task.cancel();
                    }
                    countdownPlayers.remove(countdownChallengeId);
                    reservedChallenges.remove(countdownChallengeId);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player disconnected while in edit mode - restore original location
        Location editModeLocation = editorDisconnectLocations.remove(uuid);
        if (editModeLocation != null) {
            // Teleport player to their original location after a short delay (to ensure world is loaded)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.teleport(editModeLocation);
                        player.sendMessage(ChatColor.GREEN + "You have been returned to your location before entering edit mode.");
                    }
                }
            }.runTaskLater(this, 5L); // 5 ticks delay to ensure world is loaded
            return; // Don't process challenge disconnect logic if they were in edit mode
        }
        
        // Check if player was disconnected from a challenge
        DisconnectInfo disconnectInfo = disconnectedPlayers.remove(uuid);
        if (disconnectInfo != null) {
            // Cancel the timeout task
            if (disconnectInfo.timeoutTask != null && !disconnectInfo.timeoutTask.isCancelled()) {
                disconnectInfo.timeoutTask.cancel();
            }
            
            String challengeId = disconnectInfo.challengeId;
            ChallengeConfig cfg = challenges.get(challengeId);
            
            if (cfg != null) {
                long timeSinceDisconnect = System.currentTimeMillis() - disconnectInfo.disconnectTime;
                long gracePeriodMs = 30L * 1000L; // 30 seconds
                
                if (timeSinceDisconnect <= gracePeriodMs) {
                    // Player reconnected within grace period - restore to challenge
                    activeChallenges.put(uuid, challengeId);
                    challengeStartTimes.put(uuid, System.currentTimeMillis());
                    
                    // Teleport player back to challenge
                    Location teleportLoc = getTeleportLocation(cfg);
                    if (teleportLoc != null) {
                        // Schedule teleport for next tick to ensure player is fully loaded
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    player.teleport(teleportLoc);
                                    player.sendMessage(ChatColor.GREEN + "You reconnected! Welcome back to the challenge.");
                                }
                            }
                        }.runTaskLater(this, 1L);
                    }
                    
                    getLogger().info("Player " + player.getName() + " reconnected to challenge '" + challengeId + "' within grace period.");
                } else {
                    // Player reconnected after grace period - teleport to exit
                    handleDisconnectedPlayerReconnect(player, challengeId, cfg);
                }
            }
        } else {
            // Player not in disconnectedPlayers - check if they reconnected after timeout
            // This handles the case where timeout already fired and removed them
            // We can't easily track this, so we'll just let them spawn normally
            // (They would have been teleported to exit when timeout fired if they were online)
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if this player died in a challenge and needs to be teleported
        String challengeId = pendingDeathTeleports.remove(uuid);
        if (challengeId == null) return;
        
        // Schedule teleport for next tick (after respawn is complete)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                
                // Teleport player back to spawn (same as /exit)
                if (spawnLocation != null) {
                    player.teleport(spawnLocation);
                    player.sendMessage(getMessage("challenge.died"));
                }
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (npcIds.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        if (!(dead instanceof LivingEntity livingEntity)) return;
        Player killer = livingEntity.getKiller();
        if (killer == null) return;

        UUID uuid = killer.getUniqueId();
        String challengeId = activeChallenges.get(uuid);
        if (challengeId == null) return;

        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null || cfg.completionType != CompletionType.BOSS || cfg.bossRequirement == null) return;

        BossRequirement req = cfg.bossRequirement;

        if (dead.getType() != req.type) return;

        if (req.name != null && !req.name.isEmpty()) {
            String entityName = dead.getCustomName();
            if (entityName == null) return;
            String expected = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', req.name));
            String actual = ChatColor.stripColor(entityName);
            if (!actual.equalsIgnoreCase(expected)) return;
        }

        completedChallenges.put(uuid, challengeId);
        killer.sendMessage(getMessage("admin.boss-defeated", "challenge", displayName(cfg)));
        killer.sendMessage(getMessage("entry.none-instruction"));
        markEverCompleted(cfg, uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcRightClick(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        
        // Check if this entity is a registered GateKeeper NPC
        UUID entityUuid = entity.getUniqueId();
        boolean isGateKeeper = npcIds.contains(entityUuid);
        
        // If not found by UUID, check by display name (for FancyNPCs and custom entities)
        if (!isGateKeeper) {
            @SuppressWarnings("deprecation")
            String displayName = entity.getCustomName();
            if (displayName != null) {
                // Check if display name matches GateKeeper (with color codes stripped)
                String stripped = ChatColor.stripColor(displayName);
                if (stripped.equalsIgnoreCase("GateKeeper")) {
                    isGateKeeper = true;
                }
            }
        }
        
        // Also check if it's a FancyNPC that we registered (by checking all registered NPCs)
        if (!isGateKeeper && getServer().getPluginManager().getPlugin("FancyNPCs") != null) {
            try {
                Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.FancyNpcsPlugin");
                Object fancyNpcsPlugin = pluginClass.getMethod("get").invoke(null);
                if (fancyNpcsPlugin != null) {
                    Method getNpcManagerMethod = pluginClass.getMethod("getNpcManager");
                    Object npcManager = getNpcManagerMethod.invoke(fancyNpcsPlugin);
                    
                    // Get all NPCs and check if any match our registered UUIDs
                    Method getAllNpcsMethod = npcManager.getClass().getMethod("getAllNpcs");
                    @SuppressWarnings("unchecked")
                    Collection<Object> allNpcs = (Collection<Object>) getAllNpcsMethod.invoke(npcManager);
                    
                    for (Object npc : allNpcs) {
                        Method getEntityMethod = npc.getClass().getMethod("getEntity");
                        Object npcEntity = getEntityMethod.invoke(npc);
                        if (npcEntity instanceof Entity fnpcEntity && fnpcEntity.equals(entity)) {
                            // Check if this FancyNPC's entity UUID is in our registered list
                            if (npcIds.contains(fnpcEntity.getUniqueId())) {
                                isGateKeeper = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // FancyNPCs API not available or error - continue with standard check
            }
        }
        
        if (!isGateKeeper) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Anger check first
        if (isAngryAt(player)) {
            sendAngryResponse(player);
            return;
        }

        // Turn-in path
        String activeId = activeChallenges.get(uuid);
        if (activeId != null) {
            ChallengeConfig cfg = challenges.get(activeId);
            if (cfg != null) {
                switch (cfg.completionType) {
                    case BOSS, TIMER, SPEED -> {
                        if (!hasCompletedChallenge(uuid, activeId)) {
                            player.sendMessage(getMessage("admin.conditions-not-met"));
                            return;
                        }
                        player.sendMessage(getMessage("admin.gatekeeper-survived", "challenge", displayName(cfg)));
                        player.sendMessage(getMessage("admin.gatekeeper-well-done"));
                        runRewardCommands(player, cfg, activeId);
                        recordCompletionTimeForCooldown(activeId, uuid);
                        String removedChallengeId = activeChallenges.remove(uuid);
                        completedChallenges.remove(uuid);
                        challengeStartTimes.remove(uuid);
                        // Clean up difficulty tier if no players are active in this challenge
                        if (removedChallengeId != null) {
                            cleanupChallengeDifficultyIfEmpty(removedChallengeId);
                        }
                        // Check if challenge is now available and process queue
                        if (!isChallengeLocked(activeId)) {
                            processQueue(activeId);
                        }
                        return;
                    }
                    case ITEM -> {
                        if (cfg.itemRequirement == null) {
                            player.sendMessage(getMessage("admin.challenge-misconfigured-item"));
                            return;
                        }
                        if (!hasRequiredItem(player, cfg.itemRequirement)) {
                            player.sendMessage(getMessage("admin.challenge-item-not-found"));
                            return;
                        }
                        consumeRequiredItem(player, cfg.itemRequirement);
                        player.sendMessage(getMessage("admin.challenge-item-brought", "challenge", displayName(cfg)));
                        runRewardCommands(player, cfg, activeId);
                        recordCompletionTimeForCooldown(activeId, uuid);
                        String removedChallengeId = activeChallenges.remove(uuid);
                        // Clean up difficulty tier if no players are active in this challenge
                        if (removedChallengeId != null) {
                            cleanupChallengeDifficultyIfEmpty(removedChallengeId);
                        }
                        // Check if challenge is now available and process queue
                        if (!isChallengeLocked(activeId)) {
                            processQueue(activeId);
                        }
                        return;
                    }
                    case NONE -> {
                        player.sendMessage(getMessage("admin.challenge-returned", "challenge", displayName(cfg)));
                        runRewardCommands(player, cfg, activeId);
                        recordCompletionTimeForCooldown(activeId, uuid);
                        String removedChallengeId2 = activeChallenges.remove(uuid);
                        // Clean up difficulty tier if no players are active in this challenge
                        if (removedChallengeId2 != null) {
                            cleanupChallengeDifficultyIfEmpty(removedChallengeId2);
                        }
                        // Check if challenge is now available and process queue
                        if (!isChallengeLocked(activeId)) {
                            processQueue(activeId);
                        }
                        return;
                    }
                }
            }
        }

        // Check if player just completed a challenge (via /challengecomplete)
        String completedId = completedChallenges.get(uuid);
        if (completedId != null) {
            ChallengeConfig cfg = challenges.get(completedId);
            if (cfg != null) {
                // Player completed a challenge, give rewards
                player.sendMessage(getMessage("admin.gatekeeper-survived", "challenge", displayName(cfg)));
                player.sendMessage(getMessage("admin.gatekeeper-well-done"));
                runRewardCommands(player, cfg, completedId);
                recordCompletionTimeForCooldown(completedId, uuid);
                completedChallenges.remove(uuid);
                // Check if challenge is now available and process queue
                if (!isChallengeLocked(completedId)) {
                    processQueue(completedId);
                }
                return;
            }
        }

        // Normal interaction
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            player.sendMessage(getMessage("admin.npc-no-book"));
            return;
        }

        // Sword
        if (item.getType().name().endsWith("_SWORD")) {
            player.sendMessage(getMessage("admin.npc-ouch"));
            return;
        }

        // Food
        if (item.getType().isEdible()) {
            int amount = item.getAmount();
            if (amount > 1) {
                item.setAmount(amount - 1);
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.sendMessage(getMessage("admin.npc-thank-you"));
            return;
        }

        // Books (auth vs fake)
        if (item.getType() == Material.WRITTEN_BOOK) {
            // Get a fresh copy of the item meta to ensure we're reading the latest data
            BookMeta itemMeta = (BookMeta) item.getItemMeta();
            if (itemMeta == null) {
                player.sendMessage(getMessage("admin.npc-no-book"));
                return;
            }
            
            String title = itemMeta.getTitle();
            if (title == null || title.isEmpty()) {
                player.sendMessage(getMessage("admin.npc-no-book"));
                return;
            }
            
            // Get the PersistentDataContainer directly from the item's meta
            PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
            String taggedChallengeId = pdc.get(challengeKey, PersistentDataType.STRING);
            
            // Check if title matches a known challenge
            String challengeId = bookTitleToChallengeId.get(title);
            
            // 1) Check tag for real challenge book (most reliable check)
            if (taggedChallengeId != null && !taggedChallengeId.isEmpty()) {
                ChallengeConfig taggedCfg = challenges.get(taggedChallengeId);
                if (taggedCfg != null) {
                    if (taggedCfg.destination == null) {
                        player.sendMessage(getMessage("admin.challenge-no-destination-admin"));
                        return;
                    }
                    if (!checkAndWarnCooldown(player, taggedCfg)) {
                        return;
                    }
                    
                    // Check if player already has a pending challenge or party session
                    if (pendingChallenges.containsKey(uuid) || partySessions.containsKey(uuid)) {
                        player.sendMessage(getMessage("admin.npc-pending-exists"));
                        return;
                    }
                    
                    // Check if challenge is locked down
                    if (isChallengeLockedDown(taggedCfg.id)) {
                        player.sendMessage(getMessage("challenge.locked-down"));
                        return;
                    }
                    
                    pendingChallenges.put(uuid, new PendingChallenge(taggedCfg.id));
                    startPendingChallengeTimeout(uuid);
                    
                    if (isChallengeLocked(taggedCfg.id)) {
                        if (isChallengeBeingEdited(taggedCfg.id)) {
                            UUID editorUuid = getChallengeEditor(taggedCfg.id);
                            Player editor = editorUuid != null ? Bukkit.getPlayer(editorUuid) : null;
                            String editorName = editor != null && editor.isOnline() ? editor.getName() : "an admin";
                            player.sendMessage(getMessage("challenge.maintenance", "admin", editorName));
                        }
                        // Challenge is locked, add to queue
                        addToQueue(uuid, taggedCfg.id, false);
                        player.sendMessage(getMessage("admin.npc-book-detected"));
                        player.sendMessage(getMessage("admin.npc-warning"));
                        player.sendMessage(getMessage("admin.npc-options"));
                        return;
                    }
                    
                    player.sendMessage(getMessage("admin.npc-book-detected"));
                    player.sendMessage(getMessage("admin.npc-warning"));
                    player.sendMessage(getMessage("admin.npc-options"));
                    return;
                }
            }

            // 2) If title matches a known challenge but no tag
            if (challengeId != null) {
                ChallengeConfig cfg = challenges.get(challengeId);
                if (cfg != null) {
                    // Check author if required (primary forgery detection)
                    String requiredAuthor = antiForgeryRequiredAuthor();
                    if (!requiredAuthor.isEmpty() && antiForgeryEnabled()) {
                        String bookAuthor = itemMeta.getAuthor();
                        if (bookAuthor == null || !bookAuthor.equals(requiredAuthor)) {
                            // Author doesn't match required author - flag as fake
                            punishFakeBook(player, item, challengeId);
                            return;
                        }
                    } else if (taggedChallengeId == null || taggedChallengeId.isEmpty()) {
                        // No tag and no author check configured - flag as fake if anti-forgery enabled
                        if (antiForgeryEnabled()) {
                            punishFakeBook(player, item, challengeId);
                            return;
                        }
                    }
                    
                    // Proceed with challenge if we got here (has tag, or author matches, or anti-forgery disabled)
                    if (cfg.destination == null) {
                        player.sendMessage(getMessage("admin.challenge-no-destination-admin"));
                        return;
                    }
                    if (isChallengeLocked(cfg.id)) {
                        if (isChallengeBeingEdited(cfg.id)) {
                            UUID editorUuid = getChallengeEditor(cfg.id);
                            Player editor = editorUuid != null ? Bukkit.getPlayer(editorUuid) : null;
                            String editorName = editor != null && editor.isOnline() ? editor.getName() : "an admin";
                            player.sendMessage(getMessage("challenge.maintenance", "admin", editorName));
                            // Add to queue
                            addToQueue(uuid, cfg.id, false);
                            player.sendMessage(getMessage("challenge.locked-queue"));
                        } else {
                            player.sendMessage(getMessage("admin.challenge-locked"));
                        }
                        return;
                    }
                    // Check if challenge is locked down
                    if (isChallengeLockedDown(cfg.id)) {
                        player.sendMessage(getMessage("challenge.locked-down"));
                        return;
                    }
                    
                    if (!checkAndWarnCooldown(player, cfg)) {
                        return;
                    }
                    pendingChallenges.put(uuid, new PendingChallenge(cfg.id));
                    startPendingChallengeTimeout(uuid);
                    player.sendMessage(getMessage("admin.npc-book-detected"));
                    player.sendMessage(getMessage("admin.npc-warning"));
                    player.sendMessage(getMessage("admin.npc-options"));
                    return;
                }
            }
        }

        // Other items or non-matching books
        player.sendMessage(getMessage("admin.npc-no-book"));
    }

    // ====== PLACEHOLDERAPI PUBLIC METHODS ======
    
    /**
     * Gets the active challenge ID for a player, or null if not in a challenge.
     */
    public String getActiveChallenge(UUID uuid) {
        return activeChallenges.get(uuid);
    }
    
    /**
     * Gets the display name (book title or ID) for a challenge.
     */
    public String getChallengeDisplayName(String challengeId) {
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) return challengeId;
        return cfg.bookTitle != null ? cfg.bookTitle : challengeId;
    }
    
    /**
     * Gets the best time (in seconds) for a player in a challenge, or null if not completed.
     */
    public Long getBestTime(UUID uuid, String challengeId) {
        Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
        if (challengeBestTimes == null) return null;
        return challengeBestTimes.get(uuid);
    }
    
    /**
     * Checks if a player has completed a challenge.
     */
    public boolean hasCompletedChallenge(UUID uuid, String challengeId) {
        Set<UUID> completed = everCompleted.get(challengeId);
        return completed != null && completed.contains(uuid);
    }
    
    /**
     * Gets the cooldown remaining in seconds for a player in a challenge.
     */
    public long getCooldownRemaining(UUID uuid, String challengeId) {
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null || cfg.cooldownSeconds <= 0) return 0;
        
        Map<UUID, Long> lastCompletions = lastCompletionTimes.get(challengeId);
        if (lastCompletions == null) return 0;
        
        Long lastCompletion = lastCompletions.get(uuid);
        if (lastCompletion == null) return 0;
        
        long now = System.currentTimeMillis();
        long elapsed = (now - lastCompletion) / 1000; // Convert to seconds
        long remaining = cfg.cooldownSeconds - elapsed;
        
        return Math.max(0, remaining);
    }
    
    /**
     * Gets the number of times a player has completed a challenge.
     */
    public int getCompletionCount(UUID uuid, String challengeId) {
        // For now, we only track if they've ever completed it
        // This could be enhanced to track multiple completions
        return hasCompletedChallenge(uuid, challengeId) ? 1 : 0;
    }
    
    /**
     * Gets the player name at a specific position in the leaderboard for a challenge.
     */
    public String getTopPlayer(String challengeId, int position) {
        Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
        if (challengeBestTimes == null || challengeBestTimes.isEmpty()) return null;
        
        // Sort by time (ascending - lower is better)
        List<Map.Entry<UUID, Long>> sorted = challengeBestTimes.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());
        
        if (position < 1 || position > sorted.size()) return null;
        
        UUID uuid = sorted.get(position - 1).getKey();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName();
    }
    
    /**
     * Gets the time at a specific position in the leaderboard for a challenge.
     */
    public Long getTopTime(String challengeId, int position) {
        Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
        if (challengeBestTimes == null || challengeBestTimes.isEmpty()) return null;
        
        // Sort by time (ascending - lower is better)
        List<Map.Entry<UUID, Long>> sorted = challengeBestTimes.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());
        
        if (position < 1 || position > sorted.size()) return null;
        
        return sorted.get(position - 1).getValue();
    }
    
    /**
     * Gets the number of entries in the leaderboard for a challenge.
     */
    public int getLeaderboardCount(String challengeId) {
        Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
        return challengeBestTimes != null ? challengeBestTimes.size() : 0;
    }
    
    /**
     * Gets a player's rank in a challenge leaderboard (1-based, or 0 if not ranked).
     */
    public int getPlayerRank(UUID uuid, String challengeId) {
        Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
        if (challengeBestTimes == null || !challengeBestTimes.containsKey(uuid)) return 0;
        
        Long playerTime = challengeBestTimes.get(uuid);
        if (playerTime == null) return 0;
        
        // Count how many players have better (lower) times
        int rank = 1;
        for (Long time : challengeBestTimes.values()) {
            if (time < playerTime) {
                rank++;
            }
        }
        
        return rank;
    }
    
    /**
     * Gets the total number of challenge completions for a player across all challenges.
     */
    public int getTotalCompletions(UUID uuid) {
        int count = 0;
        for (Set<UUID> completed : everCompleted.values()) {
            if (completed.contains(uuid)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets the number of unique challenges a player has completed.
     */
    public int getChallengesCompletedCount(UUID uuid) {
        return getTotalCompletions(uuid); // Same as total for now
    }

    /**
     * Collects all tier rewards with inheritance (lower tiers get multiplied when inherited by higher tiers)
     * All rewards from lower tiers get the current tier's cumulative multiplier
     */
    private List<TierReward> collectTierRewards(ChallengeConfig cfg, String difficultyTier) {
        List<TierReward> allRewards = new ArrayList<>();
        List<String> tierOrder = Arrays.asList("easy", "medium", "hard", "extreme");
        int currentTierIndex = tierOrder.indexOf(difficultyTier);
        if (currentTierIndex == -1) currentTierIndex = 0; // Default to easy
        
        // Get the current tier's cumulative multiplier (this applies to all inherited rewards)
        double currentTierMultiplier = cumulativeRewardMultipliers.getOrDefault(difficultyTier, 1.0);
        
        // Collect rewards from all tiers up to and including current tier
        // All rewards get the current tier's multiplier (inheritance)
        for (int i = 0; i <= currentTierIndex; i++) {
            String tierName = tierOrder.get(i);
            List<TierReward> tierRewards = cfg.difficultyTierRewards.get(tierName);
            if (tierRewards != null) {
                // All rewards from this tier get the current tier's multiplier
                for (TierReward reward : tierRewards) {
                    // Create a copy
                    TierReward rewardCopy = new TierReward(reward.type, reward.chance);
                    rewardCopy.material = reward.material;
                    rewardCopy.amount = reward.amount;
                    rewardCopy.itemStack = reward.itemStack != null ? reward.itemStack.clone() : null;
                    rewardCopy.command = reward.command;
                    allRewards.add(rewardCopy);
                }
            }
        }
        
        return allRewards;
    }
    
    /**
     * Calculates speed multiplier based on completion time (for SPEED challenges)
     * Returns multiplier and whether it's a new record
     * Gold tier = 1.5x, Silver/Average = 1.0x, Bronze/Worse = 0.5x
     * New record = 3x (on top of tier multiplier)
     */
    private double[] calculateSpeedMultiplier(ChallengeConfig cfg, String challengeId, UUID playerUuid, int elapsedSeconds) {
        double speedMultiplier = 1.0;
        boolean isNewRecord = false;
        
        if (cfg.completionType == CompletionType.SPEED) {
            SpeedTier tier = findBestSpeedTier(cfg, elapsedSeconds);
            if (tier != null) {
                // Speed tier multipliers: Gold = 1.5x, Silver/Average = 1.0x, Bronze/Worse = 0.5x
                String tierNameUpper = tier.name.toUpperCase();
                if (tierNameUpper.contains("GOLD")) {
                    speedMultiplier = 1.5;
                } else if (tierNameUpper.contains("SILVER")) {
                    speedMultiplier = 1.0; // Average time
                } else {
                    speedMultiplier = 0.5; // Bronze or worse = bad time
                }
            } else {
                // No tier matched = bad time
                speedMultiplier = 0.5;
            }
            
            // Check if this is a new record
            Map<UUID, Long> challengeBestTimes = bestTimes.get(challengeId);
            if (challengeBestTimes != null) {
                Long previousBest = challengeBestTimes.get(playerUuid);
                if (previousBest == null || elapsedSeconds < previousBest) {
                    isNewRecord = true;
                    speedMultiplier = 3.0; // New record = 3x (replaces tier multiplier)
                }
            } else {
                isNewRecord = true; // First completion is always a record
                speedMultiplier = 3.0;
            }
        }
        
        return new double[]{speedMultiplier, isNewRecord ? 1.0 : 0.0};
    }
    
    /**
     * Checks if a material is a tool, armor, or weapon (items that shouldn't be multiplied)
     */
    private boolean isToolArmorOrWeapon(Material material) {
        if (material == null) return false;
        
        String matName = material.name().toUpperCase();
        
        // Check for tools
        if (matName.contains("_AXE") || matName.contains("_PICKAXE") || 
            matName.contains("_SHOVEL") || matName.contains("_HOE") ||
            matName.contains("_SWORD") || matName.contains("_BOW") ||
            matName.contains("_CROSSBOW") || matName.contains("_TRIDENT") ||
            matName.equals("SHEARS") || matName.equals("FLINT_AND_STEEL") ||
            matName.equals("FISHING_ROD") || matName.equals("CARROT_ON_A_STICK") ||
            matName.equals("WARPED_FUNGUS_ON_A_STICK") || matName.equals("SHIELD")) {
            return true;
        }
        
        // Check for armor
        if (matName.contains("_HELMET") || matName.contains("_CHESTPLATE") ||
            matName.contains("_LEGGINGS") || matName.contains("_BOOTS") ||
            matName.equals("ELYTRA") || matName.equals("TURTLE_HELMET")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Applies a single tier reward to a player
     */
    private void applyTierReward(Player player, TierReward reward, double difficultyMultiplier, double speedMultiplier) {
        // Check RNG chance
        if (Math.random() > reward.chance) {
            return; // Failed RNG roll
        }
        
        double totalMultiplier = difficultyMultiplier * speedMultiplier;
        
        switch (reward.type) {
            case HAND -> {
                if (reward.itemStack != null) {
                    ItemStack item = reward.itemStack.clone();
                    // Don't multiply tools, armor, or weapons
                    if (isToolArmorOrWeapon(item.getType())) {
                        // Give item as-is without multiplier
                        player.getInventory().addItem(item);
                    } else {
                        int newAmount = (int) Math.max(1, Math.round(item.getAmount() * totalMultiplier));
                        item.setAmount(newAmount);
                        player.getInventory().addItem(item);
                    }
                }
            }
            case ITEM -> {
                if (reward.material != null) {
                    // Don't multiply tools, armor, or weapons
                    if (isToolArmorOrWeapon(reward.material)) {
                        // Give item as-is without multiplier
                        ItemStack item = new ItemStack(reward.material, reward.amount);
                        player.getInventory().addItem(item);
                    } else {
                        int newAmount = (int) Math.max(1, Math.round(reward.amount * totalMultiplier));
                        ItemStack item = new ItemStack(reward.material, newAmount);
                        player.getInventory().addItem(item);
                    }
                }
            }
            case COMMAND -> {
                if (reward.command != null) {
                    String cmd = reward.command.replace("%player%", player.getName());
                    String multipliedCmd = applyRewardMultiplier(cmd, totalMultiplier);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), multipliedCmd);
                }
            }
        }
    }
    
    private void runRewardCommands(Player player, ChallengeConfig cfg, String challengeId) {
        UUID uuid = player.getUniqueId();
        
        // Get difficulty tier
        String difficultyTier = activeChallengeDifficulties.getOrDefault(challengeId, "easy");
        double difficultyMultiplier = cumulativeRewardMultipliers.getOrDefault(difficultyTier, 1.0);
        
        // Calculate speed multiplier (for SPEED challenges)
        double speedMultiplier = 1.0;
        final boolean[] isNewRecord = {false};
        if (cfg.completionType == CompletionType.SPEED) {
            Map<UUID, Long> times = bestTimes.get(challengeId);
            if (times != null) {
                Long best = times.get(uuid);
                if (best != null) {
                    double[] speedData = calculateSpeedMultiplier(cfg, challengeId, uuid, best.intValue());
                    speedMultiplier = speedData[0];
                    isNewRecord[0] = speedData[1] > 0.5;
                }
            }
        }
        
        // Collect tier rewards (with inheritance)
        List<TierReward> tierRewards = collectTierRewards(cfg, difficultyTier);
        
        // Get balance before (if economy is available)
        final double balanceBefore = economy != null ? economy.getBalance(player) : 0.0;
        
        // Track rewards given
        final double[] totalMoney = {0.0};
        final List<String> rewardMessages = new ArrayList<>();
        final double finalDifficultyMultiplier = difficultyMultiplier;
        final double finalSpeedMultiplier = speedMultiplier;
        
        // Apply tier rewards if any exist
        boolean hasTierRewards = !tierRewards.isEmpty();
        if (hasTierRewards) {
            for (TierReward reward : tierRewards) {
                applyTierReward(player, reward, finalDifficultyMultiplier, finalSpeedMultiplier);
            }
        }
        
        // Apply top time rewards if this is a new record (SPEED challenges only)
        if (isNewRecord[0] && cfg.completionType == CompletionType.SPEED && cfg.topTimeRewards != null && !cfg.topTimeRewards.isEmpty()) {
            // Top time rewards get 3x multiplier (difficulty multiplier still applies)
            for (TierReward reward : cfg.topTimeRewards) {
                applyTierReward(player, reward, finalDifficultyMultiplier, 3.0); // 3x for top time
            }
        }
        
        if (!hasTierRewards) {
            // Fallback to old system (fallbackRewardCommands)
            if (cfg.fallbackRewardCommands != null && !cfg.fallbackRewardCommands.isEmpty()) {
                for (RewardWithChance rwc : cfg.fallbackRewardCommands) {
                    // Check RNG chance
                    if (Math.random() > rwc.chance) {
                        continue; // Failed RNG roll
                    }
                    
                    String cmd = rwc.command.replace("%player%", player.getName());
                    double totalMultiplier = finalDifficultyMultiplier * finalSpeedMultiplier;
                    String multipliedCmd = applyRewardMultiplier(cmd, totalMultiplier);
                    
                    // Parse eco commands to extract money (for display purposes)
                    String lowerCmd = multipliedCmd.toLowerCase();
                    if (lowerCmd.startsWith("eco give ") || lowerCmd.startsWith("/eco give ")) {
                        String[] parts = multipliedCmd.split("\\s+");
                        if (parts.length >= 3) {
                            try {
                                for (int i = parts.length - 1; i >= 0; i--) {
                                    try {
                                        double amount = Double.parseDouble(parts[i]);
                                        totalMoney[0] += amount;
                                        break;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), multipliedCmd);
                }
            }
        }
        
        if (!hasTierRewards && (cfg.fallbackRewardCommands == null || cfg.fallbackRewardCommands.isEmpty())) {
            player.sendMessage(getMessage("admin.challenge-no-rewards"));
            return;
        }
        
        // Schedule balance check after 1 tick to ensure economy plugin has processed commands
        new BukkitRunnable() {
            @Override
            public void run() {
                // If economy is available, check actual balance change (more accurate)
                double finalTotalMoney = totalMoney[0];
                if (economy != null) {
                    double balanceAfter = economy.getBalance(player);
                    double actualChange = balanceAfter - balanceBefore;
                    if (actualChange > 0) {
                        finalTotalMoney = actualChange;
                    }
                }
                
                // Build reward message
                if (finalTotalMoney > 0) {
                    if (finalTotalMoney == (long) finalTotalMoney) {
                        rewardMessages.add(getMessage("admin.reward-money-format", "amount", String.valueOf((long) finalTotalMoney)));
                    } else {
                        rewardMessages.add(getMessage("admin.reward-money-format-decimal", "amount", String.format("%.2f", finalTotalMoney)));
                    }
                }
                
                if (isNewRecord[0] && cfg.completionType == CompletionType.SPEED) {
                    player.sendMessage("§6§lNEW RECORD! §rYou received a bonus multiplier!");
                }
                
                if (rewardMessages.isEmpty()) {
                    player.sendMessage(getMessage("admin.challenge-completed-party"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-completed-rewards", "rewards", String.join(getMessage("admin.reward-separator"), rewardMessages)));
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Applies a difficulty multiplier to a reward command.
     * Multiplies money amounts in eco commands and item amounts in give commands.
     * 
     * @param command The original command
     * @param multiplier The multiplier to apply
     * @return The command with multiplied amounts
     */
    private String applyRewardMultiplier(String command, double multiplier) {
        if (multiplier == 1.0) {
            return command; // No change needed
        }
        
        String lowerCmd = command.toLowerCase();
        String[] parts = command.split("\\s+");
        
        // Handle eco give commands: "eco give <player> <amount>" or "/eco give <player> <amount>"
        if (lowerCmd.startsWith("eco give ") || lowerCmd.startsWith("/eco give ")) {
            // Find the amount (usually the last number)
            for (int i = parts.length - 1; i >= 0; i--) {
                try {
                    double amount = Double.parseDouble(parts[i]);
                    double multiplied = amount * multiplier;
                    // Round to 2 decimal places for money
                    multiplied = Math.round(multiplied * 100.0) / 100.0;
                    parts[i] = String.valueOf(multiplied);
                    return String.join(" ", parts);
                } catch (NumberFormatException ignored) {
                    // Not a number, continue
                }
            }
        }
        
        // Handle give commands: "give <player> <material> <amount>"
        if (lowerCmd.startsWith("give ") || lowerCmd.startsWith("/give ")) {
            // Find the amount (usually after the material)
            for (int i = 0; i < parts.length - 1; i++) {
                // Check if this part looks like a material (all caps or mixed case)
                String part = parts[i];
                if (part.length() > 0 && Character.isUpperCase(part.charAt(0))) {
                    // Check if this material is a tool, armor, or weapon - don't multiply those
                    try {
                        Material material = Material.valueOf(part.toUpperCase());
                        if (isToolArmorOrWeapon(material)) {
                            // Don't multiply tools, armor, or weapons - return command as-is
                            return command;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Not a valid material, continue
                    }
                    
                    // Next part might be the amount
                    try {
                        int amount = Integer.parseInt(parts[i + 1]);
                        int multiplied = (int) Math.round(amount * multiplier);
                        if (multiplied < 1) multiplied = 1; // At least 1 item
                        parts[i + 1] = String.valueOf(multiplied);
                        return String.join(" ", parts);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                        // Not a number or out of bounds
                    }
                }
            }
        }
        
        // For other commands, try to find and multiply any numbers
        // This is a fallback for custom reward commands
        for (int i = 0; i < parts.length; i++) {
            try {
                double amount = Double.parseDouble(parts[i]);
                // Only multiply if it looks like a quantity (reasonable range)
                if (amount > 0 && amount <= 1000000) {
                    double multiplied = amount * multiplier;
                    // Round appropriately
                    if (amount == (int) amount) {
                        // Was an integer, keep as integer
                        parts[i] = String.valueOf((int) Math.round(multiplied));
                    } else {
                        // Was a decimal, keep as decimal
                        multiplied = Math.round(multiplied * 100.0) / 100.0;
                        parts[i] = String.valueOf(multiplied);
                    }
                    return String.join(" ", parts);
                }
            } catch (NumberFormatException ignored) {
                // Not a number, continue
            }
        }
        
        // If we couldn't find anything to multiply, return original command
        return command;
    }

    // ====== BOOK + INVENTORY HELPERS ======

    private ChallengeConfig getChallengeFromTaggedBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return null;
        if (!(item.getItemMeta() instanceof BookMeta meta)) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(challengeKey, PersistentDataType.STRING);
        if (id == null) return null;
        return challenges.get(id);
    }

    private boolean hasChallengeBook(Player player, String bookTitle) {
        if (bookTitle == null) return false;
        
        // Find the challenge ID for this book title
        String challengeId = bookTitleToChallengeId.get(bookTitle);
        if (challengeId == null) return false;
        
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) return false;
        
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.WRITTEN_BOOK) continue;
            if (!(stack.getItemMeta() instanceof BookMeta meta)) continue;
            String title = meta.getTitle();
            if (title == null || !title.equals(bookTitle)) continue;
            
            // Check if book has PDC tag (most reliable)
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String taggedChallengeId = pdc.get(challengeKey, PersistentDataType.STRING);
            if (taggedChallengeId != null && taggedChallengeId.equals(challengeId)) {
                return true;
            }
            
            // If no tag, check author (same logic as GateKeeper interaction)
            String requiredAuthor = antiForgeryRequiredAuthor();
            if (!requiredAuthor.isEmpty() && antiForgeryEnabled()) {
                String bookAuthor = meta.getAuthor();
                if (bookAuthor != null && bookAuthor.equals(requiredAuthor)) {
                    return true;
                }
            } else if (!antiForgeryEnabled()) {
                // Anti-forgery disabled, accept books by title match
                return true;
            }
        }
        return false;
    }

    private void consumeChallengeBook(Player player, String bookTitle) {
        if (bookTitle == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != Material.WRITTEN_BOOK) continue;
            if (!(stack.getItemMeta() instanceof BookMeta meta)) continue;
            String title = meta.getTitle();
            if (title != null && title.equals(bookTitle)) {
                int amt = stack.getAmount();
                if (amt > 1) {
                    stack.setAmount(amt - 1);
                    contents[i] = stack;
                } else {
                    contents[i] = null;
                }
                player.getInventory().setContents(contents);
                player.updateInventory();
                return;
            }
        }
    }

    private boolean hasRequiredItem(Player player, ItemRequirement req) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (stack.getType() == req.material) {
                total += stack.getAmount();
                if (total >= req.amount) return true;
            }
        }
        return false;
    }

    private void consumeRequiredItem(Player player, ItemRequirement req) {
        if (!req.consume) return;
        int toRemove = req.amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            if (stack.getType() != req.material) continue;

            int amt = stack.getAmount();
            if (amt <= toRemove) {
                contents[i] = null;
                toRemove -= amt;
            } else {
                stack.setAmount(amt - toRemove);
                contents[i] = stack;
                toRemove = 0;
            }

            if (toRemove <= 0) break;
        }

        player.getInventory().setContents(contents);
        player.updateInventory();
    }

    // ====== COMPLETION KEY HELPERS ======

    /**
     * Finds a Completion Key in the player's inventory.
     * A Completion Key is a renamed trial key (display name must be "Completion Key").
     * 
     * @param player The player to check
     * @return The Completion Key item stack, or null if not found
     */
    @SuppressWarnings("deprecation")
    private ItemStack findCompletionKey(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            
            // Check by display name since it's renamed
            if (stack.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta.hasDisplayName()) {
                    String displayName = ChatColor.stripColor(meta.getDisplayName());
                    if (displayName.equalsIgnoreCase("Completion Key")) {
                        return stack;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Consumes one Completion Key from the player's inventory.
     * 
     * @param player The player
     * @param keyItem The Completion Key item stack to consume
     */
    @SuppressWarnings("deprecation")
    private void consumeCompletionKey(Player player, ItemStack keyItem) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            
            // Check if this is the same item (by reference or by display name)
            if (stack == keyItem) {
                int amt = stack.getAmount();
                if (amt > 1) {
                    stack.setAmount(amt - 1);
                    contents[i] = stack;
                } else {
                    contents[i] = null;
                }
                player.getInventory().setContents(contents);
                player.updateInventory();
                return;
            }
            
            // Also check by display name in case reference doesn't match
            if (stack.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta.hasDisplayName()) {
                    String displayName = ChatColor.stripColor(meta.getDisplayName());
                    if (displayName.equalsIgnoreCase("Completion Key")) {
                        int amt = stack.getAmount();
                        if (amt > 1) {
                            stack.setAmount(amt - 1);
                            contents[i] = stack;
                        } else {
                            contents[i] = null;
                        }
                        player.getInventory().setContents(contents);
                        player.updateInventory();
                        return;
                    }
                }
            }
        }
    }

    // ====== REGENERATION SYSTEM ======

    /**
     * Gets WorldEdit/FastAsyncWorldEdit selection positions (pos1 and pos2) for a player.
     * Returns null if WorldEdit is not available or selection is incomplete.
     * 
     * @param player The player to get selection for
     * @return Array of 2 Locations [pos1, pos2], or null if not available
     */
    private Location[] getWorldEditSelection(Player player) {
        // Try WorldEdit first
        try {
            org.bukkit.plugin.Plugin worldEditPlugin = getServer().getPluginManager().getPlugin("WorldEdit");
            if (worldEditPlugin == null) {
                // Try FastAsyncWorldEdit
                worldEditPlugin = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
            }
            
            if (worldEditPlugin == null) {
                return null; // WorldEdit not available
            }
            
            // Get WorldEditPlugin instance using reflection
            Class<?> worldEditPluginClass = worldEditPlugin.getClass();
            
            // Get session for player - try different method names
            Object session = null;
            try {
                Method getSessionMethod = worldEditPluginClass.getMethod("getSession", org.bukkit.entity.Player.class);
                session = getSessionMethod.invoke(worldEditPlugin, player);
            } catch (NoSuchMethodException e) {
                // Try alternative method name
                try {
                    Method getSessionMethod = worldEditPluginClass.getMethod("getSessionManager");
                    Object sessionManager = getSessionMethod.invoke(worldEditPlugin);
                    Method getMethod = sessionManager.getClass().getMethod("get", org.bukkit.entity.Player.class);
                    session = getMethod.invoke(sessionManager, player);
                } catch (Exception ex) {
                    return null;
                }
            }
            
            if (session == null) {
                return null;
            }
            
            // Get selection - try different method signatures
            Object selection = null;
            try {
                Method getSelectionMethod = session.getClass().getMethod("getSelection", org.bukkit.World.class);
                selection = getSelectionMethod.invoke(session, player.getWorld());
            } catch (NoSuchMethodException e) {
                try {
                    Method getSelectionMethod = session.getClass().getMethod("getSelection");
                    selection = getSelectionMethod.invoke(session);
                } catch (Exception ex) {
                    return null;
                }
            }
            
            if (selection == null) {
                return null; // No selection
            }
            
            // Check if selection is complete - try different method names
            Boolean isDefined = false;
            try {
                Method isDefinedMethod = selection.getClass().getMethod("isDefined");
                isDefined = (Boolean) isDefinedMethod.invoke(selection);
            } catch (NoSuchMethodException e) {
                try {
                    Method isDefinedMethod = selection.getClass().getMethod("isAreaSelected");
                    isDefined = (Boolean) isDefinedMethod.invoke(selection);
                } catch (Exception ex) {
                    // Continue anyway - try to get points
                    isDefined = true; // Assume defined if we can't check
                }
            }
            
            if (!isDefined) {
                return null; // Selection incomplete
            }
            
            // Get minimum and maximum points
            Method getMinimumPointMethod = selection.getClass().getMethod("getMinimumPoint");
            Method getMaximumPointMethod = selection.getClass().getMethod("getMaximumPoint");
            Object minPoint = getMinimumPointMethod.invoke(selection);
            Object maxPoint = getMaximumPointMethod.invoke(selection);
            
            // Convert BlockVector3 to Location
            Method getXMethod = minPoint.getClass().getMethod("getX");
            Method getYMethod = minPoint.getClass().getMethod("getY");
            Method getZMethod = minPoint.getClass().getMethod("getZ");
            
            int x1 = ((Number) getXMethod.invoke(minPoint)).intValue();
            int y1 = ((Number) getYMethod.invoke(minPoint)).intValue();
            int z1 = ((Number) getZMethod.invoke(minPoint)).intValue();
            
            int x2 = ((Number) getXMethod.invoke(maxPoint)).intValue();
            int y2 = ((Number) getYMethod.invoke(maxPoint)).intValue();
            int z2 = ((Number) getZMethod.invoke(maxPoint)).intValue();
            
            Location pos1 = new Location(player.getWorld(), x1, y1, z1);
            Location pos2 = new Location(player.getWorld(), x2, y2, z2);
            
            return new Location[]{pos1, pos2};
        } catch (Exception e) {
            // WorldEdit not available or error accessing API
            getLogger().warning("WorldEdit selection not available: " + e.getMessage());
            if (getLogger().isLoggable(java.util.logging.Level.FINE)) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Creates a barrier box around the challenge area.
     * Places barrier blocks on all faces of the bounding box.
     * 
     * @param cfg The challenge configuration
     * @param player The player (for feedback)
     */
    private void createBarrierBox(ChallengeConfig cfg, Player player) {
        if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
            return;
        }
        
        World world = cfg.challengeCorner1.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Error: Challenge area world is not loaded!");
            return;
        }
        
        int minX = Math.min(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int maxX = Math.max(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int minY = Math.min(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int maxY = Math.max(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int minZ = Math.min(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        int maxZ = Math.max(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        
        int barrierCount = 0;
        
        // Place barriers on all 6 faces of the box
        // Only replace AIR blocks to preserve player builds
        // Bottom face (minY)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, minY - 1, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        // Top face (maxY)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, maxY + 1, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        // Front face (minZ)
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(x, y, minZ - 1);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        // Back face (maxZ)
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(x, y, maxZ + 1);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        // Left face (minX)
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(minX - 1, y, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        // Right face (maxX)
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(maxX + 1, y, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER, false);
                    barrierCount++;
                }
            }
        }
        
        player.sendMessage(ChatColor.GREEN + "Barrier box created with " + barrierCount + " barrier blocks.");
    }
    
    /**
     * Removes barrier blocks around the challenge area.
     * 
     * @param cfg The challenge configuration
     * @param player The player (for feedback)
     */
    private void removeBarrierBox(ChallengeConfig cfg, Player player) {
        if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
            return;
        }
        
        World world = cfg.challengeCorner1.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Error: Challenge area world is not loaded!");
            return;
        }
        
        int minX = Math.min(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int maxX = Math.max(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int minY = Math.min(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int maxY = Math.max(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int minZ = Math.min(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        int maxZ = Math.max(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        
        int removedCount = 0;
        
        // Remove barriers on all 6 faces of the box
        // Bottom face (minY)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, minY - 1, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        // Top face (maxY)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, maxY + 1, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        // Front face (minZ)
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(x, y, minZ - 1);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        // Back face (maxZ)
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(x, y, maxZ + 1);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        // Left face (minX)
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(minX - 1, y, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        // Right face (maxX)
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(maxX + 1, y, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        
        player.sendMessage(ChatColor.GREEN + "Removed " + removedCount + " barrier blocks.");
    }

    /**
     * Helper method to schedule the next batch of block capture (recursive batching)
     */
    private void scheduleNextCaptureBatch(String challengeId, World world, List<BlockState> states, 
                                          int[] currentX, int[] currentY, int[] currentZ,
                                          int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                          int[] failedBlocks, long timeBudget, java.util.function.Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                
                // Process blocks until we hit time budget or finish
                while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                    try {
                        Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                        BlockState state = block.getState();
                        states.add(state);
                    } catch (Exception e) {
                        failedBlocks[0]++;
                        getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
                    }
                    
                    // Move to next block
                    currentZ[0]++;
                    if (currentZ[0] > maxZ) {
                        currentZ[0] = minZ;
                        currentY[0]++;
                        if (currentY[0] > maxY) {
                            currentY[0] = minY;
                            currentX[0]++;
                        }
                    }
                }
                
                // Check if we're done
                if (currentX[0] > maxX) {
                    // Done capturing all blocks
                    challengeInitialStates.put(challengeId, states);
                    if (failedBlocks[0] > 0) {
                        getLogger().warning("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks, " + failedBlocks[0] + " failed)");
                    } else {
                        getLogger().info("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks)");
                    }
                    if (callback != null) {
                        // Ensure callback runs on main thread
                        final java.util.function.Consumer<Boolean> finalCallback = callback;
                        getLogger().fine("Calling capture callback for challenge '" + challengeId + "'");
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                getLogger().fine("Executing capture callback for challenge '" + challengeId + "'");
                                finalCallback.accept(true);
                            }
                        }.runTask(ConradChallengesPlugin.this);
                    }
                } else {
                    // More blocks to process, schedule next batch (recursive call)
                    scheduleNextCaptureBatch(challengeId, world, states, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, callback);
                }
            }
        }.runTaskLater(this, 1L);
    }

    /**
     * Captures the initial state of all blocks in the challenge area for a challenge.
     * This should be called when saving changes to the challenge area.
     * Runs asynchronously to prevent server lag.
     * 
     * @param cfg The challenge configuration
     * @param callback Optional callback to run on completion (on main thread). Receives true if successful, false otherwise.
     */
    private void captureChallengeAreaState(ChallengeConfig cfg, java.util.function.Consumer<Boolean> callback) {
        if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
            if (callback != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        callback.accept(false);
                    }
                }.runTask(this);
            }
            return;
        }
        
        if (cfg.challengeCorner1.getWorld() == null || 
            !cfg.challengeCorner1.getWorld().equals(cfg.challengeCorner2.getWorld())) {
            getLogger().warning("Challenge area for challenge '" + cfg.id + "' has mismatched worlds!");
            if (callback != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        callback.accept(false);
                    }
                }.runTask(this);
            }
            return;
        }
        
        final World world = cfg.challengeCorner1.getWorld();
        final String challengeId = cfg.id;
        
        // Calculate bounding box from challenge area
        int minX = Math.min(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int maxX = Math.max(cfg.challengeCorner1.getBlockX(), cfg.challengeCorner2.getBlockX());
        int minY = Math.min(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int maxY = Math.max(cfg.challengeCorner1.getBlockY(), cfg.challengeCorner2.getBlockY());
        int minZ = Math.min(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        int maxZ = Math.max(cfg.challengeCorner1.getBlockZ(), cfg.challengeCorner2.getBlockZ());
        
        // Calculate total blocks to process
        final int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        
        // Process in batches on MAIN THREAD ONLY (required for tile entities)
        // All block access (getBlockAt, getState) must happen on server thread
        final List<BlockState> states = new ArrayList<>();
        final int[] currentX = {minX};
        final int[] currentY = {minY};
        final int[] currentZ = {minZ};
        final int[] failedBlocks = {0};
        
        // Process in batches to avoid lag
        // Time budget: ~3ms per tick (similar to regeneration)
        // Calculate dynamic time budget based on area size
        // Estimate total blocks from bounding box
        int estimatedBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long timeBudget;
        if (estimatedBlocks > 10_000_000) {
            // Very large areas (>10M blocks): 10ms per tick
            timeBudget = 10_000_000L;
        } else if (estimatedBlocks > 1_000_000) {
            // Large areas (>1M blocks): 7ms per tick
            timeBudget = 7_000_000L;
        } else if (estimatedBlocks > 100_000) {
            // Medium areas (>100K blocks): 5ms per tick
            timeBudget = 5_000_000L;
        } else {
            // Small areas: 3ms per tick (default)
            timeBudget = 3_000_000L;
        }
        
        getLogger().info("Capturing challenge area state for challenge '" + challengeId + "' (estimated " + estimatedBlocks + " blocks, time budget: " + (timeBudget / 1_000_000) + "ms per tick)");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                int processedThisTick = 0;
                
                // Process blocks until we hit time budget or finish
                while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                    try {
                        Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                        BlockState state = block.getState();
                        states.add(state);
                    } catch (Exception e) {
                        failedBlocks[0]++;
                        getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
                    }
                    
                    processedThisTick++;
                    
                    // Move to next block
                    currentZ[0]++;
                    if (currentZ[0] > maxZ) {
                        currentZ[0] = minZ;
                        currentY[0]++;
                        if (currentY[0] > maxY) {
                            currentY[0] = minY;
                            currentX[0]++;
                        }
                    }
                }
                
                // Check if we're done
                if (currentX[0] > maxX) {
                    // Done capturing all blocks - store in challengeInitialStates (same map as regeneration)
                    challengeInitialStates.put(challengeId, states);
                    if (failedBlocks[0] > 0) {
                        getLogger().warning("Captured challenge area state for challenge '" + challengeId + "' (" + states.size() + " blocks, " + failedBlocks[0] + " failed)");
                    } else {
                        getLogger().info("Captured challenge area state for challenge '" + challengeId + "' (" + states.size() + " blocks)");
                    }
                    if (callback != null) {
                        callback.accept(true);
                    }
                } else {
                    // More blocks to process, schedule next batch (create new runnable instance)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            captureChallengeAreaStateBatch(challengeId, world, states, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, callback);
                        }
                    }.runTaskLater(ConradChallengesPlugin.this, 1L);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Helper method to continue capturing challenge area state in batches.
     */
    private void captureChallengeAreaStateBatch(String challengeId, World world, List<BlockState> states, 
                                                 int[] currentX, int[] currentY, int[] currentZ,
                                                 int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                 int[] failedBlocks, long timeBudget, java.util.function.Consumer<Boolean> callback) {
        long startTime = System.nanoTime();
        
        while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
            try {
                Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                BlockState state = block.getState();
                states.add(state);
            } catch (Exception e) {
                failedBlocks[0]++;
                getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
            }
            
            // Move to next block
            currentZ[0]++;
            if (currentZ[0] > maxZ) {
                currentZ[0] = minZ;
                currentY[0]++;
                if (currentY[0] > maxY) {
                    currentY[0] = minY;
                    currentX[0]++;
                }
            }
        }
        
        // Check if we're done
        if (currentX[0] > maxX) {
            // Done capturing all blocks
            challengeInitialStates.put(challengeId, states);
            if (failedBlocks[0] > 0) {
                getLogger().warning("Captured challenge area state for challenge '" + challengeId + "' (" + states.size() + " blocks, " + failedBlocks[0] + " failed)");
            } else {
                getLogger().info("Captured challenge area state for challenge '" + challengeId + "' (" + states.size() + " blocks)");
            }
            if (callback != null) {
                callback.accept(true);
            }
        } else {
            // More blocks to process, schedule next batch
            new BukkitRunnable() {
                @Override
                public void run() {
                    captureChallengeAreaStateBatch(challengeId, world, states, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, callback);
                }
            }.runTaskLater(ConradChallengesPlugin.this, 1L);
        }
    }
    
    /**
     * Captures the initial state of all blocks in the regeneration area for a challenge.
     * This should be called once when the regeneration area is first set.
     * Runs asynchronously to prevent server lag.
     * 
     * @param cfg The challenge configuration
     * @param callback Optional callback to run on completion (on main thread). Receives true if successful, false otherwise.
     */
    private void captureInitialState(ChallengeConfig cfg, java.util.function.Consumer<Boolean> callback) {
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            if (callback != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        callback.accept(false);
                    }
                }.runTask(ConradChallengesPlugin.this);
            }
            return;
        }
        
        if (cfg.regenerationCorner1.getWorld() == null || 
            !cfg.regenerationCorner1.getWorld().equals(cfg.regenerationCorner2.getWorld())) {
            getLogger().warning("Regeneration area for challenge '" + cfg.id + "' has mismatched worlds!");
            if (callback != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        callback.accept(false);
                    }
                }.runTask(ConradChallengesPlugin.this);
            }
            return;
        }
        
        final World world = cfg.regenerationCorner1.getWorld();
        final String challengeId = cfg.id;
        
        // Calculate bounding box
        int minX = Math.min(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int maxX = Math.max(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            // Auto-extend from bedrock to build height
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1; // getMaxHeight() is exclusive
        } else {
            // Use Y coordinates from corners
            minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
            maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
        }
        int minZ = Math.min(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        int maxZ = Math.max(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        
        // Calculate total blocks to process
        final int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        
        // Process in batches on MAIN THREAD ONLY (required for tile entities)
        // All block access (getBlockAt, getState) must happen on server thread
        final List<BlockState> states = new ArrayList<>();
        final int[] currentX = {minX};
        final int[] currentY = {minY};
        final int[] currentZ = {minZ};
        final int[] failedBlocks = {0};
        
        // Process in batches to avoid lag
        // Time budget: ~3ms per tick (similar to regeneration)
        // Calculate dynamic time budget based on area size
        // Estimate total blocks from bounding box
        int estimatedBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long timeBudget;
        if (estimatedBlocks > 10_000_000) {
            // Very large areas (>10M blocks): 10ms per tick
            timeBudget = 10_000_000L;
        } else if (estimatedBlocks > 1_000_000) {
            // Large areas (>1M blocks): 7ms per tick
            timeBudget = 7_000_000L;
        } else if (estimatedBlocks > 100_000) {
            // Medium areas (>100K blocks): 5ms per tick
            timeBudget = 5_000_000L;
        } else {
            // Small areas: 3ms per tick (default)
            timeBudget = 3_000_000L;
        }
        
        getLogger().info("Capturing initial state for challenge '" + challengeId + "' (estimated " + estimatedBlocks + " blocks, time budget: " + (timeBudget / 1_000_000) + "ms per tick)");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                int processedThisTick = 0;
                
                // Process blocks until we hit time budget or finish
                while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                    try {
                        Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                        BlockState state = block.getState();
                        states.add(state);
                    } catch (Exception e) {
                        failedBlocks[0]++;
                        getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
                    }
                    
                    processedThisTick++;
                    
                    // Move to next block
                    currentZ[0]++;
                    if (currentZ[0] > maxZ) {
                        currentZ[0] = minZ;
                        currentY[0]++;
                        if (currentY[0] > maxY) {
                            currentY[0] = minY;
                            currentX[0]++;
                        }
                    }
                }
                
                // Check if we're done
                if (currentX[0] > maxX) {
                    // Done capturing all blocks
                    challengeInitialStates.put(challengeId, states);
                    if (failedBlocks[0] > 0) {
                        getLogger().warning("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks, " + failedBlocks[0] + " failed)");
                    } else {
                        getLogger().info("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks)");
                    }
                    if (callback != null) {
                        callback.accept(true);
                    }
                } else {
                    // More blocks to process, schedule next batch (create new runnable instance)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            long startTime = System.nanoTime();
                            
                            // Process blocks until we hit time budget or finish
                            while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                                try {
                                    Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                                    BlockState state = block.getState();
                                    states.add(state);
                                } catch (Exception e) {
                                    failedBlocks[0]++;
                                    getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
                                }
                                
                                // Move to next block
                                currentZ[0]++;
                                if (currentZ[0] > maxZ) {
                                    currentZ[0] = minZ;
                                    currentY[0]++;
                                    if (currentY[0] > maxY) {
                                        currentY[0] = minY;
                                        currentX[0]++;
                                    }
                                }
                            }
                            
                            // Check if we're done
                            if (currentX[0] > maxX) {
                                // Done capturing all blocks
                                challengeInitialStates.put(challengeId, states);
                                if (failedBlocks[0] > 0) {
                                    getLogger().warning("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks, " + failedBlocks[0] + " failed)");
                                } else {
                                    getLogger().info("Captured initial state for challenge '" + challengeId + "' (" + states.size() + " blocks)");
                                }
                                if (callback != null) {
                                    // Ensure callback runs on main thread
                                    final java.util.function.Consumer<Boolean> finalCallback = callback;
                                    getLogger().fine("Calling capture callback for challenge '" + challengeId + "' (from initial batch)");
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            getLogger().fine("Executing capture callback for challenge '" + challengeId + "' (from initial batch)");
                                            finalCallback.accept(true);
                                        }
                                    }.runTask(ConradChallengesPlugin.this);
                                }
                            } else {
                                // More blocks to process, schedule next batch (create new instance)
                                scheduleNextCaptureBatch(challengeId, world, states, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, callback);
                            }
                        }
                    }.runTaskLater(ConradChallengesPlugin.this, 1L);
                }
            }
        }.runTask(this);
    }
    
    /**
     * Synchronous version for backwards compatibility (deprecated - use async version)
     * @deprecated Use captureInitialState(cfg, callback) instead
     */
    @Deprecated
    private boolean captureInitialState(ChallengeConfig cfg) {
        final boolean[] result = {false};
        final Object lock = new Object();
        captureInitialState(cfg, success -> {
            synchronized (lock) {
                result[0] = success;
                lock.notify();
            }
        });
        
        // Wait for async completion (with timeout)
        synchronized (lock) {
            try {
                lock.wait(30000); // 30 second timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result[0];
    }
    
    /**
     * Helper method to schedule the next batch of chunk loading
     */
    private void scheduleNextChunkLoadBatch(String challengeId, World world, List<BlockState> finalStates,
                                            java.util.List<String> chunksList, int[] chunkIndex, 
                                            long chunkLoadBudget) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                
                while (chunkIndex[0] < chunksList.size() && (System.nanoTime() - startTime) < chunkLoadBudget) {
                    String chunkKey = chunksList.get(chunkIndex[0]);
                    String[] parts = chunkKey.split(",");
                    int chunkX = Integer.parseInt(parts[0]);
                    int chunkZ = Integer.parseInt(parts[1]);
                    
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        world.loadChunk(chunkX, chunkZ, true);
                    }
                    chunkIndex[0]++;
                }
                
                if (chunkIndex[0] >= chunksList.size()) {
                    // All chunks loaded, start comparison phase
                    getLogger().info("All chunks pre-loaded, starting block comparison...");
                    final int[] currentIndex = {0};
                    final java.util.List<BlockState> blocksToRestore = new java.util.ArrayList<>();
                    final int[] skipped = {0};
                    final long compareTimeBudget = 12_000_000L; // 12ms per tick for comparison (increased for speed)
                    scheduleNextComparisonBatch(challengeId, world, finalStates, currentIndex, compareTimeBudget, finalStates.size(), blocksToRestore, skipped);
                } else {
                    // More chunks to load, schedule next batch
                    scheduleNextChunkLoadBatch(challengeId, world, finalStates, chunksList, chunkIndex, chunkLoadBudget);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Helper method to schedule the next batch of block comparison (phase 1: find blocks that need restoration)
     * Optimized to use fast type comparison first, then detailed BlockState comparison only when needed.
     */
    private void scheduleNextComparisonBatch(String challengeId, World world, List<BlockState> finalStates,
                                             int[] currentIndex, long compareTimeBudget, int totalBlocks,
                                             java.util.List<BlockState> blocksToRestore, int[] skipped) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                
                // Process blocks until time budget is exceeded
                while (currentIndex[0] < finalStates.size()) {
                    // Check time budget
                    if (System.nanoTime() - startTime >= compareTimeBudget) {
                        // Time budget exceeded, schedule next tick
                        scheduleNextComparisonBatch(challengeId, world, finalStates, currentIndex, compareTimeBudget, totalBlocks, blocksToRestore, skipped);
                        return;
                    }
                    
                    BlockState originalState = finalStates.get(currentIndex[0]);
                    try {
                        Location loc = originalState.getLocation();
                        
                        // Fast chunk check - skip if not loaded
                        int chunkX = loc.getBlockX() >> 4;
                        int chunkZ = loc.getBlockZ() >> 4;
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            currentIndex[0]++;
                            continue;
                        }
                        
                        Block block = world.getBlockAt(loc);
                        Material originalType = originalState.getType();
                        Material currentType = block.getType();
                        
                        boolean needsRestore = false;
                        
                        // Fast path: Check type first (cheapest operation)
                        if (currentType != originalType) {
                            needsRestore = true;
                        } else {
                            // Types match - only check BlockData for blocks that can have state changes
                            // Skip BlockData check for simple blocks to speed up comparison significantly
                            boolean needsBlockDataCheck = originalState instanceof Container ||
                                                          originalState instanceof org.bukkit.block.Sign ||
                                                          originalState instanceof org.bukkit.block.Banner ||
                                                          originalState instanceof org.bukkit.block.CreatureSpawner ||
                                                          originalType.name().contains("STAIRS") ||
                                                          originalType.name().contains("SLAB") ||
                                                          originalType.name().contains("DOOR") ||
                                                          originalType.name().contains("FENCE") ||
                                                          originalType.name().contains("GATE") ||
                                                          originalType.name().contains("TRAPDOOR") ||
                                                          originalType.name().contains("BUTTON") ||
                                                          originalType.name().contains("LEVER") ||
                                                          originalType.name().contains("BED") ||
                                                          originalType.name().contains("RAIL");
                            
                            if (needsBlockDataCheck) {
                                // Check block data for blocks that can have state
                                org.bukkit.block.data.BlockData currentData = block.getBlockData();
                                org.bukkit.block.data.BlockData originalData = originalState.getBlockData();
                                
                                // Use matches() - it's reliable for state comparison
                                if (!currentData.matches(originalData)) {
                                    needsRestore = true;
                                }
                            }
                            
                            // Check special tile entities (only if block data matches or is simple block)
                            if (!needsRestore) {
                                if (originalState instanceof Container originalContainer) {
                                    // For containers, check if contents changed
                                    BlockState currentState = block.getState();
                                    if (currentState instanceof Container currentContainer) {
                                        // Compare inventory contents
                                        if (originalContainer.getInventory() != null && currentContainer.getInventory() != null) {
                                            for (int i = 0; i < originalContainer.getInventory().getSize(); i++) {
                                                ItemStack originalItem = originalContainer.getInventory().getItem(i);
                                                ItemStack currentItem = currentContainer.getInventory().getItem(i);
                                                
                                                if (originalItem == null && currentItem == null) continue;
                                                if (originalItem == null || currentItem == null) {
                                                    needsRestore = true;
                                                    break;
                                                }
                                                if (!originalItem.equals(currentItem)) {
                                                    needsRestore = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } else if (originalState instanceof org.bukkit.block.Sign originalSign) {
                                    BlockState currentState = block.getState();
                                    if (currentState instanceof org.bukkit.block.Sign currentSign) {
                                        for (int i = 0; i < 4; i++) {
                                            if (!originalSign.getLine(i).equals(currentSign.getLine(i))) {
                                                needsRestore = true;
                                                break;
                                            }
                                        }
                                    }
                                } else if (originalState instanceof org.bukkit.block.Banner originalBanner) {
                                    BlockState currentState = block.getState();
                                    if (currentState instanceof org.bukkit.block.Banner currentBanner) {
                                        if (!originalBanner.getPatterns().equals(currentBanner.getPatterns())) {
                                            needsRestore = true;
                                        }
                                    }
                                } else if (originalState instanceof org.bukkit.block.CreatureSpawner originalSpawner) {
                                    BlockState currentState = block.getState();
                                    if (currentState instanceof org.bukkit.block.CreatureSpawner currentSpawner) {
                                        if (originalSpawner.getSpawnedType() != currentSpawner.getSpawnedType()) {
                                            needsRestore = true;
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (needsRestore) {
                            blocksToRestore.add(originalState);
                        } else {
                            skipped[0]++;
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error checking block at " + originalState.getLocation() + " for challenge '" + challengeId + "': " + e.getMessage());
                        // On error, assume block needs restoration to be safe
                        blocksToRestore.add(originalState);
                    }
                    
                    currentIndex[0]++;
                }
                
                // All blocks compared
                if (currentIndex[0] >= finalStates.size()) {
                    // Comparison phase complete, start restoration phase
                    getLogger().info("Comparison complete for challenge '" + challengeId + "': " + blocksToRestore.size() + " blocks need restoration (skipped " + skipped[0] + " unchanged blocks)");
                    
                    if (blocksToRestore.isEmpty()) {
                        // No blocks need restoration - we're done!
                        getLogger().info("No blocks need restoration for challenge '" + challengeId + "'");
                        challengeRegenerationStatus.put(challengeId, true);
                        
                        // Check if countdown already finished and teleport players if so
                        CountdownTaskWrapper wrapper = activeCountdownTasks.get(challengeId);
                        if (wrapper != null && wrapper.seconds <= 0) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    wrapper.task.run();
                                }
                            }.runTask(ConradChallengesPlugin.this);
                        }
                    } else {
                        // Start restoration phase
                        final int[] restored = {0};
                        final int[] restoreIndex = {0};
                        final long restoreTimeBudget = 15_000_000L; // 15ms per tick for restoration (increased to ensure completion)
                        scheduleNextRestoreBatch(challengeId, world, blocksToRestore, restored, restoreIndex, restoreTimeBudget, blocksToRestore.size());
                    }
                } else {
                    // More blocks to compare, schedule next tick
                    scheduleNextComparisonBatch(challengeId, world, finalStates, currentIndex, compareTimeBudget, totalBlocks, blocksToRestore, skipped);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Helper method to schedule the next batch of block restoration (phase 2: restore only changed blocks)
     */
    private void scheduleNextRestoreBatch(String challengeId, World world, List<BlockState> blocksToRestore,
                                          int[] restored, int[] currentIndex, long timeBudgetNs, int totalToRestore) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                
                // Process blocks until time budget is exceeded
                while (currentIndex[0] < blocksToRestore.size()) {
                    // Check time budget
                    if (System.nanoTime() - startTime >= timeBudgetNs) {
                        // Time budget exceeded, schedule next tick
                        scheduleNextRestoreBatch(challengeId, world, blocksToRestore, restored, currentIndex, timeBudgetNs, totalToRestore);
                        return;
                    }
                    
                    BlockState originalState = blocksToRestore.get(currentIndex[0]);
                    try {
                        Location loc = originalState.getLocation();
                        
                        // Ensure chunk is loaded - reload if needed
                        int chunkX = loc.getBlockX() >> 4;
                        int chunkZ = loc.getBlockZ() >> 4;
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            // Chunk unloaded - reload it
                            world.loadChunk(chunkX, chunkZ, true);
                            // If still not loaded, skip this block and try again next tick
                            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                                currentIndex[0]++;
                                continue;
                            }
                        }
                        
                        Block block = world.getBlockAt(loc);
                        Material originalType = originalState.getType();
                        org.bukkit.block.data.BlockData originalData = originalState.getBlockData();
                        
                        // Check if this is a multi-block structure (doors, beds, etc.)
                        // These need applyPhysics=true to properly link both halves
                        boolean isMultiBlock = originalType.name().contains("DOOR") || 
                                             originalType.name().contains("BED") ||
                                             originalType.name().contains("BIG_DRIPLEAF") ||
                                             originalType.name().contains("PISTON_HEAD") ||
                                             originalType.name().contains("TALL_GRASS") ||
                                             originalType.name().contains("SUNFLOWER") ||
                                             originalType.name().contains("LILAC") ||
                                             originalType.name().contains("ROSE_BUSH") ||
                                             originalType.name().contains("PEONY") ||
                                             originalType.name().contains("TALL_SEAGRASS");
                        
                        if (isMultiBlock) {
                            // For multi-block structures (doors, beds, etc.), use BlockState.update() with applyPhysics=true
                            // This ensures both halves are properly linked together
                            // Must use BlockState path to properly restore multi-block relationships
                            BlockState newState = block.getState();
                            newState.setType(originalType);
                            newState.setBlockData(originalData);
                            
                            // Use update(true, true) - first true = force update, second true = apply physics
                            // This will automatically create/link the other half of doors/beds
                            // The physics update ensures the halves connect properly
                            newState.update(true, true);
                        } else {
                            // Fast path: Use direct Block.setType() and setBlockData() with applyPhysics=false
                            // This bypasses expensive BlockState.update() overhead for simple blocks
                            block.setType(originalType, false);  // false = no physics updates
                            block.setBlockData(originalData, false);  // false = no physics updates
                            
                            // For special tile entities, we need to use BlockState to restore data
                            if (originalState instanceof Container || 
                                originalState instanceof org.bukkit.block.Sign ||
                                originalState instanceof org.bukkit.block.Banner ||
                                originalState instanceof org.bukkit.block.CreatureSpawner ||
                                originalState instanceof org.bukkit.block.CommandBlock) {
                                
                                BlockState newState = block.getState();
                                
                                // Restore container contents if applicable
                                if (originalState instanceof Container originalContainer && newState instanceof Container newContainer) {
                                    newContainer.getInventory().clear();
                                    if (originalContainer.getInventory() != null) {
                                        for (int j = 0; j < originalContainer.getInventory().getSize(); j++) {
                                            ItemStack item = originalContainer.getInventory().getItem(j);
                                            if (item != null && item.getType() != Material.AIR) {
                                                newContainer.getInventory().setItem(j, item.clone());
                                            }
                                        }
                                    }
                                }
                                
                                // Copy tile entity data for special blocks
                                if (originalState instanceof org.bukkit.block.Sign originalSign && 
                                    newState instanceof org.bukkit.block.Sign newSign) {
                                    for (int j = 0; j < 4; j++) {
                                        newSign.setLine(j, originalSign.getLine(j));
                                    }
                                    try {
                                        if (originalSign.getColor() != null) {
                                            newSign.setColor(originalSign.getColor());
                                        }
                                    } catch (Exception e) {
                                        // Color not supported
                                    }
                                    try {
                                        newSign.setGlowingText(originalSign.isGlowingText());
                                    } catch (Exception e) {
                                        // Glowing not supported
                                    }
                                } else if (originalState instanceof org.bukkit.block.Banner originalBanner && 
                                           newState instanceof org.bukkit.block.Banner newBanner) {
                                    newBanner.setPatterns(originalBanner.getPatterns());
                                } else if (originalState instanceof org.bukkit.block.CreatureSpawner originalSpawner && 
                                           newState instanceof org.bukkit.block.CreatureSpawner newSpawner) {
                                    newSpawner.setSpawnedType(originalSpawner.getSpawnedType());
                                    newSpawner.setDelay(originalSpawner.getDelay());
                                    try {
                                        newSpawner.setMinSpawnDelay(originalSpawner.getMinSpawnDelay());
                                        newSpawner.setMaxSpawnDelay(originalSpawner.getMaxSpawnDelay());
                                        newSpawner.setSpawnCount(originalSpawner.getSpawnCount());
                                        newSpawner.setMaxNearbyEntities(originalSpawner.getMaxNearbyEntities());
                                        newSpawner.setRequiredPlayerRange(originalSpawner.getRequiredPlayerRange());
                                        newSpawner.setSpawnRange(originalSpawner.getSpawnRange());
                                    } catch (Exception e) {
                                        // Some methods might not be available
                                    }
                                } else if (originalState instanceof org.bukkit.block.CommandBlock originalCmd && 
                                           newState instanceof org.bukkit.block.CommandBlock newCmd) {
                                    newCmd.setCommand(originalCmd.getCommand());
                                    newCmd.setName(originalCmd.getName());
                                }
                                
                                // Only update for tile entities that need it
                                newState.update(true, false);
                            }
                        }
                        restored[0]++;
                    } catch (Exception e) {
                        getLogger().warning("Error restoring block at " + originalState.getLocation() + " for challenge '" + challengeId + "': " + e.getMessage());
                    }
                    
                    currentIndex[0]++;
                }
                
                // All blocks restored
                if (currentIndex[0] >= blocksToRestore.size()) {
                    // Done!
                    if (restored[0] < totalToRestore) {
                        getLogger().warning("Regeneration incomplete for challenge '" + challengeId + "': Only restored " + restored[0] + "/" + totalToRestore + " blocks. Some blocks may have been skipped due to unloaded chunks.");
                    } else {
                        getLogger().info("Regenerated challenge area for '" + challengeId + "' (" + restored[0] + "/" + totalToRestore + " blocks restored)");
                    }
                    
                    // Mark regeneration as complete
                    challengeRegenerationStatus.put(challengeId, true);
                    
                    // Check if countdown already finished and teleport players if so
                    CountdownTaskWrapper wrapper = activeCountdownTasks.get(challengeId);
                    if (wrapper != null && wrapper.seconds <= 0) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                wrapper.task.run();
                            }
                        }.runTask(ConradChallengesPlugin.this);
                    }
                } else {
                    // More blocks to restore, schedule next tick
                    scheduleNextRestoreBatch(challengeId, world, blocksToRestore, restored, currentIndex, timeBudgetNs, totalToRestore);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Regenerates the challenge area to its initial state.
     * This should be called before players are teleported into the challenge.
     * Uses a two-phase approach: first compares blocks to find what changed,
     * then only restores the blocks that actually need restoration (much more efficient).
     * 
     * @param cfg The challenge configuration
     */
    private void regenerateChallengeArea(ChallengeConfig cfg) {
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            return; // No regeneration area set
        }
        
        List<BlockState> states = challengeInitialStates.get(cfg.id);
        if (states == null || states.isEmpty()) {
            // Cannot regenerate without initial state - don't block the main thread!
            getLogger().warning("Cannot regenerate challenge '" + cfg.id + "' - no initial state captured! Please set the regeneration area first.");
            // Mark as complete so players aren't stuck waiting
            challengeRegenerationStatus.put(cfg.id, true);
            return;
        }
        
        World world = cfg.regenerationCorner1.getWorld();
        if (world == null) {
            getLogger().warning("Cannot regenerate challenge '" + cfg.id + "' - world is not loaded!");
            challengeRegenerationStatus.put(cfg.id, true);
            return;
        }
        
        final String challengeId = cfg.id;
        
        // Check if regeneration is already in progress - don't start a new one
        // false = in progress, true = complete, null = not started
        Boolean status = challengeRegenerationStatus.get(challengeId);
        if (status != null && status == false) {
            // Regeneration already in progress (status is false), skip duplicate call
            getLogger().fine("Regeneration already in progress for challenge '" + challengeId + "', skipping duplicate call");
            return;
        }
        
        final List<BlockState> finalStates = new ArrayList<>(states);
        
        // Mark regeneration as in progress
        challengeRegenerationStatus.put(challengeId, false);
        
        int totalBlocks = finalStates.size();
        getLogger().info("Starting regeneration for challenge '" + challengeId + "' (" + totalBlocks + " blocks to check)");
        
        // Pre-load chunks in time-budgeted batches on main thread, then start comparison phase
        // Collect unique chunks that need to be loaded
        final java.util.Set<String> chunksToLoad = new java.util.HashSet<>();
        for (BlockState state : finalStates) {
            Location loc = state.getLocation();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                chunksToLoad.add(chunkX + "," + chunkZ);
            }
        }
        
        if (chunksToLoad.isEmpty()) {
            // All chunks already loaded, start comparison phase immediately
            final int[] currentIndex = {0};
            final java.util.List<BlockState> blocksToRestore = new java.util.ArrayList<>();
            final int[] skipped = {0};
            final long compareTimeBudget = 12_000_000L; // 12ms per tick for comparison (increased for speed)
            scheduleNextComparisonBatch(challengeId, world, finalStates, currentIndex, compareTimeBudget, totalBlocks, blocksToRestore, skipped);
        } else {
            // Pre-load chunks in time-budgeted batches, then start comparison phase
            getLogger().info("Pre-loading " + chunksToLoad.size() + " chunks for regeneration...");
            final java.util.List<String> chunksList = new java.util.ArrayList<>(chunksToLoad);
            final int[] chunkIndex = {0};
            final long chunkLoadBudget = 8_000_000L; // 8ms per tick for chunk loading (increased for speed)
            scheduleNextChunkLoadBatch(challengeId, world, finalStates, chunksList, chunkIndex, chunkLoadBudget);
        }
    }

    // ====== EDIT MODE HELPERS ======

    /**
     * Clones a ChallengeConfig for backup purposes.
     * 
     * @param original The original config to clone
     * @return A deep copy of the config
     */
    private ChallengeConfig cloneChallengeConfig(ChallengeConfig original) {
        ChallengeConfig clone = new ChallengeConfig(original.id);
        clone.bookTitle = original.bookTitle;
        clone.destination = original.destination != null ? original.destination.clone() : null;
        clone.cooldownSeconds = original.cooldownSeconds;
        clone.completionType = original.completionType;
        clone.bossRequirement = original.bossRequirement != null ? 
            new BossRequirement(original.bossRequirement.type, original.bossRequirement.name) : null;
        clone.timerSeconds = original.timerSeconds;
        clone.itemRequirement = original.itemRequirement != null ?
            new ItemRequirement(original.itemRequirement.material, original.itemRequirement.amount, original.itemRequirement.consume) : null;
        clone.speedTiers = new ArrayList<>();
        if (original.speedTiers != null) {
            for (SpeedTier tier : original.speedTiers) {
                clone.speedTiers.add(new SpeedTier(tier.name, tier.maxSeconds, new ArrayList<>(tier.rewardCommands)));
            }
        }
        clone.fallbackRewardCommands = new ArrayList<>(original.fallbackRewardCommands);
        clone.difficultyTierRewards = new HashMap<>();
        for (Map.Entry<String, List<TierReward>> entry : original.difficultyTierRewards.entrySet()) {
            List<TierReward> clonedRewards = new ArrayList<>();
            for (TierReward reward : entry.getValue()) {
                TierReward cloned = new TierReward(reward.type, reward.chance);
                cloned.material = reward.material;
                cloned.amount = reward.amount;
                cloned.itemStack = reward.itemStack != null ? reward.itemStack.clone() : null;
                cloned.command = reward.command;
                clonedRewards.add(cloned);
            }
            clone.difficultyTierRewards.put(entry.getKey(), clonedRewards);
        }
        clone.topTimeRewards = new ArrayList<>();
        for (TierReward reward : original.topTimeRewards) {
            TierReward cloned = new TierReward(reward.type, reward.chance);
            cloned.material = reward.material;
            cloned.amount = reward.amount;
            cloned.itemStack = reward.itemStack != null ? reward.itemStack.clone() : null;
            cloned.command = reward.command;
            clone.topTimeRewards.add(cloned);
        }
        clone.maxPartySize = original.maxPartySize;
        clone.timeLimitSeconds = original.timeLimitSeconds;
        clone.timeLimitWarnings = new ArrayList<>(original.timeLimitWarnings);
        clone.regenerationCorner1 = original.regenerationCorner1 != null ? original.regenerationCorner1.clone() : null;
        clone.regenerationCorner2 = original.regenerationCorner2 != null ? original.regenerationCorner2.clone() : null;
        clone.regenerationAutoExtendY = original.regenerationAutoExtendY;
        return clone;
    }

    /**
     * Restores a ChallengeConfig from a backup.
     * 
     * @param target The config to restore
     * @param backup The backup to restore from
     */
    private void restoreChallengeConfig(ChallengeConfig target, ChallengeConfig backup) {
        target.bookTitle = backup.bookTitle;
        target.destination = backup.destination != null ? backup.destination.clone() : null;
        target.cooldownSeconds = backup.cooldownSeconds;
        target.completionType = backup.completionType;
        target.bossRequirement = backup.bossRequirement != null ? 
            new BossRequirement(backup.bossRequirement.type, backup.bossRequirement.name) : null;
        target.timerSeconds = backup.timerSeconds;
        target.itemRequirement = backup.itemRequirement != null ?
            new ItemRequirement(backup.itemRequirement.material, backup.itemRequirement.amount, backup.itemRequirement.consume) : null;
        target.speedTiers.clear();
        if (backup.speedTiers != null) {
            for (SpeedTier tier : backup.speedTiers) {
                target.speedTiers.add(new SpeedTier(tier.name, tier.maxSeconds, new ArrayList<>(tier.rewardCommands)));
            }
        }
        target.fallbackRewardCommands.clear();
        target.fallbackRewardCommands.addAll(backup.fallbackRewardCommands);
        target.difficultyTierRewards.clear();
        for (Map.Entry<String, List<TierReward>> entry : backup.difficultyTierRewards.entrySet()) {
            List<TierReward> restoredRewards = new ArrayList<>();
            for (TierReward reward : entry.getValue()) {
                TierReward restored = new TierReward(reward.type, reward.chance);
                restored.material = reward.material;
                restored.amount = reward.amount;
                restored.itemStack = reward.itemStack != null ? reward.itemStack.clone() : null;
                restored.command = reward.command;
                restoredRewards.add(restored);
            }
            target.difficultyTierRewards.put(entry.getKey(), restoredRewards);
        }
        target.topTimeRewards.clear();
        for (TierReward reward : backup.topTimeRewards) {
            TierReward restored = new TierReward(reward.type, reward.chance);
            restored.material = reward.material;
            restored.amount = reward.amount;
            restored.itemStack = reward.itemStack != null ? reward.itemStack.clone() : null;
            restored.command = reward.command;
            target.topTimeRewards.add(restored);
        }
        target.maxPartySize = backup.maxPartySize;
        target.timeLimitSeconds = backup.timeLimitSeconds;
        target.timeLimitWarnings.clear();
        target.timeLimitWarnings.addAll(backup.timeLimitWarnings);
        target.regenerationCorner1 = backup.regenerationCorner1 != null ? backup.regenerationCorner1.clone() : null;
        target.regenerationCorner2 = backup.regenerationCorner2 != null ? backup.regenerationCorner2.clone() : null;
        target.regenerationAutoExtendY = backup.regenerationAutoExtendY;
    }
    
    /**
     * Cleans up the difficulty tier for a challenge if no players are active in it.
     * 
     * @param challengeId The challenge ID to check
     */
    /**
     * Gets all party members (UUIDs) for a given challenge.
     * Includes both active players and disconnected players in grace period.
     */
    private List<UUID> getPartyMembersForChallenge(String challengeId) {
        List<UUID> members = new ArrayList<>();
        
        // Add active players
        for (Map.Entry<UUID, String> entry : activeChallenges.entrySet()) {
            if (entry.getValue().equals(challengeId)) {
                members.add(entry.getKey());
            }
        }
        
        // Add disconnected players still in grace period
        for (Map.Entry<UUID, DisconnectInfo> entry : disconnectedPlayers.entrySet()) {
            if (entry.getValue().challengeId.equals(challengeId)) {
                members.add(entry.getKey());
            }
        }
        
        return members;
    }
    
    /**
     * Handles when a disconnected player's 30s grace period expires.
     */
    private void handleDisconnectTimeout(UUID uuid, String challengeId, boolean isPartyChallenge) {
        // Remove from disconnected players
        DisconnectInfo disconnectInfo = disconnectedPlayers.remove(uuid);
        if (disconnectInfo == null) {
            return; // Already handled
        }
        
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) {
            return;
        }
        
        Player player = Bukkit.getPlayer(uuid);
        
        if (isPartyChallenge) {
            // Party challenge - check if all party members are disconnected
            // Get all players who were in this challenge (active + disconnected)
            Set<UUID> allPartyMembers = new HashSet<>();
            
            // Add active players
            for (Map.Entry<UUID, String> entry : activeChallenges.entrySet()) {
                if (entry.getValue().equals(challengeId)) {
                    allPartyMembers.add(entry.getKey());
                }
            }
            
            // Add disconnected players (including the one we're checking)
            for (Map.Entry<UUID, DisconnectInfo> entry : disconnectedPlayers.entrySet()) {
                if (entry.getValue().challengeId.equals(challengeId)) {
                    allPartyMembers.add(entry.getKey());
                }
            }
            
            boolean allDisconnected = true;
            
            for (UUID memberUuid : allPartyMembers) {
                if (memberUuid.equals(uuid)) {
                    continue; // Skip the player we're checking (they're definitely disconnected now)
                }
                
                // Check if member is still active
                if (activeChallenges.containsKey(memberUuid)) {
                    allDisconnected = false;
                    break;
                }
                
                // Check if member is still in grace period
                DisconnectInfo otherDisconnect = disconnectedPlayers.get(memberUuid);
                if (otherDisconnect != null && otherDisconnect.timeoutTask != null && !otherDisconnect.timeoutTask.isCancelled()) {
                    // Other member is still in grace period - not fully disconnected yet
                    allDisconnected = false;
                    break;
                }
            }
            
            if (allDisconnected) {
                // All party members disconnected - cancel challenge and process queue
                cleanupChallengeForDisconnect(challengeId);
                
                // Teleport disconnected player to exit if online
                if (player != null && player.isOnline()) {
                    handleDisconnectedPlayerReconnect(player, challengeId, cfg);
                }
                
                getLogger().info("All party members disconnected from challenge '" + challengeId + "'. Challenge cancelled.");
            } else {
                // Other party members still active - just teleport this player to exit if online
                if (player != null && player.isOnline()) {
                    handleDisconnectedPlayerReconnect(player, challengeId, cfg);
                }
                
                getLogger().info("Player " + (player != null ? player.getName() : uuid.toString()) + " disconnected from party challenge '" + challengeId + "' after grace period. Other members continue.");
            }
        } else {
            // Solo challenge - cancel challenge and process queue
            cleanupChallengeForDisconnect(challengeId);
            
            // Teleport player to exit if online
            if (player != null && player.isOnline()) {
                handleDisconnectedPlayerReconnect(player, challengeId, cfg);
            }
            
            getLogger().info("Solo player " + (player != null ? player.getName() : uuid.toString()) + " disconnected from challenge '" + challengeId + "' after grace period. Challenge cancelled.");
        }
    }
    
    /**
     * Cleans up challenge state when all players have disconnected.
     */
    private void cleanupChallengeForDisconnect(String challengeId) {
        // Remove all disconnected players for this challenge
        disconnectedPlayers.entrySet().removeIf(entry -> entry.getValue().challengeId.equals(challengeId));
        
        // Clean up difficulty tier
        cleanupChallengeDifficultyIfEmpty(challengeId);
        
        // Process queue if challenge is now available
        if (!isChallengeLocked(challengeId)) {
            processQueue(challengeId);
        }
    }
    
    /**
     * Handles reconnecting a player who disconnected after grace period or when challenge was cancelled.
     */
    private void handleDisconnectedPlayerReconnect(Player player, String challengeId, ChallengeConfig cfg) {
        // Teleport to challenge exit (spawn location)
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.RED + "You disconnected from the challenge. You have been returned to spawn.");
        } else {
            // Fallback to challenge destination if spawn not set
            if (cfg.destination != null) {
                player.teleport(cfg.destination);
                player.sendMessage(ChatColor.RED + "You disconnected from the challenge.");
            }
        }
    }
    
    private void cleanupChallengeDifficultyIfEmpty(String challengeId) {
        // Check if any players are still active in this challenge
        boolean hasActivePlayers = activeChallenges.values().stream()
            .anyMatch(id -> id.equals(challengeId));
        
        if (!hasActivePlayers) {
            activeChallengeDifficulties.remove(challengeId);
        }
    }
    
    /**
     * Checks if a location is within a challenge's regeneration area.
     * 
     * @param location The location to check
     * @param cfg The challenge configuration
     * @return true if the location is within the regeneration area
     */
    private boolean isLocationInRegenerationArea(Location location, ChallengeConfig cfg) {
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            return false;
        }
        
        if (!location.getWorld().equals(cfg.regenerationCorner1.getWorld()) ||
            !location.getWorld().equals(cfg.regenerationCorner2.getWorld())) {
            return false;
        }
        
        int minX = Math.min(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int maxX = Math.max(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            // Auto-extend from bedrock to build height
            World world = cfg.regenerationCorner1.getWorld();
            if (world == null) return false;
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1; // getMaxHeight() is exclusive
        } else {
            // Use Y coordinates from corners
            minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
            maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
        }
        int minZ = Math.min(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        int maxZ = Math.max(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    
    /**
     * Handles MythicMobs spawn events to apply difficulty tier level modifiers.
     * This method uses reflection to avoid hard dependency on MythicMobs.
     * Registers a listener for MythicMobs spawn events if the plugin is available.
     */
    private void setupMythicMobsListener() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return; // MythicMobs not installed
        }
        
        // Try to register a listener for MythicMobs spawn events using reflection
        try {
            // Get the MythicMobs event class
            Class<?> mythicSpawnEventClass = Class.forName("io.lumine.mythic.bukkit.events.MythicMobSpawnEvent");
            
            // Verify it extends Event
            if (!org.bukkit.event.Event.class.isAssignableFrom(mythicSpawnEventClass)) {
                getLogger().warning("MythicMobs spawn event class does not extend Event");
                return;
            }
            
            // Cast to the correct type for registerEvent
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> eventClass = (Class<? extends org.bukkit.event.Event>) mythicSpawnEventClass;
            
            // Register the event listener
            getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.HIGH,
                (listener, event) -> {
                    try {
                        handleMythicMobSpawn(event);
                    } catch (Exception e) {
                        getLogger().warning("Error handling MythicMobs spawn event: " + e.getMessage());
                    }
                },
                this
            );
            
            getLogger().info("MythicMobs integration enabled - difficulty tiers will modify mob levels");
        } catch (Exception e) {
            getLogger().info("MythicMobs found but could not register listener (version may differ): " + e.getMessage());
        }
    }
    
    /**
     * Handles a MythicMobs spawn event to apply difficulty tier level modifiers.
     */
    private void handleMythicMobSpawn(Object event) {
        try {
            // Get the entity from the event
            Method getEntityMethod = event.getClass().getMethod("getEntity");
            Entity entity = (Entity) getEntityMethod.invoke(event);
            
            if (entity == null || !(entity instanceof LivingEntity)) {
                return;
            }
            
            Location spawnLocation = entity.getLocation();
            
            // Check each active challenge to see if spawn is in its regeneration area
            for (Map.Entry<String, String> entry : activeChallengeDifficulties.entrySet()) {
                String challengeId = entry.getKey();
                String tier = entry.getValue();
                
                ChallengeConfig cfg = challenges.get(challengeId);
                if (cfg == null) continue;
                
                // Check if spawn location is in this challenge's regeneration area
                if (!isLocationInRegenerationArea(spawnLocation, cfg)) {
                    continue;
                }
                
                // Get the level modifier for this tier
                Integer modifier = difficultyTierModifiers.get(tier);
                if (modifier == null || modifier == 0) {
                    continue; // No modifier or easy tier
                }
                
                // Try to get the spawner and its base level
                int baseLevel = 1; // Default
                try {
                    Method getSpawnerMethod = event.getClass().getMethod("getSpawner");
                    Object spawner = getSpawnerMethod.invoke(event);
                    
                    if (spawner != null) {
                        Method getMobLevelMethod = spawner.getClass().getMethod("getMobLevel");
                        baseLevel = (Integer) getMobLevelMethod.invoke(spawner);
                    }
                } catch (Exception e) {
                    // Try to get level from entity metadata
                    try {
                        Method getLevelMethod = entity.getClass().getMethod("getLevel");
                        baseLevel = (Integer) getLevelMethod.invoke(entity);
                    } catch (Exception ignored) {
                        // Use default
                    }
                }
                
                // Calculate new level
                int newLevel = baseLevel + modifier;
                
                // Apply the level using MythicMobs API
                try {
                    Class<?> mythicAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                    Object mythicPlugin = mythicAPI.getMethod("inst").invoke(null);
                    Object mobManager = mythicAPI.getMethod("getMobManager").invoke(mythicPlugin);
                    Method applyLevelMethod = mobManager.getClass().getMethod("applyLevel", Entity.class, int.class);
                    applyLevelMethod.invoke(mobManager, entity, newLevel);
                } catch (Exception e) {
                    // Try alternative method
                    try {
                        Method setLevelMethod = entity.getClass().getMethod("setLevel", int.class);
                        setLevelMethod.invoke(entity, newLevel);
                    } catch (Exception e2) {
                        getLogger().fine("Could not apply level modifier to MythicMob (version may differ)");
                    }
                }
                
                // Only apply to one challenge (the first match)
                break;
            }
        } catch (Exception e) {
            getLogger().warning("Error processing MythicMobs spawn event: " + e.getMessage());
        }
    }

    /**
     * Exits edit mode for a player (cleanup helper).
     * 
     * @param player The player exiting edit mode
     * @param challengeId The challenge ID being edited
     * @param restore Whether to restore from backup
     */
    private void exitEditMode(Player player, String challengeId, boolean restore) {
        UUID uuid = player.getUniqueId();
        
        if (restore) {
            ChallengeConfig cfg = challenges.get(challengeId);
            ChallengeConfig backup = challengeEditBackups.get(challengeId);
            if (backup != null && cfg != null) {
                restoreChallengeConfig(cfg, backup);
                saveChallengeToConfig(cfg);
                
                // Restore block states
                List<BlockState> stateBackup = challengeEditStateBackups.get(challengeId);
                if (stateBackup != null) {
                    challengeInitialStates.put(challengeId, new ArrayList<>(stateBackup));
                    regenerateChallengeArea(cfg);
                }
            }
        }
        
        Location originalLoc = editorOriginalLocations.remove(uuid);
        challengeEditors.remove(challengeId);
        challengeEditBackups.remove(challengeId);
        challengeEditStateBackups.remove(challengeId);
        
        if (originalLoc != null) {
            player.teleport(originalLoc);
        }
        
        // Process queue
        if (!isChallengeLocked(challengeId)) {
            processQueue(challengeId);
        }
    }

}

