package com.gix.conradchallenges;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static com.gix.conradchallenges.ChallengeScriptTypes.*;

/**
 * Handles the challenge function tool: item, GUIs, script execution.
 * All script-related logic lives here; the plugin wires events and exposes getters.
 */
public class ChallengeScriptManager {

    private static final String SCRIPT_GUI_TITLE_PREFIX = "§8Script §8";
    private static final long ENTER_AREA_COOLDOWN_MS = 2000;

    private final ConradChallengesPlugin plugin;
    private final Map<UUID, ScriptGuiContext> scriptGuiContext = new HashMap<>();
    private final Map<String, Long> scriptEnterAreaCooldowns = new HashMap<>();

    public ChallengeScriptManager(ConradChallengesPlugin plugin) {
        this.plugin = plugin;
    }

    // ----- Function tool item (material and display name from config) -----

    public ItemStack createFunctionTool() {
        Material mat = plugin.getFunctionToolMaterial();
        if (mat == null || !mat.isItem()) mat = Material.BLAZE_ROD;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getFunctionToolDisplayName());
            meta.setLore(Collections.singletonList("§7Right-click a block in edit mode to add a script"));
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFunctionTool(ItemStack item) {
        if (item == null) return false;
        Material configMat = plugin.getFunctionToolMaterial();
        if (configMat == null) configMat = Material.BLAZE_ROD;
        if (item.getType() != configMat) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && plugin.getFunctionToolDisplayName().equals(meta.getDisplayName());
    }

