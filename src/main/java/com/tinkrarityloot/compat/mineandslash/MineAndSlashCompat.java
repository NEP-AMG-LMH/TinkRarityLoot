package com.tinkrarityloot.compat.mineandslash;

import com.tinkrarityloot.TinkRarityLoot;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Soft-dependency bridge to Mine and Slash (reflection-only).
 *
 * Compiles and runs when M&S is absent. All public methods return {@code null}
 * in that case.
 *
 * Expected M&S API (adjust class/method names to match actual 1.20.1 release):
 *
 *   com.verdantartifice.mineandslash.common.capabilities.MASCapabilities
 *     static Capability<IEntityStats> ENTITY_STATS
 *
 *   com.verdantartifice.mineandslash.common.capabilities.IEntityStats
 *     int getLevel()
 *     int getTier()   <- tier >=2 = elite, >=4 = boss (M&S convention)
 *
 * Fallback logic lives in MobLevelResolver.  This class is ONLY the M&S bridge.
 * There is intentionally no "last-resort" level heuristic here.
 */
public class MineAndSlashCompat {

    private static boolean available = false;

    private static Object capabilityKey  = null;
    private static Method getCapMethod   = null;
    private static Method getLevelMethod = null;
    private static Method getTierMethod  = null;
    /** IEntityStats#getRarity() — may not exist in all M&S builds; silently absent if missing. */
    private static Method getRarityMethod = null;

    public static void init() {
        try {
            Class<?> capsClass = Class.forName(
                    "com.verdantartifice.mineandslash.common.capabilities.MASCapabilities");
            capabilityKey = capsClass.getField("ENTITY_STATS").get(null);

            getCapMethod = LivingEntity.class.getMethod("getCapability",
                    net.minecraftforge.common.capabilities.Capability.class);

            Class<?> statsInterface = Class.forName(
                    "com.verdantartifice.mineandslash.common.capabilities.IEntityStats");
            getLevelMethod = statsInterface.getMethod("getLevel");

            try {
                getTierMethod = statsInterface.getMethod("getTier");
            } catch (NoSuchMethodException ignored) {
                TinkRarityLoot.LOGGER.debug("[TRL] M&S IEntityStats.getTier() not found.");
            }

            available = true;
            TinkRarityLoot.LOGGER.info("[TRL] Mine and Slash bridge initialised.");
        } catch (Exception e) {
            available = false;
            TinkRarityLoot.LOGGER.warn("[TRL] Mine and Slash bridge failed: {}", e.getMessage());
        }
    }

    /**
     * Returns the Mine and Slash level of {@code entity}, or {@code null} if
     * M&S is absent or the entity has no M&S stats.
     *
     * Use MobLevelResolver.resolve() for the full fallback chain.
     */
    @Nullable
    public static Integer getMobLevel(LivingEntity entity) {
        if (!available) return null;
        return invokeStatGetter(entity, getLevelMethod);
    }

    /**
     * Returns the Mine and Slash mob tier, or {@code null} if unavailable.
     * Tier >= 2 = elite, tier >= 4 = boss (M&S convention).
     */
    @Nullable
    public static Integer getMobTier(LivingEntity entity) {
        if (!available || getTierMethod == null) return null;
        return invokeStatGetter(entity, getTierMethod);
    }

    /**
     * Region level at a world position (used by chest loot path).
     */
    public static int getRegionLevelAt(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.phys.Vec3 origin) {
        return RegionLevelHelper.getRegionLevel(level, origin);
    }

    /**
     * Convenience wrapper with health-based fallback.
     * Prefer {@link com.tinkrarityloot.common.loot.MobLevelResolver#resolve} for
     * the full multi-step chain; this is kept for call-sites that need a direct
     * M&S-or-health result without the scoreboard / region steps.
     */
    public static int getMobLevelSafe(LivingEntity entity) {
        Integer level = getMobLevel(entity);
        if (level != null && level > 0) return level;
        return Math.max(1, (int)(entity.getMaxHealth() / 5f));
    }

    /**
     * Attempt to read Mine and Slash's rarity for a mob entity.
     *
     * M&S marks some elite/boss mobs with a rarity tier that TRL can inherit
     * for a part drop, so both mods agree on the "quality" of the kill reward.
     * Returns {@code null} if M&S is absent, the entity has no rarity, or the
     * API method isn't found (the try/no-such-method path in init).
     *
     * Adjust the method name ("getRarity") to match your actual M&S build.
     */
    @Nullable
    public static com.tinkrarityloot.common.rarity.TRLRarity getMobRarity(LivingEntity entity) {
        if (!available || getRarityMethod == null) return null;
        try {
            @SuppressWarnings("unchecked")
            var lazyOpt = (net.minecraftforge.common.util.LazyOptional<Object>)
                    getCapMethod.invoke(entity, capabilityKey);
            return lazyOpt.map(stats -> {
                try {
                    Object raw = getRarityMethod.invoke(stats);
                    if (raw instanceof String s) return mapMasRarity(s);
                } catch (Exception ignored) {}
                return null;
            }).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static com.tinkrarityloot.common.rarity.TRLRarity mapMasRarity(String r) {
        return switch (r.toLowerCase()) {
            case "common"                  -> com.tinkrarityloot.common.rarity.TRLRarity.COMMON;
            case "uncommon"                -> com.tinkrarityloot.common.rarity.TRLRarity.UNCOMMON;
            case "rare"                    -> com.tinkrarityloot.common.rarity.TRLRarity.RARE;
            case "epic", "exceptional"     -> com.tinkrarityloot.common.rarity.TRLRarity.EPIC;
            case "unique", "ancient"       -> com.tinkrarityloot.common.rarity.TRLRarity.UNIQUE;
            case "legendary"               -> com.tinkrarityloot.common.rarity.TRLRarity.LEGENDARY;
            case "mythic", "divine"        -> com.tinkrarityloot.common.rarity.TRLRarity.MYTHIC;
            default                        -> null;
        };
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Integer invokeStatGetter(LivingEntity entity, Method statMethod) {
        if (statMethod == null || capabilityKey == null || getCapMethod == null) return null;
        try {
            var lazyOpt = (net.minecraftforge.common.util.LazyOptional<Object>)
                    getCapMethod.invoke(entity, capabilityKey);
            return lazyOpt.map(stats -> {
                try { return (Integer) statMethod.invoke(stats); }
                catch (Exception ex) { return null; }
            }).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
