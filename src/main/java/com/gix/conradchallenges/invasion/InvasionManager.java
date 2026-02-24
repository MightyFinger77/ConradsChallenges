package com.gix.conradchallenges.invasion;

import com.gix.conradchallenges.ConradChallengesPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Souls-like invasions: crystal on GateKeeper, /invade (solo) or /invadeparty (countdown + /accept),
 * invader limits per challenge, safe teleport into regen area 1, RTP item with delay and PvP cooldown.
 */
public final class InvasionManager {

    private final ConradChallengesPlugin plugin;
    private InvasionConfig config;

    /** Player who used crystal on GateKeeper and can now /invade or /invadeparty. */
    private UUID pendingSoloInvader = null;
    /** Party invasion in countdown: leader -> state. */
    private PendingPartyInvasion pendingPartyInvasion = null;
    /** challengeId -> list of invaders (uuid + return location). */
    private final Map<String, List<InvaderEntry>> activeInvadersByChallenge = new HashMap<>();
    /** invader UUID -> challengeId (quick lookup). */
    private final Map<UUID, String> invaderToChallenge = new HashMap<>();
    /** Last time player was in PvP (dealt or took damage) for RTP cooldown. */
    private final Map<UUID, Long> lastPvpTime = new ConcurrentHashMap<>();
    /** Scheduled RTP teleport tasks (cancel on damage or move). */
    private final Map<UUID, BukkitTask> rtpScheduledTasks = new HashMap<>();
    /** Invader UUID -> pending reward to claim at Bloodcaller (after victory loot phase). */
    private final Map<UUID, PendingInvasionReward> pendingInvasionRewards = new HashMap<>();
    /** Challenge ID -> victory loot phase task (to cancel if challenge is cleaned up). */
    private final Map<String, BukkitTask> victoryLootTasks = new HashMap<>();

