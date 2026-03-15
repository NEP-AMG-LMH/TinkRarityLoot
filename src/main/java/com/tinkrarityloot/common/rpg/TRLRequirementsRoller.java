package com.tinkrarityloot.common.rpg;

import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.weapon.WeaponFamily;

/**
 * Derives level and stat requirements for an assembled TRL tool.
 *
 * Level requirement  = mobLevel (from the lootbox) — clamped to [1, 100]
 * Stat requirements  = derived from weapon family + rarity:
 *   MELEE_HEAVY  → Strength primary
 *   MELEE_LIGHT  → Dexterity primary
 *   RANGED/THROWN → Dexterity primary, minor Intelligence
 *
 * Formula per stat:
 *   req = floor(mobLevel × familyCoeff × rarityCoeff)
 */
public final class TRLRequirementsRoller {

    private TRLRequirementsRoller() {}

    public static TRLRequirements roll(int mobLevel, TRLRarity rarity, WeaponFamily family) {
        int level = Math.max(1, Math.min(100, mobLevel));

        float rarityCoeff = switch (rarity) {
            case COMMON    -> 0.50f;
            case UNCOMMON  -> 0.65f;
            case RARE      -> 0.80f;
            case EPIC      -> 1.00f;
            case UNIQUE    -> 1.10f;
            case LEGENDARY -> 1.25f;
            case MYTHIC    -> 1.50f;
        };

        int str = 0, dex = 0, intel = 0;
        switch (family) {
            case MELEE_HEAVY -> {
                str = req(level, 0.80f, rarityCoeff);
                dex = req(level, 0.20f, rarityCoeff);
            }
            case MELEE_LIGHT -> {
                dex = req(level, 0.80f, rarityCoeff);
                str = req(level, 0.20f, rarityCoeff);
            }
            case RANGED, THROWN -> {
                dex   = req(level, 0.70f, rarityCoeff);
                intel = req(level, 0.30f, rarityCoeff);
            }
        }

        return new TRLRequirements(level, str, dex, intel);
    }

    private static int req(int level, float familyCoeff, float rarityCoeff) {
        return Math.max(0, (int)(level * familyCoeff * rarityCoeff));
    }
}
