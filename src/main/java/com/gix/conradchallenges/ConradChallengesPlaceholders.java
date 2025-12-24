package com.gix.conradchallenges;

import org.bukkit.OfflinePlayer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * PlaceholderAPI expansion for ConradChallenges
 * Provides placeholders for challenge data, times, completions, etc.
 * Uses reflection and ASM to avoid compile-time dependency on PlaceholderAPI.
 */
public class ConradChallengesPlaceholders {

    private final ConradChallengesPlugin plugin;
    private Object expansionInstance;
    private static final String EXPANSION_CLASS_NAME = "com.gix.conradchallenges.ConradChallengesExpansion";

    public ConradChallengesPlaceholders(ConradChallengesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers this expansion with PlaceholderAPI using reflection and ASM.
     * Creates a concrete class that extends PlaceholderExpansion dynamically.
     */
    public boolean register() {
        try {
            // Get PlaceholderExpansion class
            Class<?> expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            
            // Create a concrete implementation using ASM
            Class<?> generatedClass = generateExpansionClass(expansionClass);
            
            // Create an instance of the generated class
            Constructor<?> constructor = generatedClass.getConstructor(ConradChallengesPlugin.class);
            expansionInstance = constructor.newInstance(plugin);
            
            // Register the expansion using PlaceholderAPI's method
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            
            // Try multiple registration approaches
            try {
                // Method 1: Static method
                Method registerMethod = papiClass.getMethod("registerExpansion", expansionClass);
                boolean registered = (Boolean) registerMethod.invoke(null, expansionInstance);
                
                if (registered) {
                    plugin.getLogger().info("PlaceholderAPI expansion registered successfully via static method!");
                    return true;
                } else {
                    plugin.getLogger().warning("PlaceholderAPI returned false when registering expansion.");
                }
            } catch (NoSuchMethodException e) {
                plugin.getLogger().fine("Static registerExpansion method not found, trying instance method...");
            }
            
            // Method 2: Instance method
            try {
                Object papiInstance = papiClass.getMethod("getInstance").invoke(null);
                Method registerMethod = papiInstance.getClass().getMethod("registerExpansion", expansionClass);
                boolean registered = (Boolean) registerMethod.invoke(papiInstance, expansionInstance);
                
                if (registered) {
                    plugin.getLogger().info("PlaceholderAPI expansion registered successfully via instance method!");
                    return true;
                } else {
                    plugin.getLogger().warning("PlaceholderAPI returned false when registering expansion via instance method.");
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Instance method registration failed: " + e.getMessage());
            }
            
            // Method 3: Try using ExpansionManager directly
            try {
                Class<?> expansionManagerClass = Class.forName("me.clip.placeholderapi.expansion.ExpansionManager");
                Object expansionManager = papiClass.getMethod("getExpansionManager").invoke(null);
                Method registerMethod = expansionManagerClass.getMethod("register", expansionClass);
                boolean registered = (Boolean) registerMethod.invoke(expansionManager, expansionInstance);
                
                if (registered) {
                    plugin.getLogger().info("PlaceholderAPI expansion registered successfully via ExpansionManager!");
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().fine("ExpansionManager registration failed: " + e.getMessage());
            }
            
            plugin.getLogger().warning("All PlaceholderAPI registration methods failed. Check server logs for details.");
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Generates a concrete class that extends PlaceholderExpansion using ASM.
     */
    private Class<?> generateExpansionClass(Class<?> expansionClass) throws Exception {
        String internalName = EXPANSION_CLASS_NAME.replace('.', '/');
        String superName = expansionClass.getName().replace('.', '/');
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName, null);
        
        // Add plugin field
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "plugin", 
            Type.getDescriptor(ConradChallengesPlugin.class), null, null).visitEnd();
        
        // Constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", 
            "(" + Type.getDescriptor(ConradChallengesPlugin.class) + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, internalName, "plugin", 
            Type.getDescriptor(ConradChallengesPlugin.class));
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
        
        // getIdentifier()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getIdentifier", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn("conradchallenges");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // getAuthor()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getAuthor", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, internalName, "plugin", 
            Type.getDescriptor(ConradChallengesPlugin.class));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/gix/conradchallenges/ConradChallengesPlaceholders", 
            "getAuthorString", "(Lcom/gix/conradchallenges/ConradChallengesPlugin;)Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // getVersion()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getVersion", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, internalName, "plugin", 
            Type.getDescriptor(ConradChallengesPlugin.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/bukkit/plugin/java/JavaPlugin", 
            "getDescription", "()Lorg/bukkit/plugin/PluginDescriptionFile;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/bukkit/plugin/PluginDescriptionFile", 
            "getVersion", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        
        // persist()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "persist", "()Z", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // canRegister()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "canRegister", "()Z", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // onRequest(OfflinePlayer, String)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "onRequest", 
            "(Lorg/bukkit/OfflinePlayer;Ljava/lang/String;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, internalName, "plugin", 
            Type.getDescriptor(ConradChallengesPlugin.class));
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/gix/conradchallenges/ConradChallengesPlaceholders", 
            "handlePlaceholderRequest", 
            "(Lcom/gix/conradchallenges/ConradChallengesPlugin;Lorg/bukkit/OfflinePlayer;Ljava/lang/String;)Ljava/lang/String;", 
            false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
        
        // onPlaceholderRequest(OfflinePlayer, String) - alternative method name
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "onPlaceholderRequest", 
            "(Lorg/bukkit/OfflinePlayer;Ljava/lang/String;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalName, "onRequest", 
            "(Lorg/bukkit/OfflinePlayer;Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
        
        // getPlaceholders() - Returns collection of available placeholders for tab completion
        // Try Collection as the most generic type that PlaceholderAPI might accept
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getPlaceholders", "()Ljava/util/Collection;", 
            "()Ljava/util/Collection<Ljava/lang/String;>;", null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/gix/conradchallenges/ConradChallengesPlaceholders", 
            "getPlaceholdersSet", "()Ljava/util/Set;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        
        // Define the class
        byte[] classBytes = cw.toByteArray();
        ClassLoader classLoader = new ExpansionClassLoader(plugin.getClass().getClassLoader());
        return ((ExpansionClassLoader) classLoader).defineClass(EXPANSION_CLASS_NAME, classBytes);
    }
    
    /**
     * Custom ClassLoader for defining the generated expansion class.
     */
    private static class ExpansionClassLoader extends ClassLoader {
        public ExpansionClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        public Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
    
    /**
     * Static method to handle placeholder requests (called from generated class).
     */
    public static String handlePlaceholderRequest(ConradChallengesPlugin plugin, OfflinePlayer player, String params) {
        return new ConradChallengesPlaceholders(plugin).onPlaceholderRequest(player, params);
    }
    
    /**
     * Static method to get author string (called from generated class).
     */
    public static String getAuthorString(ConradChallengesPlugin plugin) {
        return String.join(", ", plugin.getDescription().getAuthors());
    }
    
    /**
     * Static method to get list of placeholders (called from generated class for tab completion).
     * @deprecated Use getPlaceholdersSet() instead
     */
    @Deprecated
    public static List<String> getPlaceholdersList() {
        return Arrays.asList(
            "active",
            "active_name",
            "besttime_<challengeid>",
            "besttime_formatted_<challengeid>",
            "completed_<challengeid>",
            "cooldown_<challengeid>",
            "cooldown_formatted_<challengeid>",
            "completions_<challengeid>",
            "top_<challengeid>_<position>",
            "top_time_<challengeid>_<position>",
            "top_time_formatted_<challengeid>_<position>",
            "leaderboard_count_<challengeid>",
            "rank_<challengeid>",
            "total_completions",
            "world_alias",
            "challenge_alias_<challengeid>"
        );
    }
    
    /**
     * Static method to get set of placeholders (called from generated class for tab completion).
     * Returns a Set instead of List as PlaceholderAPI may prefer this for tab completion.
     */
    public static Set<String> getPlaceholdersSet() {
        return new HashSet<>(Arrays.asList(
            "active",
            "active_name",
            "besttime_<challengeid>",
            "besttime_formatted_<challengeid>",
            "completed_<challengeid>",
            "cooldown_<challengeid>",
            "cooldown_formatted_<challengeid>",
            "completions_<challengeid>",
            "top_<challengeid>_<position>",
            "top_time_<challengeid>_<position>",
            "top_time_formatted_<challengeid>_<position>",
            "leaderboard_count_<challengeid>",
            "rank_<challengeid>",
            "total_completions",
            "world_alias",
            "challenge_alias_<challengeid>"
        ));
    }

    /**
     * Resolves challenge ID, supporting "active" keyword to use current active challenge.
     */
    private String resolveChallengeId(UUID uuid, String challengeId) {
        if (challengeId != null && challengeId.equalsIgnoreCase("active")) {
            String active = plugin.getActiveChallenge(uuid);
            return active != null ? active : null; // Return null if no active challenge
        }
        return challengeId;
    }
    
    /**
     * Handles placeholder requests.
     */
    private String onPlaceholderRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        String[] args = params.split("_");

        // %conradchallenges_active% - Current active challenge ID (or alias if configured)
        if (params.equalsIgnoreCase("active")) {
            String active = plugin.getActiveChallenge(uuid);
            if (active == null) return "none";
            return plugin.getChallengeAlias(active);
        }

        // %conradchallenges_active_name% - Current active challenge name
        if (params.equalsIgnoreCase("active_name")) {
            String active = plugin.getActiveChallenge(uuid);
            if (active == null) return "none";
            return plugin.getChallengeDisplayName(active);
        }

        // %conradchallenges_besttime_<challengeid>% - Best time for player in challenge (in seconds)
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("besttime")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "N/A";
            Long bestTime = plugin.getBestTime(uuid, challengeId);
            if (bestTime == null) return "N/A";
            return String.valueOf(bestTime);
        }

        // %conradchallenges_besttime_formatted_<challengeid>% - Best time formatted (e.g., "1m 30s")
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 3 && args[0].equalsIgnoreCase("besttime") && args[1].equalsIgnoreCase("formatted")) {
            String challengeId = resolveChallengeId(uuid, args[2]);
            if (challengeId == null) return "N/A";
            Long bestTime = plugin.getBestTime(uuid, challengeId);
            if (bestTime == null) return "N/A";
            return formatTime(bestTime);
        }