    public InvasionManager(ConradChallengesPlugin plugin, InvasionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reloadConfig(InvasionConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public InvasionConfig getConfig() {
        return config;
    }

    // --- Pending solo: use crystal on GateKeeper ---

    public void setPendingSoloInvader(Player player) {
        pendingSoloInvader = player.getUniqueId();
    }

    public boolean isPendingSoloInvader(Player player) {
        return player != null && player.getUniqueId().equals(pendingSoloInvader);
    }

    public void clearPendingSoloInvader() {
        pendingSoloInvader = null;
    }

    // --- Invader state ---

    public boolean isInvader(Player player) {
        return player != null && invaderToChallenge.containsKey(player.getUniqueId());
    }

    public String getInvaderChallengeId(Player player) {
        return player != null ? invaderToChallenge.get(player.getUniqueId()) : null;
    }

    public int getInvaderCount(String challengeId) {
        List<InvaderEntry> list = activeInvadersByChallenge.get(challengeId);
        return list != null ? list.size() : 0;
    }

    /** Who to target: any challenge, solo challengers only, or party challenges only. */
    public enum InvasionTarget {
        /** Any challenge (solo or party). */
        ANY,
        /** Only challenges with exactly one challenger. */
        SOLO_ONLY,
        /** Only challenges with 2+ challengers. */
        PARTY_ONLY
    }

    /** Get challenges that match the target and have room for at least one more invader. */
    public List<String> getInvadableChallenges(InvasionTarget target) {
        Map<UUID, String> active = plugin.getActiveChallenges();
        if (active == null || active.isEmpty()) return Collections.emptyList();
        Map<String, Long> challengerCountByChallenge = new HashMap<>();
        for (String cid : active.values()) {
            challengerCountByChallenge.merge(cid, 1L, Long::sum);
        }
        List<String> out = new ArrayList<>();
        for (String challengeId : challengerCountByChallenge.keySet()) {
            long challengers = challengerCountByChallenge.get(challengeId);
            if (target == InvasionTarget.SOLO_ONLY && challengers != 1) continue;
            if (target == InvasionTarget.PARTY_ONLY && challengers < 2) continue;
            int max = (challengers >= 2) ? config.getMaxInvadersPartyChallenge() : config.getMaxInvadersSoloChallenge();
            int current = getInvaderCount(challengeId);
            if (current < max) out.add(challengeId);
        }
        return out;
    }

    /** Pick a random invadable challenge for the given target. */
    public String pickRandomInvadableChallenge(InvasionTarget target, Random random) {
        List<String> list = getInvadableChallenges(target);
        if (list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    // --- Crystal consume ---

    /** Remove one invasion crystal from player inventory. Returns true if removed. */
    public boolean consumeOneCrystal(Player player) {
        if (player == null || config == null) return false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (config.isCrystalItem(stack)) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    /** Check if player has at least one crystal. */
    public boolean hasCrystal(Player player) {
        if (player == null || config == null) return false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && config.isCrystalItem(stack)) return true;
        }
        return false;
    }

    // --- Bloodcaller crystal use ---

    public void onBloodcallerCrystalUse(Player player) {
        if (!isEnabled() || config == null) return;
        if (!hasCrystal(player)) return;
        setPendingSoloInvader(player);
        player.sendMessage(plugin.getMessage("invasion.crystal-gatekeeper-hint"));
    }

    // --- Solo invasion ---

    /**
     * Start a solo invasion. Target: ANY = any challenge, SOLO_ONLY = only challenges with one challenger.
     */
    public void startSoloInvasion(Player player, InvasionTarget target) {
        if (!isEnabled() || config == null) return;
        if (!isPendingSoloInvader(player)) {
            player.sendMessage(plugin.getMessage("invasion.use-crystal-first"));
            return;
        }
        // Check for invadable challenges before consuming crystal; clean up if none
        String challengeId = pickRandomInvadableChallenge(target, new Random());
        if (challengeId == null) {
            player.sendMessage(plugin.getMessage("invasion.no-active-challenges"));
            clearPendingSoloInvader();
            return;
        }
        if (!consumeOneCrystal(player)) {
            player.sendMessage(plugin.getMessage("invasion.need-crystal"));
            return;
        }
        clearPendingSoloInvader();
        Location returnLoc = player.getLocation().clone();
        Location safe = findSafeSpotInChallenge(challengeId, Collections.emptyList());
        if (safe == null) {
            player.sendMessage(plugin.getMessage("invasion.no-safe-spot"));
            return;
        }
        int invaderCountBefore = getInvaderCount(challengeId);
        addInvader(player.getUniqueId(), challengeId, returnLoc);
        player.teleport(safe);
        giveRtpItem(player);
        player.sendMessage(plugin.getMessage("invasion.invaded"));
        List<UUID> uuids = Collections.singletonList(player.getUniqueId());
        List<String> names = Collections.singletonList(player.getName());
        sendInvasionTitles(challengeId, uuids, names, false, invaderCountBefore);
    }

    private void addInvader(UUID uuid, String challengeId, Location returnLoc) {
        activeInvadersByChallenge.computeIfAbsent(challengeId, k -> new ArrayList<>()).add(new InvaderEntry(uuid, returnLoc));
        invaderToChallenge.put(uuid, challengeId);
    }

    private Location findSafeSpotInChallenge(String challengeId, List<Location> avoidNear) {
        List<Location> manualSpots = plugin.getInvasionRtpSpots(challengeId);
        if (manualSpots != null && !manualSpots.isEmpty()) {
            Random r = new Random();
            List<Location> valid = new ArrayList<>();
            for (Location loc : manualSpots) {
                if (loc == null || loc.getWorld() == null) continue;
                boolean tooClose = false;
                for (Location o : avoidNear) {
                    if (o != null && o.getWorld() != null && o.getWorld().equals(loc.getWorld()) && o.distanceSquared(loc) < 25) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) valid.add(loc);
            }
            if (!valid.isEmpty()) return valid.get(r.nextInt(valid.size()));
            return manualSpots.get(r.nextInt(manualSpots.size()));
        }
        Random r = new Random();
        // 1) Spawn in the same challenge area as the party/challenge leader
        InvasionRegenBounds bounds = plugin.getChallengeAreaBoundsContainingLeader(challengeId);
        if (bounds != null) {
            Location loc = findSafeInBounds(bounds, avoidNear, r);
            if (loc != null) return loc;
        }
        // 2) No challenge areas or leader not in one: use challenge destination (small box around it)
        Location dest = plugin.getDestinationForChallenge(challengeId);
        if (dest != null && dest.getWorld() != null) {
            int radius = 8;
            int minX = dest.getBlockX() - radius;
            int maxX = dest.getBlockX() + radius;
            int minY = dest.getWorld().getMinHeight();
            int maxY = dest.getWorld().getMaxHeight() - 1;
            int minZ = dest.getBlockZ() - radius;
            int maxZ = dest.getBlockZ() + radius;
            Location loc = InvasionSafeTeleport.findSafeLocationInBox(
                    dest.getWorld(), minX, maxX, minY, maxY, minZ, maxZ, r);
            if (loc != null) return loc;
            // No safe block in box; use destination itself (invader may need to move)
            return dest.clone();
        }
        // 3) Fallback: regen area 1
        bounds = plugin.getRegenArea1BoundsForInvasion(challengeId);
        if (bounds == null) return null;
        Location loc = findSafeInBounds(bounds, avoidNear, r);
        return loc != null ? loc : InvasionSafeTeleport.findSafeLocationInBox(
                bounds.getWorld(), bounds.getMinX(), bounds.getMaxX(),
                bounds.getMinY(), bounds.getMaxY(), bounds.getMinZ(), bounds.getMaxZ(), r);
    }

    /** Used by plugin for RTP item: find safe spot (leader's area, then destination, then regen). */
    public Location findSafeSpotForInvader(String challengeId) {
        return findSafeSpotInChallenge(challengeId, Collections.emptyList());
    }

    private Location findSafeInBounds(InvasionRegenBounds bounds, List<Location> avoidNear, Random r) {
        for (int attempt = 0; attempt < 60; attempt++) {
            Location loc = InvasionSafeTeleport.findSafeLocationInBox(
                    bounds.getWorld(), bounds.getMinX(), bounds.getMaxX(),
                    bounds.getMinY(), bounds.getMaxY(), bounds.getMinZ(), bounds.getMaxZ(), r);
            if (loc == null) continue;
            boolean tooClose = false;
            for (Location o : avoidNear) {
                if (o != null && o.getWorld() != null && o.getWorld().equals(loc.getWorld()) && o.distanceSquared(loc) < 25) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) return loc;
        }
        return null;
    }

    public void giveRtpItem(Player player) {
        if (config == null) return;
        ItemStack rtp = config.createRtpItem();
        if (rtp != null) player.getInventory().addItem(rtp);
    }

    public void removeRtpItem(Player player) {
        if (config == null) return;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (config.isRtpItem(stack)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /** Gives the stuck crystal to a challenger (call when they enter the challenge). No-op if not configured. */
    public void giveStuckCrystal(Player player) {
        if (config == null) return;
        ItemStack stuck = config.createStuckCrystalItem();
        if (stuck != null) player.getInventory().addItem(stuck);
    }

    /** Removes all stuck crystals from a challenger's inventory (call when they exit or complete the challenge). Checks main inventory and offhand. */
    public void removeStuckCrystal(Player player) {
        if (config == null) return;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (config.isStuckCrystalItem(stack)) {
                player.getInventory().setItem(i, null);
            }
        }
        if (config.isStuckCrystalItem(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    // --- Party invasion ---

    public void startPartyInvasion(Player leader) {
        if (!isEnabled() || config == null) return;
        if (!isPendingSoloInvader(leader)) {
            leader.sendMessage(plugin.getMessage("invasion.use-crystal-first"));
            return;
        }
        String challengeId = pickRandomInvadableChallenge(InvasionTarget.PARTY_ONLY, new Random());
        if (challengeId == null) {
            leader.sendMessage(plugin.getMessage("invasion.no-active-challenges"));
            clearPendingSoloInvader();
            return;
        }
        clearPendingSoloInvader();
        Set<UUID> accepted = new HashSet<>();
        accepted.add(leader.getUniqueId());
        int seconds = config.getPartyCountdownSeconds();
        leader.sendMessage(plugin.getMessage("invasion.party-countdown", "seconds", String.valueOf(seconds), "max", String.valueOf(config.getMaxInvadersPartyChallenge())));
        pendingPartyInvasion = new PendingPartyInvasion(leader.getUniqueId(), challengeId, accepted,
                Bukkit.getScheduler().runTaskLater(plugin, () -> executePartyInvasionTeleport(), seconds * 20L));
    }

    public boolean acceptPartyInvasion(Player player) {
        if (pendingPartyInvasion == null) return false;
        if (pendingPartyInvasion.accepted.size() >= config.getMaxInvadersPartyChallenge()) return false;
        if (pendingPartyInvasion.accepted.contains(player.getUniqueId())) return false;
        Player leader = Bukkit.getPlayer(pendingPartyInvasion.leaderUuid);
        if (leader == null || !leader.isOnline()) return false;
        if (leader.getWorld() != player.getWorld() || leader.getLocation().distanceSquared(player.getLocation()) > config.getPartyAcceptRadius() * config.getPartyAcceptRadius()) return false;
        if (!hasCrystal(player)) return false;
        pendingPartyInvasion.accepted.add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("invasion.joined-party"));
        return true;
    }

    private void executePartyInvasionTeleport() {
        if (pendingPartyInvasion == null) return;
        PendingPartyInvasion pp = pendingPartyInvasion;
        pendingPartyInvasion = null;
        String challengeId = pp.challengeId;
        // If challenge no longer active (e.g. ended during countdown), don't send anyone or consume crystals
        List<String> invadable = getInvadableChallenges(InvasionTarget.PARTY_ONLY);
        if (invadable == null || !invadable.contains(challengeId)) {
            String msg = plugin.getMessage("invasion.no-active-challenges");
            for (UUID uuid : pp.accepted) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) p.sendMessage(msg);
            }
            return;
        }
        int invaderCountBefore = getInvaderCount(challengeId);
        List<Location> usedSpots = new ArrayList<>();
        List<UUID> addedUuids = new ArrayList<>();
        List<String> addedNames = new ArrayList<>();
        Random r = new Random();
        for (UUID uuid : pp.accepted) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (!consumeOneCrystal(p)) continue;
            Location returnLoc = p.getLocation().clone();
            Location safe = findSafeSpotInChallenge(challengeId, usedSpots);
            if (safe != null) usedSpots.add(safe);
            if (safe == null) {
                p.sendMessage(plugin.getMessage("invasion.no-safe-spot-short"));
                continue;
            }
            addInvader(uuid, challengeId, returnLoc);
            p.teleport(safe);
            giveRtpItem(p);
            p.sendMessage(plugin.getMessage("invasion.invaded"));
            addedUuids.add(uuid);
            addedNames.add(p.getName());
        }
        if (addedUuids.isEmpty()) return;
        sendInvasionTitles(challengeId, addedUuids, addedNames, true, invaderCountBefore);
    }

    /**
     * Sends title/subtitle to challengers and invaders. %partyleader% is always the challenger who started the challenge/countdown.
     * If there were already invaders, also sends "X has joined the fight!" per new invader (actionbar or subtitle per config).
     */
    private void sendInvasionTitles(String challengeId, List<UUID> newInvaderUuids, List<String> newInvaderNames, boolean multipleInvaders, int invaderCountBefore) {
        Map<UUID, String> active = plugin.getActiveChallenges();
        if (active == null || newInvaderUuids.isEmpty()) return;
        List<UUID> challengerUuids = new ArrayList<>();
        for (Map.Entry<UUID, String> e : active.entrySet()) {
            if (challengeId.equals(e.getValue())) challengerUuids.add(e.getKey());
        }
        String partyleaderName = plugin.getChallengeLeaderName(challengeId);
        if (partyleaderName == null) partyleaderName = "Someone";
        String challengerTitle = plugin.getMessage("invasion.challenger-title");
        String challengerSubtitle = multipleInvaders
                ? plugin.getMessage("invasion.challenger-subtitle-multiple")
                : plugin.getMessage("invasion.challenger-subtitle-single", "invader", newInvaderNames.isEmpty() ? "Invader" : newInvaderNames.get(0));
        String invaderTitle = plugin.getMessage("invasion.invader-title");
        String invaderSubtitle = plugin.getMessage("invasion.invader-subtitle", "partyleader", partyleaderName);
        int fadeIn = 10, stay = 70, fadeOut = 20;
        for (UUID uuid : challengerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendTitle(challengerTitle, challengerSubtitle, fadeIn, stay, fadeOut);
        }
        String joinMode = config != null ? config.getNewInvaderJoinMessage() : "subtitle";
        if (invaderCountBefore > 0 && !joinMode.equals("none")) {
            for (String invaderName : newInvaderNames) {
                String joinMsg = plugin.getMessage("invasion.new-invader-join", "invader", invaderName);
                for (UUID uuid : challengerUuids) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;
                    if (joinMode.equals("actionbar")) {
                        try {
                            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(joinMsg));
                        } catch (Exception ignored) {
                            p.sendTitle("", joinMsg, 0, 40, 10);
                        }
                    } else {
                        p.sendTitle("", joinMsg, 0, 40, 10);
                    }
                }
            }
        }
        for (UUID uuid : newInvaderUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendTitle(invaderTitle, invaderSubtitle, fadeIn, stay, fadeOut);
        }
    }

    public boolean hasPendingPartyInvasion() {
        return pendingPartyInvasion != null;
    }

    public boolean isInPendingPartyInvasion(Player player) {
        return pendingPartyInvasion != null && pendingPartyInvasion.accepted.contains(player.getUniqueId());
    }

    // --- Invader victory: all challengers dead ---

    /**
     * Called when all challengers in a challenge have exhausted lives. Invaders get victoryLootSeconds to loot, then are teleported back and must redeem at Bloodcaller.
     */
    public void onChallengersAllDead(String challengeId, String difficultyTier) {
        if (config == null) return;
        List<InvaderEntry> list = activeInvadersByChallenge.get(challengeId);
        if (list == null || list.isEmpty()) return;
        int seconds = config.getVictoryLootSeconds();
        String msg = plugin.getMessage("invasion.victory-loot-phase", "seconds", String.valueOf(seconds));
        for (InvaderEntry e : list) {
            Player p = Bukkit.getPlayer(e.uuid);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                victoryLootTasks.remove(challengeId);
                List<InvaderEntry> invaders = activeInvadersByChallenge.remove(challengeId);
                if (invaders == null) return;
                Location spawn = plugin.getSpawnLocation();
                String redeemMsg = plugin.getMessage("invasion.return-redeem-at-bloodcaller");
                for (InvaderEntry e : invaders) {
                    invaderToChallenge.remove(e.uuid);
                    pendingInvasionRewards.put(e.uuid, new PendingInvasionReward(challengeId, difficultyTier));
                    Player p = Bukkit.getPlayer(e.uuid);
                    if (p != null && p.isOnline()) {
                        removeRtpItem(p);
                        cancelRtpDelay(p);
                        if (e.returnLocation != null && e.returnLocation.getWorld() != null) {
                            p.teleport(e.returnLocation);
                        } else if (spawn != null) {
                            p.teleport(spawn);
                        }
                        p.sendMessage(redeemMsg);
                    }
                }
            }
        }.runTaskLater(plugin, seconds * 20L);
        victoryLootTasks.put(challengeId, task);
    }

