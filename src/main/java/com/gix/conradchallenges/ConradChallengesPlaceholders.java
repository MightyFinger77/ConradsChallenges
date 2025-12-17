package com.gix.conradchallenges;

import org.bukkit.OfflinePlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * PlaceholderAPI expansion for ConradChallenges
 * Provides placeholders for challenge data, times, completions, etc.
 * Uses reflection to avoid compile-time dependency on PlaceholderAPI.
 */
public class ConradChallengesPlaceholders {

    private final ConradChallengesPlugin plugin;
    private Object expansionInstance;

    public ConradChallengesPlaceholders(ConradChallengesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers this expansion with PlaceholderAPI using reflection.
     */
    public boolean register() {
        try {
            // Get PlaceholderExpansion class
            Class<?> expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            
            // Create an anonymous inner class that extends PlaceholderExpansion
            // We'll use a proxy but ensure all required methods are handled
            expansionInstance = java.lang.reflect.Proxy.newProxyInstance(
                expansionClass.getClassLoader(),
                new Class[]{expansionClass},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    Class<?> returnType = method.getReturnType();
                    
                    // Handle all PlaceholderExpansion methods
                    if (methodName.equals("getIdentifier")) {
                        return "conradchallenges";
                    }
                    if (methodName.equals("getAuthor")) {
                        return String.join(", ", plugin.getDescription().getAuthors());
                    }
                    if (methodName.equals("getVersion")) {
                        return plugin.getDescription().getVersion();
                    }
                    if (methodName.equals("persist")) {
                        return true;
                    }
                    if (methodName.equals("canRegister")) {
                        return true;
                    }
                    if (methodName.equals("onRequest") && args.length == 2) {
                        OfflinePlayer player = (OfflinePlayer) args[0];
                        String params = (String) args[1];
                        return onPlaceholderRequest(player, params);
                    }
                    if (methodName.equals("onPlaceholderRequest") && args.length == 2) {
                        // Alternative method name
                        OfflinePlayer player = (OfflinePlayer) args[0];
                        String params = (String) args[1];
                        return onPlaceholderRequest(player, params);
                    }
                    if (methodName.equals("toString")) {
                        return "ConradChallengesPlaceholderExpansion";
                    }
                    if (methodName.equals("equals") && args.length == 1) {
                        return proxy == args[0];
                    }
                    if (methodName.equals("hashCode")) {
                        return proxy.hashCode();
                    }
                    
                    // Return default value for other methods
                    if (returnType == boolean.class || returnType == Boolean.class) {
                        return false;
                    }
                    if (returnType == int.class || returnType == Integer.class) {
                        return 0;
                    }
                    return null;
                }
            );
            
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
     * Handles placeholder requests.
     */
    private String onPlaceholderRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        String[] args = params.split("_");

        // %conradchallenges_active% - Current active challenge ID
        if (params.equalsIgnoreCase("active")) {
            String active = plugin.getActiveChallenge(uuid);
            return active != null ? active : "none";
        }

        // %conradchallenges_active_name% - Current active challenge name
        if (params.equalsIgnoreCase("active_name")) {
            String active = plugin.getActiveChallenge(uuid);
            if (active == null) return "none";
            return plugin.getChallengeDisplayName(active);
        }

        // %conradchallenges_besttime_<challengeid>% - Best time for player in challenge (in seconds)
        if (args.length >= 2 && args[0].equalsIgnoreCase("besttime")) {
            String challengeId = args[1];
            Long bestTime = plugin.getBestTime(uuid, challengeId);
            if (bestTime == null) return "N/A";
            return String.valueOf(bestTime);
        }

        // %conradchallenges_besttime_formatted_<challengeid>% - Best time formatted (e.g., "1m 30s")
        if (args.length >= 3 && args[0].equalsIgnoreCase("besttime") && args[1].equalsIgnoreCase("formatted")) {
            String challengeId = args[2];
            Long bestTime = plugin.getBestTime(uuid, challengeId);
            if (bestTime == null) return "N/A";
            return formatTime(bestTime);
        }

        // %conradchallenges_completed_<challengeid>% - Whether player has completed challenge (true/false)
        if (args.length >= 2 && args[0].equalsIgnoreCase("completed")) {
            String challengeId = args[1];
            return plugin.hasCompletedChallenge(uuid, challengeId) ? "true" : "false";
        }

        // %conradchallenges_cooldown_<challengeid>% - Cooldown remaining in seconds
        if (args.length >= 2 && args[0].equalsIgnoreCase("cooldown")) {
            String challengeId = args[1];
            long remaining = plugin.getCooldownRemaining(uuid, challengeId);
            return remaining > 0 ? String.valueOf(remaining) : "0";
        }

        // %conradchallenges_cooldown_formatted_<challengeid>% - Cooldown formatted (e.g., "5m 30s")
        if (args.length >= 3 && args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("formatted")) {
            String challengeId = args[2];
            long remaining = plugin.getCooldownRemaining(uuid, challengeId);
            if (remaining <= 0) return "Ready";
            return formatTime(remaining);
        }

        // %conradchallenges_completions_<challengeid>% - Number of times player completed challenge
        if (args.length >= 2 && args[0].equalsIgnoreCase("completions")) {
            String challengeId = args[1];
            return String.valueOf(plugin.getCompletionCount(uuid, challengeId));
        }

        // %conradchallenges_top_<challengeid>_<position>% - Player name at position in leaderboard
        if (args.length >= 3 && args[0].equalsIgnoreCase("top")) {
            String challengeId = args[1];
            try {
                int position = Integer.parseInt(args[2]);
                String topPlayer = plugin.getTopPlayer(challengeId, position);
                return topPlayer != null ? topPlayer : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_top_time_<challengeid>_<position>% - Time at position in leaderboard (seconds)
        if (args.length >= 4 && args[0].equalsIgnoreCase("top") && args[1].equalsIgnoreCase("time")) {
            String challengeId = args[2];
            try {
                int position = Integer.parseInt(args[3]);
                Long topTime = plugin.getTopTime(challengeId, position);
                return topTime != null ? String.valueOf(topTime) : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_top_time_formatted_<challengeid>_<position>% - Time at position formatted
        if (args.length >= 5 && args[0].equalsIgnoreCase("top") && args[1].equalsIgnoreCase("time") && args[2].equalsIgnoreCase("formatted")) {
            String challengeId = args[3];
            try {
                int position = Integer.parseInt(args[4]);
                Long topTime = plugin.getTopTime(challengeId, position);
                return topTime != null ? formatTime(topTime) : "N/A";
            } catch (NumberFormatException e) {
                return "Invalid";
            }
        }

        // %conradchallenges_leaderboard_count_<challengeid>% - Number of entries in leaderboard
        if (args.length >= 3 && args[0].equalsIgnoreCase("leaderboard") && args[1].equalsIgnoreCase("count")) {
            String challengeId = args[2];
            return String.valueOf(plugin.getLeaderboardCount(challengeId));
        }

        // %conradchallenges_rank_<challengeid>% - Player's rank in challenge leaderboard
        if (args.length >= 2 && args[0].equalsIgnoreCase("rank")) {
            String challengeId = args[1];
            int rank = plugin.getPlayerRank(uuid, challengeId);
            return rank > 0 ? String.valueOf(rank) : "N/A";
        }

        // %conradchallenges_total_completions% - Total completions across all challenges
        if (params.equalsIgnoreCase("total_completions")) {
            return String.valueOf(plugin.getTotalCompletions(uuid));
        }

        // %conradchallenges_challenges_completed% - Number of unique challenges completed
        if (params.equalsIgnoreCase("challenges_completed")) {
            return String.valueOf(plugin.getChallengesCompletedCount(uuid));
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
