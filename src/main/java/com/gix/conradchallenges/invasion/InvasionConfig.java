package com.gix.conradchallenges.invasion;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loaded invasion settings and item definitions (crystal, RTP item).
 * Items support full identity: material, display name, lore, custom model data (resource pack models).
 * Crystal and RTP items remain unset (blank) until configured via /invader setitem and setrtp.
 */
public final class InvasionConfig {

    private final boolean enabled;
    private final int partyCountdownSeconds;
    private final int maxInvadersSoloChallenge;
    private final int maxInvadersPartyChallenge;
    private final int partyAcceptRadius;
    private final int rtpDelaySeconds;
    private final int pvpCooldownSeconds;
    /** Null or AIR = not set. Otherwise full crystal template. */
    private final Material crystalMaterial;
    private final String crystalDisplayName;
    private final List<String> crystalLore;
    private final Integer crystalCustomModelData;
    /** Null or AIR = not set. */
    private final Material rtpMaterial;
    private final String rtpDisplayName;
    private final List<String> rtpLore;
    private final Integer rtpCustomModelData;
    /** Stuck crystal: given to challengers on entry; right-click to TP to challenge destination. Null or AIR = not set. */
    private final Material stuckCrystalMaterial;
    private final String stuckCrystalDisplayName;
    private final List<String> stuckCrystalLore;
    private final Integer stuckCrystalCustomModelData;
    private final String newInvaderJoinMessage;
    private final int victoryLootSeconds;

    public InvasionConfig(FileConfiguration config) {
        String prefix = "invasions.";
        enabled = config.getBoolean(prefix + "enabled", false);
        partyCountdownSeconds = config.getInt(prefix + "party-countdown-seconds", 10);
        maxInvadersSoloChallenge = config.getInt(prefix + "max-invaders-solo-challenge", 1);
        maxInvadersPartyChallenge = config.getInt(prefix + "max-invaders-party-challenge", 3);
        partyAcceptRadius = config.getInt(prefix + "party-accept-radius", 10);
        rtpDelaySeconds = config.getInt(prefix + "rtp-delay-seconds", 5);
        pvpCooldownSeconds = config.getInt(prefix + "pvp-cooldown-seconds", 10);
        String crystalMatStr = config.getString(prefix + "crystal-material", "");
        crystalMaterial = (crystalMatStr == null || crystalMatStr.isEmpty()) ? null : Material.matchMaterial(crystalMatStr);
        String crystalName = config.getString(prefix + "crystal-display-name", "");
        crystalDisplayName = crystalName != null ? ChatColor.translateAlternateColorCodes('&', crystalName) : "";
        List<?> crystalLoreRaw = config.getList(prefix + "crystal-lore");
        crystalLore = copyLore(crystalLoreRaw);
        crystalCustomModelData = config.getObject(prefix + "crystal-custom-model-data", Integer.class);
        String rtpMatStr = config.getString(prefix + "rtp-material", "");
        rtpMaterial = (rtpMatStr == null || rtpMatStr.isEmpty()) ? null : Material.matchMaterial(rtpMatStr);
        String rtpName = config.getString(prefix + "rtp-display-name", "");
        rtpDisplayName = rtpName != null ? ChatColor.translateAlternateColorCodes('&', rtpName) : "";
        List<?> rtpLoreRaw = config.getList(prefix + "rtp-lore");
        rtpLore = copyLore(rtpLoreRaw);
        rtpCustomModelData = config.getObject(prefix + "rtp-custom-model-data", Integer.class);
        String stuckPrefix = "stuck-crystal.";
        String stuckMatStr = config.getString(stuckPrefix + "material", "");
        stuckCrystalMaterial = (stuckMatStr == null || stuckMatStr.isEmpty()) ? null : Material.matchMaterial(stuckMatStr);
        String stuckName = config.getString(stuckPrefix + "display-name", "");
        stuckCrystalDisplayName = stuckName != null ? ChatColor.translateAlternateColorCodes('&', stuckName) : "";
        List<?> stuckLoreRaw = config.getList(stuckPrefix + "lore");
        stuckCrystalLore = copyLore(stuckLoreRaw);
        stuckCrystalCustomModelData = config.getObject(stuckPrefix + "custom-model-data", Integer.class);
        String joinMsg = config.getString(prefix + "new-invader-join-message", "subtitle");
        newInvaderJoinMessage = (joinMsg != null && (joinMsg.equals("subtitle") || joinMsg.equals("actionbar") || joinMsg.equals("none"))) ? joinMsg : "subtitle";
        victoryLootSeconds = Math.max(1, config.getInt(prefix + "victory-loot-seconds", 120));
    }