    public boolean hasPendingInvasionRewards(Player player) {
        return player != null && pendingInvasionRewards.containsKey(player.getUniqueId());
    }

    /**
     * Redeem pending invasion rewards at the Bloodcaller. Caller should warn about inventory space first.
     */
    public void redeemInvasionRewards(Player player) {
        if (player == null) return;
        PendingInvasionReward pending = pendingInvasionRewards.remove(player.getUniqueId());
        if (pending == null) return;
        plugin.runRewardCommandsForInvader(player, pending.challengeId, pending.difficultyTier);
    }

    // --- Remove invaders (on challenge end or /exit) ---

    public void removeInvadersFromChallenge(String challengeId) {
        BukkitTask victoryTask = victoryLootTasks.remove(challengeId);
        if (victoryTask != null) victoryTask.cancel();
        List<InvaderEntry> list = activeInvadersByChallenge.remove(challengeId);
        if (list == null) return;
        Location spawn = plugin.getSpawnLocation();
        for (InvaderEntry e : list) {
            invaderToChallenge.remove(e.uuid);
            Player p = Bukkit.getPlayer(e.uuid);
            if (p != null && p.isOnline()) {
                removeRtpItem(p);
                cancelRtpDelay(p);
                if (e.returnLocation != null && e.returnLocation.getWorld() != null) {
                    p.teleport(e.returnLocation);
                } else if (spawn != null) {
                    p.teleport(spawn);
                }
                p.sendMessage(plugin.getMessage("invasion.challenge-ended"));
            }
        }
    }

