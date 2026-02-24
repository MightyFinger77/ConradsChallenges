package com.gix.conradchallenges.invasion;

import com.gix.conradchallenges.ConradChallengesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class InvasionListener implements Listener {

    private final ConradChallengesPlugin plugin;
    private final InvasionManager manager;

    public InvasionListener(ConradChallengesPlugin plugin, InvasionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRtpItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;
        if (manager.getConfig() == null || !manager.getConfig().isRtpItem(hand)) return;
        if (!manager.isInvader(player)) return;
        event.setCancelled(true);
        if (manager.isRtpDelayActive(player)) {
            player.sendMessage(plugin.getMessage("invasion.rtp-already"));
            return;
        }
        if (!manager.canUseRtp(player)) {
            player.sendMessage(plugin.getMessage("invasion.rtp-pvp-blocked", "seconds", String.valueOf(manager.getConfig().getPvpCooldownSeconds())));
            return;
        }
        final String challengeId = manager.getInvaderChallengeId(player);
        if (challengeId == null) return;
        plugin.openRtpConfirmGui(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) return;
        if (event.getEntity() instanceof Player victim) {
            manager.recordPvp(victim);
            manager.cancelRtpDelay(victim);
        }
        if (event.getDamager() instanceof Player attacker) {
            manager.recordPvp(attacker);
            manager.cancelRtpDelay(attacker);
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player shooter) {
            manager.recordPvp(shooter);
            manager.cancelRtpDelay(shooter);
        }
    }

    /** Mobs ignore invaders: prevent mobs from targeting players who are invading. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!manager.isInvader(player)) return;
        event.setCancelled(true);
    }

    /** Invaders cannot open chests and get no chest loot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvaderOpenChest(InventoryOpenEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.isInvader(player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Chest) && !(holder instanceof DoubleChest)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessage("invasion.cannot-open-chest"));
    }
}