    private static List<String> copyLore(List<?> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            String line = o != null ? ChatColor.translateAlternateColorCodes('&', o.toString()) : "";
            out.add(line);
        }
        return out;
    }

    public boolean isEnabled() { return enabled; }
    public int getPartyCountdownSeconds() { return partyCountdownSeconds; }
    public int getMaxInvadersSoloChallenge() { return maxInvadersSoloChallenge; }
    public int getMaxInvadersPartyChallenge() { return maxInvadersPartyChallenge; }
    public int getPartyAcceptRadius() { return partyAcceptRadius; }
    public int getRtpDelaySeconds() { return rtpDelaySeconds; }
    public int getPvpCooldownSeconds() { return pvpCooldownSeconds; }
    public String getNewInvaderJoinMessage() { return newInvaderJoinMessage; }
    public int getVictoryLootSeconds() { return victoryLootSeconds; }

    public boolean isCrystalConfigured() { return crystalMaterial != null && !crystalMaterial.isAir(); }
    public boolean isRtpConfigured() { return rtpMaterial != null && !rtpMaterial.isAir(); }
    public boolean isStuckCrystalConfigured() { return stuckCrystalMaterial != null && !stuckCrystalMaterial.isAir(); }

    /** Check if the given item matches the configured invasion crystal (material, display name, lore, custom model data).
     * When custom model data is configured, material + CMD match is enough (MythicMobs items may have different name/lore). */
    public boolean isCrystalItem(ItemStack item) {
        if (!isCrystalConfigured()) return false;
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() != crystalMaterial) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return crystalDisplayName.isEmpty() && crystalLore.isEmpty() && crystalCustomModelData == null;
        if (crystalCustomModelData != null && meta.hasCustomModelData() && meta.getCustomModelData() == crystalCustomModelData)
            return true;
        if (crystalCustomModelData != null && (!meta.hasCustomModelData() || meta.getCustomModelData() != crystalCustomModelData)) return false;
        String itemName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (!matchesDisplayName(itemName != null ? itemName : "", crystalDisplayName)) return false;
        if (!matchesLore(meta.getLore(), crystalLore)) return false;
        return true;
    }

    /** Check if the given item matches the configured RTP item. When custom model data is set, material + CMD match is enough. */
    public boolean isRtpItem(ItemStack item) {
        if (!isRtpConfigured()) return false;
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() != rtpMaterial) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return rtpDisplayName.isEmpty() && rtpLore.isEmpty() && rtpCustomModelData == null;
        if (rtpCustomModelData != null && meta.hasCustomModelData() && meta.getCustomModelData() == rtpCustomModelData)
            return true;
        if (rtpCustomModelData != null && (!meta.hasCustomModelData() || meta.getCustomModelData() != rtpCustomModelData)) return false;
        if (!matchesDisplayName(meta.hasDisplayName() ? meta.getDisplayName() : "", rtpDisplayName)) return false;
        if (!matchesLore(meta.getLore(), rtpLore)) return false;
        return true;
    }

    /** Check if the given item matches the configured stuck crystal. When custom model data is set, material + CMD match is enough. */
    public boolean isStuckCrystalItem(ItemStack item) {
        if (!isStuckCrystalConfigured()) return false;
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() != stuckCrystalMaterial) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return stuckCrystalDisplayName.isEmpty() && stuckCrystalLore.isEmpty() && stuckCrystalCustomModelData == null;
        if (stuckCrystalCustomModelData != null && meta.hasCustomModelData() && meta.getCustomModelData() == stuckCrystalCustomModelData)
            return true;
        if (stuckCrystalCustomModelData != null && (!meta.hasCustomModelData() || meta.getCustomModelData() != stuckCrystalCustomModelData)) return false;
        if (!matchesDisplayName(meta.hasDisplayName() ? meta.getDisplayName() : "", stuckCrystalDisplayName)) return false;
        if (!matchesLore(meta.getLore(), stuckCrystalLore)) return false;
        return true;
    }

    /** Compare display names: exact match, or strip color codes and compare (handles § vs & and hex casing from MythicMobs). */
    private static boolean matchesDisplayName(String itemName, String configName) {
        if (configName == null || configName.isEmpty()) return true;
        if (Objects.equals(itemName, configName)) return true;
        String itemPlain = ChatColor.stripColor(itemName != null ? itemName : "");
        String configPlain = ChatColor.stripColor(configName);
        return Objects.equals(itemPlain, configPlain);
    }

    /** Compare lore: exact line match, or strip color codes per line (handles MythicMobs formatting). */
    private static boolean matchesLore(List<String> itemLore, List<String> configLore) {
        if (configLore == null || configLore.isEmpty()) return true;
        if (itemLore == null || itemLore.size() != configLore.size()) return false;
        for (int i = 0; i < configLore.size(); i++) {
            String itemLine = i < itemLore.size() ? itemLore.get(i) : null;
            String configLine = configLore.get(i);
            if (Objects.equals(itemLine, configLine)) continue;
            if (!Objects.equals(ChatColor.stripColor(itemLine != null ? itemLine : ""), ChatColor.stripColor(configLine != null ? configLine : "")))
                return false;
        }
        return true;
    }

    /** Build the RTP item to give to invaders (1 copy), with full meta. Returns null if RTP item not configured. */
    public ItemStack createRtpItem() {
        if (!isRtpConfigured()) return null;
        ItemStack stack = new ItemStack(rtpMaterial, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (rtpDisplayName != null && !rtpDisplayName.isEmpty()) meta.setDisplayName(rtpDisplayName);
            if (rtpLore != null && !rtpLore.isEmpty()) meta.setLore(new ArrayList<>(rtpLore));
            if (rtpCustomModelData != null) meta.setCustomModelData(rtpCustomModelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Build the stuck crystal item to give to challengers (1 copy), with full meta. Returns null if not configured. */
    public ItemStack createStuckCrystalItem() {
        if (!isStuckCrystalConfigured()) return null;
        ItemStack stack = new ItemStack(stuckCrystalMaterial, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (stuckCrystalDisplayName != null && !stuckCrystalDisplayName.isEmpty()) meta.setDisplayName(stuckCrystalDisplayName);
            if (stuckCrystalLore != null && !stuckCrystalLore.isEmpty()) meta.setLore(new ArrayList<>(stuckCrystalLore));
            if (stuckCrystalCustomModelData != null) meta.setCustomModelData(stuckCrystalCustomModelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
