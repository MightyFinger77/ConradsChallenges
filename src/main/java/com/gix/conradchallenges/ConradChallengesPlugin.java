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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
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

import com.gix.conradchallenges.ChallengeScriptTypes.ChallengeScriptNode;
import com.gix.conradchallenges.ChallengeScriptTypes.ScriptFunctionType;
import com.gix.conradchallenges.ChallengeScriptTypes.ScriptTrigger;

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
        "setregenerationarea", "setregenerationarea2", "setregenerationarea3", "setregenerationarea4",
        "clearregenerationarea", "captureregeneration", "setautoregenerationy",
        "setchallengearea", "setchallengearea2", "setchallengearea3", "setchallengearea4",
        "clearchallengearea", "areasync",
        "blockitem", "unblockitem",
        "blockbreak", "allowbreak", "disallowbreak",
        "loottable", "chestignore", "loottype",
        "functiontool",
        "test",
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

    /** Package-visible for ChallengeScriptManager. */
    static class ChallengeConfig {
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

        // Regeneration areas (multiple boxes). Each element is length-2: [corner1, corner2]. Index 0 = area 1.
        List<Location[]> regenerationAreas;
        // Legacy single area (kept in sync with first element of regenerationAreas for backward compat)
        Location regenerationCorner1;
        Location regenerationCorner2;
        // If true, Y range extends from bedrock to build height (ignores corner Y values)
        boolean regenerationAutoExtendY;

        // Challenge areas (multiple boxes). Each element is length-2: [corner1, corner2]. Index 0 = area 1.
        List<Location[]> challengeAreas;
        // Legacy single area (kept in sync with first element of challengeAreas)
        Location challengeCorner1;
        Location challengeCorner2;

        // Lockdown: if true, challenge is disabled and players cannot start it
        boolean lockedDown;

        // Blocked items: materials that cannot be taken into this challenge (moved to ender chest on entry)
        Set<Material> blockedItems;
        // Chests that ignore loot tables: use normal regen (save/restore contents). Keys are "world:x,y,z" for each block (both halves for double chests).
        Set<String> ignoredChestKeys;
        // Chests that use legendary loot tables (canonical keys). If not in this set, chest uses normal loot tables.
        Set<String> legendaryChestKeys;

        // Block breaking: when true, challengers are restricted (can only break blocks they placed or in break-allowed list); when false, challengers can break whatever
        boolean blockBreakEnabled;
        // Materials that challengers are allowed to break when blockBreakEnabled is true (in addition to blocks they placed)
        Set<Material> breakAllowedMaterials;

        // Script nodes (function tool): block/location + trigger + function + options
        List<ChallengeScriptNode> scriptNodes;

        // Pass-through (invisible wall): block keys "world:x,y,z" that players can walk through when near
        Set<String> passThroughBlockKeys;

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
            this.blockedItems = new HashSet<>();
            this.ignoredChestKeys = new HashSet<>();
            this.legendaryChestKeys = new HashSet<>();
            this.blockBreakEnabled = false;
            this.breakAllowedMaterials = new HashSet<>();
            this.scriptNodes = new ArrayList<>();
            this.passThroughBlockKeys = new HashSet<>();
            this.regenerationAreas = new ArrayList<>();
            this.challengeAreas = new ArrayList<>();
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

    /** Loot table: name, size (single/double chest), icon, contents, type (normal/legendary), and which challenges it's assigned to. */
    private static class LootTableData {
        final String id;
        String name;
        LootTableSize size; // SINGLE = 27 slots, DOUBLE = 54
        Material iconMaterial; // icon used in the list GUI
        ItemStack[] contents; // length 27 or 54
        LootTableType lootType; // NORMAL = used by normal chests, LEGENDARY = used by legendary chests
        final Set<String> assignedChallengeIds = new HashSet<>();
        long createdAt; // for "Created" sort (newer to older)

        LootTableData(String id) {
            this.id = id;
            this.name = "Unnamed";
            this.size = LootTableSize.SINGLE;
            this.iconMaterial = Material.CHEST;
            this.contents = new ItemStack[27];
            this.lootType = LootTableType.NORMAL;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private enum LootTableType {
        NORMAL,
        LEGENDARY
    }

    private enum LootTableSize {
        SINGLE(27),
        DOUBLE(54);
        final int slots;
        LootTableSize(int slots) { this.slots = slots; }
    }

    /** Tracks which loottable GUI screen a player has open. */
    private static class LootTableGuiContext {
        final LootTableScreen screen;
        final String loottableId; // when screen is OPTIONS, DELETE_CONFIRM, ASSIGN, EDIT_CONTENTS, CHALLENGE_LIST
        final List<String> listOrder; // when screen is LIST or ASSIGN: ordered ids
        final int listPage; // when screen is LIST: current page (0-based)

        LootTableGuiContext(LootTableScreen screen, String loottableId) {
            this.screen = screen;
            this.loottableId = loottableId;
            this.listOrder = null;
            this.listPage = 0;
        }
        LootTableGuiContext(LootTableScreen screen, String loottableId, List<String> listOrder) {
            this.screen = screen;
            this.loottableId = loottableId;
            this.listOrder = listOrder;
            this.listPage = 0;
        }
        LootTableGuiContext(LootTableScreen screen, String loottableId, List<String> listOrder, int listPage) {
            this.screen = screen;
            this.loottableId = loottableId;
            this.listOrder = listOrder;
            this.listPage = listPage;
        }
    }

    private static final int LOOTTABLE_LIST_SLOTS = 54;
    private static final int LOOTTABLE_LIST_CONTENT_PER_PAGE = 45; // 5 rows
    private static final int LOOTTABLE_LIST_FILTER_SLOT = 45;     // bottom-left of nav bar
    private static final int LOOTTABLE_LIST_BACK_SLOT = 53;       // last slot (bottom-right)
    private static final int LOOTTABLE_LIST_PREV_SLOT = 47;
    private static final int LOOTTABLE_LIST_NEXT_SLOT = 49;
    private final Map<UUID, Integer> lastLoottableListPage = new HashMap<>();
    private final Map<UUID, LootTableListFilterMode> lastLoottableListFilterMode = new HashMap<>();
    private final Map<UUID, String> lastLoottableListFilterParam = new HashMap<>(); // TYPE: "NORMAL"|"LEGENDARY", CHALLENGE: challengeId

    private enum LootTableScreen {
        MAIN,                 // add new / edit list
        LIST,                 // list of loottables (click to open options)
        LIST_FILTER,          // filter menu: Created, Name, Type, Challenge
        LIST_FILTER_TYPE,      // Type sub-menu: Normal, Legendary
        LIST_FILTER_CHALLENGE, // Challenge sub-menu: list of challenges
        OPTIONS,              // Size, Edit, Delete, Rename, Assign for one table
        DELETE_CONFIRM,       // yes / no
        ASSIGN,               // list of challenges to toggle assignment
        EDIT_CONTENTS,        // the actual chest inventory to edit items
        CHALLENGE_LIST        // pick challenge to assign (for Assign from OPTIONS)
    }

    /** How the loottable list is sorted/filtered. */
    private enum LootTableListFilterMode {
        CREATED,  // newer to older
        NAME,     // ABC by name
        TYPE,     // filter by type (param: NORMAL or LEGENDARY), then ABC
        CHALLENGE // filter by challenge (param: challengeId), then ABC
    }

    /** Tracks an open instance chest so we can save on close. */
    private static class InstanceChestOpenContext {
        final String challengeId;
        final String locationKey;
        final int size;

        InstanceChestOpenContext(String challengeId, String locationKey, int size) {
            this.challengeId = challengeId;
            this.locationKey = locationKey;
            this.size = size;
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
    
    // Track last spawner reset time per challenge to prevent duplicate resets
    private final Map<String, Long> lastSpawnerResetTime = new HashMap<>();
    
    // Store original spawner levels so we can revert them (challenge ID -> spawner object -> original level)
    private final Map<String, Map<Object, Integer>> originalSpawnerLevels = new HashMap<>();
    // Track which players are in which countdown (challenge ID -> list of players)
    private final Map<String, List<Player>> countdownPlayers = new HashMap<>();
    // Track bossbars for countdown display (player UUID -> bossbar)
    private final Map<UUID, BossBar> playerCountdownBossBars = new HashMap<>();

    // Anger system: when GateKeeper is mad at a player
    private final Map<UUID, Long> angryUntil = new HashMap<>();
    private final Set<UUID> requiresApology = new HashSet<>();

    // Blocked-item overflow: items that couldn't fit in ender chest, to be returned on complete/exit (player UUID -> challenge ID -> list of ItemStacks)
    private final Map<UUID, Map<String, List<ItemStack>>> blockedItemOverflowByPlayer = new HashMap<>();

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
    private Material functionToolMaterial = Material.BLAZE_ROD;
    private String functionToolDisplayName = "§6Challenge Function Tool";
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
    
    // Regeneration: Store container inventories separately (challenge ID -> map of location string to inventory items array)
    // This is necessary because BlockState snapshots don't reliably preserve container inventories
    private final Map<String, Map<String, ItemStack[]>> challengeContainerInventories = new HashMap<>();
    
    // Regeneration: Store container NBT data for exact restoration (WorldEdit-style approach)
    // challenge ID -> map of location string to serialized NBT data (byte array)
    private final Map<String, Map<String, byte[]>> challengeContainerNBT = new HashMap<>();
    
    // NMS reflection cache for performance (only initialized once)
    private java.lang.reflect.Method getTileEntityMethod;
    private java.lang.reflect.Method saveMethod;
    private java.lang.reflect.Method loadMethod;
    private boolean nmsAvailable = false;
    
    // Edit mode: Track which challenges are being edited (challenge ID -> set of editor UUIDs; multiple admins can edit)
    private final Map<String, Set<UUID>> challengeEditors = new HashMap<>();
    
    // World aliases for display names
    private final Map<String, String> worldAliases = new HashMap<>();
    private final Map<String, String> challengeAliases = new HashMap<>();
    // Edit mode: Store original locations before edit (editor UUID -> original location)
    private final Map<UUID, Location> editorOriginalLocations = new HashMap<>();
    // Test mode: Store original locations before /challenge test (tester UUID -> original location)
    private final Map<UUID, Location> testOriginalLocations = new HashMap<>();
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
    
    // Lives system: Track remaining lives per player (player UUID -> remaining lives)
    private final Map<UUID, Integer> playerLives = new HashMap<>();
    // Lives per tier (tier name -> number of lives)
    private final Map<String, Integer> tierLives = new HashMap<>();
    // Buy-back fee (amount to pay to get back into challenge with lives reset)
    private double buyBackFee = 100.0;  // Default 100
    
    // Debug logging for spawner level management
    private boolean debugSpawnerLevels = false;
    
    // Track dead party members (player UUID -> challenge ID) - for giving rewards when party completes
    private final Map<UUID, String> deadPartyMembers = new HashMap<>();

    // Challenges config (stored in challenges.yml, separate from config.yml)
    private File challengesFile;
    private FileConfiguration challengesConfig;

    // Loot tables: custom chest loot per challenge (id -> LootTable)
    private final Map<String, LootTableData> loottables = new HashMap<>();
    private File loottablesFile;
    private FileConfiguration loottablesConfig;
    // GUI context for loottable menus (player UUID -> context)
    private final Map<UUID, LootTableGuiContext> loottableGuiContext = new HashMap<>();
    private ChallengeScriptManager scriptManager;
    // Pending rename: player who is typing a new loottable name (UUID -> loottable id)
    private final Map<UUID, String> loottableRenamePending = new HashMap<>();
    private final Map<UUID, String> loottableIconPending = new HashMap<>();

    // Instance chests (Lootr-style): challengeId -> (playerUuid -> (chestLocationKey -> contents))
    private final Map<String, Map<UUID, Map<String, ItemStack[]>>> instanceChestContents = new HashMap<>();
    // When a player has an instance chest open: playerUuid -> (challengeId, locationKey, size)
    private final Map<UUID, InstanceChestOpenContext> instanceChestOpen = new HashMap<>();
    // Block breaking: challengeId -> (blockKey -> placer UUID) for "blocks they placed" when blockBreakEnabled
    private final Map<String, Map<String, UUID>> challengePlacedBlocks = new HashMap<>();
    // Pass-through blocks: "challengeId:world:x,y,z" -> saved BlockState (block is temporarily air while player near)
    private final Map<String, BlockState> passThroughSavedStates = new HashMap<>();
    // Script gates/signals: per challenge, which block triggers have fired this run (reset when no players)
    private final Map<String, Set<String>> scriptFiredTriggers = new HashMap<>();
    // Signals fired this run (challengeId -> set of signal names)
    private final Map<String, Set<String>> scriptSignalsFired = new HashMap<>();
    // Redstone receiver cooldown: "challengeId:blockKey" -> last run ms (1s cooldown)
    private final Map<String, Long> scriptRedstoneCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.challengeKey = new NamespacedKey(this, "conrad_challenge_id");

        // Init challenges file (separate from config.yml)
        challengesFile = new File(getDataFolder(), "challenges.yml");
        if (!challengesFile.exists()) {
            saveResource("challenges.yml", false);
        }
        challengesConfig = YamlConfiguration.loadConfiguration(challengesFile);

        // Migrate challenges from config.yml to challenges.yml if present (one-time)
        migrateChallengesFromConfig();

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
        loadLivesSystem();
        loadDebugSettings();
        loadChallengesFromConfig();
        loadLoottables();
        setupDataFile();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
        scriptManager = new ChallengeScriptManager(this);
        getServer().getScheduler().runTaskTimer(this, this::updatePassThroughBlocks, 20L, 5L);
        registerCommands();
        setupPlaceholderAPI();
        setupMythicMobsListener();
        
        // Initialize NMS reflection for container NBT handling (WorldEdit-style)
        initializeNMS();

        // Pass-through blocks: every 5 ticks, set to air when player near, restore when not
        getServer().getScheduler().runTaskTimer(this, this::updatePassThroughBlocks, 20L, 5L);
        // Script function tool: particles on looked-at block and blocks with functions (every 20 ticks)
        getServer().getScheduler().runTaskTimer(this, this::updateScriptParticles, 40L, 20L);

        getLogger().info("ConradChallenges v" + getDescription().getVersion() + " enabled.");
    }
    
    /**
     * Initialize NMS reflection for tile entity NBT access (WorldEdit-style approach)
     * This allows us to directly read/write container NBT data for exact restoration
     */
    private void initializeNMS() {
        try {
            // Get server version
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nmsPackage = "net.minecraft.server." + version;
            String craftPackage = "org.bukkit.craftbukkit." + version;
            
            // Get CraftBlock and BlockPosition classes
            Class<?> craftBlockClass = Class.forName(craftPackage + ".block.CraftBlock");
            Class<?> blockPositionClass = Class.forName(nmsPackage + ".core.BlockPosition");
            Class<?> worldClass = Class.forName(nmsPackage + ".world.level.World");
            Class<?> tileEntityClass = Class.forName(nmsPackage + ".world.level.block.entity.TileEntity");
            Class<?> nbtTagCompoundClass = Class.forName(nmsPackage + ".nbt.NBTTagCompound");
            
            // Get methods
            getTileEntityMethod = worldClass.getMethod("getBlockEntity", blockPositionClass);
            saveMethod = tileEntityClass.getMethod("save", nbtTagCompoundClass);
            loadMethod = tileEntityClass.getMethod("load", nbtTagCompoundClass);
            
            nmsAvailable = true;
            getLogger().info("NMS tile entity access initialized (version: " + version + ")");
        } catch (Exception e) {
            nmsAvailable = false;
            getLogger().warning("NMS tile entity access not available, falling back to Bukkit API: " + e.getMessage());
        }
    }
    
    /**
     * Capture container NBT data (WorldEdit-style) for exact restoration
     * Returns serialized NBT as byte array, or null if not available
     */
    private byte[] captureContainerNBT(Block block) {
        if (!nmsAvailable || !(block.getState() instanceof Container)) {
            return null;
        }
        
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nmsPackage = "net.minecraft.server." + version;
            String craftPackage = "org.bukkit.craftbukkit." + version;
            
            Class<?> craftBlockClass = Class.forName(craftPackage + ".block.CraftBlock");
            Class<?> blockPositionClass = Class.forName(nmsPackage + ".core.BlockPosition");
            Class<?> worldClass = Class.forName(nmsPackage + ".world.level.World");
            Class<?> nbtTagCompoundClass = Class.forName(nmsPackage + ".nbt.NBTTagCompound");
            Class<?> nbtIoClass = Class.forName(nmsPackage + ".nbt.NBTCompressedStreamTools");
            
            // Get CraftBlock instance
            Object craftBlock = craftBlockClass.cast(block);
            Object nmsWorld = craftBlockClass.getMethod("getHandle").invoke(craftBlock);
            Object blockPos = blockPositionClass.getConstructor(int.class, int.class, int.class)
                .newInstance(block.getX(), block.getY(), block.getZ());
            
            // Get tile entity
            Object tileEntity = getTileEntityMethod.invoke(nmsWorld, blockPos);
            if (tileEntity == null) {
                return null;
            }
            
            // Create NBT compound and save tile entity data
            Object nbt = nbtTagCompoundClass.getConstructor().newInstance();
            saveMethod.invoke(tileEntity, nbt);
            
            // Compress NBT to byte array
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.lang.reflect.Method writeMethod = nbtIoClass.getMethod("a", nbtTagCompoundClass, java.io.DataOutput.class);
            writeMethod.invoke(null, nbt, new java.io.DataOutputStream(baos));
            
            return baos.toByteArray();
        } catch (Exception e) {
            getLogger().fine("Failed to capture container NBT at " + block.getLocation() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Restore container NBT data (WorldEdit-style) for exact restoration
     * Returns true if successful, false otherwise
     */
    private boolean restoreContainerNBT(Block block, byte[] nbtData) {
        if (!nmsAvailable || nbtData == null || nbtData.length == 0) {
            return false;
        }
        
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nmsPackage = "net.minecraft.server." + version;
            String craftPackage = "org.bukkit.craftbukkit." + version;
            
            Class<?> craftBlockClass = Class.forName(craftPackage + ".block.CraftBlock");
            Class<?> blockPositionClass = Class.forName(nmsPackage + ".core.BlockPosition");
            Class<?> worldClass = Class.forName(nmsPackage + ".world.level.World");
            Class<?> nbtTagCompoundClass = Class.forName(nmsPackage + ".nbt.NBTTagCompound");
            Class<?> nbtIoClass = Class.forName(nmsPackage + ".nbt.NBTCompressedStreamTools");
            
            // Get CraftBlock instance
            Object craftBlock = craftBlockClass.cast(block);
            Object nmsWorld = craftBlockClass.getMethod("getHandle").invoke(craftBlock);
            Object blockPos = blockPositionClass.getConstructor(int.class, int.class, int.class)
                .newInstance(block.getX(), block.getY(), block.getZ());
            
            // Get tile entity
            Object tileEntity = getTileEntityMethod.invoke(nmsWorld, blockPos);
            if (tileEntity == null) {
                return false;
            }
            
            // Decompress NBT from byte array
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(nbtData);
            java.lang.reflect.Method readMethod = nbtIoClass.getMethod("a", java.io.DataInput.class);
            Object nbt = readMethod.invoke(null, new java.io.DataInputStream(bais));
            
            // Load NBT into tile entity
            loadMethod.invoke(tileEntity, nbt);
            
            // Update the block to persist changes
            block.getState().update(true, true);
            
            return true;
        } catch (Exception e) {
            getLogger().fine("Failed to restore container NBT at " + block.getLocation() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("ConradChallenges v" + getDescription().getVersion() + " disabled.");
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

    /**
     * Helper method to restore container items from stored item array (fallback when NBT not available)
     */
    private void restoreContainerItems(Block block, Container container, String challengeId, String locKey) {
        Map<String, ItemStack[]> containerInvMap = challengeContainerInventories.get(challengeId);
        if (containerInvMap != null && containerInvMap.containsKey(locKey)) {
            ItemStack[] storedItems = containerInvMap.get(locKey);
            container.getInventory().clear();
            int itemsRestored = 0;
            for (int j = 0; j < storedItems.length && j < container.getInventory().getSize(); j++) {
                ItemStack item = storedItems[j];
                if (item != null && item.getType() != Material.AIR) {
                    container.getInventory().setItem(j, item.clone());
                    itemsRestored++;
                }
            }
            BlockState state = block.getState();
            if (state instanceof Container) {
                state.update(true, true);
            }
            if (itemsRestored > 0) {
                getLogger().info("[CONTAINER DEBUG] Restored " + itemsRestored + " items (fallback) to container at " + locKey);
            }
        } else {
            getLogger().info("[CONTAINER DEBUG] No stored inventory data for container at " + locKey + " - initializing empty");
            BlockState state = block.getState();
            if (state instanceof Container) {
                state.update(true, true);
            }
        }
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
        
        // Load function tool (script editor) material and display name
        String toolMatStr = config.getString("function-tool.material", "BLAZE_ROD");
        Material mat = Material.matchMaterial(toolMatStr != null ? toolMatStr : "BLAZE_ROD");
        if (mat != null && mat.isItem()) {
            functionToolMaterial = mat;
        } else {
            functionToolMaterial = Material.BLAZE_ROD;
            if (mat == null || !mat.isItem()) getLogger().warning("Invalid function-tool.material '" + toolMatStr + "', using BLAZE_ROD");
        }
        String toolName = config.getString("function-tool.display-name", "§6Challenge Function Tool");
        functionToolDisplayName = (toolName != null && !toolName.isEmpty())
            ? ChatColor.translateAlternateColorCodes('&', toolName) : "§6Challenge Function Tool";
    }
    
    Material getFunctionToolMaterial() { return functionToolMaterial; }
    String getFunctionToolDisplayName() { return functionToolDisplayName; }
    
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
            
            // Read current and default as text (UTF-8) to preserve formatting
            List<String> currentLines = java.nio.file.Files.readAllLines(messagesFile.toPath(), StandardCharsets.UTF_8);
            List<String> defaultLines = new ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(defaultMessagesTextStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            // Also load as YAML to check version and missing keys
            InputStream defaultMessagesYamlStream = getResource("messages.yml");
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultMessagesYamlStream, StandardCharsets.UTF_8));
            YamlConfiguration currentMessages = YamlConfiguration.loadConfiguration(messagesFile);
            
            int defaultVersion = defaultMessages.getInt("messages_version", 1);
            int currentVersion = currentMessages.getInt("messages_version", 0);
            
            // Check for missing keys (same logic as config)
            boolean hasMissingMessageKeys = false;
            List<String> missingMessageKeys = new ArrayList<>();
            for (String key : defaultMessages.getKeys(true)) {
                if (key.equals("messages_version")) continue;
                if (!currentMessages.contains(key)) {
                    hasMissingMessageKeys = true;
                    missingMessageKeys.add(key);
                }
            }
            if (!missingMessageKeys.isEmpty()) {
                getLogger().info("Found missing message keys: " + String.join(", ", missingMessageKeys));
            }
            
            // Skip migration only if version matches, version key present, and no missing keys
            if (currentVersion == defaultVersion && currentMessages.contains("messages_version") && !hasMissingMessageKeys) {
                return;
            }
            
            // Merge messages key-by-key so new keys from default are always added (preserveUserSectionContent=false)
            List<String> mergedLines = mergeConfigs(defaultLines, currentMessages, defaultMessages, currentLines, false);
            
            Set<String> deprecatedKeys = findDeprecatedKeys(currentMessages, defaultMessages);
            if (!deprecatedKeys.isEmpty()) {
                getLogger().info("Removed deprecated message keys: " + String.join(", ", deprecatedKeys));
            }
            
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, "messages_version");
            
            // Backup then write merged messages (UTF-8)
            File backupFile = new File(getDataFolder(), "messages.yml.bak");
            java.nio.file.Files.copy(messagesFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.write(messagesFile.toPath(), mergedLines, StandardCharsets.UTF_8);
            
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
    
    private void loadDebugSettings() {
        FileConfiguration config = getConfig();
        debugSpawnerLevels = config.getBoolean("debug-spawner-levels", false);
        if (debugSpawnerLevels) {
            getLogger().info("Debug spawner level logging: enabled");
        }
    }
    
    private void loadLivesSystem() {
        FileConfiguration config = getConfig();
        
        // Load lives per tier
        ConfigurationSection livesSection = config.getConfigurationSection("lives-per-tier");
        if (livesSection == null) {
            getLogger().warning("No lives-per-tier section found in config, using defaults");
            tierLives.put("easy", 3);
            tierLives.put("medium", 2);
            tierLives.put("hard", 1);
            tierLives.put("extreme", 1);
        } else {
            tierLives.put("easy", livesSection.getInt("easy", 3));
            tierLives.put("medium", livesSection.getInt("medium", 2));
            tierLives.put("hard", livesSection.getInt("hard", 1));
            tierLives.put("extreme", livesSection.getInt("extreme", 1));
        }
        
        getLogger().info("Loaded lives per tier: Easy=" + tierLives.get("easy") + 
                        ", Medium=" + tierLives.get("medium") + 
                        ", Hard=" + tierLives.get("hard") + 
                        ", Extreme=" + tierLives.get("extreme"));
        
        // Load buy-back fee
        buyBackFee = config.getDouble("buy-back-fee", 100.0);
        if (buyBackFee < 0) {
            buyBackFee = 100.0;
            getLogger().warning("Invalid buy-back-fee in config (negative), using default: 100.0");
        }
        getLogger().info("Buy-back fee set to: $" + buyBackFee);
    }
    
    private void saveNpcIds() {
        List<String> list = npcIds.stream().map(UUID::toString).collect(Collectors.toList());
        getConfig().set("npcs", list);
        saveConfig();
    }

    private void loadLoottables() {
        loottables.clear();
        loottablesFile = new File(getDataFolder(), "loottables.yml");
        if (!loottablesFile.exists()) {
            try {
                loottablesFile.getParentFile().mkdirs();
                loottablesFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create loottables.yml: " + e.getMessage());
                return;
            }
        }
        loottablesConfig = YamlConfiguration.loadConfiguration(loottablesFile);
        ConfigurationSection root = loottablesConfig.getConfigurationSection("loottables");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            LootTableData table = new LootTableData(id);
            table.name = sec.getString("name", "Unnamed");
            String sizeStr = sec.getString("size", "SINGLE");
            try {
                table.size = LootTableSize.valueOf(sizeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                table.size = LootTableSize.SINGLE;
            }
            String iconName = sec.getString("icon");
            if (iconName != null && !iconName.isEmpty()) {
                try {
                    table.iconMaterial = Material.valueOf(iconName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    table.iconMaterial = Material.CHEST;
                }
            } else {
                table.iconMaterial = Material.CHEST;
            }
            String typeStr = sec.getString("loot-type", "NORMAL");
            try {
                table.lootType = LootTableType.valueOf(typeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                table.lootType = LootTableType.NORMAL;
            }
            int slots = table.size.slots;
            table.contents = new ItemStack[slots];
            List<?> contentsList = sec.getList("contents");
            if (contentsList != null) {
                for (int i = 0; i < Math.min(contentsList.size(), slots); i++) {
                    Object o = contentsList.get(i);
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) o;
                        if (map.isEmpty()) continue;
                        try {
                            ItemStack stack = ItemStack.deserialize(map);
                            table.contents[i] = stack;
                        } catch (Exception e) {
                            // skip invalid item
                        }
                    }
                }
            }
            List<String> assigned = sec.getStringList("assigned-challenges");
            if (assigned != null) table.assignedChallengeIds.addAll(assigned);
            table.createdAt = sec.getLong("created-at", 0L);
            loottables.put(id, table);
        }
    }

    private void saveLoottables() {
        if (loottablesConfig == null || loottablesFile == null) return;
        loottablesConfig.set("loottables", null);
        ConfigurationSection root = loottablesConfig.createSection("loottables");
        for (Map.Entry<String, LootTableData> e : loottables.entrySet()) {
            LootTableData t = e.getValue();
            String path = e.getKey();
            root.set(path + ".name", t.name);
            root.set(path + ".size", t.size.name());
            root.set(path + ".icon", (t.iconMaterial != null ? t.iconMaterial.name() : Material.CHEST.name()));
            root.set(path + ".loot-type", (t.lootType != null ? t.lootType : LootTableType.NORMAL).name());
            List<Map<String, Object>> contentsList = new ArrayList<>();
            for (ItemStack stack : t.contents) {
                if (stack != null && !stack.getType().isAir()) {
                    contentsList.add(stack.serialize());
                } else {
                    contentsList.add(new HashMap<>());
                }
            }
            root.set(path + ".contents", contentsList);
            root.set(path + ".assigned-challenges", new ArrayList<>(t.assignedChallengeIds));
            root.set(path + ".created-at", t.createdAt);
        }
        try {
            loottablesConfig.save(loottablesFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save loottables.yml: " + ex.getMessage());
        }
    }

    private static final String LOOTTABLE_GUI_TITLE_PREFIX = "§8Loottable §8";

    private void openLoottableMainGui(Player player) {
        // 4 rows (27 + 9) so we have a bottom row for navigation (Back button).
        Inventory inv = Bukkit.createInventory(null, 36, LOOTTABLE_GUI_TITLE_PREFIX + "Menu");
        ItemStack green = new ItemStack(Material.LIME_CONCRETE);
        ItemStack red = new ItemStack(Material.RED_CONCRETE);
        org.bukkit.inventory.meta.ItemMeta gm = green.getItemMeta();
        if (gm != null) {
            gm.setDisplayName(ChatColor.GREEN + "Add new loottable");
            gm.setLore(Arrays.asList(ChatColor.GRAY + "Create a new loot table"));
            green.setItemMeta(gm);
        }
        org.bukkit.inventory.meta.ItemMeta rm = red.getItemMeta();
        if (rm != null) {
            rm.setDisplayName(ChatColor.RED + "Edit loottable");
            rm.setLore(Arrays.asList(ChatColor.GRAY + "Open list of existing loot tables"));
            red.setItemMeta(rm);
        }
        inv.setItem(11, green);
        inv.setItem(15, red);
        // Back button (closes menu)
        inv.setItem(35, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Close menu"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.MAIN, null));
        player.openInventory(inv);
    }

    private void openLoottableListGui(Player player) {
        int page = lastLoottableListPage.getOrDefault(player.getUniqueId(), 0);
        openLoottableListGui(player, page);
    }

    /** Build ordered list of loottable ids based on current filter mode/param for the player. */
    private List<String> buildLoottableListIds(Player player) {
        LootTableListFilterMode mode = lastLoottableListFilterMode.getOrDefault(player.getUniqueId(), LootTableListFilterMode.CREATED);
        String param = lastLoottableListFilterParam.get(player.getUniqueId());
        List<LootTableData> list = new ArrayList<>(loottables.values());
        switch (mode) {
            case CREATED -> list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt)); // newer first
            case NAME -> list.sort((a, b) -> {
                int c = (a.name != null ? a.name : "").compareToIgnoreCase(b.name != null ? b.name : "");
                return c != 0 ? c : a.id.compareTo(b.id);
            });
            case TYPE -> {
                LootTableType want = (param != null && "LEGENDARY".equals(param)) ? LootTableType.LEGENDARY : LootTableType.NORMAL;
                list.removeIf(t -> (t.lootType != null ? t.lootType : LootTableType.NORMAL) != want);
                list.sort((a, b) -> {
                    int c = (a.name != null ? a.name : "").compareToIgnoreCase(b.name != null ? b.name : "");
                    return c != 0 ? c : a.id.compareTo(b.id);
                });
            }
            case CHALLENGE -> {
                if (param == null || param.isEmpty()) list.clear();
                else {
                    list.removeIf(t -> !t.assignedChallengeIds.contains(param));
                    list.sort((a, b) -> {
                        int c = (a.name != null ? a.name : "").compareToIgnoreCase(b.name != null ? b.name : "");
                        return c != 0 ? c : a.id.compareTo(b.id);
                    });
                }
            }
        }
        List<String> ids = new ArrayList<>();
        for (LootTableData t : list) ids.add(t.id);
        return ids;
    }

    private void openLoottableListGui(Player player, int page) {
        List<String> ids = buildLoottableListIds(player);
        int totalPages = Math.max(1, (ids.size() + LOOTTABLE_LIST_CONTENT_PER_PAGE - 1) / LOOTTABLE_LIST_CONTENT_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        lastLoottableListPage.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, LOOTTABLE_LIST_SLOTS, LOOTTABLE_GUI_TITLE_PREFIX + "List");
        int start = page * LOOTTABLE_LIST_CONTENT_PER_PAGE;
        // Content only in slots 0-44; bottom row (45-53) is nav bar only — never put loot table icons there
        for (int i = 0; i < LOOTTABLE_LIST_CONTENT_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= ids.size()) break;
            LootTableData t = loottables.get(ids.get(idx));
            if (t == null) continue;
            Material iconMat = (t.iconMaterial != null ? t.iconMaterial : Material.CHEST);
            ItemStack icon = new ItemStack(iconMat);
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + t.name);
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "ID: " + t.id,
                    ChatColor.GRAY + "Size: " + t.size.name(),
                    ChatColor.GRAY + "Assigned: " + t.assignedChallengeIds.size() + " challenge(s)"
                ));
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }
        // Bottom row: filter (45), empty, prev, empty, next, empty, empty, empty, back (53)
        inv.setItem(LOOTTABLE_LIST_FILTER_SLOT, makeLoottableGuiItem(Material.HOPPER, ChatColor.GOLD + "Filter / Sort", ChatColor.GRAY + "Change how loottables are listed"));
        inv.setItem(LOOTTABLE_LIST_BACK_SLOT, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to menu"));
        // Always show Prev/Next so layout is consistent; disable when not applicable
        if (page > 0) {
            inv.setItem(LOOTTABLE_LIST_PREV_SLOT, makeLoottableGuiItem(Material.ARROW, ChatColor.GOLD + "Previous page", ChatColor.GRAY + "Page " + page));
        } else {
            inv.setItem(LOOTTABLE_LIST_PREV_SLOT, makeLoottableGuiItem(Material.ARROW, ChatColor.GRAY + "Previous page", ChatColor.DARK_GRAY + (totalPages <= 1 ? "Only one page" : "Already on first page")));
        }
        if (page < totalPages - 1) {
            inv.setItem(LOOTTABLE_LIST_NEXT_SLOT, makeLoottableGuiItem(Material.ARROW, ChatColor.GOLD + "Next page", ChatColor.GRAY + "Page " + (page + 2)));
        } else {
            inv.setItem(LOOTTABLE_LIST_NEXT_SLOT, makeLoottableGuiItem(Material.ARROW, ChatColor.GRAY + "Next page", ChatColor.DARK_GRAY + (totalPages <= 1 ? "Only one page" : "Already on last page")));
        }
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.LIST, null, ids, page));
        player.openInventory(inv);
    }

    private void openLoottableFilterGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, LOOTTABLE_GUI_TITLE_PREFIX + "Filter");
        inv.setItem(10, makeLoottableGuiItem(Material.CLOCK, ChatColor.YELLOW + "Created", ChatColor.GRAY + "Newer to older"));
        inv.setItem(12, makeLoottableGuiItem(Material.NAME_TAG, ChatColor.AQUA + "Name", ChatColor.GRAY + "A–Z by name"));
        inv.setItem(14, makeLoottableGuiItem(Material.IRON_INGOT, ChatColor.WHITE + "Type", ChatColor.GRAY + "Normal or Legendary"));
        inv.setItem(16, makeLoottableGuiItem(Material.COMPASS, ChatColor.GREEN + "Challenge", ChatColor.GRAY + "By challenge"));
        inv.setItem(35, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to list"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.LIST_FILTER, null));
        player.openInventory(inv);
    }

    private void openLoottableFilterTypeGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, LOOTTABLE_GUI_TITLE_PREFIX + "Filter by Type");
        inv.setItem(11, makeLoottableGuiItem(Material.IRON_INGOT, ChatColor.WHITE + "Normal", ChatColor.GRAY + "Loottables of type Normal (A–Z)"));
        inv.setItem(15, makeLoottableGuiItem(Material.GOLD_INGOT, ChatColor.GOLD + "Legendary", ChatColor.GRAY + "Loottables of type Legendary (A–Z)"));
        inv.setItem(35, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to filter menu"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.LIST_FILTER_TYPE, null));
        player.openInventory(inv);
    }

    private void openLoottableFilterChallengeGui(Player player) {
        openLoottableFilterChallengeGui(player, 0);
    }

    private static final int LOOTTABLE_FILTER_CHALLENGE_SLOTS = 54;
    private static final int LOOTTABLE_FILTER_CHALLENGE_CONTENT = 45;
    private static final int LOOTTABLE_FILTER_CHALLENGE_BACK_SLOT = 53;

    private void openLoottableFilterChallengeGui(Player player, int page) {
        List<String> challengeIds = new ArrayList<>(challenges.keySet());
        challengeIds.sort(String.CASE_INSENSITIVE_ORDER);
        int totalPages = Math.max(1, (challengeIds.size() + LOOTTABLE_FILTER_CHALLENGE_CONTENT - 1) / LOOTTABLE_FILTER_CHALLENGE_CONTENT);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        Inventory inv = Bukkit.createInventory(null, LOOTTABLE_FILTER_CHALLENGE_SLOTS, LOOTTABLE_GUI_TITLE_PREFIX + "Filter by Challenge");
        int start = page * LOOTTABLE_FILTER_CHALLENGE_CONTENT;
        for (int i = 0; i < LOOTTABLE_FILTER_CHALLENGE_CONTENT; i++) {
            int idx = start + i;
            if (idx >= challengeIds.size()) break;
            String cid = challengeIds.get(idx);
            int count = 0;
            for (LootTableData t : loottables.values()) {
                if (t.assignedChallengeIds.contains(cid)) count++;
            }
            ItemStack icon = makeLoottableGuiItem(Material.PAPER, ChatColor.YELLOW + cid, ChatColor.GRAY + (count + " loottable(s) assigned"));
            inv.setItem(i, icon);
        }
        inv.setItem(LOOTTABLE_FILTER_CHALLENGE_BACK_SLOT, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to filter menu"));
        if (page > 0) {
            inv.setItem(47, makeLoottableGuiItem(Material.ARROW, ChatColor.GOLD + "Previous page", ChatColor.GRAY + "Page " + page));
        }
        if (page < totalPages - 1) {
            inv.setItem(49, makeLoottableGuiItem(Material.ARROW, ChatColor.GOLD + "Next page", ChatColor.GRAY + "Page " + (page + 2)));
        }
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.LIST_FILTER_CHALLENGE, null, challengeIds, page));
        player.openInventory(inv);
    }

    private void openLoottableOptionsGui(Player player, String loottableId) {
        LootTableData t = loottables.get(loottableId);
        if (t == null) {
            player.closeInventory();
            return;
        }
        // 4 rows to allow a bottom navigation row with a Back button.
        Inventory inv = Bukkit.createInventory(null, 36, LOOTTABLE_GUI_TITLE_PREFIX + "Options");
        inv.setItem(0, makeLoottableGuiItem(Material.CHEST, ChatColor.YELLOW + "Size", "Current: " + t.size.name(), "Single (27) or Double (54)"));
        inv.setItem(2, makeLoottableGuiItem(Material.COMPASS, ChatColor.AQUA + "Edit contents", "Edit the chest layout"));
        inv.setItem(4, makeLoottableGuiItem(Material.NAME_TAG, ChatColor.WHITE + "Rename", "Type new name in chat"));
        inv.setItem(6, makeLoottableGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Assign to challenge(s)", "Choose which challenges use this table"));
        inv.setItem(8, makeLoottableGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Delete", "Remove this loot table"));
        // Second row: icon options, staggered (slots 10, 12)
        inv.setItem(10, makeLoottableGuiItem(Material.ITEM_FRAME, ChatColor.GOLD + "Change icon", "Type the item name in chat", "(e.g. ender_pearl, diamond)"));
        inv.setItem(12, makeLoottableGuiItem(Material.CRAFTING_TABLE, ChatColor.GOLD + "Change icon (hand)", "Use the item in your main hand", "as the list icon for this loottable."));
        // Loot type: Normal (used by normal chests) or Legendary (used by legendary chests)
        LootTableType currentType = t.lootType != null ? t.lootType : LootTableType.NORMAL;
        Material typeIcon = currentType == LootTableType.LEGENDARY ? Material.GOLD_INGOT : Material.IRON_INGOT;
        inv.setItem(14, makeLoottableGuiItem(typeIcon, ChatColor.AQUA + "Loot type: " + currentType.name(), "Current: " + currentType.name(), "Normal = used by default chests", "Legendary = used by chests toggled with /challenge loottype", "Click to toggle"));
        // Back button (return to list)
        inv.setItem(35, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to list"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.OPTIONS, loottableId));
        player.openInventory(inv);
    }

    private void openLoottableDeleteConfirmGui(Player player, String loottableId) {
        // 4 rows so we have a Back button row.
        Inventory inv = Bukkit.createInventory(null, 36, LOOTTABLE_GUI_TITLE_PREFIX + "Confirm");
        inv.setItem(11, makeLoottableGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Yes, delete", "Permanently delete this loot table"));
        inv.setItem(15, makeLoottableGuiItem(Material.RED_CONCRETE, ChatColor.RED + "No, cancel", "Return to options"));
        // Back button (same as cancel)
        inv.setItem(35, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to options"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.DELETE_CONFIRM, loottableId));
        player.openInventory(inv);
    }

    private void openLoottableAssignGui(Player player, String loottableId) {
        List<String> challengeIds = new ArrayList<>(challenges.keySet());
        // At least 4 rows, at most 6; keep last slot for Back.
        int rows = Math.min(6, Math.max(4, (challengeIds.size() + 8) / 9));
        int size = rows * 9;
        Inventory inv = Bukkit.createInventory(null, size, LOOTTABLE_GUI_TITLE_PREFIX + "Assign");
        LootTableData t = loottables.get(loottableId);
        Set<String> assigned = t != null ? t.assignedChallengeIds : Collections.emptySet();
        int backSlot = size - 1;
        for (int i = 0; i < challengeIds.size() && i < backSlot; i++) {
            String cid = challengeIds.get(i);
            boolean isAssigned = assigned.contains(cid);
            ItemStack icon = new ItemStack(isAssigned ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((isAssigned ? ChatColor.GREEN : ChatColor.GRAY) + cid);
                meta.setLore(Arrays.asList(isAssigned ? ChatColor.GRAY + "Click to unassign" : ChatColor.GRAY + "Click to assign"));
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }
        // Back button (return to options)
        inv.setItem(backSlot, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to options"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.ASSIGN, loottableId, challengeIds));
        player.openInventory(inv);
    }

    private void openLoottableEditContentsGui(Player player, String loottableId) {
        LootTableData t = loottables.get(loottableId);
        if (t == null) {
            player.closeInventory();
            return;
        }
        // Logical content slots: single chest (27), double chest (54).
        int contentSlots = (t.size == LootTableSize.SINGLE ? 27 : 54);
        // Visual inventory size: add an extra row for singles (36), keep 54 for doubles (cannot exceed 54).
        int invSize = (t.size == LootTableSize.SINGLE ? 36 : 54);
        Inventory inv = Bukkit.createInventory(null, invSize, LOOTTABLE_GUI_TITLE_PREFIX + "Edit");
        // Fill only the logical content area; remaining slots (including the last row for singles) are navigation.
        for (int i = 0; i < contentSlots && i < invSize; i++) {
            if (t.contents != null && i < t.contents.length && t.contents[i] != null && !t.contents[i].getType().isAir()) {
                inv.setItem(i, t.contents[i].clone());
            }
        }
        // Back button in the very last slot
        int backSlot = invSize - 1;
        inv.setItem(backSlot, makeLoottableGuiItem(Material.BARRIER, ChatColor.RED + "Back", ChatColor.GRAY + "Return to options"));
        loottableGuiContext.put(player.getUniqueId(), new LootTableGuiContext(LootTableScreen.EDIT_CONTENTS, loottableId));
        player.openInventory(inv);
    }

    private ItemStack makeLoottableGuiItem(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void createNewLoottable(Player player) {
        String id = "loottable_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LootTableData t = new LootTableData(id);
        t.name = "New table";
        t.iconMaterial = Material.CHEST;
        loottables.put(id, t);
        saveLoottables();
        openLoottableOptionsGui(player, id);
    }

    private void startLoottableRename(Player player, String loottableId) {
        loottableRenamePending.put(player.getUniqueId(), loottableId);
        loottableGuiContext.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(getMessage("loottable.prompt-name"));
    }

    private void startLoottableIconChange(Player player, String loottableId) {
        loottableIconPending.put(player.getUniqueId(), loottableId);
        loottableGuiContext.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(getMessage("loottable.prompt-icon"));
    }

    /** Resolve user input to a valid Material for display (items/blocks). Returns null if not found. */
    private static Material parseMaterialIcon(String input) {
        if (input == null || input.isEmpty()) return null;
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            Material m = Material.valueOf(normalized);
            if (m.isItem() || m.isBlock()) return m;
            return m; // still allow any material for icon
        } catch (IllegalArgumentException ignored) {
            // Try match without underscores: "enderpearl" -> ENDER_PEARL
            String noUnderscore = normalized.replace("_", "");
            for (Material m : Material.values()) {
                if (m.name().replace("_", "").equals(noUnderscore)) return m;
            }
        }
        return null;
    }

    private static String chestLocationKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * Gets the other half of a double chest, or null if single chest.
     * Uses Bukkit's DoubleChest inventory holder (same approach as Locktight) so both halves are always treated together.
     */
    private static Block getDoubleChestPartner(Block block) {
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) return null;
        BlockState state = block.getState();
        if (!(state instanceof Chest chestState)) return null;
        org.bukkit.inventory.InventoryHolder holder = chestState.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) return null;
        org.bukkit.inventory.InventoryHolder leftHolder = doubleChest.getLeftSide();
        org.bukkit.inventory.InventoryHolder rightHolder = doubleChest.getRightSide();
        Location ourLoc = block.getLocation();
        if (leftHolder instanceof Chest leftChest && !leftChest.getLocation().equals(ourLoc)) return leftChest.getBlock();
        if (rightHolder instanceof Chest rightChest && !rightChest.getLocation().equals(ourLoc)) return rightChest.getBlock();
        return null;
    }

    /** Returns the canonical key for this chest (one key per inventory; for double chests both halves share the same key). Uses left side for consistency. */
    private static String getCanonicalChestKey(Block block) {
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) return null;
        BlockState state = block.getState();
        if (!(state instanceof Chest chestState)) return chestLocationKey(block.getLocation());
        org.bukkit.inventory.InventoryHolder holder = chestState.getInventory().getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            org.bukkit.inventory.InventoryHolder leftHolder = doubleChest.getLeftSide();
            if (leftHolder instanceof Chest leftChest) return chestLocationKey(leftChest.getLocation());
        }
        return chestLocationKey(block.getLocation());
    }

    /** Returns both blocks if double chest (for ignored set), else singleton list. Uses DoubleChest API so both halves are always included. */
    private static List<Block> getChestBlocksForIgnore(Block block) {
        List<Block> out = new ArrayList<>();
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) return out;
        out.add(block);
        Block partner = getDoubleChestPartner(block);
        if (partner != null) out.add(partner);
        return out;
    }

    private String getChallengeIdForChestLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (Map.Entry<String, ChallengeConfig> e : challenges.entrySet()) {
            ChallengeConfig cfg = e.getValue();
            if (!isLocationInRegenArea(cfg, loc)) continue;
            return e.getKey();
        }
        return null;
    }

    /** True if this chest (or its double-chest partner) is in the ignored set. Either half being ignored means the whole double chest is ignored. */
    private boolean isChestIgnored(ChallengeConfig cfg, Block block) {
        if (cfg == null || cfg.ignoredChestKeys == null) return false;
        if (cfg.ignoredChestKeys.contains(chestLocationKey(block.getLocation()))) return true;
        Block partner = getDoubleChestPartner(block);
        return partner != null && cfg.ignoredChestKeys.contains(chestLocationKey(partner.getLocation()));
    }

    private boolean isChestLegendary(ChallengeConfig cfg, String canonicalChestKey) {
        return cfg != null && cfg.legendaryChestKeys != null && canonicalChestKey != null && cfg.legendaryChestKeys.contains(canonicalChestKey);
    }

    private void clearInstanceChestDataForPlayer(UUID playerUuid, String challengeId) {
        if (challengeId == null) return;
        Map<UUID, Map<String, ItemStack[]>> perChallenge = instanceChestContents.get(challengeId);
        if (perChallenge != null) perChallenge.remove(playerUuid);
    }

    private void fillChestsWithLoottables(String challengeId, ChallengeConfig cfg) {
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) return;
        instanceChestContents.remove(challengeId);
        List<LootTableData> singleTables = new ArrayList<>();
        List<LootTableData> doubleTables = new ArrayList<>();
        for (LootTableData t : loottables.values()) {
            if (!t.assignedChallengeIds.contains(challengeId)) continue;
            if (t.size == LootTableSize.SINGLE) singleTables.add(t);
            else doubleTables.add(t);
        }
        boolean hasAssignedLoottables = !singleTables.isEmpty() || !doubleTables.isEmpty();
        if (!hasAssignedLoottables) {
            return;
        }
        World world = cfg.regenerationCorner1.getWorld();
        if (world == null) return;
        int minX = (int) Math.min(cfg.regenerationCorner1.getX(), cfg.regenerationCorner2.getX());
        int maxX = (int) Math.max(cfg.regenerationCorner1.getX(), cfg.regenerationCorner2.getX());
        int minY = (int) Math.min(cfg.regenerationCorner1.getY(), cfg.regenerationCorner2.getY());
        int maxY = (int) Math.max(cfg.regenerationCorner1.getY(), cfg.regenerationCorner2.getY());
        int minZ = (int) Math.min(cfg.regenerationCorner1.getZ(), cfg.regenerationCorner2.getZ());
        int maxZ = (int) Math.max(cfg.regenerationCorner1.getZ(), cfg.regenerationCorner2.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) continue;
                    BlockState state = block.getState();
                    if (!(state instanceof Chest chestState)) continue;
                    org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
                    if (chestData.getType() == org.bukkit.block.data.type.Chest.Type.RIGHT) continue;
                    if (isChestIgnored(cfg, block)) continue;
                    chestState.getInventory().clear();
                }
            }
        }
    }

    private void loadChallengesFromConfig() {
        challenges.clear();
        bookTitleToChallengeId.clear();

        // Reload challenges file from disk (e.g. on /reload)
        if (challengesFile != null && challengesFile.exists()) {
            challengesConfig = YamlConfiguration.loadConfiguration(challengesFile);
        }
        ConfigurationSection root = challengesConfig != null ? challengesConfig.getConfigurationSection("challenges") : null;
        if (root == null) {
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
                                        Object itemDataObj = map.get("item-data");
                                        if (itemDataObj != null) {
                                            try {
                                                // Try to deserialize as Map (new format with full ItemStack data)
                                                if (itemDataObj instanceof Map) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> itemMap = (Map<String, Object>) itemDataObj;
                                                    reward.itemStack = ItemStack.deserialize(itemMap);
                                                } else {
                                                    // Fallback to old format (Material;amount) for backward compatibility
                                                    String itemData = String.valueOf(itemDataObj);
                                                    if (!itemData.isEmpty()) {
                                                        if (itemData.contains(";")) {
                                                            String[] parts = itemData.split(";");
                                                            Material mat = Material.valueOf(parts[0]);
                                                            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                                                            reward.itemStack = new ItemStack(mat, amount);
                                                        } else {
                                                            Material mat = Material.valueOf(itemData);
                                                            reward.itemStack = new ItemStack(mat, 1);
                                                        }
                                                    }
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
                                Object itemDataObj = map.get("item-data");
                                if (itemDataObj != null) {
                                    try {
                                        // Try to deserialize as Map (new format with full ItemStack data)
                                        if (itemDataObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> itemMap = (Map<String, Object>) itemDataObj;
                                            reward.itemStack = ItemStack.deserialize(itemMap);
                                        } else {
                                            // Fallback to old format (Material;amount) for backward compatibility
                                            String itemData = String.valueOf(itemDataObj);
                                            if (!itemData.isEmpty()) {
                                                if (itemData.contains(";")) {
                                                    String[] parts = itemData.split(";");
                                                    Material mat = Material.valueOf(parts[0]);
                                                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                                                    reward.itemStack = new ItemStack(mat, amount);
                                                } else {
                                                    Material mat = Material.valueOf(itemData);
                                                    reward.itemStack = new ItemStack(mat, 1);
                                                }
                                            }
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
            
            // Migrate old speed tier rewards to fallback rewards if no other rewards are configured
            // This provides backward compatibility for old configs
            boolean hasTierRewards = cfg.difficultyTierRewards != null && !cfg.difficultyTierRewards.isEmpty();
            if (!hasTierRewards && cfg.fallbackRewardCommands.isEmpty() && cfg.completionType == CompletionType.SPEED 
                    && cfg.speedTiers != null && !cfg.speedTiers.isEmpty()) {
                // Collect all reward commands from speed tiers and add them as fallback rewards
                for (SpeedTier tier : cfg.speedTiers) {
                    if (tier.rewardCommands != null) {
                        for (String cmd : tier.rewardCommands) {
                            if (cmd != null && !cmd.isEmpty()) {
                                cfg.fallbackRewardCommands.add(new RewardWithChance(cmd, 1.0));
                            }
                        }
                    }
                }
                if (!cfg.fallbackRewardCommands.isEmpty()) {
                    getLogger().info("Migrated " + cfg.fallbackRewardCommands.size() + " old speed tier reward(s) to fallback rewards for challenge " + id);
                }
            }

            // Load regeneration areas (multiple boxes). New format: regeneration-areas list; fallback: regeneration (singular)
            List<?> regenAreasList = cs.getList("regeneration-areas");
            if (regenAreasList != null && !regenAreasList.isEmpty()) {
                cfg.regenerationAreas.clear();
                for (Object item : regenAreasList) {
                    if (!(item instanceof Map)) continue;
                    @SuppressWarnings("unchecked") Map<?, ?> map = (Map<?, ?>) item;
                    String wName = map.get("world") != null ? String.valueOf(map.get("world")) : null;
                    if (wName == null) continue;
                    World w = Bukkit.getWorld(wName);
                    if (w == null) continue;
                    Object c1 = map.get("corner1");
                    Object c2 = map.get("corner2");
                    if (c1 instanceof Map && c2 instanceof Map) {
                        Map<?, ?> m1 = (Map<?, ?>) c1, m2 = (Map<?, ?>) c2;
                        double x1 = getDouble(m1, "x"), y1 = getDouble(m1, "y"), z1 = getDouble(m1, "z");
                        double x2 = getDouble(m2, "x"), y2 = getDouble(m2, "y"), z2 = getDouble(m2, "z");
                        cfg.regenerationAreas.add(new Location[]{ new Location(w, x1, y1, z1), new Location(w, x2, y2, z2) });
                    }
                }
                syncRegenLegacyFromList(cfg);
                ConfigurationSection regenSec = cs.getConfigurationSection("regeneration");
                if (regenSec != null) cfg.regenerationAutoExtendY = regenSec.getBoolean("auto-extend-y", false);
            } else {
                ConfigurationSection regenSec = cs.getConfigurationSection("regeneration");
                if (regenSec != null) {
                    String regenWorldName = regenSec.getString("world");
                    if (regenWorldName != null) {
                        World regenWorld = Bukkit.getWorld(regenWorldName);
                        if (regenWorld != null) {
                            ConfigurationSection corner1Sec = regenSec.getConfigurationSection("corner1");
                            ConfigurationSection corner2Sec = regenSec.getConfigurationSection("corner2");
                            if (corner1Sec != null && corner2Sec != null) {
                                double x1 = corner1Sec.getDouble("x"), y1 = corner1Sec.getDouble("y"), z1 = corner1Sec.getDouble("z");
                                double x2 = corner2Sec.getDouble("x"), y2 = corner2Sec.getDouble("y"), z2 = corner2Sec.getDouble("z");
                                cfg.regenerationCorner1 = new Location(regenWorld, x1, y1, z1);
                                cfg.regenerationCorner2 = new Location(regenWorld, x2, y2, z2);
                                cfg.regenerationAreas.add(new Location[]{ cfg.regenerationCorner1, cfg.regenerationCorner2 });
                            }
                            cfg.regenerationAutoExtendY = regenSec.getBoolean("auto-extend-y", false);
                            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null && !challengeInitialStates.containsKey(id)) {
                                captureInitialState(cfg, success -> {
                                    if (success) getLogger().info("Captured initial state for challenge '" + id + "' during config load");
                                    else getLogger().warning("Failed to capture initial state for challenge '" + id + "' during config load");
                                });
                            }
                        } else {
                            getLogger().warning("Challenge '" + id + "' has invalid regeneration world: " + regenWorldName);
                        }
                    }
                }
            }

            // Load challenge areas (multiple boxes). New format: challenge-areas list; fallback: challenge-area (singular)
            List<?> challengeAreasList = cs.getList("challenge-areas");
            if (challengeAreasList != null && !challengeAreasList.isEmpty()) {
                cfg.challengeAreas.clear();
                for (Object item : challengeAreasList) {
                    if (!(item instanceof Map)) continue;
                    @SuppressWarnings("unchecked") Map<?, ?> map = (Map<?, ?>) item;
                    String wName = map.get("world") != null ? String.valueOf(map.get("world")) : null;
                    if (wName == null) continue;
                    World w = Bukkit.getWorld(wName);
                    if (w == null) continue;
                    Object c1 = map.get("corner1"), c2 = map.get("corner2");
                    if (c1 instanceof Map && c2 instanceof Map) {
                        Map<?, ?> m1 = (Map<?, ?>) c1, m2 = (Map<?, ?>) c2;
                        double x1 = getDouble(m1, "x"), y1 = getDouble(m1, "y"), z1 = getDouble(m1, "z");
                        double x2 = getDouble(m2, "x"), y2 = getDouble(m2, "y"), z2 = getDouble(m2, "z");
                        cfg.challengeAreas.add(new Location[]{ new Location(w, x1, y1, z1), new Location(w, x2, y2, z2) });
                    }
                }
                syncChallengeLegacyFromList(cfg);
            } else {
                ConfigurationSection challengeAreaSec = cs.getConfigurationSection("challenge-area");
                if (challengeAreaSec != null) {
                    String challengeWorldName = challengeAreaSec.getString("world");
                    if (challengeWorldName != null) {
                        World challengeWorld = Bukkit.getWorld(challengeWorldName);
                        if (challengeWorld != null) {
                            ConfigurationSection corner1Sec = challengeAreaSec.getConfigurationSection("corner1");
                            ConfigurationSection corner2Sec = challengeAreaSec.getConfigurationSection("corner2");
                            if (corner1Sec != null && corner2Sec != null) {
                                double x1 = corner1Sec.getDouble("x"), y1 = corner1Sec.getDouble("y"), z1 = corner1Sec.getDouble("z");
                                double x2 = corner2Sec.getDouble("x"), y2 = corner2Sec.getDouble("y"), z2 = corner2Sec.getDouble("z");
                                cfg.challengeCorner1 = new Location(challengeWorld, x1, y1, z1);
                                cfg.challengeCorner2 = new Location(challengeWorld, x2, y2, z2);
                                cfg.challengeAreas.add(new Location[]{ cfg.challengeCorner1, cfg.challengeCorner2 });
                            }
                        } else {
                            getLogger().warning("Challenge '" + id + "' has invalid challenge area world: " + challengeWorldName);
                        }
                    }
                }
            }

            // Load blocked items (materials that cannot be taken into this challenge)
            List<String> blockedItemNames = cs.getStringList("blocked-items");
            if (blockedItemNames != null && !blockedItemNames.isEmpty()) {
                cfg.blockedItems.clear();
                for (String name : blockedItemNames) {
                    try {
                        Material mat = Material.valueOf(name.toUpperCase(Locale.ROOT));
                        if (mat != null && mat.isItem()) {
                            cfg.blockedItems.add(mat);
                        }
                    } catch (IllegalArgumentException ignored) {
                        getLogger().warning("Challenge '" + id + "' has invalid blocked item: " + name);
                    }
                }
            }

            // Load ignored chest keys (chests that skip loot tables and use normal regen)
            List<String> ignoredKeys = cs.getStringList("ignored-chests");
            if (ignoredKeys != null && !ignoredKeys.isEmpty()) {
                cfg.ignoredChestKeys.clear();
                cfg.ignoredChestKeys.addAll(ignoredKeys);
            }
            cfg.legendaryChestKeys.clear();
            List<String> legendaryKeys = cs.getStringList("legendary-chests");
            if (legendaryKeys != null && !legendaryKeys.isEmpty()) {
                cfg.legendaryChestKeys.addAll(legendaryKeys);
            }

            // Load block break settings: blockBreakEnabled = restriction on (only placed + allow list); false = challengers can break whatever
            cfg.blockBreakEnabled = cs.getBoolean("block-break-enabled", false);
            cfg.breakAllowedMaterials.clear();
            List<String> allowBreakNames = cs.getStringList("break-allowed-blocks");
            if (allowBreakNames != null) {
                for (String name : allowBreakNames) {
                    try {
                        Material mat = Material.valueOf(name.toUpperCase(Locale.ROOT));
                        if (mat != null && mat.isBlock()) {
                            cfg.breakAllowedMaterials.add(mat);
                        }
                    } catch (IllegalArgumentException ignored) {
                        getLogger().warning("Challenge '" + id + "' has invalid break-allowed block: " + name);
                    }
                }
            }

            // Load script nodes (function tool)
            cfg.scriptNodes.clear();
            List<?> scriptList = cs.getList("scripts");
            if (scriptList != null) {
                for (Object raw : scriptList) {
                    if (!(raw instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) raw;
                    String w = String.valueOf(map.get("world"));
                    Object ox = map.get("x"), oy = map.get("y"), oz = map.get("z");
                    if (w == null || w.isEmpty() || ox == null || oy == null || oz == null) continue;
                    int x = ((Number) ox).intValue(), y = ((Number) oy).intValue(), z = ((Number) oz).intValue();
                    ChallengeScriptNode node = new ChallengeScriptNode(w, x, y, z);
                    String triggerStr = String.valueOf(map.get("trigger")).toUpperCase(Locale.ROOT);
                    try { node.trigger = ScriptTrigger.valueOf(triggerStr); } catch (IllegalArgumentException ignored) { }
                    String funcStr = String.valueOf(map.get("function")).toUpperCase(Locale.ROOT);
                    try { node.functionType = ScriptFunctionType.valueOf(funcStr); } catch (IllegalArgumentException ignored) { }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fd = (Map<String, Object>) map.get("function-data");
                    if (fd != null) node.functionData.putAll(fd);
                    List<?> condList = (List<?>) map.get("conditions");
                    if (condList != null) {
                        for (Object c : condList) {
                            if (c instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> cm = new HashMap<>((Map<String, Object>) c);
                                node.conditions.add(cm);
                            }
                        }
                    }
                    cfg.scriptNodes.add(node);
                }
            }

            // Load pass-through blocks (invisible walls)
            cfg.passThroughBlockKeys.clear();
            List<String> passThroughKeys = cs.getStringList("pass-through-blocks");
            if (passThroughKeys != null && !passThroughKeys.isEmpty()) {
                cfg.passThroughBlockKeys.addAll(passThroughKeys);
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
     * One-time migration: copy challenges from config.yml to challenges.yml if present.
     * Allows existing servers to keep all challenge data when upgrading to the split config.
     */
    private void migrateChallengesFromConfig() {
        try {
            ConfigurationSection from = getConfig().getConfigurationSection("challenges");
            if (from == null || from.getKeys(false).isEmpty()) {
                return;
            }
            // One-time migration: backup current config, then move challenges to challenges.yml
            File configFile = new File(getDataFolder(), "config.yml");
            File backupFile = new File(getDataFolder(), "config.yml.old");
            java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Backed up config to config.yml.old before challenges migration");

            // Copy entire challenges section to challenges.yml
            challengesConfig.set("challenges", from.getValues(true));
            challengesConfig.save(challengesFile);
            getLogger().info("Migrated challenges from config.yml to challenges.yml (" + from.getKeys(false).size() + " challenge(s))");
        } catch (Exception e) {
            getLogger().warning("Failed to migrate challenges to challenges.yml: " + e.getMessage());
        }
    }

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
            
            // Read current and default config as text (UTF-8) to preserve formatting
            List<String> currentLines = java.nio.file.Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            List<String> defaultLines = new ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(defaultConfigTextStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            InputStream defaultConfigYamlStream = getResource("config.yml");
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigYamlStream, StandardCharsets.UTF_8));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Check config version
            int defaultVersion = defaultConfig.getInt("config_version", 1);
            int currentVersion = currentConfig.getInt("config_version", 0); // 0 means old config without version
            
            getLogger().info("Config migration check: current version=" + currentVersion + ", default version=" + defaultVersion);
            
            // Check if there are missing keys in current config that exist in default
            boolean hasMissingKeys = false;
            List<String> missingKeys = new ArrayList<>();
            for (String key : defaultConfig.getKeys(true)) {
                // Skip version key
                if (key.equals("config_version")) {
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
            List<String> mergedLines = mergeConfigs(defaultLines, currentConfig, defaultConfig, currentLines, true);
            
            // Check for and log deprecated keys that were removed
            Set<String> deprecatedKeys = findDeprecatedKeys(currentConfig, defaultConfig);
            if (!deprecatedKeys.isEmpty()) {
                getLogger().info("Removed deprecated config keys: " + String.join(", ", deprecatedKeys));
            }
            
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, "config_version");
            
            // Backup then write merged config (UTF-8)
            File configBackupFile = new File(getDataFolder(), "config.yml.bak");
            java.nio.file.Files.copy(configFile.toPath(), configBackupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.write(configFile.toPath(), mergedLines, StandardCharsets.UTF_8);
            
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
        return mergeConfigs(defaultLines, userConfig, defaultConfig, new ArrayList<>(), true);
    }
    
    /**
     * @param preserveUserSectionContent if true (config), whole sections can be taken from user file when present;
     *        if false (messages), always walk default line-by-line so new keys from default are added
     */
    private List<String> mergeConfigs(List<String> defaultLines, YamlConfiguration userConfig, YamlConfiguration defaultConfig, List<String> originalUserLines, boolean preserveUserSectionContent) {
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
                        } else if (userValue instanceof ConfigurationSection && preserveUserSectionContent) {
                            // Config only: complex nested section (e.g. challenges) - extract from original user file
                            // So we preserve exact user formatting. For messages we use preserveUserSectionContent=false
                            // so we walk default line-by-line and add any new keys.
                            String sectionHeader = line;
                            List<String> userSectionLines = extractSectionFromOriginalLines(originalUserLines, fullPath, currentIndent);
                            if (!userSectionLines.isEmpty()) {
                                merged.add(sectionHeader);
                                merged.addAll(userSectionLines);
                                int sectionStartIndent = currentIndent;
                                while (i + 1 < defaultLines.size()) {
                                    String nextLine = defaultLines.get(i + 1);
                                    String nextTrimmed = nextLine.trim();
                                    if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                        int nextIndent = nextLine.length() - nextTrimmed.length();
                                        if (nextIndent <= sectionStartIndent) break;
                                        i++;
                                    } else {
                                        int nextIndent = nextLine.length() - nextTrimmed.length();
                                        if (nextIndent <= sectionStartIndent) break;
                                        i++;
                                    }
                                }
                                pathStack.push(new Pair<>(keyPart, currentIndent));
                                continue;
                            } else {
                                getLogger().warning("Could not extract section '" + fullPath + "' from original config during migration. Using default structure.");
                                merged.add(line);
                                pathStack.push(new Pair<>(keyPart, currentIndent));
                            }
                        } else if (userValue instanceof ConfigurationSection && !preserveUserSectionContent) {
                            // Messages: do not replace whole section; add header and continue so default's keys (including new ones) are merged
                            merged.add(line);
                            pathStack.push(new Pair<>(keyPart, currentIndent));
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
                    if (keyPart.equals("config_version") || keyPart.equals("messages_version")) {
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
            if (key.equals("config_version") || key.equals("messages_version")) {
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
            if (key.equals("config_version") || key.equals("messages_version")) {
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
        } else if (value instanceof ConfigurationSection) {
            // Bukkit returns ConfigurationSection for nested keys (e.g. world-aliases: {}); never serialize as toString()
            ConfigurationSection sec = (ConfigurationSection) value;
            java.util.Map<String, Object> map = sec.getValues(false);
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
                if (sb.length() > 1) sb.append(", ");
                sb.append(e.getKey()).append(": ").append(formatYamlValue(e.getValue()));
            }
            sb.append("}");
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

    void saveChallengeToConfig(ChallengeConfig cfg) {
        if (challengesConfig == null || challengesFile == null) {
            getLogger().warning("Cannot save challenge '" + cfg.id + "': challenges config not loaded");
            return;
        }
        FileConfiguration config = challengesConfig;
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
                                // Serialize full ItemStack including NBT, custom names, lore, CustomModelData, etc.
                                // This preserves all item data including custom resource pack models/textures
                                rewardMap.put("item-data", reward.itemStack.serialize());
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

        // Save regeneration areas (list format; also keep regeneration for backward compat from first area)
        if (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) {
            List<Map<String, Object>> regenList = new ArrayList<>();
            for (Location[] corners : cfg.regenerationAreas) {
                if (corners == null || corners.length < 2) continue;
                Map<String, Object> entry = new HashMap<>();
                entry.put("world", corners[0].getWorld() != null ? corners[0].getWorld().getName() : "world");
                entry.put("corner1", cornerMap(corners[0]));
                entry.put("corner2", cornerMap(corners[1]));
                regenList.add(entry);
            }
            config.set(path + ".regeneration-areas", regenList);
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                config.set(path + ".regeneration.world", cfg.regenerationCorner1.getWorld().getName());
                config.set(path + ".regeneration.corner1.x", cfg.regenerationCorner1.getX());
                config.set(path + ".regeneration.corner1.y", cfg.regenerationCorner1.getY());
                config.set(path + ".regeneration.corner1.z", cfg.regenerationCorner1.getZ());
                config.set(path + ".regeneration.corner2.x", cfg.regenerationCorner2.getX());
                config.set(path + ".regeneration.corner2.y", cfg.regenerationCorner2.getY());
                config.set(path + ".regeneration.corner2.z", cfg.regenerationCorner2.getZ());
            }
            config.set(path + ".regeneration.auto-extend-y", cfg.regenerationAutoExtendY);
        } else {
            config.set(path + ".regeneration-areas", null);
            config.set(path + ".regeneration", null);
        }

        // Save challenge areas (list format; also keep challenge-area for backward compat from first area)
        if (cfg.challengeAreas != null && !cfg.challengeAreas.isEmpty()) {
            List<Map<String, Object>> challengeList = new ArrayList<>();
            for (Location[] corners : cfg.challengeAreas) {
                if (corners == null || corners.length < 2) continue;
                Map<String, Object> entry = new HashMap<>();
                entry.put("world", corners[0].getWorld() != null ? corners[0].getWorld().getName() : "world");
                entry.put("corner1", cornerMap(corners[0]));
                entry.put("corner2", cornerMap(corners[1]));
                challengeList.add(entry);
            }
            config.set(path + ".challenge-areas", challengeList);
            if (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null) {
                config.set(path + ".challenge-area.world", cfg.challengeCorner1.getWorld().getName());
                config.set(path + ".challenge-area.corner1.x", cfg.challengeCorner1.getX());
                config.set(path + ".challenge-area.corner1.y", cfg.challengeCorner1.getY());
                config.set(path + ".challenge-area.corner1.z", cfg.challengeCorner1.getZ());
                config.set(path + ".challenge-area.corner2.x", cfg.challengeCorner2.getX());
                config.set(path + ".challenge-area.corner2.y", cfg.challengeCorner2.getY());
                config.set(path + ".challenge-area.corner2.z", cfg.challengeCorner2.getZ());
            }
        } else {
            config.set(path + ".challenge-areas", null);
            config.set(path + ".challenge-area", null);
        }

        // Save blocked items
        if (cfg.blockedItems != null && !cfg.blockedItems.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Material m : cfg.blockedItems) {
                names.add(m.name());
            }
            config.set(path + ".blocked-items", names);
        } else {
            config.set(path + ".blocked-items", new ArrayList<>());
        }

        // Save ignored chest keys
        config.set(path + ".ignored-chests", cfg.ignoredChestKeys != null ? new ArrayList<>(cfg.ignoredChestKeys) : new ArrayList<>());
        config.set(path + ".legendary-chests", cfg.legendaryChestKeys != null ? new ArrayList<>(cfg.legendaryChestKeys) : new ArrayList<>());

        // Save block break settings
        config.set(path + ".block-break-enabled", cfg.blockBreakEnabled);
        List<String> allowBreakNames = new ArrayList<>();
        if (cfg.breakAllowedMaterials != null) {
            for (Material m : cfg.breakAllowedMaterials) {
                allowBreakNames.add(m.name());
            }
        }
        config.set(path + ".break-allowed-blocks", allowBreakNames);

        // Save script nodes
        List<Map<String, Object>> scriptsList = new ArrayList<>();
        if (cfg.scriptNodes != null) {
            for (ChallengeScriptNode node : cfg.scriptNodes) {
                Map<String, Object> m = new HashMap<>();
                m.put("world", node.worldName);
                m.put("x", node.blockX);
                m.put("y", node.blockY);
                m.put("z", node.blockZ);
                m.put("trigger", node.trigger.name());
                m.put("function", node.functionType.name());
                m.put("function-data", new HashMap<>(node.functionData));
                List<Map<String, Object>> condCopy = new ArrayList<>();
                for (Map<String, Object> c : node.conditions) {
                    condCopy.add(new HashMap<>(c));
                }
                m.put("conditions", condCopy);
                scriptsList.add(m);
            }
        }
        config.set(path + ".scripts", scriptsList);
        config.set(path + ".pass-through-blocks", cfg.passThroughBlockKeys != null ? new ArrayList<>(cfg.passThroughBlockKeys) : new ArrayList<>());

        try {
            challengesConfig.save(challengesFile);
        } catch (java.io.IOException e) {
            getLogger().warning("Failed to save challenges.yml: " + e.getMessage());
        }

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
            case "setregenerationarea2":
            case "setregenerationarea3":
            case "setregenerationarea4":
            case "clearregenerationarea":
            case "captureregeneration":
            case "setchallengearea":
            case "setchallengearea2":
            case "setchallengearea3":
            case "setchallengearea4":
            case "clearchallengearea":
            case "areasync":
            case "blockitem":
            case "unblockitem":
                // Handled in onTabComplete with material completion
                return Collections.emptyList();
            case "blockbreak":
                return Collections.emptyList();
            case "allowbreak":
            case "disallowbreak":
                for (Material mat : Material.values()) {
                    if (mat.isBlock()) completions.add(mat.name());
                }
                return filterCompletions(completions, partial);
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
        if (challengeId == null) return false;
        Set<UUID> editors = challengeEditors.get(challengeId);
        return editors != null && !editors.isEmpty();
    }
    
    /** Returns any one editor for the challenge (e.g. for messaging); null if none. */
    private UUID getChallengeEditor(String challengeId) {
        Set<UUID> editors = challengeEditors.get(challengeId);
        return (editors != null && !editors.isEmpty()) ? editors.iterator().next() : null;
    }
    
    /** Package-visible for ChallengeScriptManager. */
    Map<String, ChallengeConfig> getChallenges() { return challenges; }
    Map<UUID, String> getActiveChallenges() { return activeChallenges; }

    /**
     * Gets the challenge ID that a player is currently editing, or null if not in edit mode.
     */
    String getPlayerEditingChallenge(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(uuid)) {
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

    /**
     * Formats a chance (0.0–1.0) as an OSRS-style fraction (e.g. 0.01 -> "1/100", 1.0 -> "1/1").
     */
    private String formatChanceAsFraction(double chance) {
        if (chance >= 1.0) return "1/1";
        if (chance <= 0.0) return "0/1";
        int denom = 10000;
        int num = (int) Math.round(chance * denom);
        if (num <= 0) return "0/1";
        if (num >= denom) return "1/1";
        int g = gcd(num, denom);
        num /= g;
        denom /= g;
        return num + "/" + denom;
    }

    private static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
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

    private List<String> antiForgeryRequiredAuthors() {
        // Support:
        // - a single string
        // - a YAML string list
        // - a single comma-separated string ("name1, name2")
        Object raw = getConfig().get("anti-forgery.required-author");
        if (raw == null) return Collections.emptyList();
        if (raw instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) raw;
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    String s = o.toString().trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
            return out;
        }
        String single = raw.toString().trim();
        if (single.isEmpty()) return Collections.emptyList();
        // If the single string contains commas, treat it as a comma-separated list of names.
        if (single.contains(",")) {
            List<String> out = new ArrayList<>();
            for (String part : single.split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        return Collections.singletonList(single);
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
                loadLoottables();
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
                        // For blockitem in edit mode, complete material names (like addtierreward item)
                        if (second.equals("blockitem") && args.length == 3) {
                            for (Material mat : Material.values()) {
                                if (mat.isItem()) completions.add(mat.name());
                            }
                            return filterCompletions(completions, args[2]);
                        }
                        if ((second.equals("allowbreak") || second.equals("disallowbreak")) && args.length == 3) {
                            for (Material mat : Material.values()) {
                                if (mat.isBlock()) completions.add(mat.name());
                            }
                            return filterCompletions(completions, args[2]);
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
                        // For blockitem in edit mode (e.g. /challenge blockitem <tab>), complete material names
                        if (second.equals("blockitem")) {
                            for (Material mat : Material.values()) {
                                if (mat.isItem()) {
                                    completions.add(mat.name());
                                }
                            }
                            return filterCompletions(completions, "");
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
                    
                    // /challenge test <id> <tier> - complete difficulty tier
                    if (second.equals("test")) {
                        List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                        return filterCompletions(tiers, args[3]);
                    }
                    
                    // Not in edit mode: args are [challenge, id, subcommand, param] - complete blockitem <item> like addtierreward item
                    if (!inEditMode && args[2].equalsIgnoreCase("blockitem")) {
                        for (Material mat : Material.values()) {
                            if (mat.isItem()) {
                                completions.add(mat.name());
                            }
                        }
                        return filterCompletions(completions, args[3]);
                    }
                    
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
                    
                    // Tab completion for blockitem <itemid>
                    if (second.equals("blockitem")) {
                        int itemArgIndex = inEditMode ? 1 : 2;
                        if (args.length == itemArgIndex + 1) {
                            for (Material mat : Material.values()) {
                                if (mat.isItem()) {
                                    completions.add(mat.name());
                                }
                            }
                            return filterCompletions(completions, args[itemArgIndex]);
                        }
                    }
                    
                    // Tab completion for unblockitem <itemid>
                    if (second.equals("unblockitem")) {
                        String editId = getChallengeIdForCommand(player, args, 1);
                        if (editId != null) {
                            ChallengeConfig editCfg = challenges.get(editId);
                            if (editCfg != null && editCfg.blockedItems != null && !editCfg.blockedItems.isEmpty()) {
                                int itemArgIndex = inEditMode ? 1 : 2;
                                if (args.length == itemArgIndex + 1) {
                                    for (Material mat : editCfg.blockedItems) {
                                        completions.add(mat.name());
                                    }
                                    return filterCompletions(completions, args[itemArgIndex]);
                                }
                            }
                        }
                    }
                    // Tab completion for allowbreak <material> / disallowbreak <material>
                    if (second.equals("allowbreak") || second.equals("disallowbreak")) {
                        int matArgIndex = inEditMode ? 2 : 3;
                        if (args.length == matArgIndex + 1) {
                            if (second.equals("disallowbreak")) {
                                String editId = getChallengeIdForCommand(player, args, 1);
                                if (editId != null) {
                                    ChallengeConfig editCfg = challenges.get(editId);
                                    if (editCfg != null && editCfg.breakAllowedMaterials != null && !editCfg.breakAllowedMaterials.isEmpty()) {
                                        for (Material mat : editCfg.breakAllowedMaterials) {
                                            completions.add(mat.name());
                                        }
                                        return filterCompletions(completions, args[matArgIndex]);
                                    }
                                }
                            }
                            for (Material mat : Material.values()) {
                                if (mat.isBlock()) completions.add(mat.name());
                            }
                            return filterCompletions(completions, args[matArgIndex]);
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
            // listtierrewards is view-only: allow with conradchallenges.use; all other subcommands require admin
            if (!player.hasPermission("conradchallenges.admin")) {
                if (args.length > 0 && "listtierrewards".equalsIgnoreCase(args[0]) && player.hasPermission("conradchallenges.use")) {
                    return handleAdminChallengeCommands(player, args);
                }
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
            // Non-admins with use permission only get tab completion for listtierrewards
            if (!player.hasPermission("conradchallenges.admin")) {
                if (!player.hasPermission("conradchallenges.use")) {
                    return Collections.emptyList();
                }
                List<String> completions = new ArrayList<>();
                if (args.length == 1) {
                    String partial = args[0].toLowerCase(Locale.ROOT);
                    if ("listtierrewards".startsWith(partial)) {
                        completions.add("listtierrewards");
                    }
                    return completions;
                }
                if (args.length == 2 && "listtierrewards".equalsIgnoreCase(args[0])) {
                    return filterCompletions(new ArrayList<>(challenges.keySet()), args[1]);
                }
                if (args.length == 3 && "listtierrewards".equalsIgnoreCase(args[0])) {
                    List<String> tiers = Arrays.asList("easy", "medium", "hard", "extreme");
                    return filterCompletions(tiers, args[2]);
                }
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
            List<Player> keyHolders = new ArrayList<>();
            
            // Check all party members for completion keys
            for (Player member : partyMembers) {
                ItemStack key = findCompletionKey(member);
                if (key != null) {
                    keyHolders.add(member);
                }
            }

            if (keyHolders.isEmpty()) {
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
            
            // Consume keys from all party members who have them
            for (Player keyHolder : keyHolders) {
                ItemStack key = findCompletionKey(keyHolder);
                if (key != null) {
                    consumeCompletionKey(keyHolder, key);
                    // Notify if it's not the command user
                    if (!keyHolder.equals(player)) {
                        keyHolder.sendMessage(getMessage("completion.key-consumed", "player", player.getName()));
                    }
                }
            }
            
            // Notify command user if multiple keys were consumed
            if (keyHolders.size() > 1) {
                player.sendMessage(getMessage("completion.keys-consumed-multiple", "count", String.valueOf(keyHolders.size())));
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
                
                // Return any blocked-item overflow (items that couldn't fit in ender chest)
                giveBackBlockedItemOverflow(member, challengeId);
                
                // Clean up challenge state
                activeChallenges.remove(memberUuid);
                challengeStartTimes.remove(memberUuid);
                playerLives.remove(memberUuid);
                clearInstanceChestDataForPlayer(memberUuid, challengeId);
            }
            
            // Give rewards to dead party members (easy tier with 0.5x multiplier)
            for (Map.Entry<UUID, String> entry : new HashMap<>(deadPartyMembers).entrySet()) {
                UUID deadPlayerUuid = entry.getKey();
                String deadPlayerChallengeId = entry.getValue();
                
                if (deadPlayerChallengeId.equals(challengeId)) {
                    Player deadPlayer = Bukkit.getPlayer(deadPlayerUuid);
                    if (deadPlayer != null && deadPlayer.isOnline()) {
                        // Give easy tier rewards with 0.5x multiplier
                        runRewardCommandsForDeadPartyMember(deadPlayer, cfg, challengeId);
                        deadPartyMembers.remove(deadPlayerUuid);
                    }
                }
            }
            
                // Clean up difficulty tier if no players are active in this challenge
                cleanupChallengeDifficultyIfEmpty(cfg.id);
            
            // Revert spawner levels back to original before resetting cooldowns
            revertSpawnerLevelsForChallenge(cfg);
            
            // Reset MythicMobs spawner cooldowns in the challenge area
            resetMythicMobsSpawnersInArea(cfg);
            
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

        // /buyback - Buy back into challenge after running out of lives
        Objects.requireNonNull(getCommand("buyback")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            
            // Check if player was in a challenge but ran out of lives
            // First check dead party members
            String challengeId = deadPartyMembers.get(uuid);
            
            // If not found, check pending death teleports (for solo players or recently dead)
            if (challengeId == null) {
                challengeId = pendingDeathTeleports.get(uuid);
            }
            
            if (challengeId == null) {
                player.sendMessage(getMessage("buyback.no-challenge"));
                return true;
            }
            
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }
            
            // Check if economy is available
            if (economy == null) {
                player.sendMessage(getMessage("buyback.no-economy"));
                return true;
            }
            
            // Check if player has enough money
            double balance = economy.getBalance(player);
            if (balance < buyBackFee) {
                player.sendMessage(getMessage("buyback.insufficient-funds", "fee", String.format("%.2f", buyBackFee), "balance", String.format("%.2f", balance)));
                return true;
            }
            
            // Check if challenge is still available (not locked or in use by others)
            if (isChallengeLocked(challengeId)) {
                player.sendMessage(getMessage("buyback.challenge-locked"));
                return true;
            }
            
            // Charge the fee
            economy.withdrawPlayer(player, buyBackFee);
            
            // Reset lives and add player back to challenge
            String tier = activeChallengeDifficulties.getOrDefault(challengeId, "easy");
            int lives = tierLives.getOrDefault(tier, 3);
            playerLives.put(uuid, lives);
            
            // Add back to active challenges
            activeChallenges.put(uuid, challengeId);
            challengeStartTimes.put(uuid, System.currentTimeMillis());
            
            // Remove from dead party members and pending teleports
            deadPartyMembers.remove(uuid);
            pendingDeathTeleports.remove(uuid);
            
            // Teleport player back to challenge destination
            Location challengeDest = getTeleportLocation(cfg);
            if (challengeDest != null) {
                player.teleport(challengeDest);
                player.sendMessage(getMessage("buyback.success", "fee", String.format("%.2f", buyBackFee), "lives", String.valueOf(lives)));
            } else {
                player.sendMessage(getMessage("admin.challenge-no-teleport-location"));
            }
            
            return true;
        });

        // /exit - Exit challenge without completing (no rewards)
        Objects.requireNonNull(getCommand("exit")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("general.players-only"));
                return true;
            }
            if (activeChallenges.get(player.getUniqueId()) == null) {
                player.sendMessage(getMessage("challenge.no-active"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(activeChallenges.get(player.getUniqueId()));
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.config-invalid"));
                return true;
            }
            if (spawnLocation == null) {
                player.sendMessage(getMessage("challenge.spawn-not-configured"));
                return true;
            }
            exitPlayerFromChallenge(player);
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

    /** Parse area number from subcommand e.g. setregenerationarea2 -> 2, setregenerationarea -> 1. */
    private int parseAreaNumber(String subcommand, String prefix) {
        if (subcommand == null || !subcommand.startsWith(prefix)) return 1;
        String suffix = subcommand.substring(prefix.length());
        if (suffix.isEmpty()) return 1;
        try {
            int n = Integer.parseInt(suffix);
            return n >= 1 ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private boolean handleAdminChallengeCommands(Player player, String[] args) {
        // listtierrewards is view-only: allow with conradchallenges.use; all other subcommands require admin
        boolean isListTierRewards = args.length > 0 && "listtierrewards".equalsIgnoreCase(args[0]);
        if (!isListTierRewards && !player.hasPermission("conradchallenges.admin")) {
            player.sendMessage(getMessage("general.no-permission"));
            return true;
        }
        if (isListTierRewards && !player.hasPermission("conradchallenges.use")) {
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
            player.sendMessage(getMessage("admin.challenge-blockitem-command"));
            player.sendMessage(getMessage("admin.challenge-unblockitem-command"));
            player.sendMessage(getMessage("admin.challenge-help-loottable"));
            player.sendMessage(getMessage("admin.challenge-help-chestignore"));
            player.sendMessage(getMessage("admin.challenge-help-loottype"));
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
            
            // Remove from challenges file
            if (challengesConfig != null && challengesFile != null) {
                challengesConfig.set("challenges." + id, null);
                try {
                    challengesConfig.save(challengesFile);
                } catch (java.io.IOException e) {
                    getLogger().warning("Failed to save challenges.yml: " + e.getMessage());
                }
            }
            
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
                player.sendMessage(getMessage("rewards-cmd.addreward-usage"));
                player.sendMessage(getMessage("rewards-cmd.addreward-types"));
                player.sendMessage(getMessage("rewards-cmd.addreward-chance"));
                return true;
            }
            
            String typeStr = args[argStartIndex].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("rewards-cmd.addreward-invalid-type"));
                return true;
            }
            
            TierReward reward = null;
            double chance = 1.0;
            
            switch (type) {
                case HAND -> {
                    if (args.length < argStartIndex + 1) {
                        player.sendMessage(getMessage("rewards-cmd.addreward-hand-usage"));
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
                        player.sendMessage(getMessage("rewards-cmd.addreward-hold-item"));
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage(getMessage("rewards-cmd.addreward-item-usage"));
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
                        player.sendMessage(getMessage("rewards-cmd.addreward-invalid-material", "material", args[argStartIndex + 1]));
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage(getMessage("rewards-cmd.addreward-command-usage"));
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
                player.sendMessage(getMessage("rewards-cmd.addreward-create-failed"));
                return true;
            }
            
            // Convert TierReward to RewardWithChance for fallback rewards
            String cmd = null;
            switch (reward.type) {
                case HAND -> {
                    if (reward.itemStack != null) {
                        cmd = "give %player% " + reward.itemStack.getType().name() + " " + reward.itemStack.getAmount();
                    } else {
                        player.sendMessage(getMessage("rewards-cmd.addreward-no-item"));
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
                player.sendMessage(getMessage("rewards-cmd.addreward-command-failed"));
                return true;
            }
            
            cfg.fallbackRewardCommands.add(new RewardWithChance(cmd, reward.chance));
            saveChallengeToConfig(cfg);
            String chanceStr = formatChanceAsFraction(reward.chance);
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
                player.sendMessage(getMessage("rewards-cmd.addtierreward-usage"));
                player.sendMessage(getMessage("rewards-cmd.addtierreward-tiers"));
                player.sendMessage(getMessage("rewards-cmd.addreward-types"));
                player.sendMessage(getMessage("rewards-cmd.addreward-chance"));
                return true;
            }
            
            String tier = args[argStartIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-tier"));
                return true;
            }
            
            String typeStr = args[argStartIndex + 1].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("rewards-cmd.addreward-invalid-type"));
                return true;
            }
            
            TierReward reward = null;
            double chance = 1.0;
            
            switch (type) {
                case HAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage(getMessage("rewards-cmd.addtierreward-hand-usage"));
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
                        player.sendMessage(getMessage("rewards-cmd.addreward-hold-item"));
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 3) {
                        player.sendMessage(getMessage("rewards-cmd.addtierreward-item-usage"));
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
                                            player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-amount"));
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
                                    player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-amount"));
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
                        player.sendMessage(getMessage("rewards-cmd.addreward-invalid-material", "material", args[argStartIndex + 2]));
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 3) {
                        player.sendMessage(getMessage("rewards-cmd.addtierreward-command-usage"));
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
                player.sendMessage(getMessage("rewards-cmd.addreward-create-failed"));
                return true;
            }
            
            cfg.difficultyTierRewards.computeIfAbsent(tier, k -> new ArrayList<>()).add(reward);
            saveChallengeToConfig(cfg);
            String chanceStr = formatChanceAsFraction(reward.chance);
            player.sendMessage(getMessage("rewards-cmd.addtierreward-added", "type", typeStr, "tier", tier, "chance", chanceStr));
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
                player.sendMessage(getMessage("rewards-cmd.cleartierrewards-usage"));
                player.sendMessage(getMessage("rewards-cmd.addtierreward-tiers"));
                return true;
            }
            String tier = args[argIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-tier"));
                return true;
            }
            List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
            if (rewards == null || rewards.isEmpty()) {
                player.sendMessage(getMessage("rewards-cmd.cleartierrewards-no-rewards", "tier", tier));
                return true;
            }
            rewards.clear();
            if (rewards.isEmpty()) {
                cfg.difficultyTierRewards.remove(tier);
            }
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("rewards-cmd.cleartierrewards-cleared", "tier", tier));
            return true;
        }

        if (sub.equals("listtierrewards")) {
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                if (isPlayerInEditMode(player)) {
                    player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                } else {
                    player.sendMessage(getMessage("rewards-cmd.listtierrewards-usage"));
                    player.sendMessage(getMessage("rewards-cmd.addtierreward-tiers"));
                }
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
                filterTier = args[argIndex].toLowerCase().trim();
                if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(filterTier)) {
                    player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-tier"));
                    return true;
                }
            }
            
            List<String> tiersToShow = filterTier != null ? Arrays.asList(filterTier) : Arrays.asList("easy", "medium", "hard", "extreme");
            boolean foundAny = false;
            for (String tier : tiersToShow) {
                List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
                if (rewards != null && !rewards.isEmpty()) {
                    foundAny = true;
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("rewards-cmd.listtierrewards-header", "tier", tier.toUpperCase(), "count", String.valueOf(rewards.size()))));
                    for (int i = 0; i < rewards.size(); i++) {
                        TierReward reward = rewards.get(i);
                        String chanceStr = formatChanceAsFraction(reward.chance);
                        switch (reward.type) {
                            case HAND -> {
                                if (reward.itemStack != null) {
                                    String itemName = getItemDisplayName(reward.itemStack);
                                    String itemInfo = itemName + " x" + reward.itemStack.getAmount();
                                    player.sendMessage(getMessage("rewards-cmd.listtierrewards-hand", "num", String.valueOf(i + 1), "info", itemInfo, "chance", chanceStr));
                                }
                            }
                            case ITEM -> {
                                player.sendMessage(getMessage("rewards-cmd.listtierrewards-item", "num", String.valueOf(i + 1), "material", reward.material.name(), "amount", String.valueOf(reward.amount), "chance", chanceStr));
                            }
                            case COMMAND -> {
                                player.sendMessage(getMessage("rewards-cmd.listtierrewards-command", "num", String.valueOf(i + 1), "command", reward.command, "chance", chanceStr));
                            }
                        }
                    }
                }
            }
            if (!foundAny) {
                player.sendMessage(getMessage("rewards-cmd.listtierrewards-empty", "filter", filterTier != null ? getMessage("rewards-cmd.listtierrewards-filter", "tier", filterTier) : ""));
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
                player.sendMessage(getMessage("rewards-cmd.removereward-usage"));
                player.sendMessage(getMessage("rewards-cmd.addtierreward-tiers"));
                player.sendMessage(getMessage("rewards-cmd.removereward-use-list"));
                return true;
            }
            
            String tier = args[argStartIndex].toLowerCase();
            if (!Arrays.asList("easy", "medium", "hard", "extreme").contains(tier)) {
                player.sendMessage(getMessage("rewards-cmd.addtierreward-invalid-tier"));
                return true;
            }
            
            List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
            if (rewards == null || rewards.isEmpty()) {
                player.sendMessage(getMessage("rewards-cmd.cleartierrewards-no-rewards", "tier", tier));
                return true;
            }
            
            try {
                int rewardNumber = Integer.parseInt(args[argStartIndex + 1]);
                if (rewardNumber < 1 || rewardNumber > rewards.size()) {
                    player.sendMessage(getMessage("rewards-cmd.removereward-invalid-number", "max", String.valueOf(rewards.size())));
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
                player.sendMessage(getMessage("rewards-cmd.removereward-removed", "num", String.valueOf(rewardNumber), "tier", tier, "type", rewardType));
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("rewards-cmd.removereward-invalid-arg", "arg", args[argStartIndex + 1]));
                player.sendMessage(getMessage("rewards-cmd.removereward-see-list", "tier", tier));
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
                player.sendMessage(getMessage("rewards-cmd.addtoptimereward-usage"));
                player.sendMessage(getMessage("rewards-cmd.addreward-types"));
                player.sendMessage(getMessage("rewards-cmd.addreward-chance"));
                player.sendMessage(getMessage("rewards-cmd.addtoptimereward-note"));
                return true;
            }
            
            String typeStr = args[argStartIndex].toLowerCase();
            TierReward.Type type;
            try {
                type = TierReward.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("rewards-cmd.addreward-invalid-type"));
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
                        player.sendMessage(getMessage("rewards-cmd.addtoptimereward-hand-usage"));
                        return true;
                    }
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem == null || handItem.getType().isAir()) {
                        player.sendMessage(getMessage("rewards-cmd.addreward-hold-item"));
                        return true;
                    }
                    reward.itemStack = handItem.clone();
                }
                case ITEM -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage(getMessage("rewards-cmd.addtoptimereward-item-usage"));
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
                        player.sendMessage(getMessage("rewards-cmd.addreward-invalid-material", "material", args[argStartIndex + 1]));
                        return true;
                    }
                }
                case COMMAND -> {
                    if (args.length < argStartIndex + 2) {
                        player.sendMessage(getMessage("rewards-cmd.addtoptimereward-command-usage"));
                        return true;
                    }
                    reward.command = String.join(" ", Arrays.copyOfRange(args, argStartIndex + 1, argEndIndex));
                }
            }
            
            cfg.topTimeRewards.add(reward);
            saveChallengeToConfig(cfg);
            String chanceStr = formatChanceAsFraction(chance);
            player.sendMessage(getMessage("rewards-cmd.addtoptimereward-added", "type", typeStr, "chance", chanceStr));
            return true;
        }

        if (sub.startsWith("setregenerationarea") && (sub.length() == 20 || sub.substring(20).matches("\\d+"))) {
            int areaNumber = parseAreaNumber(sub, "setregenerationarea");
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
            challengeInitialStates.remove(id);
            challengeContainerInventories.remove(id);
            challengeContainerNBT.remove(id);
            challengeRegenerationStatus.remove(id);
            Location[] weSelection = getWorldEditSelection(player);
            if (weSelection != null && weSelection.length == 2) {
                Location corner1Loc = weSelection[0];
                Location corner2Loc = weSelection[1];
                String overlappingChallenge = checkAreaOverlap(corner1Loc, corner2Loc, id, true, true);
                if (overlappingChallenge != null) {
                    player.sendMessage(getMessage("admin.challenge-regen-overlap", "id", overlappingChallenge));
                    return true;
                }
                while (cfg.regenerationAreas.size() < areaNumber) cfg.regenerationAreas.add(null);
                cfg.regenerationAreas.set(areaNumber - 1, new Location[]{ corner1Loc, corner2Loc });
                syncRegenLegacyFromList(cfg);
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-corner1", "id", id,
                    "x", String.valueOf(corner1Loc.getBlockX()), "y", String.valueOf(corner1Loc.getBlockY()), "z", String.valueOf(corner1Loc.getBlockZ())));
                player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-corner2", "id", id,
                    "x", String.valueOf(corner2Loc.getBlockX()), "y", String.valueOf(corner2Loc.getBlockY()), "z", String.valueOf(corner2Loc.getBlockZ())));
                if (areaNumber > 1) player.sendMessage(ChatColor.GREEN + "Regeneration area " + areaNumber + " set.");
                player.sendMessage(getMessage("admin.challenge-capturing-initial-state"));
                final Player finalPlayer = player;
                captureInitialState(cfg, success -> {
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                        else finalPlayer.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                    }
                });
            } else if (areaNumber > 1) {
                player.sendMessage(ChatColor.YELLOW + "For regeneration area " + areaNumber + " and above, use WorldEdit selection (//1 and //2).");
                return true;
            } else {
                Location currentLoc = player.getLocation();
                if (cfg.regenerationCorner1 == null) {
                    cfg.regenerationCorner1 = currentLoc;
                    if (cfg.regenerationAreas.isEmpty()) cfg.regenerationAreas.add(null);
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner1", "id", id,
                        "x", String.valueOf(currentLoc.getBlockX()), "y", String.valueOf(currentLoc.getBlockY()), "z", String.valueOf(currentLoc.getBlockZ())));
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-hint"));
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-we-hint"));
                } else if (cfg.regenerationCorner2 == null) {
                    String overlappingChallenge = checkAreaOverlap(cfg.regenerationCorner1, currentLoc, id, true, true);
                    if (overlappingChallenge != null) {
                        player.sendMessage(getMessage("admin.challenge-regen-overlap", "id", overlappingChallenge));
                        return true;
                    }
                    cfg.regenerationCorner2 = currentLoc;
                    if (cfg.regenerationAreas.isEmpty()) cfg.regenerationAreas.add(new Location[]{ cfg.regenerationCorner1, cfg.regenerationCorner2 });
                    else cfg.regenerationAreas.set(0, new Location[]{ cfg.regenerationCorner1, cfg.regenerationCorner2 });
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner2", "id", id,
                        "x", String.valueOf(currentLoc.getBlockX()), "y", String.valueOf(currentLoc.getBlockY()), "z", String.valueOf(currentLoc.getBlockZ())));
                    player.sendMessage(getMessage("admin.challenge-capturing-initial-state"));
                    final Player fp2 = player;
                    captureInitialState(cfg, success -> {
                        if (fp2 != null && fp2.isOnline()) {
                            if (success) fp2.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                            else fp2.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
                        }
                    });
                } else {
                    String overlappingChallenge = checkAreaOverlap(cfg.regenerationCorner1, currentLoc, id, true, true);
                    if (overlappingChallenge != null) {
                        player.sendMessage(getMessage("admin.challenge-regen-overlap", "id", overlappingChallenge));
                        return true;
                    }
                    cfg.regenerationCorner2 = currentLoc;
                    if (!cfg.regenerationAreas.isEmpty()) cfg.regenerationAreas.set(0, new Location[]{ cfg.regenerationCorner1, cfg.regenerationCorner2 });
                    player.sendMessage(getMessage("admin.challenge-setregenerationarea-corner2-updated", "id", id,
                        "x", String.valueOf(currentLoc.getBlockX()), "y", String.valueOf(currentLoc.getBlockY()), "z", String.valueOf(currentLoc.getBlockZ())));
                    player.sendMessage(getMessage("admin.challenge-capturing-initial-state"));
                    final Player fp3 = player;
                    captureInitialState(cfg, success -> {
                        if (fp3 != null && fp3.isOnline()) {
                            if (success) fp3.sendMessage(getMessage("admin.challenge-setregenerationarea-captured"));
                            else fp3.sendMessage(getMessage("admin.challenge-setregenerationarea-capture-failed"));
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
            cfg.regenerationAreas.clear();
            cfg.regenerationCorner1 = null;
            cfg.regenerationCorner2 = null;
            cfg.regenerationAutoExtendY = false;
            challengeInitialStates.remove(id);
            challengeContainerInventories.remove(id); // Also clear stored container inventories
            challengeContainerNBT.remove(id); // Also clear stored container NBT data // Clear initial states from memory
            challengeRegenerationStatus.remove(id); // Clear regeneration status
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-clearregenerationarea-success", "id", id));
            return true;
        }

        if (sub.startsWith("setchallengearea") && (sub.length() == 16 || sub.substring(16).matches("\\d+"))) {
            int areaNumber = parseAreaNumber(sub, "setchallengearea");
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-setchallengearea-usage"));
                player.sendMessage(getMessage("admin.challenge-setchallengearea-edit-mode"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-setchallengearea-usage"));
                player.sendMessage(getMessage("admin.challenge-setchallengearea-edit-mode"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            Location[] weSelection = getWorldEditSelection(player);
            if (weSelection == null || weSelection.length != 2) {
                player.sendMessage(getMessage("admin.challenge-no-worldedit-selection"));
                player.sendMessage(getMessage("admin.challenge-worldedit-selection-tip"));
                return true;
            }
            Location corner1Loc = weSelection[0];
            Location corner2Loc = weSelection[1];
            String overlappingChallenge = checkAreaOverlap(corner1Loc, corner2Loc, id, true, true);
            if (overlappingChallenge != null) {
                player.sendMessage(getMessage("admin.challenge-area-overlap", "id", overlappingChallenge));
                return true;
            }
            while (cfg.challengeAreas.size() < areaNumber) cfg.challengeAreas.add(null);
            cfg.challengeAreas.set(areaNumber - 1, new Location[]{ corner1Loc, corner2Loc });
            syncChallengeLegacyFromList(cfg);
            player.sendMessage(getMessage("admin.challenge-setchallengearea-success", "id", id));
            player.sendMessage(getMessage("admin.challenge-setchallengearea-corner1", "x", String.valueOf(corner1Loc.getBlockX()), "y", String.valueOf(corner1Loc.getBlockY()), "z", String.valueOf(corner1Loc.getBlockZ())));
            player.sendMessage(getMessage("admin.challenge-setchallengearea-corner2", "x", String.valueOf(corner2Loc.getBlockX()), "y", String.valueOf(corner2Loc.getBlockY()), "z", String.valueOf(corner2Loc.getBlockZ())));
            if (areaNumber > 1) player.sendMessage(ChatColor.GREEN + "Challenge area " + areaNumber + " set.");
            if (areaNumber == 1) {
                player.sendMessage(getMessage("admin.challenge-capturing-challenge-state"));
                final Player finalPlayer = player;
                captureChallengeAreaState(cfg, success -> {
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) finalPlayer.sendMessage(getMessage("admin.challenge-state-captured-success"));
                        else finalPlayer.sendMessage(getMessage("admin.challenge-state-capture-failed"));
                    }
                });
                if (barrierBoxingEnabled) {
                    player.sendMessage(getMessage("admin.challenge-creating-barrier"));
                    createBarrierBox(cfg, player);
                }
            }
            saveChallengeToConfig(cfg);
            return true;
        }

        if (sub.equals("clearchallengearea")) {
            // Require edit mode
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-clearchallengearea-usage"));
                player.sendMessage(getMessage("admin.challenge-clearchallengearea-edit-mode"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-clearchallengearea-usage"));
                player.sendMessage(getMessage("admin.challenge-clearchallengearea-edit-mode"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            
            if (barrierBoxingEnabled && cfg.challengeCorner1 != null && cfg.challengeCorner2 != null) {
                removeBarrierBox(cfg, player);
            }
            cfg.challengeAreas.clear();
            cfg.challengeCorner1 = null;
            cfg.challengeCorner2 = null;
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-clearchallengearea-cleared", "id", id));
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
            
            // Copy all challenge areas to regeneration areas
            cfg.regenerationAreas.clear();
            if (cfg.challengeAreas != null) {
                for (Location[] c : cfg.challengeAreas) {
                    if (c != null && c.length >= 2) cfg.regenerationAreas.add(new Location[]{ c[0].clone(), c[1].clone() });
                }
            }
            syncRegenLegacyFromList(cfg);
            if (cfg.regenerationCorner1 == null) {
                player.sendMessage(getMessage("admin.challenge-areasync-no-challenge-area", "id", id));
                return true;
            }
            // Clear any old initial states when setting a new regeneration area
            challengeInitialStates.remove(id);
            challengeContainerInventories.remove(id); // Also clear stored container inventories
            challengeContainerNBT.remove(id); // Also clear stored container NBT data
            challengeRegenerationStatus.remove(id);
            
            player.sendMessage(getMessage("admin.challenge-areasync-success", "id", id,
                "x1", String.valueOf(cfg.regenerationCorner1.getBlockX()),
                "y1", String.valueOf(cfg.regenerationCorner1.getBlockY()),
                "z1", String.valueOf(cfg.regenerationCorner1.getBlockZ()),
                "x2", String.valueOf(cfg.regenerationCorner2.getBlockX()),
                "y2", String.valueOf(cfg.regenerationCorner2.getBlockY()),
                "z2", String.valueOf(cfg.regenerationCorner2.getBlockZ())));
            
            // Re-capture initial state (async)
            player.sendMessage(getMessage("admin.challenge-capturing-initial-state"));
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
            player.sendMessage(getMessage("admin.challenge-capturing-initial-state-new-y"));
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

        if (sub.equals("blockitem")) {
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-blockitem-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-blockitem-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            int itemArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= itemArgIndex) {
                player.sendMessage(getMessage("admin.challenge-blockitem-usage"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            String itemName = args[itemArgIndex].toUpperCase(Locale.ROOT);
            Material mat;
            try {
                mat = Material.valueOf(itemName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-blockitem-invalid", "item", args[itemArgIndex]));
                return true;
            }
            if (!mat.isItem()) {
                player.sendMessage(getMessage("admin.challenge-blockitem-not-item", "item", mat.name()));
                return true;
            }
            if (cfg.blockedItems.add(mat)) {
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-blockitem-added", "id", id, "item", mat.name()));
            } else {
                player.sendMessage(getMessage("admin.challenge-blockitem-already", "id", id, "item", mat.name()));
            }
            return true;
        }

        if (sub.equals("unblockitem")) {
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-unblockitem-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-unblockitem-usage"));
                player.sendMessage(getMessage("admin.challenge-edit-mode-required"));
                return true;
            }
            int itemArgIndex = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= itemArgIndex) {
                player.sendMessage(getMessage("admin.challenge-unblockitem-usage"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            String itemName = args[itemArgIndex].toUpperCase(Locale.ROOT);
            Material mat;
            try {
                mat = Material.valueOf(itemName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-blockitem-invalid", "item", args[itemArgIndex]));
                return true;
            }
            if (cfg.blockedItems.remove(mat)) {
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-unblockitem-removed", "id", id, "item", mat.name()));
            } else {
                player.sendMessage(getMessage("admin.challenge-unblockitem-not-blocked", "id", id, "item", mat.name()));
            }
            return true;
        }

        if (sub.equals("blockbreak")) {
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-blockbreak-edit-mode"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-blockbreak-hint"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            cfg.blockBreakEnabled = !cfg.blockBreakEnabled;
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-blockbreak-toggled", "id", id, "status", cfg.blockBreakEnabled ? getMessage("admin.challenge-blockbreak-toggled-restricted") : getMessage("admin.challenge-blockbreak-toggled-off")));
            return true;
        }

        if (sub.equals("allowbreak")) {
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-blockbreak-edit-mode"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-allowbreak-hint"));
                return true;
            }
            int argIdx = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argIdx) {
                player.sendMessage(getMessage("admin.challenge-allowbreak-usage"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            String matName = args[argIdx].toUpperCase(Locale.ROOT);
            Material mat;
            try {
                mat = Material.valueOf(matName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-allowbreak-unknown-material", "material", args[argIdx]));
                return true;
            }
            if (!mat.isBlock()) {
                player.sendMessage(getMessage("admin.challenge-allowbreak-not-a-block", "material", mat.name()));
                return true;
            }
            if (cfg.breakAllowedMaterials.add(mat)) {
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-allowbreak-added", "material", mat.name(), "id", id));
            } else {
                player.sendMessage(getMessage("admin.challenge-allowbreak-already", "material", mat.name()));
            }
            return true;
        }

        if (sub.equals("disallowbreak")) {
            if (!isPlayerInEditMode(player)) {
                player.sendMessage(getMessage("admin.challenge-blockbreak-edit-mode"));
                return true;
            }
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-disallowbreak-hint"));
                return true;
            }
            int argIdx = isPlayerInEditMode(player) ? 1 : 2;
            if (args.length <= argIdx) {
                player.sendMessage(getMessage("admin.challenge-disallowbreak-usage"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            String matName = args[argIdx].toUpperCase(Locale.ROOT);
            Material mat;
            try {
                mat = Material.valueOf(matName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(getMessage("admin.challenge-allowbreak-unknown-material", "material", args[argIdx]));
                return true;
            }
            if (cfg.breakAllowedMaterials.remove(mat)) {
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-disallowbreak-removed", "material", mat.name(), "id", id));
            } else {
                player.sendMessage(getMessage("admin.challenge-disallowbreak-not-in-list", "material", mat.name()));
            }
            return true;
        }

        if (sub.equals("captureregeneration")) {
            // With edit mode: optional <id> (defaults to challenge being edited). Without edit mode: require <id>.
            String id = getChallengeIdForCommand(player, args, 1);
            if (id == null) {
                player.sendMessage(getMessage("admin.challenge-captureregeneration-usage"));
                player.sendMessage(getMessage("admin.challenge-provide-id-or-edit"));
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
            player.sendMessage(getMessage("admin.challenge-capturing-initial-state"));
            captureInitialState(cfg, success -> {
                if (success) {
                    player.sendMessage(getMessage("admin.challenge-captureregeneration-success", "id", id));
                } else {
                    player.sendMessage(getMessage("admin.challenge-captureregeneration-failed", "id", id));
                }
            });
            return true;
        }

        if (sub.equals("loottable")) {
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            openLoottableMainGui(player);
            return true;
        }

        if (sub.equals("chestignore")) {
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            Block target = player.getTargetBlockExact(6);
            if (target == null || (!target.getType().equals(Material.CHEST) && !target.getType().equals(Material.TRAPPED_CHEST))) {
                player.sendMessage(getMessage("admin.challenge-chestignore-look"));
                return true;
            }
            String challengeId = getPlayerEditingChallenge(player);
            if (challengeId == null) {
                challengeId = getChallengeIdForChestLocation(target.getLocation());
            }
            if (challengeId == null) {
                player.sendMessage(getMessage("admin.challenge-chestignore-not-in-regen"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", challengeId));
                return true;
            }
            List<Block> toToggle = getChestBlocksForIgnore(target);
            if (cfg.ignoredChestKeys == null) cfg.ignoredChestKeys = new HashSet<>();
            boolean anyIgnored = false;
            for (Block b : toToggle) {
                if (cfg.ignoredChestKeys.contains(chestLocationKey(b.getLocation()))) {
                    anyIgnored = true;
                    break;
                }
            }
            if (anyIgnored) {
                for (Block b : toToggle) {
                    cfg.ignoredChestKeys.remove(chestLocationKey(b.getLocation()));
                }
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-chestignore-unignored", "id", challengeId));
            } else {
                int added = 0;
                for (Block b : toToggle) {
                    String key = chestLocationKey(b.getLocation());
                    if (cfg.ignoredChestKeys.add(key)) added++;
                }
                saveChallengeToConfig(cfg);
                player.sendMessage(getMessage("admin.challenge-chestignore-ignored", "id", challengeId, "detail", toToggle.size() > 1 ? getMessage("admin.challenge-chestignore-detail-double") : getMessage("admin.challenge-chestignore-detail-single")));
            }
            return true;
        }

        if (sub.equals("loottype")) {
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            String challengeId = getPlayerEditingChallenge(player);
            if (challengeId == null) {
                player.sendMessage(getMessage("admin.challenge-blockbreak-edit-mode"));
                player.sendMessage(getMessage("admin.challenge-loottype-look-hint"));
                return true;
            }
            Block target = player.getTargetBlockExact(6);
            if (target == null || (!target.getType().equals(Material.CHEST) && !target.getType().equals(Material.TRAPPED_CHEST))) {
                player.sendMessage(getMessage("admin.challenge-loottype-look"));
                return true;
            }
            ChallengeConfig cfg = challenges.get(challengeId);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", challengeId));
                return true;
            }
            if (!isLocationInRegenArea(cfg, target.getLocation())) {
                player.sendMessage(getMessage("admin.challenge-loottype-not-in-regen"));
                return true;
            }
            if (isChestIgnored(cfg, target)) {
                player.sendMessage(getMessage("admin.challenge-loottype-ignored-unignore-first"));
                return true;
            }
            String key = getCanonicalChestKey(target);
            if (key == null) return true;
            if (cfg.legendaryChestKeys == null) cfg.legendaryChestKeys = new HashSet<>();
            boolean nowLegendary;
            if (cfg.legendaryChestKeys.contains(key)) {
                cfg.legendaryChestKeys.remove(key);
                nowLegendary = false;
            } else {
                cfg.legendaryChestKeys.add(key);
                nowLegendary = true;
            }
            saveChallengeToConfig(cfg);
            player.sendMessage(getMessage("admin.challenge-loottype-toggled", "type", nowLegendary ? getMessage("admin.challenge-loottype-legendary") : getMessage("admin.challenge-loottype-normal"), "pool", nowLegendary ? getMessage("admin.challenge-loottype-pool-legendary") : getMessage("admin.challenge-loottype-pool-normal")));
            return true;
        }

        if (sub.equals("functiontool")) {
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            scriptManager.giveFunctionTool(player);
            player.sendMessage(ChatColor.GREEN + "Function tool added. Use it in edit mode by right-clicking a block.");
            return true;
        }

        if (sub.equals("test")) {
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(getMessage("general.no-permission"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(getMessage("admin.challenge-test-usage"));
                return true;
            }
            String id = args[1];
            ChallengeConfig cfg = challenges.get(id);
            if (cfg == null) {
                player.sendMessage(getMessage("challenge.unknown-id", "id", id));
                return true;
            }
            if (cfg.destination == null) {
                player.sendMessage(getMessage("admin.challenge-no-destination-admin"));
                return true;
            }
            UUID uuid = player.getUniqueId();
            // Do not allow test if challenge is currently being edited by anyone.
            if (isChallengeBeingEdited(id)) {
                player.sendMessage(getMessage("admin.challenge-test-being-edited"));
                return true;
            }
            // Do not allow test if any player is currently running this challenge.
            for (Map.Entry<UUID, String> entry : activeChallenges.entrySet()) {
                if (id.equals(entry.getValue())) {
                    player.sendMessage(getMessage("admin.challenge-test-active"));
                    return true;
                }
            }
            // Optional difficulty tier: /challenge test <id> [easy|medium|hard|extreme]
            String tierArg = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
            String selectedTier = "easy";
            if (tierArg != null) {
                if (tierArg.equals("easy") || tierArg.equals("medium") || tierArg.equals("hard") || tierArg.equals("extreme")) {
                    selectedTier = tierArg;
                } else {
                    player.sendMessage(getMessage("accept.invalid-tier", "tier", tierArg));
                    player.sendMessage(getMessage("accept.valid-tiers"));
                    return true;
                }
            }
            // Ignore lockdown, cooldown, queue, pending, /accept, and countdown.
            // Teleport the admin straight into a fresh instance of the challenge.
            // Regenerate area if configured.
            // Store original location so /exit can return the tester back.
            testOriginalLocations.put(uuid, player.getLocation());
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null && !isChallengeBeingEdited(cfg.id)) {
                regenerateChallengeArea(cfg);
            } else {
                challengeRegenerationStatus.put(cfg.id, true);
            }
            activeChallenges.put(uuid, cfg.id);
            activeChallengeDifficulties.put(cfg.id, selectedTier);
            challengeStartTimes.put(uuid, System.currentTimeMillis());
            Location teleportLoc = getTeleportLocation(cfg);
            if (teleportLoc != null) {
                player.teleport(teleportLoc);
            }
            player.sendMessage(getMessage("admin.challenge-test-entered", "challenge", displayName(cfg), "tier", selectedTier));
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
            
            // If already in edit mode for this challenge, do not re-enter or teleport
            Set<UUID> currentEditors = challengeEditors.get(id);
            if (currentEditors != null && currentEditors.contains(player.getUniqueId())) {
                player.sendMessage(getMessage("admin.challenge-edit-already-in-edit-mode"));
                return true;
            }
            
            // Note: Admins can enter edit mode even if challenge is locked down
            // Multiple admins can edit the same challenge at once.
            
            // Check if player is already editing another challenge
            for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
                if (entry.getValue() != null && entry.getValue().contains(player.getUniqueId()) && !entry.getKey().equals(id)) {
                    player.sendMessage(getMessage("admin.challenge-edit-already-editing-another", "id", entry.getKey()));
                    player.sendMessage(getMessage("admin.challenge-edit-cancel-hint"));
                    return true;
                }
            }
            
            // Do not allow edit mode if any player is currently running this challenge (active or test)
            if (activeChallenges.containsValue(id)) {
                player.sendMessage(getMessage("admin.challenge-edit-active"));
                return true;
            }
            
            if (cfg.destination == null) {
                player.sendMessage(getMessage("challenge.no-destination"));
                return true;
            }
            
            // Get or create editor set for this challenge
            Set<UUID> editors = challengeEditors.computeIfAbsent(id, k -> new HashSet<>());
            boolean firstEditor = editors.isEmpty();
            editors.add(player.getUniqueId());
            
            // Save original location for this editor
            editorOriginalLocations.put(player.getUniqueId(), player.getLocation());
            
            // Only create backup and regenerate when the first editor enters
            if (firstEditor) {
                ChallengeConfig backup = cloneChallengeConfig(cfg);
                challengeEditBackups.put(id, backup);
                if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                    List<BlockState> stateBackup = challengeInitialStates.get(id);
                    if (stateBackup != null) {
                        List<BlockState> backupStates = new ArrayList<>();
                        for (BlockState state : stateBackup) {
                            backupStates.add(state);
                        }
                        challengeEditStateBackups.put(id, backupStates);
                    }
                }
            }
            
            // Regenerate the area only for the first editor (clean state); then teleport
            if (firstEditor && cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                regenerateChallengeArea(cfg);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Location teleportLoc = getTeleportLocation(cfg);
                        if (teleportLoc != null) {
                            player.teleport(teleportLoc);
                            player.sendMessage(getMessage("admin.challenge-edit-entered", "id", id));
                            player.sendMessage(getMessage("admin.challenge-edit-instructions"));
                        } else {
                            player.sendMessage(getMessage("admin.challenge-no-teleport-location"));
                        }
                        scriptManager.giveFunctionTool(player);
                    }
                }.runTaskLater(this, 20L); // 1 second delay
            } else {
                // Not first editor, or no regen area: teleport immediately
                Location teleportLoc = getTeleportLocation(cfg);
                if (teleportLoc != null) {
                    player.teleport(teleportLoc);
                    player.sendMessage(getMessage("admin.challenge-edit-entered", "id", id));
                    player.sendMessage(getMessage("admin.challenge-edit-instructions"));
                } else {
                    player.sendMessage(getMessage("admin.challenge-no-teleport-location"));
                }
                scriptManager.giveFunctionTool(player);
            }
            
            return true;
        }

        if (sub.equals("save")) {
            // Find which challenge this player is editing
            String editingChallengeId = null;
            for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
                if (entry.getValue() != null && entry.getValue().contains(player.getUniqueId())) {
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
                removeEditorFromChallenge(player, editingChallengeId);
                return true;
            }
            
            // Check if challenge area is set - required for saving
            if (cfg.challengeCorner1 == null || cfg.challengeCorner2 == null) {
                player.sendMessage(getMessage("admin.challenge-save-no-area"));
                player.sendMessage(getMessage("admin.challenge-save-use-setcommand", "id", editingChallengeId));
                return true;
            }
            
            // Recapture regeneration area state (not challenge area - that's only captured when set)
            if (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                player.sendMessage(getMessage("admin.challenge-recapturing-state"));
                final Player finalPlayer = player; // Capture player reference for callback
                captureInitialState(cfg, success -> {
                    // Callback runs on main thread, so we can safely access player
                    if (finalPlayer != null && finalPlayer.isOnline()) {
                        if (success) {
                            finalPlayer.sendMessage(getMessage("admin.challenge-state-recaptured-success"));
                        } else {
                            finalPlayer.sendMessage(getMessage("admin.challenge-state-recapture-failed"));
                        }
                    }
                });
            }
            
            // Save challenge config
            saveChallengeToConfig(cfg);
            
            // Exit edit mode for this player only; if last editor, clear backups
            UUID editorUuid = player.getUniqueId();
            Location originalLoc = editorOriginalLocations.remove(editorUuid);
            removeEditorFromChallenge(player, editingChallengeId);
            clearInstanceChestDataForPlayer(editorUuid, editingChallengeId);
            
            // Teleport back
            if (originalLoc != null) {
                player.teleport(originalLoc);
            }
            
            player.sendMessage(getMessage("admin.challenge-save-success", "id", editingChallengeId));
            
            if (!isChallengeBeingEdited(editingChallengeId) && !isChallengeLocked(editingChallengeId)) {
                processQueue(editingChallengeId);
            }
            
            return true;
        }

        if (sub.equals("cancel")) {
            String editingChallengeId = null;
            for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
                if (entry.getValue() != null && entry.getValue().contains(player.getUniqueId())) {
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
            
            UUID editorUuid = player.getUniqueId();
            Location originalLoc = editorOriginalLocations.remove(editorUuid);
            removeEditorFromChallenge(player, editingChallengeId);
            clearInstanceChestDataForPlayer(editorUuid, editingChallengeId);
            
            if (originalLoc != null) {
                player.teleport(originalLoc);
            }
            
            player.sendMessage(getMessage("admin.challenge-cancel-success", "id", editingChallengeId));
            
            if (!isChallengeBeingEdited(editingChallengeId) && !isChallengeLocked(editingChallengeId)) {
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
            if (cfg.blockedItems != null && !cfg.blockedItems.isEmpty()) {
                String list = cfg.blockedItems.stream().map(Material::name).reduce((a, b) -> a + ", " + b).orElse("");
                player.sendMessage(getMessage("admin.challenge-info-blocked-items", "list", list));
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
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("rewards-cmd.difficulty-tier-rewards-header")));
                for (String tier : Arrays.asList("easy", "medium", "hard", "extreme")) {
                    List<TierReward> rewards = cfg.difficultyTierRewards.get(tier);
                    if (rewards != null && !rewards.isEmpty()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("rewards-cmd.difficulty-tier-line", "tier", tier.toUpperCase(), "count", String.valueOf(rewards.size()))));
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
        // Spawner cooldowns will be reset after regeneration completes
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
                    // (Spawner cooldowns already reset after regeneration completed)
                    
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
                        
                        // Initialize lives based on difficulty tier
                        String tier = activeChallengeDifficulties.getOrDefault(cfg.id, "easy");
                        int lives = tierLives.getOrDefault(tier, 3);
                        playerLives.put(p.getUniqueId(), lives);

                        Location teleportLoc = getTeleportLocation(cfg);
                        if (teleportLoc != null) {
                            p.teleport(teleportLoc);
                            p.sendMessage(getMessage("entry.entered", "challenge", displayName(cfg)));
                            // Move any blocked items to ender chest and notify
                            moveBlockedItemsToEnderChest(p, cfg);
                        } else {
                            p.sendMessage(getMessage("admin.challenge-no-teleport-location"));
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
                    
                    // Set spawner levels based on difficulty tier (after all players are in)
                    // Run on main thread since MythicMobs API requires it
                    // Add 5-tick delay (0.25 seconds) after regeneration to let spawners sync
                    String tier = activeChallengeDifficulties.getOrDefault(cfg.id, "easy");
                    final String finalTier = tier;
                    Bukkit.getScheduler().runTaskLater(ConradChallengesPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            setSpawnerLevelsForChallenge(cfg, finalTier);
                        }
                    }, 5L); // 5 ticks = 0.25 seconds delay
                    
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
        clearInstanceChestDataForPlayer(uuid, cfg.id);
        
        // Clean up difficulty tier if no players are active in this challenge
        cleanupChallengeDifficultyIfEmpty(cfg.id);
        
        // Check if challenge is now available and process queue
        if (!isChallengeLocked(cfg.id)) {
            processQueue(cfg.id);
        }

        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }
        giveBackBlockedItemOverflow(player, cfg.id);
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
        for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(uuid)) {
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
                    // Allow if: player is admin, OR challenge is in edit mode, OR an admin is at the destination (e.g. admin tping someone to them)
                    boolean challengeInEditMode = challengeEditors.containsKey(targetChallenge.id);
                    boolean adminAtDestination = false;
                    if (to.getWorld() != null) {
                        double maxDistSq = 25.0; // 5 blocks
                        for (Player other : to.getWorld().getPlayers()) {
                            if (other.hasPermission("conradchallenges.admin") && other.getLocation().distanceSquared(to) <= maxDistSq) {
                                adminAtDestination = true;
                                break;
                            }
                        }
                    }
                    if (!player.hasPermission("conradchallenges.admin") && !challengeInEditMode && !adminAtDestination) {
                        event.setCancelled(true);
                        player.sendMessage(getMessage("challenge.teleport-blocked-into"));
                        return;
                    }
                    // Admin bypass, edit mode, or admin at destination - allow teleport into challenge area
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
        
        // Allow teleport to another player who is in the same challenge (e.g. /tpa to teammate)
        if (to != null && to.getWorld() != null) {
            for (Player other : to.getWorld().getPlayers()) {
                if (other.equals(player)) continue;
                if (!challengeId.equals(activeChallenges.get(other.getUniqueId()))) continue;
                if (other.getLocation().distanceSquared(to) <= 4.0) { // within 2 blocks of their location
                    return;
                }
            }
        }
        
        // Only allow teleports within this challenge's regen/challenge areas (any area - e.g. area 1 to boss room in area 2)
        if (cfg != null && to != null && from != null) {
            boolean hasRegenArea = (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) || (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null);
            if (hasRegenArea) {
                // Check if both locations are within any of this challenge's regen areas (allows teleport between area 1 and area 2, etc.)
                boolean toInRegenArea = isLocationInRegenArea(cfg, to);
                boolean fromInRegenArea = isLocationInRegenArea(cfg, from);
                if (toInRegenArea && fromInRegenArea) {
                    return;
                }
                if (!toInRegenArea) {
                    event.setCancelled(true);
                    player.sendMessage(getMessage("challenge.teleport-blocked-regen"));
                    return;
                }
            } else {
                boolean hasChallengeArea = (cfg.challengeAreas != null && !cfg.challengeAreas.isEmpty()) || (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null);
                if (hasChallengeArea) {
                    boolean toInArea = isLocationInChallengeArea(cfg, to);
                    boolean fromInArea = isLocationInChallengeArea(cfg, from);
                    if (toInArea && fromInArea) {
                        return;
                    }
                    if (!toInArea) {
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
        for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(uuid)) {
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
        
        boolean hasRegenArea = (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) || (cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null);
        if (hasRegenArea) {
            if (to != null && !isLocationInRegenArea(cfg, to)) {
                event.setCancelled(true);
                Location safeLocation = from.clone();
                if (safeLocation.getWorld() != null) player.teleport(safeLocation);
                player.sendMessage(getMessage("challenge.movement-blocked-regen"));
                return;
            }
        } else {
            boolean hasChallengeArea = (cfg.challengeAreas != null && !cfg.challengeAreas.isEmpty()) || (cfg.challengeCorner1 != null && cfg.challengeCorner2 != null);
            if (hasChallengeArea) {
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

        // PLAYER_ENTER_AREA scripts (manager handles cooldown)
        if (to != null && cfg.scriptNodes != null) {
            String key = blockKey(to);
            if (key != null) {
                scriptManager.runScriptsForTrigger(cfg, key, ScriptTrigger.PLAYER_ENTER_AREA, player, to, null);
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
    
    private static double getDouble(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v != null) try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException ignored) {}
        return 0;
    }

    private static Map<String, Object> cornerMap(Location loc) {
        Map<String, Object> m = new HashMap<>();
        m.put("x", loc.getX());
        m.put("y", loc.getY());
        m.put("z", loc.getZ());
        return m;
    }

    private void syncRegenLegacyFromList(ChallengeConfig cfg) {
        if (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) {
            Location[] first = cfg.regenerationAreas.get(0);
            if (first != null && first.length >= 2) {
                cfg.regenerationCorner1 = first[0];
                cfg.regenerationCorner2 = first[1];
                return;
            }
        }
        cfg.regenerationCorner1 = null;
        cfg.regenerationCorner2 = null;
    }

    private void syncChallengeLegacyFromList(ChallengeConfig cfg) {
        if (cfg.challengeAreas != null && !cfg.challengeAreas.isEmpty()) {
            Location[] first = cfg.challengeAreas.get(0);
            if (first != null && first.length >= 2) {
                cfg.challengeCorner1 = first[0];
                cfg.challengeCorner2 = first[1];
                return;
            }
        }
        cfg.challengeCorner1 = null;
        cfg.challengeCorner2 = null;
    }

    private boolean isLocationInBox(Location corner1, Location corner2, Location loc) {
        if (corner1 == null || corner2 == null || loc.getWorld() == null) return false;
        if (!corner1.getWorld().equals(loc.getWorld()) || !corner2.getWorld().equals(loc.getWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * @return true if the location is within any challenge area (or any regen area if no challenge areas set)
     */
    boolean isLocationInChallengeArea(ChallengeConfig cfg, Location loc) {
        if (cfg.challengeAreas != null && !cfg.challengeAreas.isEmpty()) {
            for (Location[] corners : cfg.challengeAreas) {
                if (corners != null && corners.length >= 2 && isLocationInBox(corners[0], corners[1], loc)) return true;
            }
            return false;
        }
        Location corner1 = cfg.challengeCorner1 != null ? cfg.challengeCorner1 : cfg.regenerationCorner1;
        Location corner2 = cfg.challengeCorner2 != null ? cfg.challengeCorner2 : cfg.regenerationCorner2;
        return isLocationInBox(corner1, corner2, loc);
    }
    
    /**
     * Gets the challenge config for a location if it's within any challenge area.
     * 
     * @param loc The location to check
     * @return The challenge config if location is in a challenge area, null otherwise
     */
    ChallengeConfig getChallengeForLocation(Location loc) {
        for (ChallengeConfig cfg : challenges.values()) {
            if (isLocationInChallengeArea(cfg, loc)) {
                return cfg;
            }
        }
        return null;
    }

    /**
     * Checks if a location is within any of the challenge's regeneration areas.
     */
    boolean isLocationInRegenArea(ChallengeConfig cfg, Location loc) {
        if (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) {
            for (Location[] corners : cfg.regenerationAreas) {
                if (corners != null && corners.length >= 2 && isLocationInBox(corners[0], corners[1], loc)) return true;
            }
            return false;
        }
        return isLocationInBox(cfg.regenerationCorner1, cfg.regenerationCorner2, loc);
    }

    /** Package-visible for ChallengeScriptManager. */
    static String blockKey(Location loc) {
        return loc.getWorld() != null ? loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() : null;
    }
    /** Instance method for ChallengeScriptManager. */
    String getBlockKey(Location loc) { return blockKey(loc); }

    /** Script state: get set of block keys that have fired this run for the challenge. */
    Set<String> getScriptFiredTriggers(String challengeId) {
        return scriptFiredTriggers.computeIfAbsent(challengeId, k -> new java.util.HashSet<>());
    }
    /** Script state: get set of signal names fired this run. */
    Set<String> getScriptSignalsFired(String challengeId) {
        return scriptSignalsFired.computeIfAbsent(challengeId, k -> new java.util.HashSet<>());
    }
    /** Script state: mark a signal as fired (for SIGNAL_SENDER). */
    void fireScriptSignal(String challengeId, String signalName) {
        if (challengeId != null && signalName != null && !signalName.isEmpty())
            getScriptSignalsFired(challengeId).add(signalName);
    }
    /** Script state: mark a trigger block as fired (for gates / one-shot). */
    void markScriptTriggerFired(String challengeId, String blockKey) {
        if (challengeId != null && blockKey != null && !blockKey.isEmpty())
            getScriptFiredTriggers(challengeId).add(blockKey);
    }
    /** Earliest challenge start time (ms) for any active player in this challenge; 0 if none. */
    long getChallengeStartTime(String challengeId) {
        long earliest = Long.MAX_VALUE;
        for (java.util.Map.Entry<java.util.UUID, String> e : activeChallenges.entrySet()) {
            if (!e.getValue().equals(challengeId)) continue;
            Long t = challengeStartTimes.get(e.getKey());
            if (t != null && t < earliest) earliest = t;
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }
    /** Number of players currently in this challenge. */
    int getActiveChallengePlayerCount(String challengeId) {
        int n = 0;
        for (String id : activeChallenges.values()) if (challengeId.equals(id)) n++;
        return n;
    }

    /** Difficulty tier for the challenge this run (e.g. easy, medium, hard). */
    String getDifficultyTier(String challengeId) {
        return activeChallengeDifficulties.getOrDefault(challengeId, "easy");
    }

    /** Count of players in this challenge within radius of location. */
    int getPlayersWithinRadius(Location loc, double radius, String challengeId) {
        if (loc == null || loc.getWorld() == null || radius <= 0) return 0;
        int count = 0;
        for (java.util.Map.Entry<UUID, String> e : activeChallenges.entrySet()) {
            if (!e.getValue().equals(challengeId)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline() && p.getWorld().equals(loc.getWorld())) {
                if (p.getLocation().distance(loc) <= radius) count++;
            }
        }
        return count;
    }

    /** Parse block key "world:x,y,z" to block Location; returns null if invalid. */
    public Location getLocationFromBlockKey(String blockKey) {
        if (blockKey == null || !blockKey.contains(":")) return null;
        int colon = blockKey.indexOf(':');
        String worldName = blockKey.substring(0, colon);
        String coords = blockKey.substring(colon + 1);
        String[] parts = coords.split(",");
        if (parts.length != 3) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new Location(w, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Every 5 ticks: for pass-through blocks, set to air when a challenger is near, restore when none are near.
     * Skips any challenge that has an editor (so we never overwrite blocks they place).
     */
    private void updatePassThroughBlocks() {
        for (Map.Entry<String, ChallengeConfig> entry : challenges.entrySet()) {
            String challengeId = entry.getKey();
            ChallengeConfig cfg = entry.getValue();
            if (cfg.passThroughBlockKeys == null || cfg.passThroughBlockKeys.isEmpty()) continue;
            // Do not touch pass-through blocks while any editor is in this challenge (prevents removing blocks they place)
            Set<UUID> editors = challengeEditors.get(challengeId);
            boolean hasOnlineEditor = false;
            if (editors != null && !editors.isEmpty()) {
                for (UUID uuid : editors) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        hasOnlineEditor = true;
                        break;
                    }
                }
            }
            if (hasOnlineEditor) continue; // skip this challenge entirely - editor present, don't touch any blocks
            List<UUID> playersInChallenge = new ArrayList<>();
            for (Map.Entry<UUID, String> e : activeChallenges.entrySet()) {
                if (e.getValue().equals(challengeId)) playersInChallenge.add(e.getKey());
            }
            // Include editors for "restore when empty" check only; do NOT use editors for "set to air when near"
            // so that in edit mode we never clear blocks - editors can place without them disappearing
            if (editors != null) {
                for (UUID uuid : editors) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) playersInChallenge.add(uuid);
                }
            }
            if (playersInChallenge.isEmpty()) {
                // Restore all saved pass-through blocks when no challengers and no editors
                List<String> toRemove = new ArrayList<>();
                for (Map.Entry<String, BlockState> e : passThroughSavedStates.entrySet()) {
                    if (e.getKey().startsWith(challengeId + ":")) {
                        Location loc = getLocationFromBlockKey(e.getKey().substring(challengeId.length() + 1));
                        if (loc != null) {
                            Block block = loc.getBlock();
                            e.getValue().update(true, false);
                        }
                        toRemove.add(e.getKey());
                    }
                }
                for (String key : toRemove) passThroughSavedStates.remove(key);
                continue;
            }
            // Only set pass-through blocks to air when an *active challenger* is near (not editors)
            // so edit mode can place blocks without the pass-through task clearing them
            List<UUID> challengersOnly = new ArrayList<>();
            for (Map.Entry<UUID, String> e : activeChallenges.entrySet()) {
                if (e.getValue().equals(challengeId)) challengersOnly.add(e.getKey());
            }
            double range = 2.5;
            for (String blockKey : cfg.passThroughBlockKeys) {
                Location loc = getLocationFromBlockKey(blockKey);
                if (loc == null) continue;
                Block block = loc.getBlock();
                String stateKey = challengeId + ":" + blockKey;
                boolean playerNear = false;
                for (UUID uuid : challengersOnly) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && p.getWorld().equals(loc.getWorld())) {
                        if (p.getLocation().distance(loc.getBlock().getLocation().add(0.5, 0.5, 0.5)) <= range) {
                            playerNear = true;
                            break;
                        }
                    }
                }
                if (playerNear) {
                    if (block.getType() != Material.AIR) {
                        if (!passThroughSavedStates.containsKey(stateKey)) {
                            passThroughSavedStates.put(stateKey, block.getState());
                            block.setType(Material.AIR, false);
                        }
                    }
                } else {
                    if (passThroughSavedStates.containsKey(stateKey)) {
                        BlockState saved = passThroughSavedStates.remove(stateKey);
                        if (saved != null) saved.update(true, false);
                    }
                }
            }
        }
    }

    /**
     * Spawns particles at block center with wide spread so they protrude past the edges,
     * giving a soft "surrounds the block" look. Works well for full blocks and thin blocks (ladders, signs).
     * @param world world to spawn in
     * @param blockLoc block location (block corner)
     * @param particle particle type
     */
    private void spawnParticlesAroundBlock(World world, Location blockLoc, org.bukkit.Particle particle) {
        if (world == null || blockLoc == null) return;
        double x = blockLoc.getBlockX() + 0.5;
        double y = blockLoc.getBlockY() + 0.5;
        double z = blockLoc.getBlockZ() + 0.5;
        // Center spawn, wide spread (0.5) so particles extend to and past block edges
        world.spawnParticle(particle, x, y, z, 10, 0.5, 0.5, 0.5, 0.02);
    }

    /**
     * Every 20 ticks: for players editing a challenge and holding the function tool,
     * show particles around the block they're looking at and around all blocks that have scripts/pass-through.
     */
    private void updateScriptParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                String challengeId = getPlayerEditingChallenge(player);
                if (challengeId == null) continue;
                ItemStack main = player.getInventory().getItemInMainHand();
                ItemStack off = player.getInventory().getItemInOffHand();
                if (!scriptManager.isFunctionTool(main) && !scriptManager.isFunctionTool(off)) continue;
                ChallengeConfig cfg = challenges.get(challengeId);
                if (cfg == null) continue;
                World world = player.getWorld();
                if (world == null) continue;
                try {
                    Block target = player.getTargetBlockExact(5);
                    if (target != null && target.getWorld().equals(world) && (isLocationInRegenArea(cfg, target.getLocation()) || isLocationInChallengeArea(cfg, target.getLocation()))) {
                        spawnParticlesAroundBlock(world, target.getLocation(), org.bukkit.Particle.END_ROD);
                    }
                } catch (Throwable ignored) { }
                Set<String> blockKeys = scriptManager.getBlockKeysWithFunctions(cfg);
                if (blockKeys == null) continue;
                for (String blockKey : new java.util.HashSet<>(blockKeys)) {
                    try {
                        Location loc = getLocationFromBlockKey(blockKey);
                        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
                        if (!isLocationInRegenArea(cfg, loc) && !isLocationInChallengeArea(cfg, loc)) continue;
                        spawnParticlesAroundBlock(world, loc, org.bukkit.Particle.HAPPY_VILLAGER);
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable t) {
                getLogger().warning("Script particles error for " + (player != null ? player.getName() : "?") + ": " + t.getMessage());
            }
        }
    }

    /**
     * Moves any blocked items from the player's inventory (and armor) into their ender chest.
     * Sends the configured message for each blocked item type moved.
     * Overflow that cannot fit in ender chest is stored and returned by the GateKeeper on challenge complete or exit (full item data preserved).
     */
    private void moveBlockedItemsToEnderChest(Player player, ChallengeConfig cfg) {
        if (cfg.blockedItems == null || cfg.blockedItems.isEmpty()) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        org.bukkit.inventory.Inventory ender = player.getEnderChest();
        Set<Material> notifiedMovedToEchest = new HashSet<>();
        boolean hadOverflow = false;
        
        // Check main inventory
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!cfg.blockedItems.contains(stack.getType())) continue;
            int moved = moveToEnderChest(ender, stack);
            if (moved > 0) {
                int remainder = stack.getAmount() - moved;
                if (remainder <= 0) {
                    inv.setItem(i, null);
                } else {
                    ItemStack overflow = stack.clone();
                    overflow.setAmount(remainder);
                    getOrCreateBlockedItemOverflowList(player.getUniqueId(), cfg.id).add(overflow);
                    inv.setItem(i, null);
                    hadOverflow = true;
                }
                // Only tell them "moved to ender chest" for the part that actually went there
                if (!notifiedMovedToEchest.contains(stack.getType())) {
                    notifiedMovedToEchest.add(stack.getType());
                    String itemName = formatMaterialName(stack.getType());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("entry.blocked-item-moved", "item", itemName)));
                }
            } else {
                // Nothing fit in ender chest - entire stack goes to overflow
                ItemStack overflow = stack.clone();
                getOrCreateBlockedItemOverflowList(player.getUniqueId(), cfg.id).add(overflow);
                inv.setItem(i, null);
                hadOverflow = true;
            }
        }
        
        // Check armor slots
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!cfg.blockedItems.contains(stack.getType())) continue;
            int moved = moveToEnderChest(ender, stack);
            if (moved > 0) {
                int remainder = stack.getAmount() - moved;
                if (remainder <= 0) {
                    armor[i] = null;
                } else {
                    ItemStack overflow = stack.clone();
                    overflow.setAmount(remainder);
                    getOrCreateBlockedItemOverflowList(player.getUniqueId(), cfg.id).add(overflow);
                    armor[i] = null;
                    hadOverflow = true;
                }
                armorChanged = true;
                if (!notifiedMovedToEchest.contains(stack.getType())) {
                    notifiedMovedToEchest.add(stack.getType());
                    String itemName = formatMaterialName(stack.getType());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("entry.blocked-item-moved", "item", itemName)));
                }
            } else {
                ItemStack overflow = stack.clone();
                getOrCreateBlockedItemOverflowList(player.getUniqueId(), cfg.id).add(overflow);
                armor[i] = null;
                armorChanged = true;
                hadOverflow = true;
            }
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }
        if (hadOverflow) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("entry.blocked-item-overflow-gatekeeper")));
        }
    }
    
    private List<ItemStack> getOrCreateBlockedItemOverflowList(UUID playerUuid, String challengeId) {
        return blockedItemOverflowByPlayer
            .computeIfAbsent(playerUuid, k -> new HashMap<>())
            .computeIfAbsent(challengeId, k -> new ArrayList<>());
    }
    
    /**
     * Gives back any blocked-item overflow (items that couldn't fit in ender chest) to the player.
     * Called when the player completes or exits the challenge. Items retain enchantments, lore, names, etc.
     */
    private void giveBackBlockedItemOverflow(Player player, String challengeId) {
        Map<String, List<ItemStack>> byChallenge = blockedItemOverflowByPlayer.get(player.getUniqueId());
        if (byChallenge == null) return;
        List<ItemStack> list = byChallenge.remove(challengeId);
        if (list == null || list.isEmpty()) return;
        if (byChallenge.isEmpty()) {
            blockedItemOverflowByPlayer.remove(player.getUniqueId());
        }
        for (ItemStack stack : list) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack left : leftover.values()) {
                if (left != null && left.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("entry.blocked-items-returned")));
    }

    /**
     * Exits a player from their current challenge (no completion, no rewards).
     * Same logic as /exit. Used by script function LEAVE_CHALLENGE.
     * Call only when player is in a challenge; no-op if not.
     */
    void exitPlayerFromChallenge(Player player) {
        UUID uuid = player.getUniqueId();
        String challengeId = activeChallenges.get(uuid);
        if (challengeId == null) return;
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) return;
        if (spawnLocation == null) {
            player.sendMessage(getMessage("challenge.spawn-not-configured"));
            return;
        }
        String exitedChallengeId = activeChallenges.remove(uuid);
        completedChallenges.remove(uuid);
        challengeStartTimes.remove(uuid);
        playerLives.remove(uuid);
        deadPartyMembers.remove(uuid);
        clearInstanceChestDataForPlayer(uuid, exitedChallengeId);
        if (exitedChallengeId != null) {
            cleanupChallengeDifficultyIfEmpty(exitedChallengeId);
            revertSpawnerLevelsForChallenge(cfg);
            resetMythicMobsSpawnersInArea(cfg);
        }
        if (exitedChallengeId != null && !isChallengeLocked(exitedChallengeId)) {
            processQueue(exitedChallengeId);
        }
        // Clear script gate/signal state when no players left in this challenge
        if (exitedChallengeId != null && !activeChallenges.containsValue(exitedChallengeId)) {
            scriptFiredTriggers.remove(exitedChallengeId);
            scriptSignalsFired.remove(exitedChallengeId);
        }
        Location testReturn = testOriginalLocations.remove(uuid);
        if (testReturn != null) {
            player.teleport(testReturn);
        } else {
            player.teleport(spawnLocation);
        }
        giveBackBlockedItemOverflow(player, exitedChallengeId);
        player.sendMessage(getMessage("exit.exited-returned"));
        player.sendMessage(getMessage("exit.exited-no-rewards"));
    }

    /** Tries to add as much of the stack to the ender chest as possible. Returns amount actually added. */
    private int moveToEnderChest(org.bukkit.inventory.Inventory ender, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return 0;
        int toAdd = stack.getAmount();
        ItemStack add = stack.clone();
        for (int i = 0; i < ender.getSize() && toAdd > 0; i++) {
            ItemStack slot = ender.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int put = Math.min(toAdd, add.getMaxStackSize());
                add.setAmount(put);
                ender.setItem(i, add.clone());
                toAdd -= put;
                add.setAmount(toAdd);
            } else if (slot.isSimilar(add) && slot.getAmount() < slot.getMaxStackSize()) {
                int put = Math.min(toAdd, slot.getMaxStackSize() - slot.getAmount());
                slot.setAmount(slot.getAmount() + put);
                toAdd -= put;
            }
        }
        return stack.getAmount() - toAdd;
    }
    
    private String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (name.isEmpty()) return mat.name();
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
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
            
            // Check challenge areas (all boxes)
            if (checkChallengeAreas) {
                if (otherCfg.challengeAreas != null && !otherCfg.challengeAreas.isEmpty()) {
                    for (Location[] c : otherCfg.challengeAreas) {
                        if (c != null && c.length >= 2 && c[0].getWorld() != null && c[0].getWorld().equals(corner1.getWorld())) {
                            int minX2 = Math.min(c[0].getBlockX(), c[1].getBlockX());
                            int maxX2 = Math.max(c[0].getBlockX(), c[1].getBlockX());
                            int minY2 = Math.min(c[0].getBlockY(), c[1].getBlockY());
                            int maxY2 = Math.max(c[0].getBlockY(), c[1].getBlockY());
                            int minZ2 = Math.min(c[0].getBlockZ(), c[1].getBlockZ());
                            int maxZ2 = Math.max(c[0].getBlockZ(), c[1].getBlockZ());
                            if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1, minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) return challengeId;
                        }
                    }
                } else if (otherCfg.challengeCorner1 != null && otherCfg.challengeCorner2 != null && otherCfg.challengeCorner1.getWorld() != null && otherCfg.challengeCorner1.getWorld().equals(corner1.getWorld())) {
                    int minX2 = Math.min(otherCfg.challengeCorner1.getBlockX(), otherCfg.challengeCorner2.getBlockX());
                    int maxX2 = Math.max(otherCfg.challengeCorner1.getBlockX(), otherCfg.challengeCorner2.getBlockX());
                    int minY2 = Math.min(otherCfg.challengeCorner1.getBlockY(), otherCfg.challengeCorner2.getBlockY());
                    int maxY2 = Math.max(otherCfg.challengeCorner1.getBlockY(), otherCfg.challengeCorner2.getBlockY());
                    int minZ2 = Math.min(otherCfg.challengeCorner1.getBlockZ(), otherCfg.challengeCorner2.getBlockZ());
                    int maxZ2 = Math.max(otherCfg.challengeCorner1.getBlockZ(), otherCfg.challengeCorner2.getBlockZ());
                    if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1, minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) return challengeId;
                }
            }
            // Check regeneration areas (all boxes)
            if (checkRegenerationAreas) {
                if (otherCfg.regenerationAreas != null && !otherCfg.regenerationAreas.isEmpty()) {
                    for (Location[] c : otherCfg.regenerationAreas) {
                        if (c != null && c.length >= 2 && c[0].getWorld() != null && c[0].getWorld().equals(corner1.getWorld())) {
                            int minX2 = Math.min(c[0].getBlockX(), c[1].getBlockX());
                            int maxX2 = Math.max(c[0].getBlockX(), c[1].getBlockX());
                            int minY2 = Math.min(c[0].getBlockY(), c[1].getBlockY());
                            int maxY2 = Math.max(c[0].getBlockY(), c[1].getBlockY());
                            int minZ2 = Math.min(c[0].getBlockZ(), c[1].getBlockZ());
                            int maxZ2 = Math.max(c[0].getBlockZ(), c[1].getBlockZ());
                            if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1, minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) return challengeId;
                        }
                    }
                } else if (otherCfg.regenerationCorner1 != null && otherCfg.regenerationCorner2 != null && otherCfg.regenerationCorner1.getWorld() != null && otherCfg.regenerationCorner1.getWorld().equals(corner1.getWorld())) {
                    int minX2 = Math.min(otherCfg.regenerationCorner1.getBlockX(), otherCfg.regenerationCorner2.getBlockX());
                    int maxX2 = Math.max(otherCfg.regenerationCorner1.getBlockX(), otherCfg.regenerationCorner2.getBlockX());
                    int minY2 = Math.min(otherCfg.regenerationCorner1.getBlockY(), otherCfg.regenerationCorner2.getBlockY());
                    int maxY2 = Math.max(otherCfg.regenerationCorner1.getBlockY(), otherCfg.regenerationCorner2.getBlockY());
                    int minZ2 = Math.min(otherCfg.regenerationCorner1.getBlockZ(), otherCfg.regenerationCorner2.getBlockZ());
                    int maxZ2 = Math.max(otherCfg.regenerationCorner1.getBlockZ(), otherCfg.regenerationCorner2.getBlockZ());
                    if (boundingBoxesOverlap(minX1, maxX1, minY1, maxY1, minZ1, maxZ1, minX2, maxX2, minY2, maxY2, minZ2, maxZ2)) return challengeId;
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
     * Block break rules in challenge areas:
     * - In regen area: only edit mode for that challenge can break; others get "Enter edit mode to make changes to this area."
     * - In challenge area (not regen): edit mode can always break; challengers: blockBreakEnabled off = can break whatever, on = only blocks they placed or in allow list; others = enter edit mode message.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakInChallenge(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
        if (cfg == null) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String editingId = getPlayerEditingChallenge(player);

        // In regen area: only edit mode for this challenge can break
        if (isLocationInRegenArea(cfg, block.getLocation())) {
            if (editingId != null && editingId.equals(cfg.id)) return;
            event.setCancelled(true);
            player.sendMessage(getMessage("admin.challenge-regen-enter-edit-mode"));
            return;
        }

        // In challenge area (not regen): edit mode can always break; challengers (and test mode) use block break rules
        if (editingId != null && editingId.equals(cfg.id)) return; // Edit mode: free break

        String activeId = activeChallenges.get(uuid);
        if (cfg.id.equals(activeId)) {
            // Challenger or test mode: blockBreakEnabled off = can break whatever; on = only placed + allow list
            if (!cfg.blockBreakEnabled) return;
            String key = blockKey(block.getLocation());
            Map<String, UUID> placed = challengePlacedBlocks.get(cfg.id);
            boolean placedBySelf = placed != null && uuid.equals(placed.get(key));
            boolean inAllowList = cfg.breakAllowedMaterials != null && cfg.breakAllowedMaterials.contains(block.getType());
            if (placedBySelf || inAllowList) {
                if (placed != null && key != null) placed.remove(key);
                return;
            }
            event.setCancelled(true);
            player.sendMessage(getMessage("admin.challenge-regen-only-break-placed"));
            return;
        }

        event.setCancelled(true);
        player.sendMessage(getMessage("admin.challenge-regen-enter-edit-mode"));
    }

    /**
     * Run BLOCK_BREAK scripts when a block is broken in a challenge.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakScript(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
        if (cfg == null) return;
        String key = blockKey(block.getLocation());
        if (key == null) return;
        scriptManager.runScriptsForTrigger(cfg, key, ScriptTrigger.BLOCK_BREAK, event.getPlayer(), block.getLocation(), null);
    }

    /**
     * Block placement in regeneration areas unless the player is in edit mode for that challenge
     * or is an active challenger. Others get "Enter edit mode to make changes to this area."
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceInChallengeRestrict(BlockPlaceEvent event) {
        ChallengeConfig cfg = getChallengeForLocation(event.getBlock().getLocation());
        if (cfg == null) return;
        if (!isLocationInRegenArea(cfg, event.getBlock().getLocation())) return; // Only restrict inside regen area
        Player player = event.getPlayer();
        String editingId = getPlayerEditingChallenge(player);
        if (editingId != null && editingId.equals(cfg.id)) return; // Edit mode for this challenge can place
        if (cfg.id.equals(activeChallenges.get(player.getUniqueId()))) return; // Active challenger can place
        event.setCancelled(true);
        player.sendMessage(getMessage("admin.challenge-regen-enter-edit-mode"));
    }

    /**
     * Track blocks placed by challengers (for blockBreakEnabled "only break what you placed or allow list").
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceInChallenge(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String challengeId = activeChallenges.get(player.getUniqueId());
        if (challengeId == null) return;
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null || !cfg.blockBreakEnabled) return;
        if (!isLocationInChallengeArea(cfg, event.getBlock().getLocation())) return;
        String key = blockKey(event.getBlock().getLocation());
        if (key == null) return;
        challengePlacedBlocks.computeIfAbsent(challengeId, k -> new HashMap<>()).put(key, player.getUniqueId());
        scriptManager.runScriptsForTrigger(cfg, key, ScriptTrigger.BLOCK_PLACE, player, event.getBlock().getLocation(), null);
    }

    /**
     * Prevents chests inside challenge regen areas from being broken during normal runs.
     * - Allowed when the player is in edit mode for that challenge.
     * - Allowed outside any challenge regen area.
     * - Cancelled for challengers and other players inside challenge areas.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestBreakInChallenge(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }
        ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
        if (cfg == null) {
            return; // not inside any challenge regen area
        }
        Player player = event.getPlayer();
        // Allow if this player is currently editing this specific challenge
        String editingId = getPlayerEditingChallenge(player);
        if (editingId != null && editingId.equals(cfg.id)) {
            // Clean up chest data before the break: ignored, legendary, and instance contents
            cleanupChestDataOnBreak(cfg, block);
            return;
        }
        // Otherwise, block breaking the chest
        event.setCancelled(true);
        boolean inActiveChallenge = activeChallenges.get(player.getUniqueId()) != null;
        player.sendMessage(getMessage(inActiveChallenge ? "admin.challenge-regen-cannot-break-chests" : "admin.challenge-regen-cannot-break-chests-outside-edit"));
    }

    /**
     * When a chest is broken (in edit mode), remove its keys from ignored/legendary sets
     * and clear any instance chest contents for that location.
     */
    private void cleanupChestDataOnBreak(ChallengeConfig cfg, Block block) {
        if (cfg == null || block == null) return;
        // Collect keys while block is still a chest (both halves for double chest)
        List<Block> chestBlocks = getChestBlocksForIgnore(block);
        String canonicalKey = getCanonicalChestKey(block);
        // Remove from ignored set (both halves)
        if (cfg.ignoredChestKeys != null) {
            for (Block b : chestBlocks) {
                cfg.ignoredChestKeys.remove(chestLocationKey(b.getLocation()));
            }
        }
        // Remove from legendary set (one canonical key per inventory)
        if (cfg.legendaryChestKeys != null && canonicalKey != null) {
            cfg.legendaryChestKeys.remove(canonicalKey);
        }
        // Clear instance chest contents for this chest (all players) so we don't keep orphaned data
        Map<UUID, Map<String, ItemStack[]>> perChallenge = instanceChestContents.get(cfg.id);
        if (perChallenge != null && canonicalKey != null) {
            for (Map<String, ItemStack[]> perPlayer : perChallenge.values()) {
                perPlayer.remove(canonicalKey);
            }
        }
        saveChallengeToConfig(cfg);
    }

    /** Function tool: right-click block in edit mode to add a script. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFunctionToolInteract(PlayerInteractEvent event) {
        scriptManager.handleFunctionToolInteract(event);
    }

    /** Run BLOCK_INTERACT scripts when a challenger right-clicks a block (not with function tool). Run early so key item gate gets event before e.g. book use cancels it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockInteractScript(PlayerInteractEvent event) {
        scriptManager.handleBlockInteractScript(event);
    }

    /** Run REDSTONE_RECEIVER scripts when a block in a challenge receives redstone power. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysicsRedstoneScript(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        ChallengeConfig cfg = getChallengeForLocation(block.getLocation());
        if (cfg == null || cfg.scriptNodes == null) return;
        String key = getBlockKey(block.getLocation());
        if (key == null) return;
        boolean hasRedstoneReceiver = false;
        for (ChallengeScriptNode node : cfg.scriptNodes) {
            if (node.blockKey().equals(key) && node.trigger == ScriptTrigger.REDSTONE_RECEIVER) {
                hasRedstoneReceiver = true;
                break;
            }
        }
        if (!hasRedstoneReceiver) return;
        if (block.getBlockPower() == 0 && !block.isBlockIndirectlyPowered()) return;
        String cooldownKey = cfg.id + ":" + key;
        long now = System.currentTimeMillis();
        if (now - scriptRedstoneCooldowns.getOrDefault(cooldownKey, 0L) < 1000L) return;
        scriptRedstoneCooldowns.put(cooldownKey, now);
        scriptManager.runScriptsForTrigger(cfg, key, ScriptTrigger.REDSTONE_RECEIVER, null, block.getLocation(), null);
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
        
        // Check if player has lives remaining
        Integer remainingLives = playerLives.get(uuid);
        if (remainingLives == null) {
            remainingLives = tierLives.getOrDefault(activeChallengeDifficulties.getOrDefault(challengeId, "easy"), 3);
            playerLives.put(uuid, remainingLives);
        }
        
        if (remainingLives > 0) {
            // Player has lives remaining - keep inventory and teleport back to challenge
            remainingLives--;
            playerLives.put(uuid, remainingLives);
            
            // Keep inventory (cancel drops)
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            
            // Schedule teleport back to challenge destination on respawn
            pendingDeathTeleports.put(uuid, challengeId);
            
            // Send message about remaining lives
            player.sendMessage(getMessage("lives.died-with-lives", "remaining", String.valueOf(remainingLives)));
            
            // Don't remove from challenge - they're still in it
            // Don't process queue - challenge is still active
        } else {
            // No lives remaining - remove from challenge; drop inventory unless easy mode (easy keeps inventory)
            String tier = activeChallengeDifficulties.getOrDefault(challengeId, "easy");
            if ("easy".equals(tier)) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
            
            String removedChallengeId = activeChallenges.remove(uuid);
            completedChallenges.remove(uuid);
            challengeStartTimes.remove(uuid);
            playerLives.remove(uuid);
            clearInstanceChestDataForPlayer(uuid, challengeId);
            
            // Track as dead party member if in a party
            // Check if there are other active players in this challenge
            boolean hasOtherActivePlayers = false;
            for (Map.Entry<UUID, String> entry : activeChallenges.entrySet()) {
                if (entry.getValue().equals(challengeId) && !entry.getKey().equals(uuid)) {
                    hasOtherActivePlayers = true;
                    break;
                }
            }
            if (hasOtherActivePlayers) {
                // Party member died but party is still active - track for rewards
                deadPartyMembers.put(uuid, challengeId);
            }
            
            // Clean up difficulty tier if no players are active in this challenge
            if (removedChallengeId != null) {
                cleanupChallengeDifficultyIfEmpty(removedChallengeId);
            }
            
            // Mark player for teleport to spawn on respawn
            pendingDeathTeleports.put(uuid, challengeId);
            
            // Send message about no lives
            player.sendMessage(getMessage("lives.died-no-lives", "fee", String.format("%.2f", buyBackFee)));
            
            // Check if challenge is now available and process queue
            if (!isChallengeLocked(challengeId)) {
                processQueue(challengeId);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onScriptGuiInventoryClick(InventoryClickEvent event) {
        scriptManager.handleScriptGuiClick(event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLoottableInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(LOOTTABLE_GUI_TITLE_PREFIX)) return;
        LootTableGuiContext ctx = loottableGuiContext.get(player.getUniqueId());
        if (ctx == null) return;
        int slot = event.getRawSlot();
        if (slot != event.getSlot()) return;
        int backSlot = (ctx.screen == LootTableScreen.LIST) ? LOOTTABLE_LIST_BACK_SLOT
                : (ctx.screen == LootTableScreen.LIST_FILTER_CHALLENGE ? LOOTTABLE_FILTER_CHALLENGE_BACK_SLOT
                : (event.getInventory().getSize() - 1));

        // Handle the Back button for all loot table GUIs (including EDIT_CONTENTS).
        if (slot == backSlot) {
            event.setCancelled(true);
            switch (ctx.screen) {
                case MAIN -> {
                    // Close menu
                    loottableGuiContext.remove(player.getUniqueId());
                    player.closeInventory();
                }
                case LIST -> {
                    openLoottableMainGui(player);
                }
                case LIST_FILTER -> {
                    openLoottableListGui(player);
                }
                case LIST_FILTER_TYPE -> {
                    openLoottableFilterGui(player);
                }
                case LIST_FILTER_CHALLENGE -> {
                    openLoottableFilterGui(player);
                }
                case OPTIONS -> {
                    openLoottableListGui(player);
                }
                case DELETE_CONFIRM -> {
                    if (ctx.loottableId != null) {
                        openLoottableOptionsGui(player, ctx.loottableId);
                    } else {
                        openLoottableMainGui(player);
                    }
                }
                case ASSIGN -> {
                    if (ctx.loottableId != null) {
                        openLoottableOptionsGui(player, ctx.loottableId);
                    } else {
                        openLoottableMainGui(player);
                    }
                }
                case EDIT_CONTENTS -> {
                    if (ctx.loottableId != null) {
                        openLoottableOptionsGui(player, ctx.loottableId);
                    } else {
                        openLoottableMainGui(player);
                    }
                }
            }
            return;
        }

        // For all non-editor loot GUIs, block normal item movement.
        if (ctx.screen != LootTableScreen.EDIT_CONTENTS) event.setCancelled(true);

        switch (ctx.screen) {
            case MAIN -> {
                if (slot == 11) {
                    createNewLoottable(player);
                } else if (slot == 15) {
                    openLoottableListGui(player);
                }
            }
            case LIST -> {
                if (slot == LOOTTABLE_LIST_FILTER_SLOT) {
                    openLoottableFilterGui(player);
                } else if (slot == LOOTTABLE_LIST_PREV_SLOT && ctx.listPage > 0) {
                    openLoottableListGui(player, ctx.listPage - 1);
                } else if (slot == LOOTTABLE_LIST_NEXT_SLOT) {
                    int totalPages = Math.max(1, (ctx.listOrder.size() + LOOTTABLE_LIST_CONTENT_PER_PAGE - 1) / LOOTTABLE_LIST_CONTENT_PER_PAGE);
                    if (ctx.listPage < totalPages - 1) {
                        openLoottableListGui(player, ctx.listPage + 1);
                    }
                } else if (slot >= 0 && slot < LOOTTABLE_LIST_CONTENT_PER_PAGE && ctx.listOrder != null) {
                    int idx = ctx.listPage * LOOTTABLE_LIST_CONTENT_PER_PAGE + slot;
                    if (idx >= 0 && idx < ctx.listOrder.size()) {
                        String id = ctx.listOrder.get(idx);
                        openLoottableOptionsGui(player, id);
                    }
                }
            }
            case LIST_FILTER -> {
                if (slot == 10) {
                    lastLoottableListFilterMode.put(player.getUniqueId(), LootTableListFilterMode.CREATED);
                    lastLoottableListFilterParam.remove(player.getUniqueId());
                    lastLoottableListPage.put(player.getUniqueId(), 0);
                    openLoottableListGui(player, 0);
                } else if (slot == 12) {
                    lastLoottableListFilterMode.put(player.getUniqueId(), LootTableListFilterMode.NAME);
                    lastLoottableListFilterParam.remove(player.getUniqueId());
                    lastLoottableListPage.put(player.getUniqueId(), 0);
                    openLoottableListGui(player, 0);
                } else if (slot == 14) {
                    openLoottableFilterTypeGui(player);
                } else if (slot == 16) {
                    openLoottableFilterChallengeGui(player);
                }
            }
            case LIST_FILTER_TYPE -> {
                if (slot == 11) {
                    lastLoottableListFilterMode.put(player.getUniqueId(), LootTableListFilterMode.TYPE);
                    lastLoottableListFilterParam.put(player.getUniqueId(), "NORMAL");
                    lastLoottableListPage.put(player.getUniqueId(), 0);
                    openLoottableListGui(player, 0);
                } else if (slot == 15) {
                    lastLoottableListFilterMode.put(player.getUniqueId(), LootTableListFilterMode.TYPE);
                    lastLoottableListFilterParam.put(player.getUniqueId(), "LEGENDARY");
                    lastLoottableListPage.put(player.getUniqueId(), 0);
                    openLoottableListGui(player, 0);
                }
            }
            case LIST_FILTER_CHALLENGE -> {
                if (slot == 47 && ctx.listPage > 0) {
                    openLoottableFilterChallengeGui(player, ctx.listPage - 1);
                } else if (slot == 49 && ctx.listOrder != null) {
                    int totalPages = Math.max(1, (ctx.listOrder.size() + LOOTTABLE_FILTER_CHALLENGE_CONTENT - 1) / LOOTTABLE_FILTER_CHALLENGE_CONTENT);
                    if (ctx.listPage < totalPages - 1) {
                        openLoottableFilterChallengeGui(player, ctx.listPage + 1);
                    }
                } else if (slot >= 0 && slot < LOOTTABLE_FILTER_CHALLENGE_CONTENT && ctx.listOrder != null) {
                    int idx = ctx.listPage * LOOTTABLE_FILTER_CHALLENGE_CONTENT + slot;
                    if (idx >= 0 && idx < ctx.listOrder.size()) {
                        String challengeId = ctx.listOrder.get(idx);
                        lastLoottableListFilterMode.put(player.getUniqueId(), LootTableListFilterMode.CHALLENGE);
                        lastLoottableListFilterParam.put(player.getUniqueId(), challengeId);
                        lastLoottableListPage.put(player.getUniqueId(), 0);
                        openLoottableListGui(player, 0);
                    }
                }
            }
            case OPTIONS -> {
                if (ctx.loottableId == null) break;
                LootTableData t = loottables.get(ctx.loottableId);
                if (t == null) break;
                if (slot == 0) {
                    t.size = t.size == LootTableSize.SINGLE ? LootTableSize.DOUBLE : LootTableSize.SINGLE;
                    if (t.size == LootTableSize.DOUBLE) {
                        ItemStack[] newContents = new ItemStack[54];
                        if (t.contents != null) for (int i = 0; i < Math.min(27, t.contents.length); i++) newContents[i] = t.contents[i];
                        t.contents = newContents;
                    } else {
                        ItemStack[] newContents = new ItemStack[27];
                        if (t.contents != null) for (int i = 0; i < Math.min(27, t.contents.length); i++) newContents[i] = t.contents[i];
                        t.contents = newContents;
                    }
                    saveLoottables();
                    player.sendMessage(getMessage("loottable.size-set", "size", t.size.name()));
                    openLoottableOptionsGui(player, ctx.loottableId);
                } else if (slot == 2) {
                    openLoottableEditContentsGui(player, ctx.loottableId);
                } else if (slot == 4) {
                    startLoottableRename(player, ctx.loottableId);
                } else if (slot == 6) {
                    openLoottableAssignGui(player, ctx.loottableId);
                } else if (slot == 8) {
                    openLoottableDeleteConfirmGui(player, ctx.loottableId);
                } else if (slot == 10) {
                    startLoottableIconChange(player, ctx.loottableId);
                } else if (slot == 12) {
                    ItemStack inHand = player.getInventory().getItemInMainHand();
                    if (inHand == null || inHand.getType().isAir()) {
                        player.sendMessage(getMessage("loottable.hold-item-for-icon"));
                    } else {
                        t.iconMaterial = inHand.getType();
                        saveLoottables();
                        player.sendMessage(getMessage("loottable.icon-updated", "material", t.iconMaterial.name()));
                        openLoottableOptionsGui(player, ctx.loottableId);
                    }
                } else if (slot == 14) {
                    t.lootType = (t.lootType == LootTableType.LEGENDARY) ? LootTableType.NORMAL : LootTableType.LEGENDARY;
                    saveLoottables();
                    player.sendMessage(getMessage("loottable.loot-type-set", "type", t.lootType.name()));
                    openLoottableOptionsGui(player, ctx.loottableId);
                }
            }
            case DELETE_CONFIRM -> {
                if (ctx.loottableId == null) break;
                if (slot == 11) {
                    loottables.remove(ctx.loottableId);
                    saveLoottables();
                    loottableGuiContext.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(getMessage("loottable.deleted"));
                    openLoottableMainGui(player);
                } else if (slot == 15) {
                    openLoottableOptionsGui(player, ctx.loottableId);
                }
            }
            case ASSIGN -> {
                if (ctx.loottableId == null || ctx.listOrder == null) break;
                if (slot >= 0 && slot < ctx.listOrder.size()) {
                    String cid = ctx.listOrder.get(slot);
                    LootTableData tbl = loottables.get(ctx.loottableId);
                    if (tbl != null) {
                        if (tbl.assignedChallengeIds.contains(cid)) tbl.assignedChallengeIds.remove(cid);
                        else tbl.assignedChallengeIds.add(cid);
                        saveLoottables();
                        openLoottableAssignGui(player, ctx.loottableId);
                    }
                }
            }
            case EDIT_CONTENTS -> {
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInstanceChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory openedInv = event.getInventory();
        Block block;
        Chest chestHolder = null;
        if (openedInv.getHolder() instanceof Chest singleChestHolder) {
            chestHolder = singleChestHolder;
            block = singleChestHolder.getBlock();
        } else if (openedInv.getHolder() instanceof DoubleChest doubleChestHolder) {
            // For double chests, use the left side as canonical; if null, fall back to right.
            Chest left = (Chest) doubleChestHolder.getLeftSide();
            Chest right = (Chest) doubleChestHolder.getRightSide();
            Chest ref = left != null ? left : right;
            if (ref == null) return;
            chestHolder = ref;
            block = ref.getBlock();
        } else {
            return;
        }
        String challengeId = getChallengeIdForChestLocation(block.getLocation());
        if (challengeId == null) return;
        UUID uuid = player.getUniqueId();
        String activeChallengeId = activeChallenges.get(uuid);
        // Only handle instance loot for players actively in the challenge (normal runs via books/queue),
        // not while in edit mode.
        if (!challengeId.equals(activeChallengeId)) return;
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null || isChestIgnored(cfg, block)) return;
        event.setCancelled(true);
        String locationKey = getCanonicalChestKey(block);
        if (locationKey == null) return;
        boolean legendary = isChestLegendary(cfg, locationKey);
        LootTableType wantedType = legendary ? LootTableType.LEGENDARY : LootTableType.NORMAL;
        int size = chestHolder.getInventory().getSize();
        List<LootTableData> singleTables = new ArrayList<>();
        List<LootTableData> doubleTables = new ArrayList<>();
        for (LootTableData t : loottables.values()) {
            if (!t.assignedChallengeIds.contains(challengeId)) continue;
            if (t.lootType != wantedType) continue;
            if (t.size == LootTableSize.SINGLE) singleTables.add(t);
            else doubleTables.add(t);
        }
        Map<UUID, Map<String, ItemStack[]>> perChallenge = instanceChestContents.computeIfAbsent(challengeId, k -> new HashMap<>());
        Map<String, ItemStack[]> perPlayer = perChallenge.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ItemStack[] contents = perPlayer.get(locationKey);
        if (contents == null) {
            List<LootTableData> pool = size == 27 ? singleTables : doubleTables;
            if (!pool.isEmpty()) {
                LootTableData chosen = pool.get(new Random().nextInt(pool.size()));
                contents = new ItemStack[size];
                if (chosen.contents != null) {
                    for (int i = 0; i < Math.min(chosen.contents.length, size); i++) {
                        if (chosen.contents[i] != null && !chosen.contents[i].getType().isAir()) {
                            contents[i] = chosen.contents[i].clone();
                        }
                    }
                }
                perPlayer.put(locationKey, contents);
            } else {
                Inventory blockInv = chestHolder.getInventory();
                contents = new ItemStack[size];
                for (int i = 0; i < Math.min(size, blockInv.getSize()); i++) {
                    ItemStack stack = blockInv.getItem(i);
                    if (stack != null && !stack.getType().isAir()) {
                        contents[i] = stack.clone();
                    }
                }
                perPlayer.put(locationKey, contents);
            }
        }
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GRAY + "Chest");
        for (int i = 0; i < size && i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                inv.setItem(i, contents[i].clone());
            }
        }
        instanceChestOpen.put(player.getUniqueId(), new InstanceChestOpenContext(challengeId, locationKey, size));
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInstanceChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InstanceChestOpenContext ctx = instanceChestOpen.remove(player.getUniqueId());
        if (ctx == null) return;
        Inventory inv = event.getInventory();
        if (inv.getSize() != ctx.size) return;
        Map<UUID, Map<String, ItemStack[]>> perChallenge = instanceChestContents.get(ctx.challengeId);
        if (perChallenge == null) return;
        Map<String, ItemStack[]> perPlayer = perChallenge.get(player.getUniqueId());
        if (perPlayer == null) return;
        ItemStack[] contents = new ItemStack[ctx.size];
        for (int i = 0; i < ctx.size; i++) {
            ItemStack stack = inv.getItem(i);
            contents[i] = (stack != null && !stack.getType().isAir()) ? stack.clone() : null;
        }
        perPlayer.put(ctx.locationKey, contents);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoottableInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(LOOTTABLE_GUI_TITLE_PREFIX)) return;
        LootTableGuiContext ctx = loottableGuiContext.get(player.getUniqueId());
        if (ctx == null) return;
        // Only treat closes of the actual loot contents editor (title "...Edit") as EDIT_CONTENTS,
        // because Bukkit first closes the previous GUI before opening the next one.
        if (title.startsWith(LOOTTABLE_GUI_TITLE_PREFIX + "Edit") && ctx.loottableId != null) {
            LootTableData t = loottables.get(ctx.loottableId);
            if (t != null) {
                Inventory inv = event.getInventory();
                // Logical content slots: single = 27, double = 54.
                int contentSlots = (t.size == LootTableSize.SINGLE ? 27 : 54);
                t.contents = new ItemStack[contentSlots];
                // Persist only logical content slots; ignore navigation row / Back button.
                int max = Math.min(contentSlots, inv.getSize());
                for (int i = 0; i < max; i++) {
                    ItemStack stack = inv.getItem(i);
                    t.contents[i] = (stack != null && !stack.getType().isAir()) ? stack.clone() : null;
                }
                saveLoottables();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScriptConfigureChat(AsyncPlayerChatEvent event) {
        if (!scriptManager.isAwaitingScriptChat(event.getPlayer())) return;
        event.setCancelled(true);
        String msg = event.getMessage();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> scriptManager.handleScriptChatInput(player, msg));
    }

    @EventHandler
    public void onLoottableRenameChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String msg = event.getMessage().trim();

        // Icon change: type material name (e.g. ender_pearl)
        String iconLoottableId = loottableIconPending.remove(uuid);
        if (iconLoottableId != null) {
            event.setCancelled(true);
            if (msg.equalsIgnoreCase("cancel")) {
                event.getPlayer().sendMessage(getMessage("loottable.icon-cancelled"));
                Bukkit.getScheduler().runTask(this, () -> openLoottableOptionsGui(event.getPlayer(), iconLoottableId));
                return;
            }
            Material mat = parseMaterialIcon(msg);
            if (mat == null) {
                event.getPlayer().sendMessage(getMessage("loottable.unknown-item", "item", msg));
                loottableIconPending.put(uuid, iconLoottableId); // put back so they can try again
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                LootTableData t = loottables.get(iconLoottableId);
                if (t != null) {
                    t.iconMaterial = mat;
                    saveLoottables();
                    event.getPlayer().sendMessage(getMessage("loottable.icon-set", "material", mat.name()));
                    openLoottableOptionsGui(event.getPlayer(), iconLoottableId);
                }
            });
            return;
        }

        // Rename: type new name
        String loottableId = loottableRenamePending.remove(uuid);
        if (loottableId == null) return;
        event.setCancelled(true);
        if (msg.equalsIgnoreCase("cancel")) {
            event.getPlayer().sendMessage(getMessage("loottable.rename-cancelled"));
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            LootTableData t = loottables.get(loottableId);
            if (t != null) {
                t.name = msg.isEmpty() ? "Unnamed" : msg;
                saveLoottables();
                event.getPlayer().sendMessage(getMessage("loottable.renamed-to", "name", t.name));
                openLoottableOptionsGui(event.getPlayer(), loottableId);
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Clean up edit mode if player was editing
        String editingChallengeId = null;
        for (Map.Entry<String, Set<UUID>> entry : challengeEditors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(uuid)) {
                editingChallengeId = entry.getKey();
                break;
            }
        }
        
        if (editingChallengeId != null) {
            ChallengeConfig cfg = challenges.get(editingChallengeId);
            
            Location originalLoc = editorOriginalLocations.get(uuid);
            if (originalLoc != null) {
                editorDisconnectLocations.put(uuid, originalLoc.clone());
            }
            
            if ("save".equalsIgnoreCase(editModeDisconnectAction)) {
                if (cfg != null && cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
                    // Async capture only - never block server thread (blocking caused 30s freezes on quit)
                    final ChallengeConfig cfgToSave = cfg;
                    final UUID uuidFinal = uuid;
                    final String challengeIdFinal = editingChallengeId;
                    final String playerName = player.getName();
                    captureInitialState(cfg, success -> {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (cfgToSave != null) saveChallengeToConfig(cfgToSave);
                                editorOriginalLocations.remove(uuidFinal);
                                removeEditorFromChallenge(uuidFinal, challengeIdFinal);
                                getLogger().info("Player " + playerName + " disconnected while editing challenge '" + challengeIdFinal + "'. Edit mode saved and changes kept.");
                                if (!isChallengeBeingEdited(challengeIdFinal) && !isChallengeLocked(challengeIdFinal)) {
                                    processQueue(challengeIdFinal);
                                }
                            }
                        }.runTask(ConradChallengesPlugin.this);
                    });
                } else {
                    if (cfg != null) saveChallengeToConfig(cfg);
                    editorOriginalLocations.remove(uuid);
                    removeEditorFromChallenge(uuid, editingChallengeId);
                    getLogger().info("Player " + player.getName() + " disconnected while editing challenge '" + editingChallengeId + "'. Edit mode saved and changes kept.");
                }
            } else {
                ChallengeConfig backup = challengeEditBackups.get(editingChallengeId);
                if (backup != null && cfg != null) {
                    restoreChallengeConfig(cfg, backup);
                    saveChallengeToConfig(cfg);
                    List<BlockState> stateBackup = challengeEditStateBackups.get(editingChallengeId);
                    if (stateBackup != null) {
                        challengeInitialStates.put(editingChallengeId, new ArrayList<>(stateBackup));
                    }
                }
                editorOriginalLocations.remove(uuid);
                removeEditorFromChallenge(uuid, editingChallengeId);
                getLogger().info("Player " + player.getName() + " disconnected while editing challenge '" + editingChallengeId + "'. Edit mode cancelled and challenge restored.");
            }
            
            if (!isChallengeBeingEdited(editingChallengeId) && !isChallengeLocked(editingChallengeId)) {
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
                        player.sendMessage(getMessage("admin.challenge-edit-returned-before-edit"));
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
                    
                    // Initialize lives based on difficulty tier
                    String tier = activeChallengeDifficulties.getOrDefault(challengeId, "easy");
                    int lives = tierLives.getOrDefault(tier, 3);
                    playerLives.put(uuid, lives);
                    
                    // Teleport player back to challenge
                    Location teleportLoc = getTeleportLocation(cfg);
                    if (teleportLoc != null) {
                        // Schedule teleport for next tick to ensure player is fully loaded
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    player.teleport(teleportLoc);
                                    player.sendMessage(getMessage("admin.challenge-edit-reconnected-welcome"));
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
        
        ChallengeConfig cfg = challenges.get(challengeId);
        if (cfg == null) return;
        
        // Schedule teleport for next tick (after respawn is complete)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                
                // Check if player still has lives (still in challenge)
                Integer remainingLives = playerLives.get(uuid);
                if (remainingLives != null && remainingLives >= 0 && activeChallenges.containsKey(uuid)) {
                    // Player has lives and is still in challenge - teleport back to challenge destination
                    Location challengeDest = getTeleportLocation(cfg);
                    if (challengeDest != null) {
                        player.teleport(challengeDest);
                        player.sendMessage(getMessage("lives.respawned-in-challenge", "remaining", String.valueOf(remainingLives)));
                    }
                } else {
                    // No lives or not in challenge - teleport to spawn
                    if (spawnLocation != null) {
                        player.teleport(spawnLocation);
                        giveBackBlockedItemOverflow(player, challengeId);
                        player.sendMessage(getMessage("challenge.died"));
                    }
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

    /**
     * Run MOB_DEATH scripts when a mob dies near a script block in a challenge.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeathScript(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        Location loc = entity.getLocation();
        ChallengeConfig cfg = getChallengeForLocation(loc);
        if (cfg == null || cfg.scriptNodes == null) return;
        Player killer = entity.getKiller();
        int ex = loc.getBlockX(), ey = loc.getBlockY(), ez = loc.getBlockZ();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
        if (worldName == null) return;
        for (ChallengeScriptNode node : cfg.scriptNodes) {
            if (node.trigger != ScriptTrigger.MOB_DEATH) continue;
            if (!node.worldName.equals(worldName)) continue;
            if (Math.abs(node.blockX - ex) > 5 || Math.abs(node.blockY - ey) > 5 || Math.abs(node.blockZ - ez) > 5) continue;
            Location nodeLoc = new Location(loc.getWorld(), node.blockX + 0.5, node.blockY + 0.5, node.blockZ + 0.5);
            scriptManager.runScriptsForTrigger(cfg, node.blockKey(), ScriptTrigger.MOB_DEATH, killer, nodeLoc, entity);
        }
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

        event.setCancelled(true); // Prevent opening the book when using it on the GateKeeper

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
                        // Capture difficulty tier BEFORE removing player from activeChallenges
                        String difficultyTier = activeChallengeDifficulties.getOrDefault(activeId, "easy");
                        runRewardCommands(player, cfg, activeId, difficultyTier);
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
                        // Capture difficulty tier BEFORE removing player from activeChallenges
                        String difficultyTier2 = activeChallengeDifficulties.getOrDefault(activeId, "easy");
                        runRewardCommands(player, cfg, activeId, difficultyTier2);
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
                        // Capture difficulty tier BEFORE removing player from activeChallenges
                        String difficultyTier3 = activeChallengeDifficulties.getOrDefault(activeId, "easy");
                        runRewardCommands(player, cfg, activeId, difficultyTier3);
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
                // Capture difficulty tier BEFORE cleanup (completedId might still be in activeChallengeDifficulties)
                String difficultyTier4 = activeChallengeDifficulties.getOrDefault(completedId, "easy");
                runRewardCommands(player, cfg, completedId, difficultyTier4);
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
                    // Check author(s) if required (primary forgery detection)
                    List<String> requiredAuthors = antiForgeryRequiredAuthors();
                    if (!requiredAuthors.isEmpty() && antiForgeryEnabled()) {
                        String bookAuthor = itemMeta.getAuthor();
                        boolean authorMatch = false;
                        if (bookAuthor != null) {
                            for (String allowed : requiredAuthors) {
                                if (bookAuthor.equals(allowed)) {
                                    authorMatch = true;
                                    break;
                                }
                            }
                        }
                        if (!authorMatch) {
                            // Author doesn't match any allowed author - flag as fake
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
     * Gets the remaining lives for a player in their current challenge, or 0 if not in a challenge.
     */
    public int getPlayerLives(UUID uuid) {
        Integer lives = playerLives.get(uuid);
        return lives != null ? lives : 0;
    }

    /**
     * Collects all tier rewards with inheritance (lower tiers get multiplied when inherited by higher tiers)
     * All rewards from lower tiers get the current tier's cumulative multiplier.
     * Prevents duplicate rewards - if multiple tiers have the EXACT same reward (same type, material, amount, command),
     * only the highest tier version is kept. Different amounts/values are treated as different rewards.
     */
    private List<TierReward> collectTierRewards(ChallengeConfig cfg, String difficultyTier) {
        List<TierReward> allRewards = new ArrayList<>();
        List<String> tierOrder = Arrays.asList("easy", "medium", "hard", "extreme");
        int currentTierIndex = tierOrder.indexOf(difficultyTier);
        if (currentTierIndex == -1) currentTierIndex = 0; // Default to easy
        
        // Get the current tier's cumulative multiplier (this applies to all inherited rewards)
        double currentTierMultiplier = cumulativeRewardMultipliers.getOrDefault(difficultyTier, 1.0);
        
        // Track rewards we've already added to prevent duplicates
        // Key format includes amounts/values to distinguish similar rewards
        Set<String> seenRewards = new HashSet<>();
        
        // Collect rewards in REVERSE order (higher tiers first) so higher tier rewards take precedence
        for (int i = currentTierIndex; i >= 0; i--) {
            String tierName = tierOrder.get(i);
            List<TierReward> tierRewards = cfg.difficultyTierRewards.get(tierName);
            if (tierRewards != null) {
                for (TierReward reward : tierRewards) {
                    // Generate a unique key for this reward to check for duplicates
                    // Now includes amounts/values so different amounts are treated as different rewards
                    String rewardKey = getRewardKey(reward);
                    
                    // Only add if we haven't seen this EXACT reward yet (higher tiers processed first)
                    if (!seenRewards.contains(rewardKey)) {
                        seenRewards.add(rewardKey);
                        
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
        }
        
        return allRewards;
    }
    
    /**
     * Generates a unique key for a reward to detect duplicates.
     * Now includes amounts/values to distinguish similar rewards with different amounts.
     * Custom items (with CustomModelData, custom name, or custom lore) are treated as different from regular items.
     * Commands include the full command (with amounts) to distinguish different reward values.
     */
    private String getRewardKey(TierReward reward) {
        switch (reward.type) {
            case HAND:
                if (reward.itemStack != null) {
                    // Check if item has custom data (CustomModelData, custom name, custom lore)
                    String customSuffix = getItemCustomDataSuffix(reward.itemStack);
                    // Include custom data in key so custom items are treated as different from regular items
                    // Also include a hash of the item's NBT data to distinguish items with different properties
                    String nbtHash = "";
                    try {
                        if (reward.itemStack.hasItemMeta()) {
                            // Create a simple hash from item meta to distinguish items
                            org.bukkit.inventory.meta.ItemMeta meta = reward.itemStack.getItemMeta();
                            if (meta != null) {
                                // Use display name and lore as part of the key
                                if (meta.hasDisplayName()) {
                                    nbtHash += ":" + meta.getDisplayName().hashCode();
                                }
                                if (meta.hasLore()) {
                                    nbtHash += ":" + meta.getLore().hashCode();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors
                    }
                    return "HAND:" + reward.itemStack.getType().name() + customSuffix + nbtHash;
                }
                return "HAND:UNKNOWN";
            case ITEM:
                if (reward.material != null) {
                    // Include amount in key so different amounts are treated as different rewards
                    int amount = reward.amount > 0 ? reward.amount : 1;
                    return "ITEM:" + reward.material.name() + ":" + amount;
                }
                return "ITEM:UNKNOWN";
            case COMMAND:
                if (reward.command != null) {
                    // Include the FULL command (with amounts) so different reward values are treated as different
                    // Normalize: remove player placeholder, normalize whitespace, but keep amounts
                    String normalized = reward.command.toLowerCase()
                        .replace("%player%", "")
                        .replaceAll("\\s+", " ") // Normalize whitespace
                        .trim();
                    // Use the full normalized command as the key (includes amounts)
                    return "COMMAND:" + normalized;
                }
                return "COMMAND:UNKNOWN";
            default:
                return "UNKNOWN:" + reward.type.name();
        }
    }
    
    /**
     * Gets a suffix string for custom item data to include in duplicate detection keys.
     * Returns empty string for regular items, or a suffix like ":CUSTOM:123" for custom items.
     */
    private String getItemCustomDataSuffix(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        
        // Check for CustomModelData (most common custom item identifier)
        if (meta.hasCustomModelData()) {
            return ":CUSTOM:" + meta.getCustomModelData();
        }
        
        // Check for custom display name
        if (meta.hasDisplayName()) {
            return ":CUSTOM:NAME";
        }
        
        // Check for custom lore
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            return ":CUSTOM:LORE";
        }
        
        // Check for enchantments (could be considered custom)
        if (meta.hasEnchants() && !meta.getEnchants().isEmpty()) {
            return ":CUSTOM:ENCHANT";
        }
        
        return "";
    }
    
    /**
     * Checks if an item is a custom item (has CustomModelData, custom name, or custom lore).
     */
    private boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        // Check for CustomModelData (most common custom item identifier)
        if (meta.hasCustomModelData()) {
            return true;
        }
        
        // Check for custom display name
        if (meta.hasDisplayName()) {
            return true;
        }
        
        // Check for custom lore
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            return true;
        }
        
        return false;
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
     * @return The ItemStack that was given (if any), or null if no item was given or RNG failed
     */
    private ItemStack applyTierReward(Player player, TierReward reward, double difficultyMultiplier, double speedMultiplier) {
        // Check RNG chance
        if (Math.random() > reward.chance) {
            return null; // Failed RNG roll
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
                        return item;
                    } else {
                        int newAmount = (int) Math.max(1, Math.round(item.getAmount() * totalMultiplier));
                        item.setAmount(newAmount);
                        player.getInventory().addItem(item);
                        return item;
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
                        return item;
                    } else {
                        int newAmount = (int) Math.max(1, Math.round(reward.amount * totalMultiplier));
                        ItemStack item = new ItemStack(reward.material, newAmount);
                        player.getInventory().addItem(item);
                        return item;
                    }
                }
            }
            case COMMAND -> {
                if (reward.command != null) {
                    String cmd = reward.command.replace("%player%", player.getName());
                    String multipliedCmd = applyRewardMultiplier(cmd, totalMultiplier);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), multipliedCmd);
                }
                return null; // Commands don't return items
            }
        }
        return null;
    }
    
    /**
     * Gets a display name for an item, using custom name if available, otherwise formatted material name
     * Handles both old ItemMeta display names and new 1.20.5+ component system
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }
        
        // First, try the old ItemMeta display name (for older items)
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
        }
        
        // Try to get display name from component system (1.20.5+)
        // Check the serialized item data for component-based custom name
        try {
            java.util.Map<String, Object> serialized = item.serialize();
            if (serialized != null) {
                // Check top-level components (1.20.5+ format)
                Object componentsObj = serialized.get("components");
                if (componentsObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> components = (java.util.Map<String, Object>) componentsObj;
                    
                    // Look for minecraft:custom_name
                    Object customNameObj = components.get("minecraft:custom_name");
                    if (customNameObj != null) {
                        String displayName = extractTextFromComponent(customNameObj);
                        if (displayName != null && !displayName.isEmpty()) {
                            return displayName;
                        }
                    }
                }
                
                // Also check under meta (older format or different structure)
                Object metaObj = serialized.get("meta");
                if (metaObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metaMap = (java.util.Map<String, Object>) metaObj;
                    
                    // Check for component-based display name (1.20.5+)
                    Object metaComponentsObj = metaMap.get("components");
                    if (metaComponentsObj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> metaComponents = (java.util.Map<String, Object>) metaComponentsObj;
                        
                        // Look for minecraft:custom_name
                        Object customNameObj = metaComponents.get("minecraft:custom_name");
                        if (customNameObj != null) {
                            String displayName = extractTextFromComponent(customNameObj);
                            if (displayName != null && !displayName.isEmpty()) {
                                return displayName;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Serialization or parsing failed, continue to material name
        }
        
        // Try reflection to access NMS ItemStack display name (for runtime items)
        try {
            // Get the NMS ItemStack using CraftItemStack.asNMSCopy
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            java.lang.reflect.Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);
            
            if (nmsItemStack != null) {
                // Try to get display name component
                try {
                    java.lang.reflect.Method getDisplayNameMethod = nmsItemStack.getClass().getMethod("getDisplayName");
                    Object displayNameComponent = getDisplayNameMethod.invoke(nmsItemStack);
                    
                    if (displayNameComponent != null) {
                        // Try to get string representation
                        try {
                            java.lang.reflect.Method getStringMethod = displayNameComponent.getClass().getMethod("getString");
                            String displayName = (String) getStringMethod.invoke(displayNameComponent);
                            if (displayName != null && !displayName.isEmpty()) {
                                return displayName;
                            }
                        } catch (Exception e) {
                            // Try toString() as fallback
                            String displayName = displayNameComponent.toString();
                            if (displayName != null && !displayName.isEmpty() && !displayName.equals("null")) {
                                return displayName;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Method not available
                }
            }
        } catch (Exception e) {
            // Reflection failed, fall through to material name
        }
        
        // Format material name (IRON_HOE -> Iron Hoe)
        String matName = item.getType().name().toLowerCase().replace("_", " ");
        String[] words = matName.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1));
                }
                formatted.append(" ");
            }
        }
        return formatted.toString().trim();
    }
    
    /**
     * Extracts text from a component object (can be Map, String JSON, etc.)
     */
    private String extractTextFromComponent(Object component) {
        if (component == null) {
            return null;
        }
        
        // If it's a Map, look for "text" field first
        if (component instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) component;
            Object text = map.get("text");
            if (text != null) {
                return text.toString();
            }
            
            // Also check for nested structures (like in "extra" arrays)
            Object extra = map.get("extra");
            if (extra instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> extraList = (java.util.List<Object>) extra;
                for (Object extraItem : extraList) {
                    String result = extractTextFromComponent(extraItem);
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
            }
            
            // Check all values recursively
            for (Object value : map.values()) {
                if (value != null && !(value instanceof java.util.Map) && !(value instanceof java.util.List)) {
                    continue; // Skip non-nested values
                }
                String result = extractTextFromComponent(value);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
        }
        
        // If it's a List, check each item
        if (component instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) component;
            for (Object item : list) {
                String result = extractTextFromComponent(item);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
        }
        
        // If it's already a String, try to parse it
        if (component instanceof String) {
            String str = (String) component;
            // Try to parse as JSON/YAML-like structure
            if (str.trim().startsWith("{")) {
                // Look for text:"..." pattern (handles both JSON and YAML-like formats)
                // Pattern: text:"value" or text: "value"
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("text\\s*[:=]\\s*[\"']([^\"']+)[\"']");
                java.util.regex.Matcher matcher = pattern.matcher(str);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                
                // Also try without quotes
                pattern = java.util.regex.Pattern.compile("text\\s*[:=]\\s*([^,}\\s]+)");
                matcher = pattern.matcher(str);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            // If it doesn't look like a structure, return as-is (might be plain text)
            if (!str.trim().startsWith("{") && !str.trim().isEmpty()) {
                return str;
            }
        }
        
        return null;
    }
    
    private void runRewardCommands(Player player, ChallengeConfig cfg, String challengeId) {
        // Default: get tier from activeChallengeDifficulties
        String difficultyTier = activeChallengeDifficulties.getOrDefault(challengeId, "easy");
        runRewardCommands(player, cfg, challengeId, difficultyTier);
    }
    
    private void runRewardCommands(Player player, ChallengeConfig cfg, String challengeId, String difficultyTier) {
        UUID uuid = player.getUniqueId();
        
        // Use the provided difficulty tier (captured before cleanup)
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
                ItemStack givenItem = applyTierReward(player, reward, finalDifficultyMultiplier, finalSpeedMultiplier);
                if (givenItem != null) {
                    // Add item to reward message
                    String itemName = getItemDisplayName(givenItem);
                    int amount = givenItem.getAmount();
                    if (amount > 1) {
                        rewardMessages.add("&e" + amount + "x " + itemName);
                    } else {
                        rewardMessages.add("&e" + itemName);
                    }
                }
            }
        }
        
        // Apply top time rewards if this is a new record (SPEED challenges only)
        if (isNewRecord[0] && cfg.completionType == CompletionType.SPEED && cfg.topTimeRewards != null && !cfg.topTimeRewards.isEmpty()) {
            // Top time rewards get 3x multiplier (difficulty multiplier still applies)
            for (TierReward reward : cfg.topTimeRewards) {
                ItemStack givenItem = applyTierReward(player, reward, finalDifficultyMultiplier, 3.0); // 3x for top time
                if (givenItem != null) {
                    // Add item to reward message
                    String itemName = getItemDisplayName(givenItem);
                    int amount = givenItem.getAmount();
                    if (amount > 1) {
                        rewardMessages.add("&e" + amount + "x " + itemName);
                    } else {
                        rewardMessages.add("&e" + itemName);
                    }
                }
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
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("rewards-cmd.new-record-bonus")));
                }
                
                if (rewardMessages.isEmpty()) {
                    player.sendMessage(getMessage("admin.challenge-completed-party"));
                } else {
                    // Translate color codes in reward messages before joining
                    List<String> translatedRewardMessages = new ArrayList<>();
                    for (String msg : rewardMessages) {
                        translatedRewardMessages.add(ChatColor.translateAlternateColorCodes('&', msg));
                    }
                    String separator = ChatColor.translateAlternateColorCodes('&', getMessage("admin.reward-separator"));
                    String rewardsText = String.join(separator, translatedRewardMessages);
                    player.sendMessage(getMessage("admin.challenge-completed-rewards", "rewards", rewardsText));
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    /**
     * Gives rewards to a dead party member who died but the party completed the challenge.
     * Uses easy tier rewards with 0.5x multiplier.
     */
    private void runRewardCommandsForDeadPartyMember(Player player, ChallengeConfig cfg, String challengeId) {
        UUID uuid = player.getUniqueId();
        
        // Always use easy tier with 0.5x multiplier for dead party members
        String difficultyTier = "easy";
        double difficultyMultiplier = 0.5; // 0.5x multiplier
        
        // No speed multiplier for dead players
        double speedMultiplier = 1.0;
        
        // Collect easy tier rewards only
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
                ItemStack givenItem = applyTierReward(player, reward, finalDifficultyMultiplier, finalSpeedMultiplier);
                if (givenItem != null) {
                    // Add item to reward message
                    String itemName = getItemDisplayName(givenItem);
                    int amount = givenItem.getAmount();
                    if (amount > 1) {
                        rewardMessages.add("&e" + amount + "x " + itemName);
                    } else {
                        rewardMessages.add("&e" + itemName);
                    }
                }
            }
        }
        
        if (!hasTierRewards) {
            // Fallback to old system (fallbackRewardCommands) with 0.5x multiplier
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
            return; // No rewards to give
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
                
                if (rewardMessages.isEmpty()) {
                    player.sendMessage(getMessage("lives.dead-party-reward-none"));
                } else {
                    // Translate color codes in reward messages before joining
                    List<String> translatedRewardMessages = new ArrayList<>();
                    for (String msg : rewardMessages) {
                        translatedRewardMessages.add(ChatColor.translateAlternateColorCodes('&', msg));
                    }
                    String separator = ChatColor.translateAlternateColorCodes('&', getMessage("admin.reward-separator"));
                    String rewardsText = String.join(separator, translatedRewardMessages);
                    player.sendMessage(getMessage("lives.dead-party-reward", "rewards", rewardsText));
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
            
            // If no tag, check author(s) (same logic as GateKeeper interaction)
            List<String> requiredAuthors = antiForgeryRequiredAuthors();
            if (!requiredAuthors.isEmpty() && antiForgeryEnabled()) {
                String bookAuthor = meta.getAuthor();
                if (bookAuthor != null) {
                    for (String allowed : requiredAuthors) {
                        if (bookAuthor.equals(allowed)) {
                            return true;
                        }
                    }
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
            player.sendMessage(getMessage("admin.challenge-area-world-not-loaded"));
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
        
        player.sendMessage(getMessage("admin.challenge-barrier-created", "count", String.valueOf(barrierCount)));
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
            player.sendMessage(getMessage("admin.challenge-area-world-not-loaded"));
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
        
        player.sendMessage(getMessage("admin.challenge-barrier-removed", "count", String.valueOf(removedCount)));
    }

    /**
     * Schedules next batch of block capture for one regen box; when box is complete calls whenDone.run() (for multi-area chaining).
     */
    private void scheduleNextCaptureBatchRunnable(String challengeId, World world, List<BlockState> states,
                                                  int[] currentX, int[] currentY, int[] currentZ,
                                                  int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                  int[] failedBlocks, long timeBudget, Runnable whenDone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                    try {
                        Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                        BlockState state = block.getState();
                        if (state instanceof Container container) {
                            try {
                                String locKey = currentX[0] + "," + currentY[0] + "," + currentZ[0];
                                byte[] nbtData = captureContainerNBT(block);
                                if (nbtData != null && nbtData.length > 0) {
                                    challengeContainerNBT.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, nbtData);
                                } else {
                                    org.bukkit.inventory.Inventory inv = container.getInventory();
                                    if (inv != null) {
                                        ItemStack[] items = new ItemStack[inv.getSize()];
                                        boolean hasItems = false;
                                        for (int i = 0; i < inv.getSize(); i++) {
                                            ItemStack item = inv.getItem(i);
                                            if (item != null && item.getType() != Material.AIR) {
                                                items[i] = item.clone();
                                                hasItems = true;
                                            } else items[i] = null;
                                        }
                                        if (hasItems) challengeContainerInventories.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, items);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        states.add(state);
                    } catch (Exception e) {
                        failedBlocks[0]++;
                    }
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
                if (currentX[0] > maxX) whenDone.run();
                else scheduleNextCaptureBatchRunnable(challengeId, world, states, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, whenDone);
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
                        
                        // For containers, capture NBT data (WorldEdit-style) for exact restoration
                        // This is more reliable than BlockState snapshots
                        if (state instanceof Container container) {
                            try {
                                String locKey = currentX[0] + "," + currentY[0] + "," + currentZ[0];
                                
                                // Try to capture NBT data first (most reliable)
                                byte[] nbtData = captureContainerNBT(block);
                                if (nbtData != null && nbtData.length > 0) {
                                    challengeContainerNBT.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, nbtData);
                                    getLogger().info("[CONTAINER DEBUG] Stored container NBT at " + locKey + " for challenge '" + challengeId + "' (" + nbtData.length + " bytes)");
                                } else {
                                    // Fallback: store inventory items if NBT capture fails
                                    org.bukkit.inventory.Inventory inv = container.getInventory();
                                    if (inv != null) {
                                        ItemStack[] items = new ItemStack[inv.getSize()];
                                        boolean hasItems = false;
                                        for (int i = 0; i < inv.getSize(); i++) {
                                            ItemStack item = inv.getItem(i);
                                            if (item != null && item.getType() != Material.AIR) {
                                                items[i] = item.clone();
                                                hasItems = true;
                                            } else {
                                                items[i] = null;
                                            }
                                        }
                                        challengeContainerInventories.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, items);
                                        long itemCount = java.util.Arrays.stream(items).filter(item -> item != null && item.getType() != Material.AIR).count();
                                        if (hasItems) {
                                            getLogger().info("[CONTAINER DEBUG] Stored container inventory (fallback) at " + locKey + " for challenge '" + challengeId + "' (" + itemCount + " items)");
                                        }
                                    }
                                }
                            } catch (Exception invException) {
                                // If container access fails, log but continue - state will still be captured
                                getLogger().fine("Could not access container at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " (continuing anyway)");
                            }
                        }
                        
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
                
                // For containers, explicitly access the inventory to ensure it's loaded and captured
                // This is done in a separate try-catch to avoid breaking the entire capture if one container fails
                if (state instanceof Container container) {
                    try {
                        // Access the inventory to ensure it's loaded in the BlockState snapshot
                        // This is critical for proper inventory preservation during regeneration
                        container.getInventory();
                    } catch (Exception invException) {
                        // If inventory access fails, log but continue - state will still be captured
                        // This can happen if the container is in an invalid state
                        getLogger().fine("Could not access inventory for container at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " (continuing anyway)");
                    }
                }
                
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
     * Captures the initial state of all blocks in the regeneration area(s) for a challenge.
     * With multiple regen areas, captures each box in sequence and merges into one state list;
     * regeneration then restores all blocks from that list (smooth, one pass).
     * Runs in batches on main thread to avoid lag.
     */
    private void captureInitialState(ChallengeConfig cfg, java.util.function.Consumer<Boolean> callback) {
        // Build list of regen boxes (multiple areas or legacy single)
        List<Location[]> boxes = new ArrayList<>();
        if (cfg.regenerationAreas != null && !cfg.regenerationAreas.isEmpty()) {
            for (Location[] c : cfg.regenerationAreas) {
                if (c != null && c.length >= 2) boxes.add(c);
            }
        }
        if (boxes.isEmpty() && cfg.regenerationCorner1 != null && cfg.regenerationCorner2 != null) {
            boxes.add(new Location[]{ cfg.regenerationCorner1, cfg.regenerationCorner2 });
        }
        if (boxes.isEmpty()) {
            if (callback != null) new BukkitRunnable() { @Override public void run() { callback.accept(false); } }.runTask(ConradChallengesPlugin.this);
            return;
        }
        final String challengeId = cfg.id;
        challengeContainerInventories.remove(challengeId);
        challengeContainerNBT.remove(challengeId);
        final List<BlockState> allStates = new ArrayList<>();
        final java.util.function.Consumer<Boolean> finalCallback = callback;
        Runnable whenAllDone = () -> {
            challengeInitialStates.put(challengeId, allStates);
            if (boxes.size() > 1) getLogger().info("Captured initial state for challenge '" + challengeId + "' (" + allStates.size() + " blocks across " + boxes.size() + " regen areas)");
            if (finalCallback != null) new BukkitRunnable() { @Override public void run() { finalCallback.accept(true); } }.runTask(ConradChallengesPlugin.this);
        };
        captureNextRegenBox(cfg, boxes, 0, allStates, whenAllDone);
    }

    /** Captures one regen box, then runs whenDone (chain for multi-area). */
    private void captureNextRegenBox(ChallengeConfig cfg, List<Location[]> boxes, int boxIndex, List<BlockState> allStates, Runnable whenAllDone) {
        if (boxIndex >= boxes.size()) {
            whenAllDone.run();
            return;
        }
        Location[] corners = boxes.get(boxIndex);
        Location c1 = corners[0], c2 = corners[1];
        if (c1.getWorld() == null || !c1.getWorld().equals(c2.getWorld())) {
            getLogger().warning("Regen area " + (boxIndex + 1) + " for challenge '" + cfg.id + "' has mismatched worlds; skipping.");
            captureNextRegenBox(cfg, boxes, boxIndex + 1, allStates, whenAllDone);
            return;
        }
        final World world = c1.getWorld();
        final String challengeId = cfg.id;
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1;
        } else {
            minY = Math.min(c1.getBlockY(), c2.getBlockY());
            maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        }
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());
        final Runnable whenThisBoxDone = () -> captureNextRegenBox(cfg, boxes, boxIndex + 1, allStates, whenAllDone);
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
        
        if (boxes.size() == 1) getLogger().info("Capturing initial state for challenge '" + challengeId + "' (estimated " + estimatedBlocks + " blocks, time budget: " + (timeBudget / 1_000_000) + "ms per tick)");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                // Process blocks until we hit time budget or finish
                while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                    try {
                        Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                        BlockState state = block.getState();
                        
                        // For containers, capture NBT data (WorldEdit-style) for exact restoration
                        // This is more reliable than BlockState snapshots
                        if (state instanceof Container container) {
                            try {
                                String locKey = currentX[0] + "," + currentY[0] + "," + currentZ[0];
                                
                                // Try to capture NBT data first (most reliable)
                                byte[] nbtData = captureContainerNBT(block);
                                if (nbtData != null && nbtData.length > 0) {
                                    challengeContainerNBT.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, nbtData);
                                    getLogger().info("[CONTAINER DEBUG] Stored container NBT at " + locKey + " for challenge '" + challengeId + "' (" + nbtData.length + " bytes)");
                                } else {
                                    // Fallback: store inventory items if NBT capture fails
                                    org.bukkit.inventory.Inventory inv = container.getInventory();
                                    if (inv != null) {
                                        ItemStack[] items = new ItemStack[inv.getSize()];
                                        boolean hasItems = false;
                                        for (int i = 0; i < inv.getSize(); i++) {
                                            ItemStack item = inv.getItem(i);
                                            if (item != null && item.getType() != Material.AIR) {
                                                items[i] = item.clone();
                                                hasItems = true;
                                            } else {
                                                items[i] = null;
                                            }
                                        }
                                        challengeContainerInventories.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, items);
                                        long itemCount = java.util.Arrays.stream(items).filter(item -> item != null && item.getType() != Material.AIR).count();
                                        if (hasItems) {
                                            getLogger().info("[CONTAINER DEBUG] Stored container inventory (fallback) at " + locKey + " for challenge '" + challengeId + "' (" + itemCount + " items)");
                                        }
                                    }
                                }
                            } catch (Exception invException) {
                                // If container access fails, log but continue - state will still be captured
                                getLogger().fine("Could not access container at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " (continuing anyway)");
                            }
                        }
                        
                        allStates.add(state);
                    } catch (Exception e) {
                        failedBlocks[0]++;
                        getLogger().warning("Error capturing block state at " + currentX[0] + "," + currentY[0] + "," + currentZ[0] + " for challenge '" + challengeId + "': " + e.getMessage());
                    }
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
                if (currentX[0] > maxX) {
                    if (boxIndex == 0 && boxes.size() == 1) {
                        if (failedBlocks[0] > 0) getLogger().warning("Captured initial state for challenge '" + challengeId + "' (" + allStates.size() + " blocks, " + failedBlocks[0] + " failed)");
                        else getLogger().info("Captured initial state for challenge '" + challengeId + "' (" + allStates.size() + " blocks)");
                    }
                    whenThisBoxDone.run();
                } else {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            long startTime = System.nanoTime();
                            while (currentX[0] <= maxX && (System.nanoTime() - startTime) < timeBudget) {
                                try {
                                    Block block = world.getBlockAt(currentX[0], currentY[0], currentZ[0]);
                                    BlockState state = block.getState();
                                    if (state instanceof Container container) {
                                        try {
                                            org.bukkit.inventory.Inventory inv = container.getInventory();
                                            if (inv != null) {
                                                ItemStack[] items = new ItemStack[inv.getSize()];
                                                boolean hasItems = false;
                                                for (int i = 0; i < inv.getSize(); i++) {
                                                    ItemStack item = inv.getItem(i);
                                                    if (item != null && item.getType() != Material.AIR) {
                                                        items[i] = item.clone();
                                                        hasItems = true;
                                                    } else items[i] = null;
                                                }
                                                if (hasItems) {
                                                    String locKey = currentX[0] + "," + currentY[0] + "," + currentZ[0];
                                                    challengeContainerInventories.computeIfAbsent(challengeId, k -> new HashMap<>()).put(locKey, items);
                                                }
                                            }
                                        } catch (Exception invException) {
                                            getLogger().fine("Could not access inventory for container at " + currentX[0] + "," + currentY[0] + "," + currentZ[0]);
                                        }
                                    }
                                    allStates.add(state);
                                } catch (Exception e) {
                                    failedBlocks[0]++;
                                }
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
                            if (currentX[0] > maxX) whenThisBoxDone.run();
                            else scheduleNextCaptureBatchRunnable(challengeId, world, allStates, currentX, currentY, currentZ, minX, maxX, minY, maxY, minZ, maxZ, failedBlocks, timeBudget, whenThisBoxDone);
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
     * Helper method to collect chunks that need loading in time-budgeted batches (async)
     * This prevents blocking the main thread when iterating through millions of blocks
     */
    private void scheduleChunkCollection(String challengeId, World world, List<BlockState> finalStates,
                                         int[] currentIndex, java.util.Set<String> chunksToLoad,
                                         long timeBudgetNs, int totalBlocks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                
                // Process blocks until time budget is exceeded
                while (currentIndex[0] < finalStates.size()) {
                    // Check time budget
                    if (System.nanoTime() - startTime >= timeBudgetNs) {
                        // Time budget exceeded, schedule next tick
                        scheduleChunkCollection(challengeId, world, finalStates, currentIndex, chunksToLoad, timeBudgetNs, totalBlocks);
                        return;
                    }
                    
                    BlockState state = finalStates.get(currentIndex[0]);
                    Location loc = state.getLocation();
                    int chunkX = loc.getBlockX() >> 4;
                    int chunkZ = loc.getBlockZ() >> 4;
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        chunksToLoad.add(chunkX + "," + chunkZ);
                    }
                    
                    currentIndex[0]++;
                }
                
                // All chunks collected
                if (currentIndex[0] >= finalStates.size()) {
                    // Chunk collection complete, proceed with chunk loading or comparison
                    if (chunksToLoad.isEmpty()) {
                        // All chunks already loaded, start comparison phase immediately
                        final int[] compareIndex = {0};
                        final java.util.List<BlockState> blocksToRestore = new java.util.ArrayList<>();
                        final int[] skipped = {0};
                        // Scale comparison time budget for very large areas (e.g. 30M blocks) to reduce total ticks
                        long compareTimeBudget = 12_000_000L;
                        if (totalBlocks > 10_000_000) compareTimeBudget = 25_000_000L;
                        else if (totalBlocks > 2_000_000) compareTimeBudget = 18_000_000L;
                        scheduleNextComparisonBatch(challengeId, world, finalStates, compareIndex, compareTimeBudget, totalBlocks, blocksToRestore, skipped);
                    } else {
                        // Pre-load chunks in time-budgeted batches, then start comparison phase
                        getLogger().info("Pre-loading " + chunksToLoad.size() + " chunks for regeneration...");
                        final java.util.List<String> chunksList = new java.util.ArrayList<>(chunksToLoad);
                        final int[] chunkIndex = {0};
                        final long chunkLoadBudget = 8_000_000L; // 8ms per tick for chunk loading
                        scheduleNextChunkLoadBatch(challengeId, world, finalStates, chunksList, chunkIndex, chunkLoadBudget);
                    }
                } else {
                    // More blocks to process, schedule next tick
                    scheduleChunkCollection(challengeId, world, finalStates, currentIndex, chunksToLoad, timeBudgetNs, totalBlocks);
                }
            }
        }.runTaskLater(this, 1L);
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
                    int totalBlocks = finalStates.size();
                    long compareTimeBudget = 12_000_000L;
                    if (totalBlocks > 10_000_000) compareTimeBudget = 25_000_000L;
                    else if (totalBlocks > 2_000_000) compareTimeBudget = 18_000_000L;
                    scheduleNextComparisonBatch(challengeId, world, finalStates, currentIndex, compareTimeBudget, totalBlocks, blocksToRestore, skipped);
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
                            
                            // ALWAYS check containers for inventory changes (inventory changes don't affect block type/data)
                            // This must run regardless of needsRestore status
                            if (originalState instanceof Container originalContainer) {
                                // For containers, ALWAYS check if contents changed (inventory changes don't affect block type/data)
                                BlockState currentState = block.getState();
                                if (currentState instanceof Container currentContainer) {
                                    // Compare inventory contents - use isSimilar() for better comparison
                                    if (originalContainer.getInventory() != null && currentContainer.getInventory() != null) {
                                        int inventorySize = originalContainer.getInventory().getSize();
                                        if (inventorySize != currentContainer.getInventory().getSize()) {
                                            needsRestore = true;
                                        } else {
                                            for (int i = 0; i < inventorySize; i++) {
                                                ItemStack originalItem = originalContainer.getInventory().getItem(i);
                                                ItemStack currentItem = currentContainer.getInventory().getItem(i);
                                                
                                                // Both null = same, continue
                                                if (originalItem == null && currentItem == null) continue;
                                                
                                                // One null, one not = different
                                                if (originalItem == null || currentItem == null) {
                                                    needsRestore = true;
                                                    break;
                                                }
                                                
                                                // Compare using isSimilar() which handles NBT and metadata better
                                                // Also check amount separately as isSimilar() ignores amount
                                                if (!originalItem.isSimilar(currentItem) || originalItem.getAmount() != currentItem.getAmount()) {
                                                    needsRestore = true;
                                                    break;
                                                }
                                            }
                                        }
                                    } else {
                                        // One inventory is null but not both - needs restore
                                        if (originalContainer.getInventory() == null != (currentContainer.getInventory() == null)) {
                                            needsRestore = true;
                                        }
                                    }
                                } else {
                                    // Current state is not a container but original was - needs restore
                                    needsRestore = true;
                                }
                            }
                            
                            // Check other special tile entities (only if block data matches or is simple block)
                            if (!needsRestore) {
                                if (originalState instanceof org.bukkit.block.Sign originalSign) {
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
                        
                        // Always restore containers if we have stored inventory data for them
                        // This is the most reliable check since BlockState snapshots don't preserve inventories
                        if (!needsRestore && originalState instanceof Container) {
                            String locKey = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                            Map<String, ItemStack[]> containerInvMap = challengeContainerInventories.get(challengeId);
                            if (containerInvMap != null && containerInvMap.containsKey(locKey)) {
                                ItemStack[] storedItems = containerInvMap.get(locKey);
                                // Check if stored inventory has any items
                                for (ItemStack item : storedItems) {
                                    if (item != null && item.getType() != Material.AIR) {
                                        needsRestore = true;
                                        getLogger().fine("Forcing container restore at " + locKey + " - has stored inventory");
                                        break;
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
                        // Scale time budget for very large restorations (e.g. 30M blocks with lots of fire→air) to reduce total ticks
                        int toRestore = blocksToRestore.size();
                        long restoreTimeBudget = 15_000_000L; // 15ms default
                        if (toRestore > 2_000_000) restoreTimeBudget = 35_000_000L;  // 35ms for huge regens
                        else if (toRestore > 500_000) restoreTimeBudget = 25_000_000L; // 25ms for large
                        else if (toRestore > 100_000) restoreTimeBudget = 20_000_000L; // 20ms for medium-large
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
                        
                        // Fast path: restoring to AIR (e.g. clearing fire, water, etc.) - no BlockData or tile logic needed
                        if (originalType == Material.AIR) {
                            block.setType(Material.AIR, false);
                            restored[0]++;
                            currentIndex[0]++;
                            continue;
                        }
                        
                        org.bukkit.block.data.BlockData originalData = originalState.getBlockData();
                        
                        // Check if this is a container or other tile entity that needs special handling
                        // Containers MUST use BlockState path to preserve inventory contents
                        boolean needsTileEntityHandling = originalState instanceof Container || 
                                                         originalState instanceof org.bukkit.block.Sign ||
                                                         originalState instanceof org.bukkit.block.Banner ||
                                                         originalState instanceof org.bukkit.block.CreatureSpawner ||
                                                         originalState instanceof org.bukkit.block.CommandBlock;
                        
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
                        
                        if (isMultiBlock || needsTileEntityHandling) {
                            // For multi-block structures and tile entities, use BlockState.update()
                            // This ensures proper restoration of container inventories, signs, spawners, etc.
                            
                            // For containers, we need to set the block type first, then get a fresh BlockState
                            // This ensures the container is properly initialized before we try to set inventory
                            if (originalState instanceof Container) {
                                getLogger().info("[CONTAINER DEBUG] Restoring container at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                                // Set block type and data first to initialize the container
                                block.setType(originalType, false);
                                block.setBlockData(originalData, false);
                                
                                // Now get a fresh BlockState (this will have the container properly initialized)
                                BlockState newState = block.getState();
                                
                                // Restore container contents using NBT data (WorldEdit-style) for exact restoration
                                // Falls back to item array if NBT not available
                                if (newState instanceof Container newContainer) {
                                    String locKey = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                                    
                                    // Try NBT restoration first (most reliable)
                                    Map<String, byte[]> containerNBTMap = challengeContainerNBT.get(challengeId);
                                    if (containerNBTMap != null && containerNBTMap.containsKey(locKey)) {
                                        byte[] nbtData = containerNBTMap.get(locKey);
                                        if (restoreContainerNBT(block, nbtData)) {
                                            getLogger().info("[CONTAINER DEBUG] Restored container NBT at " + locKey + " for challenge '" + challengeId + "'");
                                        } else {
                                            getLogger().warning("[CONTAINER DEBUG] Failed to restore container NBT at " + locKey + ", falling back to item array");
                                            // Fall through to item array restoration
                                            restoreContainerItems(block, newContainer, challengeId, locKey);
                                        }
                                    } else {
                                        // No NBT data, use item array fallback
                                        restoreContainerItems(block, newContainer, challengeId, locKey);
                                    }
                                }
                            } else {
                                // For non-container tile entities, use the standard BlockState approach
                                BlockState newState = block.getState();
                                newState.setType(originalType);
                                newState.setBlockData(originalData);
                                
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
                                
                                // Update with appropriate physics setting
                                // Multi-block structures need physics=true, containers/tile entities need physics=false
                                newState.update(true, isMultiBlock);
                            }
                        } else {
                            // Fast path: Use direct Block.setType() and setBlockData() with applyPhysics=false
                            // This bypasses expensive BlockState.update() overhead for simple blocks
                            block.setType(originalType, false);  // false = no physics updates
                            block.setBlockData(originalData, false);  // false = no physics updates
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
                    
                    // Fill chests with assigned loot tables (clear then random roll per chest)
                    ChallengeConfig cfgForLoot = challenges.get(challengeId);
                    if (cfgForLoot != null) {
                        fillChestsWithLoottables(challengeId, cfgForLoot);
                    }
                    
                    // Spawner cooldowns will be reset when challenge completes/exits, not during start
                    
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
        challengePlacedBlocks.remove(cfg.id);

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
        
        // Collect chunks that need to be loaded in async batches to avoid blocking main thread
        // This is especially important for large areas with millions of blocks
        final int[] chunkCollectionIndex = {0};
        final java.util.Set<String> chunksToLoad = new java.util.HashSet<>();
        final long chunkCollectionBudget = 10_000_000L; // 10ms per tick for chunk collection
        scheduleChunkCollection(challengeId, world, finalStates, chunkCollectionIndex, chunksToLoad, chunkCollectionBudget, totalBlocks);
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
        clone.regenerationAreas = new ArrayList<>();
        if (original.regenerationAreas != null) {
            for (Location[] corners : original.regenerationAreas) {
                if (corners != null && corners.length >= 2) {
                    clone.regenerationAreas.add(new Location[]{ corners[0].clone(), corners[1].clone() });
                } else {
                    clone.regenerationAreas.add(null);
                }
            }
        }
        clone.regenerationCorner1 = original.regenerationCorner1 != null ? original.regenerationCorner1.clone() : null;
        clone.regenerationCorner2 = original.regenerationCorner2 != null ? original.regenerationCorner2.clone() : null;
        clone.regenerationAutoExtendY = original.regenerationAutoExtendY;
        clone.challengeAreas = new ArrayList<>();
        if (original.challengeAreas != null) {
            for (Location[] corners : original.challengeAreas) {
                if (corners != null && corners.length >= 2) {
                    clone.challengeAreas.add(new Location[]{ corners[0].clone(), corners[1].clone() });
                } else {
                    clone.challengeAreas.add(null);
                }
            }
        }
        clone.challengeCorner1 = original.challengeCorner1 != null ? original.challengeCorner1.clone() : null;
        clone.challengeCorner2 = original.challengeCorner2 != null ? original.challengeCorner2.clone() : null;
        clone.blockedItems = original.blockedItems != null ? new HashSet<>(original.blockedItems) : new HashSet<>();
        clone.ignoredChestKeys = original.ignoredChestKeys != null ? new HashSet<>(original.ignoredChestKeys) : new HashSet<>();
        clone.legendaryChestKeys = original.legendaryChestKeys != null ? new HashSet<>(original.legendaryChestKeys) : new HashSet<>();
        clone.scriptNodes = new ArrayList<>();
        if (original.scriptNodes != null) {
            for (ChallengeScriptNode n : original.scriptNodes) {
                ChallengeScriptNode copy = new ChallengeScriptNode(n.worldName, n.blockX, n.blockY, n.blockZ);
                copy.trigger = n.trigger;
                copy.functionType = n.functionType;
                copy.functionData.putAll(n.functionData);
                for (Map<String, Object> c : n.conditions) {
                    copy.conditions.add(new HashMap<>(c));
                }
                clone.scriptNodes.add(copy);
            }
        }
        clone.passThroughBlockKeys = original.passThroughBlockKeys != null ? new HashSet<>(original.passThroughBlockKeys) : new HashSet<>();
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
        target.regenerationAreas.clear();
        if (backup.regenerationAreas != null) {
            for (Location[] c : backup.regenerationAreas) {
                if (c != null && c.length >= 2) target.regenerationAreas.add(new Location[]{ c[0].clone(), c[1].clone() });
            }
        }
        target.regenerationCorner1 = backup.regenerationCorner1 != null ? backup.regenerationCorner1.clone() : null;
        target.regenerationCorner2 = backup.regenerationCorner2 != null ? backup.regenerationCorner2.clone() : null;
        target.regenerationAutoExtendY = backup.regenerationAutoExtendY;
        target.challengeAreas.clear();
        if (backup.challengeAreas != null) {
            for (Location[] c : backup.challengeAreas) {
                if (c != null && c.length >= 2) target.challengeAreas.add(new Location[]{ c[0].clone(), c[1].clone() });
            }
        }
        target.challengeCorner1 = backup.challengeCorner1 != null ? backup.challengeCorner1.clone() : null;
        target.challengeCorner2 = backup.challengeCorner2 != null ? backup.challengeCorner2.clone() : null;
        target.blockedItems = backup.blockedItems != null ? new HashSet<>(backup.blockedItems) : new HashSet<>();
        target.ignoredChestKeys = backup.ignoredChestKeys != null ? new HashSet<>(backup.ignoredChestKeys) : new HashSet<>();
        target.legendaryChestKeys = backup.legendaryChestKeys != null ? new HashSet<>(backup.legendaryChestKeys) : new HashSet<>();
        target.scriptNodes.clear();
        if (backup.scriptNodes != null) {
            for (ChallengeScriptNode n : backup.scriptNodes) {
                ChallengeScriptNode copy = new ChallengeScriptNode(n.worldName, n.blockX, n.blockY, n.blockZ);
                copy.trigger = n.trigger;
                copy.functionType = n.functionType;
                copy.functionData.putAll(n.functionData);
                for (Map<String, Object> c : n.conditions) {
                    copy.conditions.add(new HashMap<>(c));
                }
                target.scriptNodes.add(copy);
            }
        }
        target.passThroughBlockKeys = backup.passThroughBlockKeys != null ? new HashSet<>(backup.passThroughBlockKeys) : new HashSet<>();
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
            giveBackBlockedItemOverflow(player, challengeId);
            player.sendMessage(getMessage("admin.challenge-disconnect-returned-to-spawn"));
        } else {
            // Fallback to challenge destination if spawn not set
            if (cfg.destination != null) {
                player.teleport(cfg.destination);
                giveBackBlockedItemOverflow(player, challengeId);
                player.sendMessage(getMessage("admin.challenge-disconnect-no-return"));
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
                
                if (!isLocationInRegenArea(cfg, spawnLocation)) {
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
     * Resets cooldowns for all MythicMobs spawners within a challenge's regeneration area.
     * This ensures teams don't have to wait on spawner cooldowns from previous teams.
     * 
     * @param cfg The challenge configuration
     */
    private void resetMythicMobsSpawnersInArea(ChallengeConfig cfg) {
        // Cooldown check: prevent duplicate resets within 2 seconds
        Long lastReset = lastSpawnerResetTime.get(cfg.id);
        long currentTime = System.currentTimeMillis();
        if (lastReset != null && (currentTime - lastReset) < 2000L) {
            getLogger().fine("Skipping spawner reset for challenge '" + cfg.id + "' - reset was called " + 
                ((currentTime - lastReset) / 1000.0) + " seconds ago (cooldown: 2 seconds)");
            return; // Too soon since last reset
        }
        
        // Update last reset time
        lastSpawnerResetTime.put(cfg.id, currentTime);
        
        // Run async to avoid TPS hangups - filter spawners async, then reset on main thread
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            findAndResetSpawnersAsync(cfg);
        });
    }
    
    private void findAndResetSpawnersAsync(ChallengeConfig cfg) {
        if (debugSpawnerLevels) {
            getLogger().info("Finding MythicMobs spawners to reset for challenge '" + cfg.id + "' (async)");
        }
        
        // Check if MythicMobs is installed
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().fine("MythicMobs not installed, skipping spawner reset");
            return; // MythicMobs not installed
        }
        
        // Check if regeneration area is set
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            getLogger().fine("No regeneration area set for challenge '" + cfg.id + "', skipping spawner reset");
            return; // No regeneration area set
        }
        
        World world = cfg.regenerationCorner1.getWorld();
        if (world == null) {
            getLogger().fine("World not loaded for challenge '" + cfg.id + "', skipping spawner reset");
            return; // World not loaded
        }
        
        // Calculate regeneration area bounds
        int minX = Math.min(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int maxX = Math.max(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1;
        } else {
            minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
            maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
        }
        int minZ = Math.min(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        int maxZ = Math.max(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        
        String worldName = world.getName();
        if (debugSpawnerLevels) {
            getLogger().info("Regeneration area bounds: X[" + minX + " to " + maxX + "] Y[" + minY + " to " + maxY + "] Z[" + minZ + " to " + maxZ + "] in world '" + worldName + "'");
        }
        
        try {
            // Get MythicMobs API using reflection
            Class<?> mythicAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicPlugin = mythicAPI.getMethod("inst").invoke(null);
            
            // Get spawner manager
            Object spawnerManager = mythicAPI.getMethod("getSpawnerManager").invoke(mythicPlugin);
            if (spawnerManager == null) {
                getLogger().warning("MythicMobs spawner manager is null - cannot reset spawners");
                return;
            }
            
            if (debugSpawnerLevels) {
                getLogger().info("Got spawner manager: " + spawnerManager.getClass().getName());
            }
            
            // MythicMobs spawners are stored by name/ID, typically in a Map<String, Spawner>
            // Try to get all spawners - they're usually stored as a Map
            java.util.Collection<?> spawners = null;
            
            // Try method 1: getSpawners() - most common MythicMobs method
            try {
                Method getSpawnersMethod = spawnerManager.getClass().getMethod("getSpawners");
                Object result = getSpawnersMethod.invoke(spawnerManager);
                if (result instanceof java.util.Map) {
                    spawners = ((java.util.Map<?, ?>) result).values();
                    if (debugSpawnerLevels) {
                        getLogger().info("Got spawners via getSpawners() returning Map: " + (spawners != null ? spawners.size() : "null"));
                    }
                } else if (result instanceof java.util.Collection) {
                    spawners = (java.util.Collection<?>) result;
                    if (debugSpawnerLevels) {
                        getLogger().info("Got spawners via getSpawners() returning Collection: " + (spawners != null ? spawners.size() : "null"));
                    }
                } else {
                    if (debugSpawnerLevels) {
                        getLogger().info("getSpawners() returned unexpected type: " + (result != null ? result.getClass().getName() : "null"));
                    }
                }
            } catch (NoSuchMethodException e) {
                if (debugSpawnerLevels) {
                    getLogger().info("getSpawners() method not found, trying alternatives");
                }
            } catch (Exception e) {
                getLogger().warning("Error calling getSpawners(): " + e.getMessage());
                e.printStackTrace();
            }
            
            // Try method 2: getSpawnerMap() or similar Map-returning methods
            if (spawners == null || spawners.isEmpty()) {
                String[] methodNames = {"getSpawnerMap", "getSpawnersMap", "getAllSpawners", "getActiveSpawners"};
                for (String methodName : methodNames) {
                    try {
                        Method method = spawnerManager.getClass().getMethod(methodName);
                        Object result = method.invoke(spawnerManager);
                        if (result instanceof java.util.Map) {
                            spawners = ((java.util.Map<?, ?>) result).values();
                            getLogger().fine("Got spawners via " + methodName + "() returning Map: " + (spawners != null ? spawners.size() : "null"));
                            break;
                        } else if (result instanceof java.util.Collection) {
                            spawners = (java.util.Collection<?>) result;
                            getLogger().fine("Got spawners via " + methodName + "() returning Collection: " + (spawners != null ? spawners.size() : "null"));
                            break;
                        }
                    } catch (NoSuchMethodException e) {
                        // Try next method
                    } catch (Exception e) {
                        getLogger().fine("Error calling " + methodName + "(): " + e.getMessage());
                    }
                }
            }
            
            // Try method 3: Direct field access to spawners map (MythicMobs often stores as Map<String, Spawner>)
            if (spawners == null || spawners.isEmpty()) {
                try {
                    java.lang.reflect.Field[] fields = spawnerManager.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        String fieldName = field.getName().toLowerCase();
                        // Look for fields that might contain spawners (map, spawners, etc.)
                        if ((fieldName.contains("spawner") || fieldName.contains("map")) && 
                            java.util.Map.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            Object fieldValue = field.get(spawnerManager);
                            if (fieldValue instanceof java.util.Map) {
                                spawners = ((java.util.Map<?, ?>) fieldValue).values();
                                getLogger().fine("Got spawners via field " + field.getName() + ": " + (spawners != null ? spawners.size() : "null"));
                                if (spawners != null && !spawners.isEmpty()) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    getLogger().fine("Error accessing spawner fields: " + e.getMessage());
                }
            }
            
            // Try method 4: Try all public methods that return Map or Collection
            if (spawners == null || spawners.isEmpty()) {
                getLogger().fine("Trying all public methods on spawner manager to find spawners...");
                for (Method method : spawnerManager.getClass().getMethods()) {
                    if (method.getParameterCount() == 0) {
                        Class<?> returnType = method.getReturnType();
                        if (java.util.Map.class.isAssignableFrom(returnType) || 
                            java.util.Collection.class.isAssignableFrom(returnType)) {
                            try {
                                Object result = method.invoke(spawnerManager);
                                if (result instanceof java.util.Map) {
                                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
                                    if (!map.isEmpty()) {
                                        spawners = map.values();
                                        getLogger().fine("Found spawners via method " + method.getName() + "(): " + spawners.size());
                                        break;
                                    }
                                } else if (result instanceof java.util.Collection) {
                                    java.util.Collection<?> coll = (java.util.Collection<?>) result;
                                    if (!coll.isEmpty()) {
                                        spawners = coll;
                                        getLogger().fine("Found spawners via method " + method.getName() + "(): " + spawners.size());
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // Skip this method
                            }
                        }
                    }
                }
            }
            
            if (spawners == null || spawners.isEmpty()) {
                getLogger().warning("No MythicMobs spawners found globally - spawner collection is " + (spawners == null ? "null" : "empty"));
                getLogger().warning("Spawner manager class: " + spawnerManager.getClass().getName());
                // Log available methods for debugging
                String spawnerMethods = java.util.Arrays.stream(spawnerManager.getClass().getMethods())
                    .map(Method::getName)
                    .filter(name -> name.toLowerCase().contains("spawner"))
                    .collect(java.util.stream.Collectors.joining(", "));
                if (debugSpawnerLevels) {
                    getLogger().info("Available spawner-related methods: " + spawnerMethods);
                }
                
                // Try alternative approach: get spawners by checking blocks in the area
                if (debugSpawnerLevels) {
                    getLogger().info("Trying alternative approach: checking blocks in regeneration area for MythicMobs spawners...");
                }
                try {
                    // Try getSpawnerAt(Location) method
                    Method getSpawnerAtMethod = null;
                    try {
                        getSpawnerAtMethod = spawnerManager.getClass().getMethod("getSpawnerAt", Location.class);
                    } catch (NoSuchMethodException e) {
                        // Try with Block parameter
                        try {
                            getSpawnerAtMethod = spawnerManager.getClass().getMethod("getSpawnerAt", Block.class);
                        } catch (NoSuchMethodException e2) {
                            if (debugSpawnerLevels) {
                                getLogger().info("getSpawnerAt() method not found");
                            }
                        }
                    }
                    
                    if (getSpawnerAtMethod != null) {
                        java.util.List<Object> foundSpawners = new java.util.ArrayList<>();
                        if (debugSpawnerLevels) {
                            getLogger().info("Checking all spawner blocks in regeneration area for MythicMobs spawners...");
                        }
                        int checkedBlocks = 0;
                        int spawnerBlocks = 0;
                        // Check all blocks in the regeneration area
                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    checkedBlocks++;
                                    Location checkLoc = new Location(world, x, y, z);
                                    Block block = checkLoc.getBlock();
                                    if (block.getType() == Material.SPAWNER) {
                                        spawnerBlocks++;
                                        try {
                                            Object spawner = getSpawnerAtMethod.getParameterCount() == 1 && 
                                                             getSpawnerAtMethod.getParameterTypes()[0] == Location.class
                                                             ? getSpawnerAtMethod.invoke(spawnerManager, checkLoc)
                                                             : getSpawnerAtMethod.invoke(spawnerManager, block);
                                            if (spawner != null && !foundSpawners.contains(spawner)) {
                                                foundSpawners.add(spawner);
                                                if (debugSpawnerLevels) {
                                                    getLogger().info("Found MythicMobs spawner at " + checkLoc);
                                                }
                                            }
                                        } catch (Exception e) {
                                            // Not a MythicMobs spawner or error
                                        }
                                    }
                                }
                            }
                        }
                        if (debugSpawnerLevels) {
                            getLogger().info("Checked " + checkedBlocks + " blocks, found " + spawnerBlocks + " spawner blocks, " + foundSpawners.size() + " MythicMobs spawners");
                        }
                        if (!foundSpawners.isEmpty()) {
                            spawners = foundSpawners;
                            if (debugSpawnerLevels) {
                                getLogger().info("Found " + spawners.size() + " MythicMobs spawner(s) by checking blocks in area");
                            }
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Error trying alternative spawner detection: " + e.getMessage());
                }
                
                if (spawners == null || spawners.isEmpty()) {
                    return; // No spawners found
                }
            }
            
            if (debugSpawnerLevels) {
                getLogger().info("Found " + spawners.size() + " total MythicMobs spawner(s) to check (async filtering)");
            }
            
            // Collect spawners that need resetting (async filtering)
            java.util.List<Object> spawnersToReset = new java.util.ArrayList<>();
            int checkedCount = 0;
            int noLocationCount = 0;
            int wrongWorldCount = 0;
            
            // Iterate through all spawners and find those in the regeneration area (async)
            // OPTIMIZATION: Check world first before expensive location conversion
            for (Object spawner : spawners) {
                try {
                    // Step 1: Get AbstractLocation and check world FIRST (cheap check)
                    Object abstractLoc = null;
                    String spawnerWorldName = null;
                    World spawnerWorldObj = null;
                    
                    try {
                        Method getLocationMethod = spawner.getClass().getMethod("getLocation");
                        abstractLoc = getLocationMethod.invoke(spawner);
                        if (abstractLoc != null) {
                            // Extract world from AbstractLocation first (cheap check)
                            try {
                                Method getWorld = abstractLoc.getClass().getMethod("getWorld");
                                Object worldObj = getWorld.invoke(abstractLoc);
                                
                                if (worldObj instanceof World) {
                                    spawnerWorldObj = (World) worldObj;
                                    spawnerWorldName = spawnerWorldObj.getName();
                                } else if (worldObj instanceof String) {
                                    spawnerWorldName = (String) worldObj;
                                    spawnerWorldObj = Bukkit.getWorld(spawnerWorldName);
                                }
                            } catch (Exception e) {
                                // Can't get world from AbstractLocation, will try other methods
                            }
                        }
                    } catch (Exception e) {
                        // getLocation() failed, will try other methods below
                    }
                    
                    // Step 2: Skip spawners in wrong world immediately (FAST FILTER)
                    if (spawnerWorldName != null && !spawnerWorldName.equals(worldName)) {
                        wrongWorldCount++;
                        continue; // Skip this spawner - wrong world
                    }
                    
                    // Step 3: Only convert to full Location if world matches (or couldn't determine world)
                    Location spawnerLoc = null;
                    
                    if (abstractLoc != null && (spawnerWorldName == null || spawnerWorldName.equals(worldName))) {
                        // World matches (or unknown) - now get full location
                        // Try BukkitAdapter.adapt() first (fastest)
                        try {
                            Class<?> bukkitAdapterClass = Class.forName("io.lumine.mythic.bukkit.utils.BukkitAdapter");
                            Method adaptMethod = bukkitAdapterClass.getMethod("adapt", abstractLoc.getClass());
                            spawnerLoc = (Location) adaptMethod.invoke(null, abstractLoc);
                            if (spawnerLoc != null && debugSpawnerLevels) {
                                getLogger().fine("Got location via BukkitAdapter.adapt()");
                            }
                        } catch (Exception e) {
                            // BukkitAdapter failed, extract coordinates directly
                        }
                        
                        // If adapt failed, extract coordinates from AbstractLocation
                        if (spawnerLoc == null) {
                            try {
                                Method getX = abstractLoc.getClass().getMethod("getX");
                                Method getY = abstractLoc.getClass().getMethod("getY");
                                Method getZ = abstractLoc.getClass().getMethod("getZ");
                                double x = ((Number) getX.invoke(abstractLoc)).doubleValue();
                                double y = ((Number) getY.invoke(abstractLoc)).doubleValue();
                                double z = ((Number) getZ.invoke(abstractLoc)).doubleValue();
                                
                                // Use world we already got, or fallback
                                World locWorld = spawnerWorldObj != null ? spawnerWorldObj : world;
                                spawnerLoc = new Location(locWorld, x, y, z);
                                if (debugSpawnerLevels) {
                                    getLogger().fine("Got location from AbstractLocation coordinates");
                                }
                            } catch (Exception e) {
                                if (debugSpawnerLevels) {
                                    getLogger().fine("Could not extract coordinates from AbstractLocation: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // Fallback methods if AbstractLocation approach didn't work
                    if (spawnerLoc == null) {
                        // Try getBlockLocation() if getLocation() didn't work
                        try {
                            Method getBlockLocationMethod = spawner.getClass().getMethod("getBlockLocation");
                            Location tempLoc = (Location) getBlockLocationMethod.invoke(spawner);
                            if (tempLoc != null) {
                                // Check world before using
                                if (tempLoc.getWorld() != null && tempLoc.getWorld().getName().equals(worldName)) {
                                    spawnerLoc = tempLoc;
                                    if (debugSpawnerLevels) {
                                        getLogger().fine("Got location via getBlockLocation()");
                                    }
                                } else {
                                    wrongWorldCount++;
                                    continue; // Wrong world
                                }
                            }
                        } catch (Exception e) {
                            // Try getBlock() and get location from block
                            try {
                                Method getBlockMethod = spawner.getClass().getMethod("getBlock");
                                Block block = (Block) getBlockMethod.invoke(spawner);
                                if (block != null) {
                                    Location tempLoc = block.getLocation();
                                    // Check world before using
                                    if (tempLoc.getWorld() != null && tempLoc.getWorld().getName().equals(worldName)) {
                                        spawnerLoc = tempLoc;
                                        if (debugSpawnerLevels) {
                                            getLogger().fine("Got location via getBlock().getLocation()");
                                        }
                                    } else {
                                        wrongWorldCount++;
                                        continue; // Wrong world
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    
                    // Final check - if we still don't have a location, skip
                    if (spawnerLoc == null) {
                        noLocationCount++;
                        if (noLocationCount <= 3) { // Log first 3 failures
                            getLogger().info("Could not get location for spawner: " + spawner.getClass().getName());
                        }
                        continue;
                    }
                    
                    // Verify world one more time (safety check)
                    World spawnerWorld = spawnerLoc.getWorld();
                    if (spawnerWorld == null || !spawnerWorld.getName().equals(worldName)) {
                        wrongWorldCount++;
                        continue; // Wrong world
                    }
                    
                    // Check if spawner is within regeneration area bounds
                    // Use block coordinates for precise comparison (avoid floating point issues)
                    int x = spawnerLoc.getBlockX();
                    int y = spawnerLoc.getBlockY();
                    int z = spawnerLoc.getBlockZ();
                    
                    checkedCount++;
                    
                    // Manual coordinate check to avoid precision errors
                    boolean inBounds = (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ);
                    
                    // Log first few spawners checked for debugging (only if in correct world)
                    if (debugSpawnerLevels && checkedCount <= 3) {
                        getLogger().fine("Checking spawner at " + spawnerLoc + " (X:" + x + " Y:" + y + " Z:" + z + 
                            ") - Bounds: X[" + minX + "-" + maxX + "] Y[" + minY + "-" + maxY + "] Z[" + minZ + "-" + maxZ + "] - In bounds: " + inBounds);
                    }
                    
                    if (inBounds) {
                        // Spawner is in the regeneration area - add to list for resetting
                        if (debugSpawnerLevels) {
                            getLogger().info("Found spawner in regeneration area at " + spawnerLoc + " (X:" + x + " Y:" + y + " Z:" + z + ")");
                        }
                        spawnersToReset.add(spawner);
                        
                    }
                } catch (Exception e) {
                    // Skip this spawner if we can't process it
                    getLogger().fine("Error processing MythicMobs spawner: " + e.getMessage());
                }
            }
            
            // Reset spawners on main thread (MythicMobs API needs main thread)
            final java.util.List<Object> finalSpawnersToReset = new java.util.ArrayList<>(spawnersToReset);
            final int finalCheckedCount = checkedCount;
            final int finalNoLocationCount = noLocationCount;
            final int finalWrongWorldCount = wrongWorldCount;
            
            if (!finalSpawnersToReset.isEmpty()) {
                Bukkit.getScheduler().runTask(this, () -> {
                    int resetCount = 0;
                    for (Object spawner : finalSpawnersToReset) {
                        try {
                            boolean reset = false;
                            
                            // Try resetTimer() method (singular - most common in MythicMobs)
                            try {
                                Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                                resetTimerMethod.invoke(spawner);
                                reset = true;
                                if (debugSpawnerLevels) {
                                    getLogger().info("Reset spawner using resetTimer()");
                                }
                            } catch (NoSuchMethodException e) {
                                // Try resetTimers() (plural)
                                try {
                                    Method resetTimersMethod = spawner.getClass().getMethod("resetTimers");
                                    resetTimersMethod.invoke(spawner);
                                    reset = true;
                                    if (debugSpawnerLevels) {
                                        getLogger().info("Reset spawner using resetTimers()");
                                    }
                                } catch (Exception ignored) {
                                }
                            } catch (Exception e) {
                                if (debugSpawnerLevels) {
                                    getLogger().fine("Error calling resetTimer(): " + e.getMessage());
                                }
                            }
                            
                            if (reset) {
                                resetCount++;
                            }
                        } catch (Exception e) {
                            getLogger().fine("Error resetting spawner: " + e.getMessage());
                        }
                    }
                    
                    if (debugSpawnerLevels) {
                        getLogger().info("Checked " + finalCheckedCount + " MythicMobs spawner(s), reset cooldowns for " + resetCount + " spawner(s) in challenge area '" + cfg.id + "'");
                    }
                    if (finalNoLocationCount > 0) {
                        getLogger().info("Could not get location for " + finalNoLocationCount + " spawner(s)");
                    }
                    if (finalWrongWorldCount > 0) {
                        getLogger().info(finalWrongWorldCount + " spawner(s) were in wrong world");
                    }
                    if (resetCount == 0 && finalCheckedCount > 0) {
                        getLogger().warning("No spawners were reset in challenge area '" + cfg.id + "' - check if spawners are within regeneration bounds");
                    }
                });
            } else {
                getLogger().info("Checked " + finalCheckedCount + " MythicMobs spawner(s), no spawners found in regeneration area '" + cfg.id + "'");
                if (finalNoLocationCount > 0) {
                    getLogger().info("Could not get location for " + finalNoLocationCount + " spawner(s)");
                }
                if (finalWrongWorldCount > 0) {
                    getLogger().info(finalWrongWorldCount + " spawner(s) were in wrong world");
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error finding/resetting MythicMobs spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sets spawner levels based on difficulty tier for all spawners in a challenge's regeneration area.
     * Stores original levels so they can be reverted later.
     * 
     * @param cfg The challenge configuration
     * @param tier The difficulty tier ("easy", "medium", "hard", "extreme")
     */
    private void setSpawnerLevelsForChallenge(ChallengeConfig cfg, String tier) {
        // Get the level modifier for this tier
        Integer levelModifier = difficultyTierModifiers.get(tier);
        if (levelModifier == null) {
            levelModifier = 0; // Default to easy
        }
        
        if (levelModifier == 0) {
            getLogger().fine("Tier '" + tier + "' has no level modifier, skipping spawner level update");
            return; // Easy tier, no change needed
        }
        
        getLogger().info("Setting spawner levels for challenge '" + cfg.id + "' to tier '" + tier + "' (+" + levelModifier + " levels)");
        
        // Check if MythicMobs is installed
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().warning("MythicMobs not installed, cannot set spawner levels");
            return; // MythicMobs not installed
        }
        
        // Check if regeneration area is set
        if (cfg.regenerationCorner1 == null || cfg.regenerationCorner2 == null) {
            getLogger().warning("No regeneration area set for challenge '" + cfg.id + "', cannot set spawner levels");
            return; // No regeneration area set
        }
        
        World world = cfg.regenerationCorner1.getWorld();
        if (world == null) {
            getLogger().warning("World not loaded for challenge '" + cfg.id + "', cannot set spawner levels");
            return; // World not loaded
        }
        
        if (debugSpawnerLevels) {
            getLogger().info("World: " + world.getName() + ", Regeneration area: " + cfg.regenerationCorner1 + " to " + cfg.regenerationCorner2);
        }
        
        // Calculate regeneration area bounds
        int minX = Math.min(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int maxX = Math.max(cfg.regenerationCorner1.getBlockX(), cfg.regenerationCorner2.getBlockX());
        int minY, maxY;
        if (cfg.regenerationAutoExtendY) {
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1;
        } else {
            minY = Math.min(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
            maxY = Math.max(cfg.regenerationCorner1.getBlockY(), cfg.regenerationCorner2.getBlockY());
        }
        int minZ = Math.min(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        int maxZ = Math.max(cfg.regenerationCorner1.getBlockZ(), cfg.regenerationCorner2.getBlockZ());
        
        String worldName = world.getName();
        
        // Initialize storage for original levels if needed
        originalSpawnerLevels.putIfAbsent(cfg.id, new HashMap<>());
        Map<Object, Integer> originalLevels = originalSpawnerLevels.get(cfg.id);
        
        try {
            // Get MythicMobs API using reflection
            Class<?> mythicAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicPlugin = mythicAPI.getMethod("inst").invoke(null);
            Object spawnerManager = mythicAPI.getMethod("getSpawnerManager").invoke(mythicPlugin);
            if (spawnerManager == null) {
                return;
            }
            
            // Get all spawners
            Method getSpawnersMethod = spawnerManager.getClass().getMethod("getSpawners");
            Object result = getSpawnersMethod.invoke(spawnerManager);
            java.util.Collection<?> spawners = null;
            if (result instanceof java.util.Map) {
                spawners = ((java.util.Map<?, ?>) result).values();
            } else if (result instanceof java.util.Collection) {
                spawners = (java.util.Collection<?>) result;
            }
            
            if (spawners == null || spawners.isEmpty()) {
                getLogger().warning("No spawners found from SpawnerManager");
                return;
            }
            
            if (debugSpawnerLevels) {
                getLogger().info("Found " + spawners.size() + " total spawners to check for level update");
                getLogger().info("Regeneration area bounds: X[" + minX + "-" + maxX + "] Y[" + minY + "-" + maxY + "] Z[" + minZ + "-" + maxZ + "] in world '" + worldName + "'");
            }
            int updatedCount = 0;
            int checkedCount = 0;
            
            // Find spawners in the regeneration area and set their levels
            // Use the same location retrieval logic as resetMythicMobsSpawnersInArea
            for (Object spawner : spawners) {
                try {
                    // Step 1: Get AbstractLocation and check world FIRST (cheap check) - same as reset method
                    Object abstractLoc = null;
                    String spawnerWorldName = null;
                    World spawnerWorldObj = null;
                    
                    try {
                        Method getLocationMethod = spawner.getClass().getMethod("getLocation");
                        abstractLoc = getLocationMethod.invoke(spawner);
                        if (abstractLoc != null) {
                            // Extract world from AbstractLocation first (cheap check)
                            try {
                                Method getWorld = abstractLoc.getClass().getMethod("getWorld");
                                Object worldObj = getWorld.invoke(abstractLoc);
                                
                                if (worldObj instanceof World) {
                                    spawnerWorldObj = (World) worldObj;
                                    spawnerWorldName = spawnerWorldObj.getName();
                                } else if (worldObj instanceof String) {
                                    spawnerWorldName = (String) worldObj;
                                    spawnerWorldObj = Bukkit.getWorld(spawnerWorldName);
                                }
                            } catch (Exception e) {
                                // Can't get world from AbstractLocation, will try other methods
                            }
                        }
                    } catch (Exception e) {
                        // getLocation() failed, will try other methods below
                    }
                    
                    // Step 2: Skip spawners in wrong world immediately (FAST FILTER)
                    if (spawnerWorldName != null && !spawnerWorldName.equals(worldName)) {
                        continue; // Skip this spawner - wrong world
                    }
                    
                    // Step 3: Only convert to full Location if world matches (or couldn't determine world)
                    Location spawnerLoc = null;
                    
                    if (abstractLoc != null && (spawnerWorldName == null || spawnerWorldName.equals(worldName))) {
                        // World matches (or unknown) - now get full location
                        // Try BukkitAdapter.adapt() first (fastest)
                        try {
                            Class<?> bukkitAdapterClass = Class.forName("io.lumine.mythic.bukkit.utils.BukkitAdapter");
                            Method adaptMethod = bukkitAdapterClass.getMethod("adapt", abstractLoc.getClass());
                            spawnerLoc = (Location) adaptMethod.invoke(null, abstractLoc);
                            if (spawnerLoc != null) {
                                getLogger().fine("Got location via BukkitAdapter.adapt()");
                            }
                        } catch (Exception e) {
                            // BukkitAdapter failed, extract coordinates directly
                        }
                        
                        // If adapt failed, extract coordinates from AbstractLocation
                        if (spawnerLoc == null) {
                            try {
                                Method getX = abstractLoc.getClass().getMethod("getX");
                                Method getY = abstractLoc.getClass().getMethod("getY");
                                Method getZ = abstractLoc.getClass().getMethod("getZ");
                                double x = ((Number) getX.invoke(abstractLoc)).doubleValue();
                                double y = ((Number) getY.invoke(abstractLoc)).doubleValue();
                                double z = ((Number) getZ.invoke(abstractLoc)).doubleValue();
                                
                                // Use world we already got, or fallback
                                World locWorld = spawnerWorldObj != null ? spawnerWorldObj : world;
                                spawnerLoc = new Location(locWorld, x, y, z);
                                getLogger().fine("Got location from AbstractLocation coordinates");
                            } catch (Exception e) {
                                getLogger().fine("Could not extract coordinates from AbstractLocation: " + e.getMessage());
                            }
                        }
                    }
                    
                    // Fallback methods if AbstractLocation approach didn't work
                    if (spawnerLoc == null) {
                        // Try getBlockLocation() if getLocation() didn't work
                        try {
                            Method getBlockLocationMethod = spawner.getClass().getMethod("getBlockLocation");
                            Location tempLoc = (Location) getBlockLocationMethod.invoke(spawner);
                            if (tempLoc != null) {
                                // Check world before using
                                if (tempLoc.getWorld() != null && tempLoc.getWorld().getName().equals(worldName)) {
                                    spawnerLoc = tempLoc;
                                    if (debugSpawnerLevels) {
                                        getLogger().fine("Got location via getBlockLocation()");
                                    }
                                } else {
                                    continue; // Wrong world
                                }
                            }
                        } catch (Exception e) {
                            // Try getBlock() and get location from block
                            try {
                                Method getBlockMethod = spawner.getClass().getMethod("getBlock");
                                Block block = (Block) getBlockMethod.invoke(spawner);
                                if (block != null) {
                                    Location tempLoc = block.getLocation();
                                    // Check world before using
                                    if (tempLoc.getWorld() != null && tempLoc.getWorld().getName().equals(worldName)) {
                                        spawnerLoc = tempLoc;
                                        if (debugSpawnerLevels) {
                                            getLogger().fine("Got location via getBlock().getLocation()");
                                        }
                                    } else {
                                        continue; // Wrong world
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    
                    // Final check - if we still don't have a location, skip
                    if (spawnerLoc == null) {
                        continue;
                    }
                    
                    // Verify world one more time (safety check)
                    World spawnerWorld = spawnerLoc.getWorld();
                    if (spawnerWorld == null || !spawnerWorld.getName().equals(worldName)) {
                        continue; // Wrong world
                    }
                    
                    // Check if spawner is in regeneration area
                    int x = spawnerLoc.getBlockX();
                    int y = spawnerLoc.getBlockY();
                    int z = spawnerLoc.getBlockZ();
                    
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                        // Get original level and store it (if not already stored)
                        if (!originalLevels.containsKey(spawner)) {
                            Integer originalLevel = null;
                            
                            // Get spawner name first for better logging
                            String spawnerName = null;
                            try {
                                Method getNameMethod = spawner.getClass().getMethod("getName");
                                Object nameObj = getNameMethod.invoke(spawner);
                                if (nameObj != null) {
                                    spawnerName = nameObj.toString();
                                }
                            } catch (Exception ignored) {
                            }
                            
                            // Try multiple methods to get the original level
                            try {
                                Method getMobLevelMethod = spawner.getClass().getMethod("getMobLevel");
                                Object levelObj = getMobLevelMethod.invoke(spawner);
                                if (levelObj instanceof Number) {
                                    originalLevel = ((Number) levelObj).intValue();
                                    if (debugSpawnerLevels) {
                                        getLogger().info("Got original level " + originalLevel + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc + " via getMobLevel()");
                                    }
                                } else if (levelObj != null) {
                                    try {
                                        originalLevel = Integer.parseInt(levelObj.toString());
                                        if (debugSpawnerLevels) {
                                            getLogger().info("Got original level " + originalLevel + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc + " via getMobLevel() (parsed)");
                                        }
                                    } catch (NumberFormatException e) {
                                        if (debugSpawnerLevels) {
                                            getLogger().warning("Could not parse mob level as integer: " + levelObj + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "'");
                                        }
                                    }
                                } else {
                                    if (debugSpawnerLevels) {
                                        getLogger().warning("getMobLevel() returned null for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc);
                                    }
                                }
                            } catch (NoSuchMethodException e) {
                                // Try getLevel() as alternative
                                try {
                                    Method getLevelMethod = spawner.getClass().getMethod("getLevel");
                                    Object levelObj = getLevelMethod.invoke(spawner);
                                    if (levelObj instanceof Number) {
                                        originalLevel = ((Number) levelObj).intValue();
                                        if (debugSpawnerLevels) {
                                            getLogger().info("Got original level " + originalLevel + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc + " via getLevel()");
                                        }
                                    }
                                } catch (Exception e2) {
                                    if (debugSpawnerLevels) {
                                        getLogger().fine("Could not get level via getLevel(): " + e2.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                if (debugSpawnerLevels) {
                                    getLogger().warning("Error getting original level via getMobLevel(): " + e.getMessage() + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "'");
                                }
                            }
                            
                            // If we still don't have a level, default to 1 but warn
                            if (originalLevel == null) {
                                if (debugSpawnerLevels) {
                                    getLogger().warning("Could not get original level for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc + " via API, defaulting to 1. Check spawner config - spawner may already be at level 1.");
                                }
                                originalLevel = 1; // Default fallback
                            }
                            
                            originalLevels.put(spawner, originalLevel);
                            if (debugSpawnerLevels) {
                                getLogger().info("Stored original level " + originalLevel + " for spawner '" + (spawnerName != null ? spawnerName : "unknown") + "' at " + spawnerLoc);
                            }
                        }
                        
                        // Get original level and add modifier
                        Integer originalLevel = originalLevels.get(spawner);
                        int newLevel = originalLevel + levelModifier;
                        
                        if (debugSpawnerLevels) {
                            getLogger().info("Setting spawner at " + spawnerLoc + " from level " + originalLevel + " to " + newLevel + " (tier: " + tier + ", modifier: +" + levelModifier + ")");
                        }
                        
                        // Set the new level - MythicMobs 5.x+ uses setMobLevel(double) and needs resetTimer()
                        boolean levelSet = false;
                        try {
                            // Try setMobLevel(double) first (correct for MythicMobs 5.x+)
                            Method setMobLevelMethod = spawner.getClass().getMethod("setMobLevel", double.class);
                            setMobLevelMethod.invoke(spawner, (double) newLevel);
                            
                            // Immediately reset timer to force the change (critical!)
                            try {
                                Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                                resetTimerMethod.invoke(spawner);
                            } catch (Exception e) {
                                // Try resetTimers() as fallback
                                try {
                                    Method resetTimersMethod = spawner.getClass().getMethod("resetTimers");
                                    resetTimersMethod.invoke(spawner);
                                } catch (Exception ignored) {
                                }
                            }
                            
                            levelSet = true;
                            if (debugSpawnerLevels) {
                                getLogger().info("Set spawner level at " + spawnerLoc + " from " + originalLevel + " to " + newLevel + " (tier: " + tier + ") using setMobLevel(double)");
                            }
                        } catch (NoSuchMethodException e) {
                            // Try setMobLevel(int) as fallback
                            try {
                                Method setMobLevelMethod = spawner.getClass().getMethod("setMobLevel", int.class);
                                setMobLevelMethod.invoke(spawner, newLevel);
                                
                                // Reset timer
                                try {
                                    Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                                    resetTimerMethod.invoke(spawner);
                                } catch (Exception ignored) {
                                }
                                
                                levelSet = true;
                                if (debugSpawnerLevels) {
                                    getLogger().info("Set spawner level at " + spawnerLoc + " from " + originalLevel + " to " + newLevel + " (tier: " + tier + ") using setMobLevel(int)");
                                }
                            } catch (NoSuchMethodException e2) {
                                // Try accessing internal config as last resort
                                try {
                                    Method getInternalConfigMethod = spawner.getClass().getMethod("getInternalConfig");
                                    Object config = getInternalConfigMethod.invoke(spawner);
                                    if (config != null) {
                                        Method setMethod = config.getClass().getMethod("set", String.class, Object.class);
                                        setMethod.invoke(config, "MobLevel", (double) newLevel);
                                        
                                        // Reset timer
                                        try {
                                            Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                                            resetTimerMethod.invoke(spawner);
                                        } catch (Exception ignored) {
                                        }
                                        
                                        levelSet = true;
                                        if (debugSpawnerLevels) {
                                            getLogger().info("Set spawner level at " + spawnerLoc + " from " + originalLevel + " to " + newLevel + " (tier: " + tier + ") using internal config");
                                        }
                                    }
                                } catch (Exception e3) {
                                    // Final fallback: use console command
                                    try {
                                        // Get spawner name for command
                                        String spawnerName = null;
                                        try {
                                            Method getNameMethod = spawner.getClass().getMethod("getName");
                                            Object nameObj = getNameMethod.invoke(spawner);
                                            if (nameObj != null) {
                                                spawnerName = nameObj.toString();
                                            }
                                        } catch (Exception ignored) {
                                        }
                                        
                                        if (spawnerName != null && !spawnerName.isEmpty()) {
                                            final String finalSpawnerName = spawnerName;
                                            Bukkit.getScheduler().runTask(ConradChallengesPlugin.this, () -> {
                                                try {
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm s set " + finalSpawnerName + " moblevel " + newLevel);
                                                    if (debugSpawnerLevels) {
                                                        getLogger().info("Set spawner level via command for " + finalSpawnerName + " to " + newLevel);
                                                    }
                                                } catch (Exception cmdEx) {
                                                    getLogger().warning("Failed to set spawner level via command: " + cmdEx.getMessage());
                                                }
                                            });
                                            levelSet = true;
                                        } else {
                                            getLogger().warning("Could not set spawner level - all methods failed and no spawner name available");
                                        }
                                    } catch (Exception e4) {
                                        getLogger().warning("Could not set spawner level - all methods failed: " + e4.getMessage());
                                    }
                                }
                            } catch (Exception e2) {
                                getLogger().warning("Error calling setMobLevel(int): " + e2.getMessage());
                            }
                        } catch (Exception e) {
                            getLogger().warning("Error calling setMobLevel(double): " + e.getMessage());
                        }
                        
                        // Try to save/update the spawner after setting level
                        if (levelSet) {
                            try {
                                // Try save() method
                                try {
                                    Method saveMethod = spawner.getClass().getMethod("save");
                                    saveMethod.invoke(spawner);
                                    getLogger().fine("Saved spawner after level update");
                                } catch (NoSuchMethodException e) {
                                    // Try update() method
                                    try {
                                        Method updateMethod = spawner.getClass().getMethod("update");
                                        updateMethod.invoke(spawner);
                                        getLogger().fine("Updated spawner after level update");
                                    } catch (Exception e2) {
                                        // Try reload() method
                                        try {
                                            Method reloadMethod = spawner.getClass().getMethod("reload");
                                            reloadMethod.invoke(spawner);
                                            getLogger().fine("Reloaded spawner after level update");
                                        } catch (Exception e3) {
                                            // No save/update method available, that's okay
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                getLogger().fine("Could not save/update spawner: " + e.getMessage());
                            }
                            
                            updatedCount++;
                        }
                    }
                } catch (Exception e) {
                    getLogger().fine("Error processing spawner for level update: " + e.getMessage());
                }
            }
            
            if (updatedCount > 0) {
                getLogger().info("Updated " + updatedCount + " spawner level(s) for challenge '" + cfg.id + "' to tier '" + tier + "'");
            } else {
                getLogger().warning("No spawners were updated! Checked " + checkedCount + " spawner(s) in regeneration area for challenge '" + cfg.id + "'");
            }
        } catch (Exception e) {
            getLogger().warning("Error setting spawner levels: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reverts spawner levels back to their original values for a challenge.
     * 
     * @param cfg The challenge configuration
     */
    private void revertSpawnerLevelsForChallenge(ChallengeConfig cfg) {
        Map<Object, Integer> originalLevels = originalSpawnerLevels.get(cfg.id);
        if (originalLevels == null || originalLevels.isEmpty()) {
            getLogger().fine("No spawner levels to revert for challenge '" + cfg.id + "'");
            return; // No levels were changed
        }
        
        if (debugSpawnerLevels) {
            getLogger().info("Reverting spawner levels for challenge '" + cfg.id + "'");
        }
        
        int revertedCount = 0;
        
        for (Map.Entry<Object, Integer> entry : originalLevels.entrySet()) {
            Object spawner = entry.getKey();
            Integer originalLevel = entry.getValue();
            
            try {
                boolean reverted = false;
                
                // Try setMobLevel(double) first (MythicMobs 5.x+)
                try {
                    Method setMobLevelMethod = spawner.getClass().getMethod("setMobLevel", double.class);
                    setMobLevelMethod.invoke(spawner, originalLevel.doubleValue());
                    
                    // Reset timer to force the change
                    try {
                        Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                        resetTimerMethod.invoke(spawner);
                    } catch (Exception ignored) {
                    }
                    
                                    reverted = true;
                                    if (debugSpawnerLevels) {
                                        getLogger().info("Reverted spawner level to " + originalLevel + " using setMobLevel(double)");
                                    }
                } catch (NoSuchMethodException e) {
                    // Try setMobLevel(int) as fallback
                    try {
                        Method setMobLevelMethod = spawner.getClass().getMethod("setMobLevel", int.class);
                        setMobLevelMethod.invoke(spawner, originalLevel);
                        
                        // Reset timer
                        try {
                            Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                            resetTimerMethod.invoke(spawner);
                        } catch (Exception ignored) {
                        }
                        
                        reverted = true;
                        if (debugSpawnerLevels) {
                            getLogger().info("Reverted spawner level to " + originalLevel + " using setMobLevel(int)");
                        }
                    } catch (NoSuchMethodException e2) {
                        // Try internal config
                        try {
                            Method getInternalConfigMethod = spawner.getClass().getMethod("getInternalConfig");
                            Object config = getInternalConfigMethod.invoke(spawner);
                            if (config != null) {
                                Method setMethod = config.getClass().getMethod("set", String.class, Object.class);
                                setMethod.invoke(config, "MobLevel", originalLevel.doubleValue());
                                
                                // Reset timer
                                try {
                                    Method resetTimerMethod = spawner.getClass().getMethod("resetTimer");
                                    resetTimerMethod.invoke(spawner);
                                } catch (Exception ignored) {
                                }
                                
                                reverted = true;
                                if (debugSpawnerLevels) {
                                    getLogger().info("Reverted spawner level to " + originalLevel + " using internal config");
                                }
                            }
                        } catch (Exception e3) {
                            // Final fallback: use console command
                            try {
                                // Get spawner name for command
                                String spawnerName = null;
                                try {
                                    Method getNameMethod = spawner.getClass().getMethod("getName");
                                    Object nameObj = getNameMethod.invoke(spawner);
                                    if (nameObj != null) {
                                        spawnerName = nameObj.toString();
                                    }
                                } catch (Exception ignored) {
                                }
                                
                                if (spawnerName != null && !spawnerName.isEmpty()) {
                                    final String finalSpawnerName = spawnerName;
                                    final int finalOriginalLevel = originalLevel;
                                    Bukkit.getScheduler().runTask(ConradChallengesPlugin.this, () -> {
                                        try {
                                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm s set " + finalSpawnerName + " moblevel " + finalOriginalLevel);
                                            if (debugSpawnerLevels) {
                                                getLogger().info("Reverted spawner level via command for " + finalSpawnerName + " to " + finalOriginalLevel);
                                            }
                                        } catch (Exception cmdEx) {
                                            getLogger().warning("Failed to revert spawner level via command: " + cmdEx.getMessage());
                                        }
                                    });
                                    reverted = true;
                                } else {
                                    getLogger().warning("Could not revert spawner level - all methods failed and no spawner name available");
                                }
                            } catch (Exception e4) {
                                getLogger().warning("Could not revert spawner level - all methods failed: " + e4.getMessage());
                            }
                        }
                    } catch (Exception e2) {
                        getLogger().warning("Error calling setMobLevel(int): " + e2.getMessage());
                    }
                } catch (Exception e) {
                    getLogger().warning("Error calling setMobLevel(double): " + e.getMessage());
                }
                
                if (reverted) {
                    revertedCount++;
                }
            } catch (Exception e) {
                getLogger().fine("Error processing spawner for level revert: " + e.getMessage());
            }
        }
        
        // Clean up stored levels
        originalSpawnerLevels.remove(cfg.id);
        
        if (revertedCount > 0) {
            if (debugSpawnerLevels) {
                getLogger().info("Reverted " + revertedCount + " spawner level(s) for challenge '" + cfg.id + "'");
            }
        } else {
            getLogger().warning("No spawner levels were reverted for challenge '" + cfg.id + "' - check logs for errors");
        }
    }
    
    /**
     * Removes one editor from a challenge's editor set. If they were the last editor,
     * removes the challenge from challengeEditors and clears backups. Does not teleport.
     */
    private void removeEditorFromChallenge(Player player, String challengeId) {
        removeEditorFromChallenge(player.getUniqueId(), challengeId);
    }
    
    private void removeEditorFromChallenge(UUID editorUuid, String challengeId) {
        Set<UUID> editors = challengeEditors.get(challengeId);
        if (editors == null) return;
        editors.remove(editorUuid);
        if (editors.isEmpty()) {
            challengeEditors.remove(challengeId);
            challengeEditBackups.remove(challengeId);
            challengeEditStateBackups.remove(challengeId);
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
                List<BlockState> stateBackup = challengeEditStateBackups.get(challengeId);
                if (stateBackup != null) {
                    challengeInitialStates.put(challengeId, new ArrayList<>(stateBackup));
                    regenerateChallengeArea(cfg);
                }
            }
        }
        
        Location originalLoc = editorOriginalLocations.remove(uuid);
        removeEditorFromChallenge(uuid, challengeId);
        
        if (originalLoc != null) {
            player.teleport(originalLoc);
        }
        
        if (!isChallengeLocked(challengeId)) {
            processQueue(challengeId);
        }
    }

}