    public void giveFunctionTool(Player player) {
        if (player == null) return;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isFunctionTool(stack)) return;
        }
        player.getInventory().addItem(createFunctionTool());
    }

    /** Block keys (world:x,y,z) that have script nodes or pass-through for the given challenge. */
    public Set<String> getBlockKeysWithFunctions(ConradChallengesPlugin.ChallengeConfig cfg) {
        Set<String> set = new HashSet<>();
        if (cfg == null) return set;
        if (cfg.scriptNodes != null) {
            for (ChallengeScriptNode n : cfg.scriptNodes) set.add(n.blockKey());
        }
        if (cfg.passThroughBlockKeys != null) set.addAll(cfg.passThroughBlockKeys);
        return set;
    }

    // ----- Script execution -----

    /**
     * Finds and runs script nodes for a block and trigger.
     * Handles PLAYER_ENTER_AREA cooldown internally.
     */
    public void runScriptsForTrigger(ConradChallengesPlugin.ChallengeConfig cfg, String blockKey,
                                     ScriptTrigger trigger, Player triggerPlayer, Location blockLocation,
                                     LivingEntity killedEntity) {
        if (cfg.scriptNodes == null) return;
        if (trigger == ScriptTrigger.PLAYER_ENTER_AREA && triggerPlayer != null) {
            String cooldownKey = triggerPlayer.getUniqueId() + ":" + blockKey;
            long now = System.currentTimeMillis();
            if (now - scriptEnterAreaCooldowns.getOrDefault(cooldownKey, 0L) < ENTER_AREA_COOLDOWN_MS) return;
            scriptEnterAreaCooldowns.put(cooldownKey, now);
        }
        for (ChallengeScriptNode node : cfg.scriptNodes) {
            if (!node.blockKey().equals(blockKey) || node.trigger != trigger) continue;
            if (!evaluateScriptConditions(node, cfg, blockLocation)) continue;
            executeScriptFunction(node, cfg, triggerPlayer, blockLocation, killedEntity);
        }
        afterTriggerFired(cfg, blockKey, trigger, triggerPlayer, blockLocation, killedEntity);
    }

    /**
     * Runs PLAYER_ENTER_AREA scripts when the player moves. Each script fires if the player is within
     * its configured distance (enterAreaDistance, default 3 blocks) of the script's block.
     */
    public void runPlayerEnterAreaScripts(ConradChallengesPlugin.ChallengeConfig cfg, Player player, Location playerLoc) {
        if (cfg.scriptNodes == null || playerLoc == null || playerLoc.getWorld() == null) return;
        for (ChallengeScriptNode node : cfg.scriptNodes) {
            if (node.trigger != ScriptTrigger.PLAYER_ENTER_AREA) continue;
            org.bukkit.Location blockLoc = plugin.getLocationFromBlockKey(node.blockKey());
            if (blockLoc == null || blockLoc.getWorld() == null || !blockLoc.getWorld().equals(playerLoc.getWorld())) continue;
            int distBlocks = node.functionData.get("enterAreaDistance") instanceof Number ? ((Number) node.functionData.get("enterAreaDistance")).intValue() : 3;
            distBlocks = Math.max(1, Math.min(32, distBlocks));
            org.bukkit.Location center = blockLoc.getBlock().getLocation().add(0.5, 0.5, 0.5);
            if (playerLoc.distance(center) <= distBlocks) {
                runScriptsForTrigger(cfg, node.blockKey(), ScriptTrigger.PLAYER_ENTER_AREA, player, playerLoc, null);
            }
        }
    }

    private boolean evaluateScriptConditions(ChallengeScriptNode node, ConradChallengesPlugin.ChallengeConfig cfg, Location blockLocation) {
        if (node.conditions == null || node.conditions.isEmpty()) return true;
        String challengeId = cfg != null ? cfg.id : null;
        for (Map<String, Object> cond : node.conditions) {
            String type = String.valueOf(cond.get("type")).toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if ("CHANCE".equals(type)) {
                double chance = cond.get("chance") instanceof Number ? ((Number) cond.get("chance")).doubleValue() : 1.0;
                if (new Random().nextDouble() >= chance) return false;
            } else if ("PLAYERS_WITHIN".equals(type) && challengeId != null && blockLocation != null) {
                double radius = cond.get("radius") instanceof Number ? ((Number) cond.get("radius")).doubleValue() : 5.0;
                int min = cond.get("min") instanceof Number ? ((Number) cond.get("min")).intValue() : 1;
                int count = plugin.getPlayersWithinRadius(blockLocation, radius, challengeId);
                if (count < min) return false;
                if (cond.containsKey("max") && cond.get("max") instanceof Number) {
                    int max = ((Number) cond.get("max")).intValue();
                    if (count > max) return false;
                }
            } else if ("PLAYER_COUNT".equals(type) && challengeId != null) {
                int n = plugin.getActiveChallengePlayerCount(challengeId);
                if (cond.get("min") instanceof Number) {
                    if (n < ((Number) cond.get("min")).intValue()) return false;
                }
                if (cond.get("max") instanceof Number) {
                    if (n > ((Number) cond.get("max")).intValue()) return false;
                }
            } else if ("TIME_ELAPSED".equals(type) && challengeId != null) {
                long start = plugin.getChallengeStartTime(challengeId);
                if (start <= 0) return false;
                long elapsedSec = (System.currentTimeMillis() - start) / 1000;
                if (cond.get("minSeconds") instanceof Number) {
                    if (elapsedSec < ((Number) cond.get("minSeconds")).longValue()) return false;
                }
                if (cond.get("maxSeconds") instanceof Number) {
                    if (elapsedSec > ((Number) cond.get("maxSeconds")).longValue()) return false;
                }
            } else if ("DIFFICULTY_TIER".equals(type) && challengeId != null) {
                String tier = plugin.getDifficultyTier(challengeId);
                Object allowed = cond.get("tier");
                if (allowed instanceof String) {
                    if (!tier.equalsIgnoreCase((String) allowed)) return false;
                } else if (allowed instanceof List) {
                    boolean ok = false;
                    for (Object o : (List<?>) allowed) {
                        if (String.valueOf(o).equalsIgnoreCase(tier)) { ok = true; break; }
                    }
                    if (!ok) return false;
                }
            }
        }
        return true;
    }

    private void executeScriptFunction(ChallengeScriptNode node, ConradChallengesPlugin.ChallengeConfig cfg,
                                       Player triggerPlayer, Location blockLocation, LivingEntity killedEntity) {
        Player targetPlayer = triggerPlayer;
        Location at = blockLocation != null ? blockLocation : (cfg.destination != null ? cfg.destination : null);
        boolean needsLoc = node.functionType != ScriptFunctionType.LEAVE_CHALLENGE && node.functionType != ScriptFunctionType.SIGNAL_SENDER;
        if (needsLoc && at == null) return;
        World world = at != null ? at.getWorld() : null;
        if (needsLoc && world == null) return;
        Map<UUID, String> activeChallenges = plugin.getActiveChallenges();

        switch (node.functionType) {
            case SEND_MESSAGE, BROADCAST_MESSAGE -> {
                String msg = node.functionData.containsKey("message") ? String.valueOf(node.functionData.get("message")) : "";
                if (msg.isEmpty()) break;
                String translated = ChatColor.translateAlternateColorCodes('&', msg);
                if (node.functionType == ScriptFunctionType.SEND_MESSAGE && targetPlayer != null) {
                    targetPlayer.sendMessage(translated);
                } else if (node.functionType == ScriptFunctionType.BROADCAST_MESSAGE) {
                    for (Map.Entry<UUID, String> e : activeChallenges.entrySet()) {
                        if (!e.getValue().equals(cfg.id)) continue;
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null && p.isOnline()) p.sendMessage(translated);
                    }
                }
            }
            case PLAY_SOUND -> {
                String soundName = node.functionData.containsKey("sound") ? String.valueOf(node.functionData.get("sound")) : "ENTITY_EXPERIENCE_ORB_PICKUP";
                float volume = node.functionData.get("volume") instanceof Number ? ((Number) node.functionData.get("volume")).floatValue() : 1.0f;
                float pitch = node.functionData.get("pitch") instanceof Number ? ((Number) node.functionData.get("pitch")).floatValue() : 1.0f;
                try {
                    Sound sound = Sound.valueOf(soundName);
                    world.playSound(at, sound, volume, pitch);
                } catch (IllegalArgumentException ignored) {
                    world.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, pitch);
                }
            }
            case RUN_COMMAND -> {
                String cmd = node.functionData.containsKey("command") ? String.valueOf(node.functionData.get("command")).trim() : "";
                if (cmd.isEmpty()) break;
                if (targetPlayer != null) {
                    String run = cmd.replace("%player%", targetPlayer.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
            case TELEPORT_PLAYER -> {
                if (targetPlayer == null) break;
                Boolean useDest = node.functionData.get("useDestination") instanceof Boolean ? (Boolean) node.functionData.get("useDestination") : true;
                if (useDest != null && useDest && cfg.destination != null) {
                    targetPlayer.teleport(cfg.destination);
                } else if (node.functionData.containsKey("x") && node.functionData.containsKey("y") && node.functionData.containsKey("z")) {
                    World w = node.functionData.containsKey("world") ? Bukkit.getWorld(String.valueOf(node.functionData.get("world"))) : world;
                    if (w != null) {
                        double x = ((Number) node.functionData.get("x")).doubleValue();
                        double y = ((Number) node.functionData.get("y")).doubleValue();
                        double z = ((Number) node.functionData.get("z")).doubleValue();
                        float yaw = node.functionData.containsKey("yaw") ? ((Number) node.functionData.get("yaw")).floatValue() : 0;
                        float pitch = node.functionData.containsKey("pitch") ? ((Number) node.functionData.get("pitch")).floatValue() : 0;
                        targetPlayer.teleport(new Location(w, x, y, z, yaw, pitch));
                    }
                }
            }
            case PASS_THROUGH -> { /* Handled by plugin pass-through blocks task */ }
            case LEAVE_CHALLENGE -> {
                if (targetPlayer != null)
                    plugin.exitPlayerFromChallenge(targetPlayer);
            }
            case SIGNAL_SENDER -> {
                String signal = node.functionData.containsKey("signal") ? String.valueOf(node.functionData.get("signal")).trim() : "";
                if (!signal.isEmpty())
                    plugin.fireScriptSignal(cfg.id, signal);
            }
            case REMOVE_BLOCK_WHEN_ITEM_NEAR -> { /* Handled by tryKeyItemGate on block right-click */ }
        }
    }

    /** After running scripts for a trigger, mark block as fired and process signal receivers + gates. */
    private void afterTriggerFired(ConradChallengesPlugin.ChallengeConfig cfg, String blockKey,
                                   ScriptTrigger trigger, Player triggerPlayer, Location blockLocation,
                                   LivingEntity killedEntity) {
        plugin.markScriptTriggerFired(cfg.id, blockKey);
        processSignalReceiversAndGates(cfg, triggerPlayer, blockLocation, killedEntity);
    }

    /** Run SIGNAL_RECEIVER nodes whose signal was fired, and AND/OR gates whose dependencies are met. */
    @SuppressWarnings("unchecked")
    private void processSignalReceiversAndGates(ConradChallengesPlugin.ChallengeConfig cfg,
                                                Player triggerPlayer, Location fallbackLoc,
                                                LivingEntity killedEntity) {
        if (cfg.scriptNodes == null) return;
        Set<String> fired = plugin.getScriptFiredTriggers(cfg.id);
        Set<String> signals = plugin.getScriptSignalsFired(cfg.id);
        int maxRounds = 50;
        Set<String> receiversFiredThisRun = new HashSet<>();
        while (maxRounds-- > 0) {
            boolean any = false;
            for (ChallengeScriptNode node : cfg.scriptNodes) {
                if (node.trigger == ScriptTrigger.SIGNAL_RECEIVER) {
                    String sig = node.functionData != null && node.functionData.containsKey("signal")
                            ? String.valueOf(node.functionData.get("signal")).trim() : "";
                    if (sig.isEmpty() || !signals.contains(sig)) continue;
                    String rk = node.blockKey() + ":signal:" + sig;
                    if (receiversFiredThisRun.contains(rk)) continue;
                    if (!evaluateScriptConditions(node, cfg, fallbackLoc)) continue;
                    receiversFiredThisRun.add(rk);
                    executeScriptFunction(node, cfg, triggerPlayer, fallbackLoc, killedEntity);
                    any = true;
                } else if (node.trigger == ScriptTrigger.AND_GATE || node.trigger == ScriptTrigger.OR_GATE) {
                    if (fired.contains(node.blockKey())) continue; // already fired this run
                    Object depObj = node.functionData != null ? node.functionData.get("dependencies") : null;
                    List<String> deps = depObj instanceof List ? (List<String>) depObj : Collections.emptyList();
                    if (deps.isEmpty()) continue;
                    boolean satisfied = node.trigger == ScriptTrigger.AND_GATE
                            ? fired.containsAll(deps)
                            : deps.stream().anyMatch(fired::contains);
                    if (!satisfied) continue;
                    if (!evaluateScriptConditions(node, cfg, fallbackLoc)) continue;
                    plugin.markScriptTriggerFired(cfg.id, node.blockKey());
                    executeScriptFunction(node, cfg, triggerPlayer, fallbackLoc, killedEntity);
                    any = true;
                }
            }
            if (!any) break;
        }
    }

    // ----- GUIs -----

    private static ItemStack makeGuiItem(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public void openScriptFunctionTypeGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.FUNCTION_TYPE;
        Inventory inv = Bukkit.createInventory(null, 18, SCRIPT_GUI_TITLE_PREFIX + "Function");
        inv.setItem(0, makeGuiItem(Material.PAPER, ChatColor.GREEN + "Send message", "§7Send message to triggering player"));
        inv.setItem(1, makeGuiItem(Material.WRITABLE_BOOK, ChatColor.GREEN + "Broadcast message", "§7Broadcast to all in challenge"));
        inv.setItem(2, makeGuiItem(Material.NOTE_BLOCK, ChatColor.AQUA + "Play sound", "§7Play a sound at location"));
        inv.setItem(3, makeGuiItem(Material.COMMAND_BLOCK, ChatColor.LIGHT_PURPLE + "Run command", "§7Execute a command"));
        inv.setItem(4, makeGuiItem(Material.ENDER_PEARL, ChatColor.GOLD + "Teleport player", "§7Teleport to destination or coords"));
        inv.setItem(5, makeGuiItem(Material.BARRIER, ChatColor.GRAY + "Pass-through block", "§7Players can walk through when near"));
        inv.setItem(6, makeGuiItem(Material.OAK_DOOR, ChatColor.RED + "Leave challenge", "§7Exit challenge (like /exit)"));
        inv.setItem(7, makeGuiItem(Material.REPEATER, ChatColor.YELLOW + "Send signal", "§7Fire signal for receivers/gates"));
        inv.setItem(8, makeGuiItem(Material.IRON_BARS, ChatColor.GOLD + "Remove block (use item)", "§7Right-click with required item on this block or any linked block to remove them.", "§7Consumes items. Stays gone until regen."));
        inv.setItem(9, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to block menu"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptTriggerGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.TRIGGER;
        Inventory inv = Bukkit.createInventory(null, 18, SCRIPT_GUI_TITLE_PREFIX + "Trigger");
        ScriptTrigger[] triggers = {
            ScriptTrigger.BLOCK_INTERACT, ScriptTrigger.BLOCK_BREAK, ScriptTrigger.BLOCK_PLACE,
            ScriptTrigger.PLAYER_ENTER_AREA, ScriptTrigger.MOB_DEATH,
            ScriptTrigger.SIGNAL_RECEIVER, ScriptTrigger.REDSTONE_RECEIVER,
            ScriptTrigger.AND_GATE, ScriptTrigger.OR_GATE
        };
        String[] names = {
            "Block interact", "Block break", "Block place", "Player enter area", "Mob death",
            "Signal receiver", "Redstone receiver", "AND gate", "OR gate"
        };
        String[] lores = {
            "§7Right-click block", "§7Block broken", "§7Block placed", "§7Player enters block area", "§7Mob dies near block",
            "§7When signal sent", "§7Block powered", "§7All deps fired", "§7Any dep fired"
        };
        Material[] mats = {
            Material.STONE_BUTTON, Material.DIAMOND_PICKAXE, Material.GRASS_BLOCK, Material.LEATHER_BOOTS, Material.SKELETON_SKULL,
            Material.REPEATER, Material.REDSTONE_TORCH, Material.ANDESITE, Material.ORANGE_DYE
        };
        for (int i = 0; i < triggers.length && i < 9; i++) {
            inv.setItem(i, makeGuiItem(mats[i], ChatColor.YELLOW + names[i], lores[i]));
        }
        inv.setItem(9, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to function type"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptConfigureGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.CONFIGURE;
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Configure");
        ScriptFunctionType ft = ctx.node.functionType;
        ScriptTrigger tr = ctx.node.trigger;
        if (ft == ScriptFunctionType.SEND_MESSAGE || ft == ScriptFunctionType.BROADCAST_MESSAGE) {
            inv.setItem(0, makeGuiItem(Material.PAPER, ChatColor.YELLOW + "Set message (chat)", "§7Type in chat after closing", "§7Current: " + truncate(String.valueOf(ctx.node.functionData.getOrDefault("message", "")), 20)));
            inv.setItem(4, makeGuiItem(Material.BOOK, ChatColor.GRAY + "Message", "§7" + truncate(String.valueOf(ctx.node.functionData.getOrDefault("message", "(none)")), 32)));
        } else if (ft == ScriptFunctionType.PLAY_SOUND) {
            inv.setItem(0, makeGuiItem(Material.NOTE_BLOCK, ChatColor.YELLOW + "Set sound (chat)", "§7e.g. ENTITY_EXPERIENCE_ORB_PICKUP", "§7Current: " + String.valueOf(ctx.node.functionData.getOrDefault("sound", ""))));
            inv.setItem(1, makeGuiItem(Material.LIME_DYE, ChatColor.GREEN + "Volume: " + ctx.node.functionData.getOrDefault("volume", 1.0), "§7Click to cycle 0.5 / 1.0 / 2.0"));
            inv.setItem(2, makeGuiItem(Material.MAGENTA_DYE, ChatColor.LIGHT_PURPLE + "Pitch: " + ctx.node.functionData.getOrDefault("pitch", 1.0), "§7Click to cycle 0.5 / 1.0 / 2.0"));
        } else if (ft == ScriptFunctionType.RUN_COMMAND) {
            inv.setItem(0, makeGuiItem(Material.COMMAND_BLOCK, ChatColor.YELLOW + "Set command (chat)", "§7Use %player% for name", "§7Current: " + truncate(String.valueOf(ctx.node.functionData.getOrDefault("command", "")), 20)));
        } else if (ft == ScriptFunctionType.TELEPORT_PLAYER) {
            boolean useDest = ctx.node.functionData.get("useDestination") != Boolean.FALSE;
            inv.setItem(0, makeGuiItem(useDest ? Material.ENDER_PEARL : Material.PAPER, ChatColor.GOLD + "Use destination: " + useDest, "§7Toggle: use challenge destination or custom coords"));
        } else if (ft == ScriptFunctionType.SIGNAL_SENDER) {
            inv.setItem(0, makeGuiItem(Material.REPEATER, ChatColor.YELLOW + "Set signal name (chat)", "§7Current: " + String.valueOf(ctx.node.functionData.getOrDefault("signal", ""))));
        } else if (tr == ScriptTrigger.SIGNAL_RECEIVER) {
            inv.setItem(0, makeGuiItem(Material.REPEATER, ChatColor.YELLOW + "Set signal name (chat)", "§7Listen for this signal", "§7Current: " + String.valueOf(ctx.node.functionData.getOrDefault("signal", ""))));
        } else if (tr == ScriptTrigger.AND_GATE || tr == ScriptTrigger.OR_GATE) {
            inv.setItem(0, makeGuiItem(Material.COMPARATOR, ChatColor.YELLOW + "Set dependencies (chat)", "§7Comma-separated block keys", "§7e.g. world:10,20,30,world:11,21,31"));
        } else if (ft == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
            String keyMat = String.valueOf(ctx.node.functionData.getOrDefault("keyItemMaterial", ""));
            String keyDisp = String.valueOf(ctx.node.functionData.getOrDefault("keyItemDisplayName", ctx.node.functionData.getOrDefault("itemDisplayName", "")));
            String keyLabel = keyMat.isEmpty() && keyDisp.isEmpty() ? "(none)" : (keyMat.isEmpty() ? keyDisp : keyMat + (keyDisp.isEmpty() ? "" : " - " + truncate(keyDisp, 12)));
            inv.setItem(0, makeGuiItem(Material.TRIPWIRE_HOOK, ChatColor.YELLOW + "Set key item", "§7Close GUI, hold item, right-click block", "§7Current: " + truncate(keyLabel, 20)));
            inv.setItem(1, makeGuiItem(Material.BONE, ChatColor.YELLOW + "Set amount (chat)", "§7Consumed per use", "§7Current: " + ctx.node.functionData.getOrDefault("amount", 1)));
            Object lb = ctx.node.functionData.get("linkedBlocks");
            int linkedCount = lb instanceof List ? ((List<?>) lb).size() : 0;
            inv.setItem(2, makeGuiItem(Material.CHAIN, ChatColor.GOLD + "Link blocks", "§7Click blocks with function tool to link", "§7Current: " + linkedCount + " block(s) linked"));
        }
        if (tr == ScriptTrigger.PLAYER_ENTER_AREA) {
            int dist = ctx.node.functionData.get("enterAreaDistance") instanceof Number ? ((Number) ctx.node.functionData.get("enterAreaDistance")).intValue() : 3;
            inv.setItem(6, makeGuiItem(Material.LEATHER_BOOTS, ChatColor.YELLOW + "Set distance (chat)", "§7Blocks from trigger block", "§7Current: " + dist + " block(s)"));
        }
        inv.setItem(7, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to trigger"));
        inv.setItem(8, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Done", "§7Continue to confirm"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void handleConfigureGuiClick(Player player, ScriptGuiContext ctx, int slot) {
        ScriptFunctionType ft = ctx.node.functionType;
        ScriptTrigger tr = ctx.node.trigger;
        if (slot == 7) {
            if (ft == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg != null) openScriptBlockMenuGui(player, ctx, cfg, ctx.node.blockKey());
            } else {
                openScriptTriggerGui(player, ctx);
            }
            return;
        }
        if (slot == 8) {
            openScriptConfirmGui(player, ctx);
            return;
        }
        if (slot == 0) {
            if (ft == ScriptFunctionType.SEND_MESSAGE || ft == ScriptFunctionType.BROADCAST_MESSAGE) {
                startChatInput(player, ctx, "message", "Type the message in chat (or 'cancel'):");
            } else if (ft == ScriptFunctionType.PLAY_SOUND) {
                startChatInput(player, ctx, "sound", "Type sound name (e.g. ENTITY_EXPERIENCE_ORB_PICKUP) or 'cancel':");
            } else if (ft == ScriptFunctionType.RUN_COMMAND) {
                startChatInput(player, ctx, "command", "Type the command (use %player% for name) or 'cancel':");
            } else if (ft == ScriptFunctionType.SIGNAL_SENDER || tr == ScriptTrigger.SIGNAL_RECEIVER) {
                startChatInput(player, ctx, "signal", "Type signal name or 'cancel':");
            } else if (tr == ScriptTrigger.AND_GATE || tr == ScriptTrigger.OR_GATE) {
                startChatInput(player, ctx, "dependencies", "Type comma-separated block keys (world:x,y,z) or 'cancel':");
            } else if (ft == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR && slot == 0) {
                ctx.step = ScriptGuiStep.CONFIGURE_AWAIT_KEY_ITEM;
                scriptGuiContext.put(player.getUniqueId(), ctx);
                player.closeInventory();
                player.sendMessage(ChatColor.GRAY + "Hold the key item in your main hand and right-click any block to set it.");
            }
            return;
        }
        if (slot == 1 && ft == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
            startChatInput(player, ctx, "amount", "Type amount to consume per use (number) or 'cancel':");
            return;
        }
        if (slot == 2 && ft == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
            ctx.step = ScriptGuiStep.LINK_BLOCKS_AWAIT_CLICK;
            scriptGuiContext.put(player.getUniqueId(), ctx);
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "Right-click a block with the function tool to link it (then choose Link another / Finish / Unlink).");
            return;
        }
        if (slot == 6 && tr == ScriptTrigger.PLAYER_ENTER_AREA) {
            startChatInput(player, ctx, "enterAreaDistance", "Type distance in blocks (1–32) or 'cancel':");
            return;
        }
        if (slot == 1 && ft == ScriptFunctionType.PLAY_SOUND) {
            double v = ((Number) ctx.node.functionData.getOrDefault("volume", 1.0)).doubleValue();
            v = (v == 0.5 ? 1.0 : v == 1.0 ? 2.0 : 0.5);
            ctx.node.functionData.put("volume", v);
            openScriptConfigureGui(player, ctx);
            return;
        }
        if (slot == 2 && ft == ScriptFunctionType.PLAY_SOUND) {
            double p = ((Number) ctx.node.functionData.getOrDefault("pitch", 1.0)).doubleValue();
            p = (p == 0.5 ? 1.0 : p == 1.0 ? 2.0 : 0.5);
            ctx.node.functionData.put("pitch", p);
            openScriptConfigureGui(player, ctx);
            return;
        }
        if (slot == 0 && ft == ScriptFunctionType.TELEPORT_PLAYER) {
            boolean useDest = ctx.node.functionData.get("useDestination") != Boolean.FALSE;
            ctx.node.functionData.put("useDestination", !useDest);
            openScriptConfigureGui(player, ctx);
        }
    }

    private void startChatInput(Player player, ScriptGuiContext ctx, String field, String prompt) {
        ctx.step = ScriptGuiStep.CONFIGURE_AWAIT_CHAT;
        ctx.awaitingField = field;
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + prompt);
    }

    public boolean isAwaitingScriptChat(Player player) {
        ScriptGuiContext ctx = scriptGuiContext.get(player.getUniqueId());
        return ctx != null && ctx.step == ScriptGuiStep.CONFIGURE_AWAIT_CHAT;
    }

    /** Called by plugin when player sends chat and context is CONFIGURE_AWAIT_CHAT. Runs on main thread. */
    public void handleScriptChatInput(Player player, String message) {
        ScriptGuiContext ctx = scriptGuiContext.get(player.getUniqueId());
        if (ctx == null || ctx.step != ScriptGuiStep.CONFIGURE_AWAIT_CHAT || ctx.awaitingField == null) return;
        if ("cancel".equalsIgnoreCase(message.trim())) {
            ctx.step = ScriptGuiStep.CONFIGURE;
            ctx.awaitingField = null;
            openScriptConfigureGui(player, ctx);
            return;
        }
        String field = ctx.awaitingField;
        ctx.awaitingField = null;
        if ("message".equals(field)) {
            ctx.node.functionData.put("message", message);
        } else if ("command".equals(field)) {
            ctx.node.functionData.put("command", message);
        } else if ("signal".equals(field)) {
            ctx.node.functionData.put("signal", message.trim());
        } else if ("sound".equals(field)) {
            ctx.node.functionData.put("sound", message.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } else if ("dependencies".equals(field)) {
            List<String> deps = new ArrayList<>();
            for (String part : message.split(",")) {
                String k = part.trim();
                if (!k.isEmpty()) deps.add(k);
            }
            ctx.node.functionData.put("dependencies", deps);
        } else if ("itemDisplayName".equals(field)) {
            ctx.node.functionData.put("itemDisplayName", message.trim());
        } else if ("amount".equals(field)) {
            try {
                int amt = Integer.parseInt(message.trim());
                if (amt > 0) ctx.node.functionData.put("amount", amt);
            } catch (NumberFormatException ignored) { }
        } else if ("enterAreaDistance".equals(field)) {
            try {
                int d = Integer.parseInt(message.trim());
                d = Math.max(1, Math.min(32, d));
                ctx.node.functionData.put("enterAreaDistance", d);
            } catch (NumberFormatException ignored) { }
        } else if ("distance".equals(field)) {
            try {
                double d = Double.parseDouble(message.trim().replace(',', '.'));
                if (d > 0) ctx.node.functionData.put("distance", d);
            } catch (NumberFormatException ignored) { }
        }
        ctx.step = ScriptGuiStep.CONFIGURE;
        openScriptConfigureGui(player, ctx);
    }

    private void openScriptConfirmGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.CONFIRM;
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Confirm");
        if (ctx.node.functionType == ScriptFunctionType.PASS_THROUGH) {
            inv.setItem(3, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Set as pass-through", "§7Players can walk through this block when near"));
        } else if (ctx.node.functionType == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
            String label = ctx.editingNodeIndex >= 0 ? "Save changes" : "Add remove-block (use item)";
            inv.setItem(3, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + label, "§7Right-click with required item on this block or any linked block to remove them."));
        } else {
            String label = ctx.editingNodeIndex >= 0 ? "Save changes" : "Add script";
            inv.setItem(3, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + label, "§7" + ctx.node.functionType + " on " + ctx.node.trigger));
        }
        inv.setItem(4, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to configure"));
        inv.setItem(5, makeGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Cancel", "§7Discard and close"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    // ----- Event handlers (called from plugin) -----

    public void handleFunctionToolInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        ItemStack hand = event.getHand() == EquipmentSlot.HAND ? event.getPlayer().getInventory().getItemInMainHand() : event.getPlayer().getInventory().getItemInOffHand();
        Player player = event.getPlayer();
        String challengeId = plugin.getPlayerEditingChallenge(player);
        if (challengeId == null) return;
        ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(challengeId);
        if (cfg == null) return;
        if (!plugin.isLocationInRegenArea(cfg, block.getLocation()) && !plugin.isLocationInChallengeArea(cfg, block.getLocation())) return;
        // Only cancel when we actually handle the click (function tool / script GUI). Do not cancel here or block placement in edit mode would never fire.
        // Awaiting key item: close GUI, hold item, right-click any block to set it
        ScriptGuiContext awaitKeyCtx = scriptGuiContext.get(player.getUniqueId());
        if (awaitKeyCtx != null && awaitKeyCtx.step == ScriptGuiStep.CONFIGURE_AWAIT_KEY_ITEM) {
            if (hand != null && !hand.getType().isAir() && !isFunctionTool(hand)) {
                event.setCancelled(true);
                awaitKeyCtx.node.functionData.put("keyItemMaterial", hand.getType().name());
                awaitKeyCtx.node.functionData.put("keyItemDisplayName", hand.hasItemMeta() && hand.getItemMeta().hasDisplayName() ? hand.getItemMeta().getDisplayName() : "");
                awaitKeyCtx.step = ScriptGuiStep.CONFIGURE;
                scriptGuiContext.put(player.getUniqueId(), awaitKeyCtx);
                player.sendMessage(ChatColor.GREEN + "Key item set: " + hand.getType().name() + (hand.getItemMeta() != null && hand.getItemMeta().hasDisplayName() ? " - " + ChatColor.stripColor(hand.getItemMeta().getDisplayName()) : ""));
                openScriptConfigureGui(player, awaitKeyCtx);
            }
            return;
        }
        if (!isFunctionTool(hand)) return;
        String blockKey = plugin.getBlockKey(block.getLocation());
        if (blockKey == null) return;
        ScriptGuiContext linkCtx = scriptGuiContext.get(player.getUniqueId());
        if (linkCtx != null && linkCtx.step == ScriptGuiStep.LINK_BLOCKS_AWAIT_CLICK) {
            event.setCancelled(true);
            if (linkCtx.node.functionType == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR && !blockKey.equals(linkCtx.node.blockKey())) {
                @SuppressWarnings("unchecked")
                List<String> linked = (List<String>) linkCtx.node.functionData.computeIfAbsent("linkedBlocks", k -> new ArrayList<String>());
                if (!linked.contains(blockKey)) linked.add(blockKey);
            }
            openScriptLinkBlocksMenuGui(player, linkCtx);
            return;
        }
        // If we're in the middle of editing this same block, resume at current step (e.g. after Link blocks -> Finish)
        ScriptGuiContext existingCtx = scriptGuiContext.get(player.getUniqueId());
        if (existingCtx != null && existingCtx.challengeId.equals(challengeId) && existingCtx.node.blockKey().equals(blockKey)) {
            event.setCancelled(true);
            switch (existingCtx.step) {
                case CONFIGURE:
                case CONFIGURE_AWAIT_CHAT:
                    openScriptConfigureGui(player, existingCtx);
                    return;
                case CONFIRM:
                    openScriptConfirmGui(player, existingCtx);
                    return;
                case TRIGGER:
                    openScriptTriggerGui(player, existingCtx);
                    return;
                case LINK_BLOCKS_MENU:
                    openScriptLinkBlocksMenuGui(player, existingCtx);
                    return;
                case FUNCTION_TYPE:
                    openScriptFunctionTypeGui(player, existingCtx);
                    return;
                case CONFIGURE_AWAIT_KEY_ITEM:
                    openScriptConfigureGui(player, existingCtx);
                    return;
                default:
                    break;
            }
        }
        boolean hasScripts = false;
        if (cfg.scriptNodes != null) {
            for (ChallengeScriptNode n : cfg.scriptNodes) {
                if (n.blockKey().equals(blockKey)) { hasScripts = true; break; }
            }
        }
        boolean hasPassThrough = cfg.passThroughBlockKeys != null && cfg.passThroughBlockKeys.contains(blockKey);
        ChallengeScriptNode node = ChallengeScriptNode.fromBlock(block);
        ScriptGuiContext ctx = new ScriptGuiContext(challengeId, node);
        event.setCancelled(true);
        if (hasScripts || hasPassThrough) {
            openScriptBlockMenuGui(player, ctx, cfg, blockKey);
        } else {
            openScriptFunctionTypeGui(player, ctx);
        }
    }

    private void openScriptBlockMenuGui(Player player, ScriptGuiContext ctx, ConradChallengesPlugin.ChallengeConfig cfg, String blockKey) {
        ctx.step = ScriptGuiStep.BLOCK_MENU;
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Block");
        inv.setItem(2, makeGuiItem(Material.LIME_DYE, ChatColor.GREEN + "Add function", "§7Add a new script to this block"));
        inv.setItem(6, makeGuiItem(Material.ORANGE_DYE, ChatColor.GOLD + "Edit / Remove", "§7Edit or remove existing scripts"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptEditListGui(Player player, ScriptGuiContext ctx, ConradChallengesPlugin.ChallengeConfig cfg, String blockKey) {
        ctx.step = ScriptGuiStep.EDIT_LIST;
        ctx.blockEditList = new ArrayList<>();
        if (cfg.scriptNodes != null) {
            for (int i = 0; i < cfg.scriptNodes.size(); i++) {
                if (cfg.scriptNodes.get(i).blockKey().equals(blockKey))
                    ctx.blockEditList.add(i);
            }
        }
        if (cfg.passThroughBlockKeys != null && cfg.passThroughBlockKeys.contains(blockKey))
            ctx.blockEditList.add(-1); // -1 = pass-through
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Edit/Remove");
        for (int i = 0; i < ctx.blockEditList.size() && i < 7; i++) {
            int idx = ctx.blockEditList.get(i);
            if (idx == -1) {
                inv.setItem(i, makeGuiItem(Material.BARRIER, ChatColor.GRAY + "Pass-through", "§7Click to remove"));
            } else {
                ChallengeScriptNode n = cfg.scriptNodes.get(idx);
                String label = n.functionType + " (" + n.trigger + ")";
                inv.setItem(i, makeGuiItem(Material.PAPER, ChatColor.YELLOW + truncate(label, 22), "§7Click to edit or remove"));
            }
        }
        inv.setItem(8, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to block menu"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptEditOrRemoveGui(Player player, ScriptGuiContext ctx, int listSlot) {
        ctx.step = ScriptGuiStep.EDIT_OR_REMOVE;
        ctx.editListClickedIndex = listSlot;
        ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
        if (cfg == null) return;
        int value = ctx.blockEditList.get(listSlot);
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Edit?");
        if (value == -1) {
            inv.setItem(2, makeGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Remove pass-through", "§7Remove pass-through from this block"));
        } else {
            inv.setItem(0, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Edit", "§7Change message/trigger/etc."));
            inv.setItem(4, makeGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Remove", "§7Delete this script"));
        }
        inv.setItem(8, makeGuiItem(Material.ARROW, ChatColor.GRAY + "Back", "§7Back to list"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptRemoveConfirmGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.REMOVE_CONFIRM;
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Remove?");
        inv.setItem(2, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm remove", "§7Remove this script"));
        inv.setItem(6, makeGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Cancel", "§7Keep it"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    private void openScriptLinkBlocksMenuGui(Player player, ScriptGuiContext ctx) {
        ctx.step = ScriptGuiStep.LINK_BLOCKS_MENU;
        Object lb = ctx.node.functionData.get("linkedBlocks");
        List<?> list = lb instanceof List ? (List<?>) lb : Collections.emptyList();
        int count = list.size();
        Inventory inv = Bukkit.createInventory(null, 9, SCRIPT_GUI_TITLE_PREFIX + "Link blocks");
        inv.setItem(1, makeGuiItem(Material.LIME_DYE, ChatColor.GREEN + "Link another block", "§7Close and right-click a block to link it"));
        inv.setItem(4, makeGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Finish", "§7Done linking – back to configure"));
        if (count > 0) {
            inv.setItem(7, makeGuiItem(Material.RED_DYE, ChatColor.RED + "Unlink block", "§7Remove the last linked block"));
        } else {
            inv.setItem(7, makeGuiItem(Material.GRAY_DYE, ChatColor.GRAY + "Unlink block", "§7No blocks linked"));
        }
        inv.setItem(0, makeGuiItem(Material.CHAIN, ChatColor.GRAY + "Linked: " + count + " block(s)", "§7Use Link another to add more"));
        scriptGuiContext.put(player.getUniqueId(), ctx);
        player.openInventory(inv);
    }

    public void handleBlockInteractScript(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack offHand = event.getPlayer().getInventory().getItemInOffHand();
        if (isFunctionTool(mainHand) || isFunctionTool(offHand)) return;
        ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallengeForLocation(block.getLocation());
        if (cfg == null) return;
        Player player = event.getPlayer();
        String activeId = plugin.getActiveChallenges().get(player.getUniqueId());
        if (activeId == null || !activeId.equals(cfg.id)) return;
        String key = plugin.getBlockKey(block.getLocation());
        if (key == null) return;
        if (tryKeyItemGate(player, block, mainHand, offHand)) {
            event.setCancelled(true);
            return;
        }
        runScriptsForTrigger(cfg, key, ScriptTrigger.BLOCK_INTERACT, player, block.getLocation(), null);
    }

    /**
     * MythicDungeons-style: use key item on block to remove it + linked blocks and consume items.
     * Returns true if the event was handled (key item matched and gate triggered).
     */
    public boolean tryKeyItemGate(Player player, Block block, ItemStack mainHand, ItemStack offHand) {
        ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallengeForLocation(block.getLocation());
        if (cfg == null || cfg.scriptNodes == null) return false;
        String blockKey = plugin.getBlockKey(block.getLocation());
        if (blockKey == null) return false;
        ChallengeScriptNode node = null;
        for (ChallengeScriptNode n : cfg.scriptNodes) {
            if (n.functionType != ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) continue;
            if (n.blockKey().equals(blockKey)) {
                node = n;
                break;
            }
            Object lbObj = n.functionData != null ? n.functionData.get("linkedBlocks") : null;
            if (lbObj instanceof List && ((List<?>) lbObj).contains(blockKey)) {
                node = n;
                break;
            }
        }
        if (node == null) return false;
        Map<String, Object> fd = node.functionData;
        if (fd == null) return false;
        ItemStack used = null;
        if (itemMatchesKeyItem(mainHand, fd)) used = mainHand;
        else if (itemMatchesKeyItem(offHand, fd)) used = offHand;
        if (used == null) return false;
        int amount = fd.get("amount") instanceof Number ? ((Number) fd.get("amount")).intValue() : 1;
        if (amount <= 0) amount = 1;
        int count = countItemsMatchingKeyItem(player, fd);
        if (count < amount) return false;
        removeItemsMatchingKeyItem(player, fd, amount);
        Location triggerLoc = plugin.getLocationFromBlockKey(node.blockKey());
        if (triggerLoc != null && triggerLoc.getWorld() != null) {
            Block triggerBlock = triggerLoc.getBlock();
            if (triggerBlock.getType() != Material.AIR) triggerBlock.setType(Material.AIR, false);
        }
        Object lbObj = fd.get("linkedBlocks");
        if (lbObj instanceof List) {
            for (Object o : (List<?>) lbObj) {
                String k = o != null ? String.valueOf(o).trim() : "";
                if (k.isEmpty()) continue;
                Location loc = plugin.getLocationFromBlockKey(k);
                if (loc != null && loc.getWorld() != null) {
                    Block b = loc.getBlock();
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
            }
        }
        return true;
    }

    private static boolean itemMatchesKeyItem(ItemStack stack, Map<String, Object> fd) {
        if (stack == null || stack.getType().isAir()) return false;
        String materialName = fd != null && fd.get("keyItemMaterial") != null ? String.valueOf(fd.get("keyItemMaterial")).trim() : "";
        String displayName = fd != null && fd.get("keyItemDisplayName") != null ? String.valueOf(fd.get("keyItemDisplayName")).trim() : "";
        if (displayName.isEmpty() && fd != null && fd.get("itemDisplayName") != null) displayName = String.valueOf(fd.get("itemDisplayName")).trim();
        if (materialName.isEmpty() && displayName.isEmpty()) return false;
        if (!materialName.isEmpty() && !stack.getType().name().equalsIgnoreCase(materialName)) return false;
        if (!displayName.isEmpty()) {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return false;
            String normItem = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', meta.getDisplayName())).trim();
            String normStored = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName)).trim();
            if (!normItem.equalsIgnoreCase(normStored)) return false;
        }
        return true;
    }

    private static int countItemsMatchingKeyItem(Player player, Map<String, Object> fd) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (itemMatchesKeyItem(stack, fd)) total += stack.getAmount();
        }
        return total;
    }

    private static void removeItemsMatchingKeyItem(Player player, Map<String, Object> fd, int toRemove) {
        int remaining = toRemove;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            if (!itemMatchesKeyItem(stack, fd)) continue;
            int have = stack.getAmount();
            if (have <= remaining) {
                player.getInventory().setItem(i, null);
                remaining -= have;
            } else {
                stack.setAmount(have - remaining);
                remaining = 0;
            }
        }
    }

    public void handleScriptGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(SCRIPT_GUI_TITLE_PREFIX)) return;
        event.setCancelled(true);
        ScriptGuiContext ctx = scriptGuiContext.get(player.getUniqueId());
        if (ctx == null) return;
        if (event.getRawSlot() != event.getSlot()) return;
        int slot = event.getSlot();

        if (ctx.step == ScriptGuiStep.FUNCTION_TYPE) {
            if (slot == 9) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg != null) openScriptBlockMenuGui(player, ctx, cfg, ctx.node.blockKey());
                return;
            }
            ScriptFunctionType[] types = { ScriptFunctionType.SEND_MESSAGE, ScriptFunctionType.BROADCAST_MESSAGE, ScriptFunctionType.PLAY_SOUND, ScriptFunctionType.RUN_COMMAND, ScriptFunctionType.TELEPORT_PLAYER, ScriptFunctionType.PASS_THROUGH, ScriptFunctionType.LEAVE_CHALLENGE, ScriptFunctionType.SIGNAL_SENDER, ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR };
            if (slot >= 0 && slot < types.length) {
                ctx.node.functionType = types[slot];
                if (ctx.node.functionType == ScriptFunctionType.PASS_THROUGH) {
                    openScriptConfirmGui(player, ctx);
                } else if (ctx.node.functionType == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
                    ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                    if (cfg != null) {
                        ctx.node.functionData.putIfAbsent("keyItemMaterial", "");
                        ctx.node.functionData.putIfAbsent("keyItemDisplayName", "");
                        ctx.node.functionData.putIfAbsent("amount", 1);
                        ctx.node.functionData.putIfAbsent("linkedBlocks", new ArrayList<String>());
                        if (cfg.scriptNodes == null) cfg.scriptNodes = new ArrayList<>();
                        cfg.scriptNodes.add(ctx.node);
                        plugin.saveChallengeToConfig(cfg);
                        ctx.editingNodeIndex = cfg.scriptNodes.size() - 1;
                        player.sendMessage(ChatColor.GREEN + "Block set as remove-block (use item). Set key item and link blocks in Configure (or later via Edit).");
                    }
                    openScriptConfigureGui(player, ctx);
                } else {
                    openScriptTriggerGui(player, ctx);
                }
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.TRIGGER) {
            if (slot == 9) {
                openScriptFunctionTypeGui(player, ctx);
                return;
            }
            ScriptTrigger[] triggers = { ScriptTrigger.BLOCK_INTERACT, ScriptTrigger.BLOCK_BREAK, ScriptTrigger.BLOCK_PLACE, ScriptTrigger.PLAYER_ENTER_AREA, ScriptTrigger.MOB_DEATH, ScriptTrigger.SIGNAL_RECEIVER, ScriptTrigger.REDSTONE_RECEIVER, ScriptTrigger.AND_GATE, ScriptTrigger.OR_GATE };
            if (slot >= 0 && slot < triggers.length) {
                ctx.node.trigger = triggers[slot];
                if (ctx.node.functionType == ScriptFunctionType.LEAVE_CHALLENGE)
                    openScriptConfirmGui(player, ctx);
                else
                    openScriptConfigureGui(player, ctx);
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.CONFIGURE) {
            handleConfigureGuiClick(player, ctx, slot);
            return;
        }
        if (ctx.step == ScriptGuiStep.LINK_BLOCKS_MENU) {
            if (slot == 1) {
                ctx.step = ScriptGuiStep.LINK_BLOCKS_AWAIT_CLICK;
                scriptGuiContext.put(player.getUniqueId(), ctx);
                player.closeInventory();
                player.sendMessage(ChatColor.GRAY + "Right-click a block with the function tool to link it.");
            } else if (slot == 4) {
                openScriptConfigureGui(player, ctx);
            } else if (slot == 7) {
                Object lb = ctx.node.functionData.get("linkedBlocks");
                if (lb instanceof List && !((List<?>) lb).isEmpty()) {
                    ((List<?>) lb).remove(((List<?>) lb).size() - 1);
                }
                openScriptLinkBlocksMenuGui(player, ctx);
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.BLOCK_MENU) {
            ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
            String blockKey = ctx.node.blockKey();
            if (slot == 2) {
                openScriptFunctionTypeGui(player, ctx);
            } else if (slot == 6 && cfg != null) {
                openScriptEditListGui(player, ctx, cfg, blockKey);
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.EDIT_LIST) {
            if (slot == 8) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg != null) openScriptBlockMenuGui(player, ctx, cfg, ctx.node.blockKey());
                return;
            }
            if (slot >= 0 && slot < ctx.blockEditList.size()) {
                openScriptEditOrRemoveGui(player, ctx, slot);
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.EDIT_OR_REMOVE) {
            ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
            if (cfg == null) return;
            int listIdx = ctx.editListClickedIndex;
            int value = ctx.blockEditList.get(listIdx);
            if (slot == 8) {
                openScriptEditListGui(player, ctx, cfg, ctx.node.blockKey());
                return;
            }
            if (value == -1) {
                if (slot == 2) openScriptRemoveConfirmGui(player, ctx);
                return;
            }
            if (slot == 0) {
                ChallengeScriptNode existing = cfg.scriptNodes.get(value);
                ctx.node.trigger = existing.trigger;
                ctx.node.functionType = existing.functionType;
                ctx.node.functionData.clear();
                ctx.node.functionData.putAll(existing.functionData);
                ctx.node.conditions.clear();
                if (existing.conditions != null) ctx.node.conditions.addAll(existing.conditions);
                ctx.editingNodeIndex = value;
                if (ctx.node.functionType == ScriptFunctionType.PASS_THROUGH) {
                    openScriptConfirmGui(player, ctx);
                } else if (ctx.node.functionType == ScriptFunctionType.LEAVE_CHALLENGE) {
                    openScriptConfirmGui(player, ctx);
                } else if (ctx.node.functionType == ScriptFunctionType.REMOVE_BLOCK_WHEN_ITEM_NEAR) {
                    openScriptConfigureGui(player, ctx);
                } else {
                    openScriptConfigureGui(player, ctx);
                }
            } else if (slot == 4) {
                openScriptRemoveConfirmGui(player, ctx);
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.REMOVE_CONFIRM) {
            if (slot == 6) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg != null) openScriptEditListGui(player, ctx, cfg, ctx.node.blockKey());
                return;
            }
            if (slot == 2) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg == null) return;
                int value = ctx.blockEditList.get(ctx.editListClickedIndex);
                if (value == -1) {
                    if (cfg.passThroughBlockKeys != null) cfg.passThroughBlockKeys.remove(ctx.node.blockKey());
                    plugin.saveChallengeToConfig(cfg);
                    player.sendMessage(ChatColor.GREEN + "Pass-through removed from this block.");
                } else {
                    cfg.scriptNodes.remove(value);
                    plugin.saveChallengeToConfig(cfg);
                    player.sendMessage(ChatColor.GREEN + "Script removed.");
                }
                scriptGuiContext.remove(player.getUniqueId());
                player.closeInventory();
            }
            return;
        }
        if (ctx.step == ScriptGuiStep.CONFIRM) {
            if (slot == 4) {
                openScriptConfigureGui(player, ctx);
                return;
            }
            if (slot == 3) {
                ConradChallengesPlugin.ChallengeConfig cfg = plugin.getChallenges().get(ctx.challengeId);
                if (cfg != null && ctx.node.functionType == ScriptFunctionType.PASS_THROUGH) {
                    if (cfg.passThroughBlockKeys != null) {
                        cfg.passThroughBlockKeys.add(ctx.node.blockKey());
                    } else {
                        cfg.passThroughBlockKeys = new java.util.HashSet<>();
                        cfg.passThroughBlockKeys.add(ctx.node.blockKey());
                    }
                    plugin.saveChallengeToConfig(cfg);
                    player.sendMessage(ChatColor.GREEN + "Block set as pass-through (players can walk through when near).");
                    scriptGuiContext.remove(player.getUniqueId());
                    player.closeInventory();
                    return;
                }
                // Only fill default functionData when adding a new script, not when editing
                if (ctx.editingNodeIndex < 0) {
                    switch (ctx.node.functionType) {
                        case SEND_MESSAGE:
                        case BROADCAST_MESSAGE:
                            ctx.node.functionData.putIfAbsent("message", "");
                            break;
                        case PLAY_SOUND:
                            ctx.node.functionData.putIfAbsent("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
                            ctx.node.functionData.putIfAbsent("volume", 1.0);
                            ctx.node.functionData.putIfAbsent("pitch", 1.0);
                            break;
                        case RUN_COMMAND:
                            ctx.node.functionData.putIfAbsent("command", "");
                            break;
                        case TELEPORT_PLAYER:
                            ctx.node.functionData.putIfAbsent("useDestination", true);
                            break;
                        case PASS_THROUGH:
                        case LEAVE_CHALLENGE:
                            break;
                        case SIGNAL_SENDER:
                            ctx.node.functionData.putIfAbsent("signal", "");
                            break;
                        case REMOVE_BLOCK_WHEN_ITEM_NEAR:
                            ctx.node.functionData.putIfAbsent("keyItemMaterial", "");
                            ctx.node.functionData.putIfAbsent("keyItemDisplayName", "");
                            ctx.node.functionData.putIfAbsent("amount", 1);
                            ctx.node.functionData.putIfAbsent("linkedBlocks", new ArrayList<String>());
                            break;
                    }
                    if (ctx.node.trigger == ScriptTrigger.SIGNAL_RECEIVER)
                        ctx.node.functionData.putIfAbsent("signal", "");
                    if (ctx.node.trigger == ScriptTrigger.PLAYER_ENTER_AREA)
                        ctx.node.functionData.putIfAbsent("enterAreaDistance", 3);
                    if (ctx.node.trigger == ScriptTrigger.AND_GATE || ctx.node.trigger == ScriptTrigger.OR_GATE)
                        ctx.node.functionData.putIfAbsent("dependencies", new ArrayList<String>());
                }
                if (cfg != null && cfg.scriptNodes != null) {
                    if (ctx.editingNodeIndex >= 0 && ctx.editingNodeIndex < cfg.scriptNodes.size()) {
                        ChallengeScriptNode existing = cfg.scriptNodes.get(ctx.editingNodeIndex);
                        existing.trigger = ctx.node.trigger;
                        existing.functionType = ctx.node.functionType;
                        // Copy into new map first: for REMOVE_BLOCK we add ctx.node to the list, so existing may be the same object as ctx.node and clear() would wipe the only copy
                        Map<String, Object> functionDataCopy = new HashMap<>(ctx.node.functionData);
                        existing.functionData.clear();
                        existing.functionData.putAll(functionDataCopy);
                        List<Map<String, Object>> conditionsCopy = ctx.node.conditions != null ? new ArrayList<>(ctx.node.conditions) : new ArrayList<>();
                        existing.conditions.clear();
                        existing.conditions.addAll(conditionsCopy);
                        plugin.saveChallengeToConfig(cfg);
                        player.sendMessage(ChatColor.GREEN + "Script updated.");
                    } else {
                        cfg.scriptNodes.add(ctx.node);
                        plugin.saveChallengeToConfig(cfg);
                        player.sendMessage(ChatColor.GREEN + "Script added at this block.");
                    }
                }
                scriptGuiContext.remove(player.getUniqueId());
                player.closeInventory();
            } else if (slot == 5) {
                scriptGuiContext.remove(player.getUniqueId());
                player.closeInventory();
            }
        }
    }
}
