package com.tinkrarityloot.common.weapon;

import com.tinkrarityloot.common.rarity.ConfiguredRarityRoller;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.util.TRLUtil;

import java.util.Random;

/**
 * Rolls a complete {@link WeaponStatBlock} from a {@link WeaponStatTemplate},
 * mob level, and random seed.
 *
 * ── Formula summary ────────────────────────────────────────────────────────────
 *
 *  Rarity is rolled first; its multiplier (rarityMul) drives secondary bounds.
 *
 *  DAMAGE (melee / thrown):
 *    lo = mobLevel * t.dmgLevelLo + t.dmgBase + t.dmgRarityLo * rarityMul
 *    hi = mobLevel * t.dmgLevelHi + t.dmgBase + t.dmgRarityHi * rarityMul
 *    raw  = uniform(lo, hi)
 *    biased = clamp(raw + t.dmgBias * (hi - lo), lo, hi)   ← heavy bias
 *    final  = biased * t.dmgBudget
 *
 *  DURABILITY (deterministic — no random roll):
 *    base = 75 + 25 × mobLevel + 75 × 2^rarityOrdinal
 *    final = max(1, (int)(base × t.durBudget))
 *
 *  ATTACK SPEED (melee only; rarity-gated):
 *    COMMON / UNCOMMON / RARE:
 *      atkSpd = t.atkSpdBase  (0.0 for LIGHT; -0.04 base penalty for HEAVY)
 *      — no rarity uplift; players see standard speed or the inherent heavy penalty
 *    EPIC and above:
 *      gain   = clamp((rarityMul - 1.0) * t.atkSpdRarityK, t.atkSpdMin, t.atkSpdMax)
 *      atkSpd = clamp(t.atkSpdBase + gain, t.atkSpdMin, t.atkSpdMax)
 *      — positive nudge for LIGHT; HEAVY penalty partially recovered
 *
 *  VELOCITY (ranged / thrown — universal formula):
 *    lo = 0.35 + 0.025 × level + 0.10  × rarityMul
 *    hi = 0.65 + 0.025 × level + 0.125 × rarityMul
 *    final = clamp(uniform(lo, hi), 0, t.velMax)
 *
 *  DRAW SPEED (ranged; rarity-only):
 *    lo = t.drawRarityLo * (rarityMul - 1)
 *    hi = t.drawRarityHi * (rarityMul - 1)
 *    final = clamp(uniform(lo, hi), t.drawMin, t.drawMax)
 *
 *  ACCURACY (ranged / thrown; rarity-only):
 *    lo = t.accBase + t.accRarityLo * (rarityMul - 1)
 *    hi = t.accBase + t.accRarityHi * (rarityMul - 1)
 *    final = clamp(uniform(lo, hi), t.accMin, t.accMax)
 */
public final class WeaponStatRoller {

    private WeaponStatRoller() {}

    /**
     * Roll a complete {@link WeaponStatBlock}.
     *
     * @param mobLevel Mob / region level (clamped to 1–500 internally).
     * @param family   Which weapon family this part belongs to.
     * @param template The (family × role) coefficient set.
     * @param random   Seeded RNG (per-mob-UUID for determinism).
     */
    public static WeaponStatBlock roll(int mobLevel, WeaponFamily family,
                                       WeaponStatTemplate template, Random random) {
        TRLRarity rarity = ConfiguredRarityRoller.roll(random);
        return rollWithRarity(mobLevel, family, template, rarity, random);
    }

