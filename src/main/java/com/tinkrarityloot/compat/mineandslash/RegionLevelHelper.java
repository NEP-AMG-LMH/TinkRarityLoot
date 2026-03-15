package com.tinkrarityloot.compat.mineandslash;

import com.tinkrarityloot.TinkRarityLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * Soft-dependency helper: queries Mine and Slash for the region level at a
 * given world position (used by chest loot to scale part stats to the zone).
 *
 * Adjust the class/method names to match your exact M&S build.
 *
 * Expected API:
 *   com.verdantartifice.mineandslash.common.worldgen.RegionManager
 *     static int getRegionLevel(ServerLevel level, BlockPos pos)
 */
public final class RegionLevelHelper {

    private static boolean available = false;
    private static Method getRegionLevelMethod = null;

    static {
        try {
            Class<?> regionManager = Class.forName(
                    "com.verdantartifice.mineandslash.common.worldgen.RegionManager");
            getRegionLevelMethod = regionManager.getMethod(
                    "getRegionLevel", ServerLevel.class, BlockPos.class);
            available = true;
            TinkRarityLoot.LOGGER.info("[TinkRarityLoot] M&S RegionLevelHelper ready.");
        } catch (Exception e) {
            TinkRarityLoot.LOGGER.debug("[TinkRarityLoot] RegionLevelHelper unavailable: {}",
                    e.getMessage());
        }
    }

    private RegionLevelHelper() {}

    /**
     * @param level The server level.
     * @param origin The loot context origin vector.
     * @return Region level, or 1 if unavailable.
     */
    public static int getRegionLevel(ServerLevel level, Vec3 origin) {
        if (!available || getRegionLevelMethod == null) return 1;
        try {
            BlockPos pos = new BlockPos((int) origin.x, (int) origin.y, (int) origin.z);
            Object result = getRegionLevelMethod.invoke(null, level, pos);
            return result instanceof Integer i ? Math.max(1, i) : 1;
        } catch (Exception e) {
            return 1;
        }
    }
}
