package com.tinkrarityloot.common.rpg;

import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.weapon.WeaponFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rolls prefix and suffix affixes for an assembled TRL tool.
 *
 * ── Affix count by rarity ─────────────────────────────────────────────────────
 *   Common    — 0 affixes
 *   Uncommon  — 1 prefix
 *   Rare      — 1 prefix + 1 suffix
 *   Epic      — 2 prefix + 1 suffix
 *   Unique    — 2 prefix + 2 suffix
 *   Legendary — 3 prefix + 2 suffix
 *   Mythic    — 3 prefix + 3 suffix
 *
 * ── Tier ─────────────────────────────────────────────────────────────────────
 *   Affix tiers 1–5. Value is:
 *     base_min + (base_max - base_min) × (tier-1)/4 × levelScale × rarityScale
 */
public final class TRLAffixRoller {

    private TRLAffixRoller() {}

    private record AffixDef(String type, float baseMin, float baseMax, boolean prefixOnly) {}

    private static final AffixDef[] MELEE_POOL = {
        new AffixDef(TRLAffix.PHYS_DMG_PCT,  5f,  30f, true),
        new AffixDef(TRLAffix.FIRE_DMG,       3f,  20f, true),
        new AffixDef(TRLAffix.ICE_DMG,        3f,  20f, true),
        new AffixDef(TRLAffix.LIGHTNING_DMG,  3f,  20f, true),
        new AffixDef(TRLAffix.POISON_DMG,     3f,  18f, true),
        new AffixDef(TRLAffix.CRIT_CHANCE,    2f,  12f, false),
        new AffixDef(TRLAffix.CRIT_DMG,       5f,  35f, false),
        new AffixDef(TRLAffix.LIFE_STEAL,     0.5f, 4f, false),
        new AffixDef(TRLAffix.ATK_SPEED_PCT,  2f,  15f, false),
        new AffixDef(TRLAffix.THORNS,         2f,  12f, false),
        new AffixDef(TRLAffix.MAX_HP_PCT,     3f,  15f, false),
        new AffixDef(TRLAffix.MANA_REGEN,     1f,   8f, false),
        new AffixDef(TRLAffix.EXP_BONUS,      2f,  10f, false),
    };

    private static final AffixDef[] RANGED_POOL = {
        new AffixDef(TRLAffix.PHYS_DMG_PCT,  5f,  28f, true),
        new AffixDef(TRLAffix.FIRE_DMG,       3f,  18f, true),
        new AffixDef(TRLAffix.ICE_DMG,        3f,  18f, true),
        new AffixDef(TRLAffix.LIGHTNING_DMG,  3f,  18f, true),
        new AffixDef(TRLAffix.POISON_DMG,     3f,  16f, true),
        new AffixDef(TRLAffix.CRIT_CHANCE,    2f,  14f, false),
        new AffixDef(TRLAffix.CRIT_DMG,       5f,  38f, false),
        new AffixDef(TRLAffix.PROJ_SPEED,     3f,  20f, false),
        new AffixDef(TRLAffix.PIERCE_CHANCE,  2f,  18f, false),
        new AffixDef(TRLAffix.MAX_HP_PCT,     3f,  12f, false),
        new AffixDef(TRLAffix.ENERGY_REGEN,   1f,   8f, false),
        new AffixDef(TRLAffix.EXP_BONUS,      2f,  10f, false),
    };