        // %conradchallenges_completed_<challengeid>% - Whether player has completed challenge (true/false)
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("completed")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "false";
            return plugin.hasCompletedChallenge(uuid, challengeId) ? "true" : "false";
        }

        // %conradchallenges_cooldown_<challengeid>% - Cooldown remaining in seconds
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("cooldown")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "0";
            long remaining = plugin.getCooldownRemaining(uuid, challengeId);
            return remaining > 0 ? String.valueOf(remaining) : "0";
        }

        // %conradchallenges_cooldown_formatted_<challengeid>% - Cooldown formatted (e.g., "5m 30s")
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 3 && args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("formatted")) {
            String challengeId = resolveChallengeId(uuid, args[2]);
            if (challengeId == null) return "Ready";
            long remaining = plugin.getCooldownRemaining(uuid, challengeId);
            if (remaining <= 0) return "Ready";
            return formatTime(remaining);
        }

        // %conradchallenges_completions_<challengeid>% - Number of times player completed challenge
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("completions")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "0";
            return String.valueOf(plugin.getCompletionCount(uuid, challengeId));
        }

        // %conradchallenges_top_<challengeid>_<position>% - Player name at position in leaderboard
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 3 && args[0].equalsIgnoreCase("top")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "N/A";
            try {
                int position = Integer.parseInt(args[2]);
                String topPlayer = plugin.getTopPlayer(challengeId, position);
                return topPlayer != null ? topPlayer : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_top_time_<challengeid>_<position>% - Time at position in leaderboard (seconds)
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 4 && args[0].equalsIgnoreCase("top") && args[1].equalsIgnoreCase("time")) {
            String challengeId = resolveChallengeId(uuid, args[2]);
            if (challengeId == null) return "N/A";
            try {
                int position = Integer.parseInt(args[3]);
                Long topTime = plugin.getTopTime(challengeId, position);
                return topTime != null ? String.valueOf(topTime) : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_top_time_formatted_<challengeid>_<position>% - Time at position formatted
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 5 && args[0].equalsIgnoreCase("top") && args[1].equalsIgnoreCase("time") && args[2].equalsIgnoreCase("formatted")) {
            String challengeId = resolveChallengeId(uuid, args[3]);
            if (challengeId == null) return "N/A";
            try {
                int position = Integer.parseInt(args[4]);
                Long topTime = plugin.getTopTime(challengeId, position);
                return topTime != null ? formatTime(topTime) : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_leaderboard_count_<challengeid>% - Number of entries in leaderboard
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 3 && args[0].equalsIgnoreCase("leaderboard") && args[1].equalsIgnoreCase("count")) {
            String challengeId = resolveChallengeId(uuid, args[2]);
            if (challengeId == null) return "0";
            return String.valueOf(plugin.getLeaderboardCount(challengeId));
        }

        // %conradchallenges_rank_<challengeid>% - Player's rank in challenge leaderboard
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("rank")) {
            String challengeId = resolveChallengeId(uuid, args[1]);
            if (challengeId == null) return "N/A";
            int rank = plugin.getPlayerRank(uuid, challengeId);
            return rank > 0 ? String.valueOf(rank) : "N/A";
        }

        // %conradchallenges_total_completions% - Total unique challenges completed
        if (params.equalsIgnoreCase("total_completions")) {
            return String.valueOf(plugin.getTotalCompletions(uuid));
        }

        // %conradchallenges_world_alias% - World alias for player's current world
        if (params.equalsIgnoreCase("world_alias")) {
            return plugin.getWorldAlias(player);
        }

        // %conradchallenges_challenge_alias_<challengeid>% - Challenge alias for a specific challenge ID
        // Supports "active" as challenge ID to use current active challenge
        if (args.length >= 3 && args[0].equalsIgnoreCase("challenge") && args[1].equalsIgnoreCase("alias")) {
            String challengeId = resolveChallengeId(uuid, args[2]);
            if (challengeId == null) return "N/A";
            return plugin.getChallengeAlias(challengeId);
        }

        return null; // Placeholder is unknown
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
}
