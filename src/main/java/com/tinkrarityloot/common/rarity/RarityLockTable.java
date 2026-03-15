package com.tinkrarityloot.common.rarity;

import com.tinkrarityloot.common.config.TRLConfig;

/**
 * Enforces minimum mob-level thresholds per rarity tier.
 *
 * When a rarity roll produces a tier the player hasn't "reached" yet, the
 * roll is re-mapped downward to the highest tier the mob level permits.
 * This prevents a level-1 zombie from ever dropping a Mythic box.
 *
 * ── Default thresholds ────────────────────────────────────────────────────
 *
 *   COMMON    — no minimum (always available)
 *   UNCOMMON  — level  5+
 *   RARE      — level 15+
 *   EPIC      — level 30+
 *   UNIQUE    — level 40+
 *   LEGENDARY — level 55+
 *   MYTHIC    — level 75+
 *
 * All thresholds are configurable.  A threshold of 0 disables the lock for
 * that tier (it can roll at any level).
 *
 * ── Integration point ─────────────────────────────────────────────────────
 *
 *   TRLRarity resolved = RarityLockTable.clamp(rolledRarity, mobLevel);
 *
 * This is called after ConfiguredRarityRoller.roll() and after the boss
 * double-roll, so the final rarity always respects level gates.
 */
public final class RarityLockTable {

    private RarityLockTable() {}

    /**
     * If {@code rarity} requires a higher mob level than {@code mobLevel},
     * step down through tiers until one is reachable.  Falls back to COMMON
     * if nothing else qualifies (practically only at level 0, which is clamped
     * to 1 before it reaches here).
     *
     * @param rarity   The rarity produced by the roller.
     * @param mobLevel The resolved mob level (already clamped to 1–500).
     * @return         The highest rarity the mob level permits.
     */
    public static TRLRarity clamp(TRLRarity rarity, int mobLevel) {
        // Walk down from the rolled rarity until we find one the level allows
        for (int ord = rarity.ordinal(); ord >= 0; ord--) {
            TRLRarity candidate = TRLRarity.values()[ord];
            if (mobLevel >= minLevelFor(candidate)) return candidate;
        }
        return TRLRarity.COMMON;
    }

    /**
     * Returns the minimum mob level required to roll {@code rarity}.
     * 0 means no restriction (always eligible).
     */
    public static int minLevelFor(TRLRarity rarity) {
        return switch (rarity) {
            case COMMON    -> 0;
            case UNCOMMON  -> TRLConfig.SERVER.rarityLockUncommon.get();
            case RARE      -> TRLConfig.SERVER.rarityLockRare.get();
            case EPIC      -> TRLConfig.SERVER.rarityLockEpic.get();
            case UNIQUE    -> TRLConfig.SERVER.rarityLockUnique.get();
            case LEGENDARY -> TRLConfig.SERVER.rarityLockLegendary.get();
            case MYTHIC    -> TRLConfig.SERVER.rarityLockMythic.get();
        };
    }
}