    /**
     * Roll affixes for an assembled tool.
     *
     * @param rarity   Assembled tool's dominant rarity
     * @param family   Weapon family (drives which stat pool to use)
     * @param mobLevel Mob level from the lootbox (scales affix values)
     * @param random   Seeded RNG
     */
    public static List<TRLAffix> roll(TRLRarity rarity, WeaponFamily family,
                                       int mobLevel, Random random) {
        List<TRLAffix> result = new ArrayList<>();
        int[] counts = affixCounts(rarity); // [prefixCount, suffixCount]
        if (counts[0] == 0 && counts[1] == 0) return result;

        AffixDef[] pool = (family == WeaponFamily.RANGED || family == WeaponFamily.THROWN)
                ? RANGED_POOL : MELEE_POOL;

        // Separate prefix and suffix candidate pools
        List<AffixDef> prefixCandidates = new ArrayList<>();
        List<AffixDef> suffixCandidates = new ArrayList<>();
        for (AffixDef def : pool) {
            prefixCandidates.add(def);
            if (!def.prefixOnly()) suffixCandidates.add(def);
        }

        // Roll prefixes (no duplicates)
        rollSlots(result, prefixCandidates, counts[0], true,
                  rarity, mobLevel, random);
        // Roll suffixes (no duplicates, different from prefixes)
        List<AffixDef> usedTypes = result.stream()
                .map(a -> findDef(pool, a.type())).toList();
        suffixCandidates.removeAll(usedTypes);
        rollSlots(result, suffixCandidates, counts[1], false,
                  rarity, mobLevel, random);

        return result;
    }

    private static void rollSlots(List<TRLAffix> out, List<AffixDef> candidates,
                                   int count, boolean isPrefix,
                                   TRLRarity rarity, int mobLevel, Random random) {
        List<AffixDef> pool = new ArrayList<>(candidates);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int idx = random.nextInt(pool.size());
            AffixDef def = pool.remove(idx);
            int tier = rollTier(rarity, random);
            float value = calcValue(def, tier, mobLevel, rarity);
            out.add(new TRLAffix(def.type(), value, tier, isPrefix));
        }
    }

    /** Tier 1–5, biased upward by rarity. */
    private static int rollTier(TRLRarity rarity, Random random) {
        // Min tier increases with rarity:
        // Common=1, Uncommon=1, Rare=2, Epic=2, Unique=3, Legendary=3, Mythic=4
        int minTier = switch (rarity) {
            case RARE, EPIC  -> 2;
            case UNIQUE, LEGENDARY -> 3;
            case MYTHIC      -> 4;
            default          -> 1;
        };
        return minTier + random.nextInt(6 - minTier); // minTier..5
    }

    /** Value = interpolated between baseMin and baseMax by tier, scaled by level + rarity. */
    private static float calcValue(AffixDef def, int tier, int mobLevel, TRLRarity rarity) {
        float t = (tier - 1) / 4f;  // 0.0 at tier1, 1.0 at tier5
        float base = def.baseMin() + (def.baseMax() - def.baseMin()) * t;
        // Level scaling: +0.5% per level, capped at +50%
        float levelScale = 1f + Math.min(0.50f, mobLevel * 0.005f);
        // Rarity scale: matches rarityMul roughly
        float rarityScale = switch (rarity) {
            case UNCOMMON  -> 1.1f;
            case RARE      -> 1.3f;
            case EPIC      -> 1.6f;
            case UNIQUE    -> 1.9f;
            case LEGENDARY -> 2.5f;
            case MYTHIC    -> 3.5f;
            default        -> 1.0f;
        };
        return Math.round(base * levelScale * rarityScale * 10f) / 10f;
    }

    /** [prefixCount, suffixCount] by rarity. */
    private static int[] affixCounts(TRLRarity rarity) {
        return switch (rarity) {
            case COMMON    -> new int[]{0, 0};
            case UNCOMMON  -> new int[]{1, 0};
            case RARE      -> new int[]{1, 1};
            case EPIC      -> new int[]{2, 1};
            case UNIQUE    -> new int[]{2, 2};
            case LEGENDARY -> new int[]{3, 2};
            case MYTHIC    -> new int[]{3, 3};
        };
    }

    private static AffixDef findDef(AffixDef[] pool, String type) {
        for (AffixDef d : pool) if (d.type().equals(type)) return d;
        return null;
    }
}