    /**
     * Roll a {@link WeaponStatBlock} with a caller-supplied rarity.
     *
     * Use this when rarity has already been resolved externally — for example,
     * when inheriting a mob's Mine and Slash rarity, or when applying a boss
     * double-roll (roll twice, take the higher tier).
     *
     * @param mobLevel Mob / region level (clamped internally).
     * @param family   Weapon family.
     * @param template Coefficient set.
     * @param rarity   Pre-resolved rarity — skips the internal ConfiguredRarityRoller call.
     * @param random   Seeded RNG.
     */
    public static WeaponStatBlock rollWithRarity(int mobLevel, WeaponFamily family,
                                                  WeaponStatTemplate template,
                                                  TRLRarity rarity, Random random) {
        int    level = TRLUtil.clampMobLevel(mobLevel);
        double mul   = ConfiguredRarityRoller.multiplierFor(rarity);
        float  mulF  = (float) mul;

        // 2. Damage
        float damage = 0f;
        if (template.dmgLevelHi() > 0 || template.dmgRarityHi() > 0) {
            float dLo = level * template.dmgLevelLo() + template.dmgBase()
                        + template.dmgRarityLo() * mulF;
            float dHi = level * template.dmgLevelHi() + template.dmgBase()
                        + template.dmgRarityHi() * mulF;
            dHi = Math.max(dLo + 0.01f, dHi);
            float raw    = dLo + random.nextFloat() * (dHi - dLo);
            // Heavy bias: nudge the roll toward the upper portion of the range
            float biased = template.dmgBias() != 0f
                    ? clamp(raw + template.dmgBias() * (dHi - dLo), dLo, dHi)
                    : raw;
            damage = Math.max(0f, biased * template.dmgBudget());
        }

        // 3. Durability — formula base + ±20% random variance:
        //    base     = 75 + 25 × level + 75 × 2^rarityOrdinal
        //    variance = uniform(0.80, 1.20)
        //    final    = max(1, (int)(base × variance × durBudget))
        int durability = Math.max(1, (int)(
                (75f + 25f * level + 75f * (1 << rarity.ordinal()))
                * (0.80f + random.nextFloat() * 0.40f)
                * template.durBudget()));

        // 4. Attack speed (melee only; rarity-gated)
        //
        // DESIGN: Common / Uncommon / Rare melee weapons have standard attack
        // speed — no TRL modifier.  Only Epic and above start to push the stat:
        //   EPIC+  → small positive delta for LIGHT weapons (faster feel)
        //   EPIC+  → rarity partially recovers the inherent negative on HEAVY
        //
        // rarityGain formula stays the same; we just zero it for RARE and below.
        // Attack speed is always stored on the part at a small universal base value.
        // The base is constant regardless of rarity — every melee part carries it.
        // The patcher only INJECTS it into tic_stats at EPIC+ rarity.
        // This way all parts show an atkSpd value in their tooltip, but assembled
        // tools only gain the actual bonus once they reach EPIC tier.
        float atkSpd = 0f;
        if (family == WeaponFamily.MELEE_LIGHT || family == WeaponFamily.MELEE_HEAVY) {
            if (rarity.ordinal() >= TRLRarity.EPIC.ordinal()) {
                // EPIC+: base + rarity scaling
                float rarityGain = clamp((mulF - 1f) * template.atkSpdRarityK(),
                                         template.atkSpdMin(), template.atkSpdMax());
                atkSpd = clamp(template.atkSpdBase() + rarityGain,
                               template.atkSpdMin(), template.atkSpdMax());
            } else {
                // COMMON–RARE: always store the base amount on the part (for tooltip
                // display and future use), but patcher ignores it below EPIC.
                atkSpd = template.atkSpdBase();
            }
        }

        // 5. Velocity (ranged / thrown)
        //
        // Universal formula for all ranged/thrown parts:
        //   lo = 0.35 + 0.025 × level + 0.10  × rarityMul
        //   hi = 0.65 + 0.025 × level + 0.125 × rarityMul
        //   final = clamp(uniform(lo, hi), 0, velMax)
        //
        // velMax from the template still acts as a hard ceiling so individual
        // parts can cap out at sensible values (e.g. arrow heads cap lower than bow limbs).
        float velocity = 0f;
        if (family == WeaponFamily.RANGED || family == WeaponFamily.THROWN) {
            float vLo = 0.35f + 0.025f * level + 0.10f  * mulF;
            float vHi = 0.65f + 0.025f * level + 0.125f * mulF;
            vHi = Math.max(vLo + 0.001f, vHi);
            float cap = template.velMax() > 0 ? template.velMax() : 99f;
            velocity = clamp(vLo + random.nextFloat() * (vHi - vLo), 0f, cap);
        }

        // 6. Draw speed (ranged only; rarity-only)
        float drawSpeed = 0f;
        if (family == WeaponFamily.RANGED && (template.drawRarityLo() != 0 || template.drawRarityHi() != 0)) {
            float rm1 = mulF - 1f;
            float dsLo = template.drawRarityLo() * rm1;
            float dsHi = template.drawRarityHi() * rm1;
            if (dsLo > dsHi) { float t = dsLo; dsLo = dsHi; dsHi = t; } // ensure lo < hi
            dsHi = Math.max(dsLo + 0.001f, dsHi);
            drawSpeed = clamp(dsLo + random.nextFloat() * (dsHi - dsLo),
                              template.drawMin(), template.drawMax());
        }

        // 7. Accuracy (ranged / thrown; rarity-only)
        float accuracy = 0f;
        if (family == WeaponFamily.RANGED || family == WeaponFamily.THROWN) {
            float rm1 = mulF - 1f;
            float aLo = template.accBase() + template.accRarityLo() * rm1;
            float aHi = template.accBase() + template.accRarityHi() * rm1;
            aHi = Math.max(aLo + 0.001f, aHi);
            accuracy = clamp(aLo + random.nextFloat() * (aHi - aLo),
                             template.accMin(), template.accMax());
        }

        return WeaponStatBlock.builder()
                .rarity(rarity)
                .family(family)
                .durability(durability)
                .mobLevel(level)
                .damage(damage)
                .atkSpeed(atkSpd)
                .velocity(velocity)
                .drawSpeed(drawSpeed)
                .accuracy(accuracy)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
