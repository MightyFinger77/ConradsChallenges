package com.gix.conradchallenges.invasion;

import org.bukkit.World;

/**
 * Bounding box for challenge regen area 1 (used for invader spawn and RTP).
 */
public final class InvasionRegenBounds {
    private final World world;
    private final int minX, maxX, minY, maxY, minZ, maxZ;

    public InvasionRegenBounds(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public World getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }
}
