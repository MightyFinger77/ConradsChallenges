package com.gix.conradchallenges.invasion;

import com.gix.conradchallenges.ConradChallengesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class InvasionCommand implements CommandExecutor, TabCompleter {

    private final ConradChallengesPlugin plugin;
    private final InvasionManager manager;

    public InvasionCommand(ConradChallengesPlugin plugin, InvasionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if (sub.equals("setitem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("invasion.admin-players-only"));
                return true;
            }
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(plugin.getMessage("invasion.admin-no-permission"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(plugin.getMessage("invasion.admin-hold-crystal"));
                return true;
            }
            FileConfiguration config = plugin.getConfig();
            saveItemToConfig(config, "crystal", hand);
            plugin.saveConfig();
            manager.reloadConfig(new InvasionConfig(plugin.getConfig()));
            player.sendMessage(plugin.getMessage("invasion.admin-crystal-set", "material", hand.getType().name(), "name", ""));
            return true;
        }

        if (sub.equals("setrtp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("invasion.admin-players-only"));
                return true;
            }
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(plugin.getMessage("invasion.admin-no-permission"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(plugin.getMessage("invasion.admin-hold-rtp"));
                return true;
            }
            FileConfiguration config = plugin.getConfig();
            saveItemToConfig(config, "rtp", hand);
            plugin.saveConfig();
            manager.reloadConfig(new InvasionConfig(plugin.getConfig()));
            player.sendMessage(plugin.getMessage("invasion.admin-rtp-set", "material", hand.getType().name(), "name", ""));
            return true;
        }

        if (sub.equals("setnpc")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("invasion.admin-players-only"));
                return true;
            }
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(plugin.getMessage("invasion.admin-no-permission"));
                return true;
            }
            Entity target = plugin.getTargetEntity(player, 10.0);
            if (target == null) {
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-setnpc-look"));
                return true;
            }
            if (plugin.addBloodcallerNpc(target.getUniqueId())) {
                plugin.setBloodcallerDisplayName(target);
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-setnpc-success"));
            } else {
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-setnpc-already"));
            }
            return true;
        }

        if (sub.equals("removenpc")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("invasion.admin-players-only"));
                return true;
            }
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(plugin.getMessage("invasion.admin-no-permission"));
                return true;
            }
            Entity target = plugin.getTargetEntity(player, 10.0);
            if (target == null) {
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-removenpc-look"));
                return true;
            }
            if (plugin.removeBloodcallerNpc(target.getUniqueId())) {
                plugin.clearBloodcallerDisplayName(target);
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-removenpc-success"));
            } else {
                player.sendMessage(plugin.getMessage("invasion.bloodcaller-removenpc-not"));
            }
            return true;
        }

        if (sub.equals("rtp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("invasion.admin-players-only"));
                return true;
            }
            if (!player.hasPermission("conradchallenges.admin")) {
                player.sendMessage(plugin.getMessage("invasion.admin-no-permission"));
                return true;
            }
            String challengeId = plugin.getPlayerEditingChallenge(player);
            if (challengeId == null) {
                player.sendMessage(plugin.getMessage("invasion.rtp-spot-edit-mode"));
                return true;
            }
            if (!plugin.hasChallenge(challengeId)) {
                player.sendMessage(plugin.getMessage("challenge.unknown-id", "id", challengeId));
                return true;
            }
            plugin.addInvasionRtpSpot(challengeId, player.getLocation());
            player.sendMessage(plugin.getMessage("invasion.rtp-spot-added", "id", challengeId));
            return true;
        }

        sender.sendMessage(plugin.getMessage("invasion.admin-usage"));
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> out = new ArrayList<>();
            String p = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            if ("setitem".startsWith(p)) out.add("setitem");
            if ("setrtp".startsWith(p)) out.add("setrtp");
            if ("setnpc".startsWith(p)) out.add("setnpc");
            if ("removenpc".startsWith(p)) out.add("removenpc");
            if ("rtp".startsWith(p)) out.add("rtp");
            return out;
        }
        return Collections.emptyList();
    }

    /** Save full item to config under the given base path (e.g. stuck-crystal. or invasions.crystal-). */
    private void saveItemToConfigBase(FileConfiguration config, String basePath, ItemStack hand) {
        String base = (basePath.endsWith(".") || basePath.endsWith("-")) ? basePath : basePath + "-";
        config.set(base + "material", hand.getType().name());
        ItemMeta meta = hand.getItemMeta();
        if (meta != null) {
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : "";
            config.set(base + "display-name", displayName.replace('§', '&'));
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                List<String> loreConfig = new ArrayList<>(lore.size());
                for (String line : lore) loreConfig.add(line.replace('§', '&'));
                config.set(base + "lore", loreConfig);
            } else {
                config.set(base + "lore", new ArrayList<String>());
            }
            if (meta.hasCustomModelData()) {
                config.set(base + "custom-model-data", meta.getCustomModelData());
            } else {
                config.set(base + "custom-model-data", null);
            }
        } else {
            config.set(base + "display-name", "");
            config.set(base + "lore", new ArrayList<String>());
            config.set(base + "custom-model-data", null);
        }
    }

    /** Save full item (material, display name, lore, custom model data) to config under invasions.{prefix}-*. */
    private void saveItemToConfig(FileConfiguration config, String prefix, ItemStack hand) {
        saveItemToConfigBase(config, "invasions." + prefix, hand);
    }
}
