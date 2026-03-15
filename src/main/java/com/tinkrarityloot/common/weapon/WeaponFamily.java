package com.tinkrarityloot.common.weapon;

/**
 * Classifies a Tinkers part drop into one of four weapon families.
 *
 * The family drives:
 *   • Which stats are rolled (primary / secondary)
 *   • Attack speed delta rules (heavy = negative, light = small positive)
 *   • Stat budget weights per component slot
 *   • Tooltip display (stat labels change per family)
 *
 * ┌──────────────┬──────────────────────────────────────────────────────────┐
 * │ Family       │ Typical parts                                            │
 * ├──────────────┼──────────────────────────────────────────────────────────┤
 * │ MELEE_LIGHT  │ SwordBlade, SmallBlade, ToolRod, WideGuard, CrossGuard  │
 * │ MELEE_HEAVY  │ BroadBlade, ToughHandle, LargePlate, Hammerhead         │
 * │ THROWN       │ HandAxeHead, any head used in thrown tool context         │
 * │ RANGED       │ BowLimb, Bowstring, Crossbow-grip parts                  │
 * └──────────────┴──────────────────────────────────────────────────────────┘
 */
public enum WeaponFamily {

    /**
     * One-handed melee: sword, rapier, etc.
     * Primary: damage, durability.
     * Secondary: tiny positive attack-speed delta from rarity only.
     * Budget weights: blade 70% dmg / 60% dur — guard 30% dmg / 10% dur — rod 0% dmg / 30% dur.
     */
    MELEE_LIGHT,

    /**
     * Heavy melee: cleaver, broad sword, scythe, hammer.
     * Primary: higher damage, higher durability.
     * Secondary: small negative attack-speed delta (heavy feel), rarity softens it.
     * Budget weights: blade 80% dmg / 65% dur — rod 20–30% dur — guard tiny.
     * Damage roll biased +0.10 toward upper bound.
     */
    MELEE_HEAVY,

    /**
     * Thrown weapons: javelin, throwing axe, shuriken.
     * Primary: throw-damage, velocity.
     * Secondary: accuracy (rarity-only).
     * Attack speed irrelevant (cooldown-driven animation).
     */
    THROWN,

    /**
     * Ranged launchers: bow, longbow, crossbow.
     * Primary: velocity, draw_speed, accuracy.
     * Damage is indirect (arrow-head driven).
     * Mob level has minimal influence on ranged stats to prevent absurd values.
     */
    RANGED
}
