package com.gix.conradchallenges.invasion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;
import java.util.Set;

/**
 * Finds a random safe teleport location within a bounding box (e.g. challenge regen area 1).
 * Uses the lowest valid standing spot at each (x,z) so invaders land on floor/ground, not roofs.
 */
public final class InvasionSafeTeleport {

    private static final int MAX_ATTEMPTS = 80;
    private static final Set<Material> UNSAFE_BELOW = Set.of(
            Material.LAVA, Material.FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.SOUL_FIRE, Material.WITHER_ROSE
    );

    private InvasionSafeTeleport() {}

    /**
     * Find a safe location inside the given axis-aligned box.
     * Safe = solid block under feet, 2 blocks of air above, not standing on unsafe blocks.
     *
     * @param world world
     * @param minX  min block X (inclusive)
     * @param maxX  max block X (inclusive)
     * @param minY  min block Y (inclusive)
     * @param maxY  max block Y (inclusive)
     * @param minZ  min block Z (inclusive)
     * @param maxZ  max block Z (inclusive)
     * @param random RNG for reproducibility
     * @return safe location (feet at block top + 1) or null if none found
     */
    public static Location findSafeLocationInBox(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Random random) {
        if (world == null || maxX < minX || maxY < minY || maxZ < minZ) return null;
        int rangeX = maxX - minX + 1;
        int rangeZ = maxZ - minZ + 1;
        if (rangeX <= 0 || rangeZ <= 0) return null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = minX + random.nextInt(rangeX);
            int z = minZ + random.nextInt(rangeZ);
            int y = findLowestSafeStandingY(world, x, z, minY, maxY);
            if (y == Integer.MIN_VALUE) continue;
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    /**
     * Finds the lowest safe standing Y at (x,z) within [minY, maxY].
     * Prefers floor/ground so invaders are not placed on roofs or inaccessible ceilings.
     */
    private static int findLowestSafeStandingY(World world, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type.isSolid()
                    && isSafe(world, x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isSafe(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block below = world.getBlockAt(x, y - 1, z);
        Block above1 = world.getBlockAt(x, y + 1, z);
        Block above2 = world.getBlockAt(x, y + 2, z);
        if (UNSAFE_BELOW.contains(below.getType())) return false;
        if (above1.getType() != Material.AIR && above1.getType() != Material.CAVE_AIR && above1.getType() != Material.VOID_AIR) return false;
        if (above2.getType() != Material.AIR && above2.getType() != Material.CAVE_AIR && above2.getType() != Material.VOID_AIR) return false;
        return true;
    }
}