    public void exitInvader(Player player) {
        String challengeId = invaderToChallenge.get(player.getUniqueId());
        if (challengeId == null) return;
        List<InvaderEntry> list = activeInvadersByChallenge.get(challengeId);
        Location returnLoc = null;
        if (list != null) {
            for (InvaderEntry e : list) {
                if (e.uuid.equals(player.getUniqueId())) {
                    returnLoc = e.returnLocation;
                    break;
                }
            }
            list.removeIf(e -> e.uuid.equals(player.getUniqueId()));
            if (list.isEmpty()) activeInvadersByChallenge.remove(challengeId);
        }
        invaderToChallenge.remove(player.getUniqueId());
        removeRtpItem(player);
        cancelRtpDelay(player);
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleport(returnLoc);
        } else if (plugin.getSpawnLocation() != null) {
            player.teleport(plugin.getSpawnLocation());
        }
        player.sendMessage(plugin.getMessage("invasion.left-invasion"));
    }


    // --- RTP item: delay and PvP cooldown ---

    public boolean canUseRtp(Player player) {
        if (!isInvader(player)) return false;
        Long last = lastPvpTime.get(player.getUniqueId());
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= config.getPvpCooldownSeconds() * 1000L;
    }

    public void recordPvp(Player player) {
        if (player != null) lastPvpTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void startRtpDelay(Player player, Runnable onComplete) {
        cancelRtpDelay(player);
        int delayTicks = config.getRtpDelaySeconds() * 20;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                rtpScheduledTasks.remove(player.getUniqueId());
                if (player.isOnline()) onComplete.run();
            }
        }.runTaskLater(plugin, delayTicks);
        rtpScheduledTasks.put(player.getUniqueId(), task);
    }

    public void cancelRtpDelay(Player player) {
        BukkitTask t = rtpScheduledTasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }

    public boolean isRtpDelayActive(Player player) {
        return rtpScheduledTasks.containsKey(player.getUniqueId());
    }

    // --- Inner types ---

    private static final class InvaderEntry {
        final UUID uuid;
        final Location returnLocation;
        InvaderEntry(UUID uuid, Location returnLocation) {
            this.uuid = uuid;
            this.returnLocation = returnLocation;
        }
    }

    private static final class PendingPartyInvasion {
        final UUID leaderUuid;
        final String challengeId;
        final Set<UUID> accepted;
        final BukkitTask countdownTask;
        PendingPartyInvasion(UUID leaderUuid, String challengeId, Set<UUID> accepted, BukkitTask countdownTask) {
            this.leaderUuid = leaderUuid;
            this.challengeId = challengeId;
            this.accepted = accepted;
            this.countdownTask = countdownTask;
        }
    }

    private static final class PendingInvasionReward {
        final String challengeId;
        final String difficultyTier;
        PendingInvasionReward(String challengeId, String difficultyTier) {
            this.challengeId = challengeId;
            this.difficultyTier = difficultyTier;
        }
    }
}
