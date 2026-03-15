package com.tinkrarityloot.common.weapon;

/**
 * Coefficient set for a single (WeaponFamily × ComponentRole) combination.
 *
 * Every field is a coefficient used in a bounded formula:
 *
 *   value_lo = levelSlope_lo * mobLevel + base + raritySlope_lo * rarityMul
 *   value_hi = levelSlope_hi * mobLevel + base + raritySlope_hi * rarityMul
 *   rolled   = uniform(value_lo, value_hi) [+ optional bias for heavy]
 *   final    = clamp(rolled * budgetWeight, hardMin, hardMax)
 *
 * ── Stat coverage by family ───────────────────────────────────────────────────
 *
 *  MELEE_LIGHT  →  damage, durability, atkSpeedDelta
 *  MELEE_HEAVY  →  damage (biased hi), durability, atkSpeedDelta (negative)
 *  THROWN       →  damage (throw), durability, velocity, accuracy
 *  RANGED       →  velocity, drawSpeed, accuracy, durability (small)
 *
 * Fields unused by a given family are left at 0.
 *
 * ── Budget weights ────────────────────────────────────────────────────────────
 * The budget weights (0–1) express what fraction of the weapon's total stat
 * budget this ROLE contributes.  They are applied after the raw range roll:
 *
 *   MELEE_LIGHT  BLADE:   dmgBudget=0.70  durBudget=0.60
 *   MELEE_LIGHT  HANDLE:  dmgBudget=0.00  durBudget=0.30
 *   MELEE_LIGHT  GUARD:   dmgBudget=0.30  durBudget=0.10
 *
 *   MELEE_HEAVY  BLADE:   dmgBudget=0.80  durBudget=0.65
 *   MELEE_HEAVY  HANDLE:  dmgBudget=0.00  durBudget=0.30
 *   MELEE_HEAVY  GUARD:   dmgBudget=0.10  durBudget=0.05
 *
 * These are NOT normalised and do NOT need to sum to 1.  They represent
 * additive contributions that are summed at tool-assembly time.
 */
public record WeaponStatTemplate(

    // ── Damage (melee / thrown) ───────────────────────────────────────────────
    float dmgLevelLo,   // per-level slope, lower bound
    float dmgLevelHi,   // per-level slope, upper bound
    float dmgBase,      // flat additive base (both bounds share one base)
    float dmgRarityLo,  // rarity-multiplier coefficient, lower bound
    float dmgRarityHi,  // rarity-multiplier coefficient, upper bound
    float dmgBias,      // uniform roll bias added after roll (+ for heavy, 0 otherwise)
    float dmgBudget,    // fraction of weapon damage budget this role owns

    // ── Durability ────────────────────────────────────────────────────────────
    float durScaleLo,   // per-level scale, lower bound
    float durScaleHi,   // per-level scale, upper bound
    float durBudget,    // fraction of durability budget this role owns

    // ── Attack speed (melee only; rarity-only, no mob level) ─────────────────
    float atkSpdBase,   // base delta before rarity adjustment (negative = heavy)
    float atkSpdRarityK,// coefficient: atkSpeedDelta += clamp((rarityMul-1)*atkSpdRarityK, ...)
    float atkSpdMax,    // hard cap on the positive side (magnitude)
    float atkSpdMin,    // hard cap on the negative side (negative value)

    // ── Velocity (ranged / thrown) ────────────────────────────────────────────
    float velLevelLo,   // per-level slope lo
    float velLevelHi,   // per-level slope hi
    float velBase,      // flat base
    float velRarityLo,  // rarity lo slope
    float velRarityHi,  // rarity hi slope
    float velMax,       // hard cap

    // ── Draw speed (ranged only; rarity-only) ─────────────────────────────────
    float drawRarityLo, // draw_lo = drawRarityLo * (rarityMul - 1)
    float drawRarityHi, // draw_hi = drawRarityHi * (rarityMul - 1)
    float drawMin,      // clamp min (more-negative = faster)
    float drawMax,      // clamp max

    // ── Accuracy (ranged / thrown; rarity-only) ───────────────────────────────
    float accBase,      // flat offset centred near 0
    float accRarityLo,  // acc_lo += accRarityLo * (rarityMul - 1)
    float accRarityHi,  // acc_hi += accRarityHi * (rarityMul - 1)
    float accMin,       // clamp min
    float accMax        // clamp max

) {

    // ══════════════════════════════════════════════════════════════════════════
    //  MELEE_LIGHT templates
    // ══════════════════════════════════════════════════════════════════════════

    public static final WeaponStatTemplate LIGHT_BLADE = new WeaponStatTemplate(
        // damage
        0.20f, 0.50f, 3.0f, 1.00f, 1.25f, 0.00f, 0.70f,
        // durability
        25f, 75f, 0.60f,
        // attack speed: 0.04 base on all parts; +rarity scaling at EPIC+, max +0.12
        0.04f, 0.06f, 0.12f, 0.00f,
        // velocity / draw / accuracy – N/A for melee
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    public static final WeaponStatTemplate LIGHT_HANDLE = new WeaponStatTemplate(
        // damage: handles don't contribute raw damage
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        // durability – handles contribute 30 % of weapon budget
        15f, 40f, 0.30f,
        // attack speed: 0.02 base on all parts; +rarity scaling at EPIC+
        0.02f, 0.03f, 0.06f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    public static final WeaponStatTemplate LIGHT_GUARD = new WeaponStatTemplate(
        // damage: guards contribute 30 % of damage budget (defensive / parry feel)
        0.02f, 0.08f, 0.2f, 0.10f, 0.25f, 0.00f, 0.30f,
        // durability: minor
        10f, 30f, 0.10f,
        // attack speed: 0.02 base on all parts; guards contribute less
        0.02f, 0.02f, 0.04f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    // ══════════════════════════════════════════════════════════════════════════
    //  MELEE_HEAVY templates
    // ══════════════════════════════════════════════════════════════════════════

    public static final WeaponStatTemplate HEAVY_BLADE = new WeaponStatTemplate(
        // damage: higher slopes, +0.10 bias toward upper half of range
        0.25f, 0.60f, 4.0f, 1.25f, 1.50f, 0.10f, 0.80f,
        // durability: heavy weapons have more
        30f, 90f, 0.65f,
        // attack speed: negative base, rarity slowly recovers it; cap -0.03 .. +0.02
        -0.04f, 0.02f, 0.02f, -0.03f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    public static final WeaponStatTemplate HEAVY_HANDLE = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        20f, 55f, 0.30f,
        // handles contribute no atk speed for heavy weapons
        0.00f, 0.01f, 0.02f, -0.02f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    public static final WeaponStatTemplate HEAVY_GUARD = new WeaponStatTemplate(
        // guards on heavy weapons give almost no damage, minor durability
        0.01f, 0.04f, 0.1f, 0.05f, 0.10f, 0.00f, 0.10f,
        8f, 25f, 0.05f,
        0.00f, 0.01f, 0.01f, -0.01f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    // ══════════════════════════════════════════════════════════════════════════
    //  THROWN templates
    // ══════════════════════════════════════════════════════════════════════════

    public static final WeaponStatTemplate THROWN_HEAD = new WeaponStatTemplate(
        // throw damage: slightly lower slope than melee
        0.16f, 0.40f, 2.5f, 0.90f, 1.15f, 0.00f, 0.75f,
        // durability: thrown weapons break more — moderate
        20f, 60f, 0.70f,
        // attack speed: irrelevant for thrown
        0.00f, 0.00f, 0.00f, 0.00f,
        // velocity: scales gently with level, capped at 0.60
        0.002f, 0.005f, 0.05f, 0.02f, 0.03f, 0.60f,
        0f, 0f, 0f, 0f,
        // accuracy: thrown, minor benefit from rarity
        -0.01f, 0.005f, 0.015f, -0.10f, 0.10f
    );

    public static final WeaponStatTemplate THROWN_SHAFT = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        12f, 35f, 0.30f,
        0.00f, 0.00f, 0.00f, 0.00f,
        // shaft drives most of velocity
        0.003f, 0.007f, 0.08f, 0.02f, 0.04f, 0.65f,
        0f, 0f, 0f, 0f,
        0.00f, 0.005f, 0.010f, -0.08f, 0.08f
    );

    // ══════════════════════════════════════════════════════════════════════════
    //  RANGED templates
    // ══════════════════════════════════════════════════════════════════════════

    public static final WeaponStatTemplate RANGED_LIMB = new WeaponStatTemplate(
        // no direct damage
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        // durability: bows can break
        18f, 50f, 0.60f,
        0.00f, 0.00f, 0.00f, 0.00f,
        // velocity: limb is primary velocity source
        0.002f, 0.004f, 0.05f, 0.03f, 0.05f, 0.70f,
        // draw speed: limb has secondary draw influence, rarity-only
        -0.04f, -0.10f, -0.30f, 0.10f,
        // accuracy: limb has minor accuracy
        -0.01f, 0.005f, 0.015f, -0.12f, 0.12f
    );

    public static final WeaponStatTemplate RANGED_STRING = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        8f, 20f, 0.20f,
        0.00f, 0.00f, 0.00f, 0.00f,
        // string: tiny velocity
        0.001f, 0.002f, 0.02f, 0.01f, 0.02f, 0.25f,
        // string is the main draw speed contributor
        -0.05f, -0.12f, -0.30f, 0.20f,
        // accuracy: minor
        0.00f, 0.010f, 0.020f, -0.05f, 0.10f
    );

    public static final WeaponStatTemplate RANGED_GRIP = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        10f, 28f, 0.20f,
        0.00f, 0.00f, 0.00f, 0.00f,
        // grip: tiny velocity tweak
        0.001f, 0.002f, 0.01f, 0.005f, 0.010f, 0.15f,
        // grip: minor draw speed
        -0.01f, -0.03f, -0.15f, 0.15f,
        // grip is the PRIMARY accuracy contributor
        -0.01f, 0.015f, 0.030f, -0.15f, 0.15f
    );

    // ══════════════════════════════════════════════════════════════════════════
    //  BASIC WEAPON templates (added for full weapon coverage)
    // ══════════════════════════════════════════════════════════════════════════

    /** Knife/dagger blade — fast, lower damage than sword. THROWN family. */
    public static final WeaponStatTemplate KNIFE_BLADE = new WeaponStatTemplate(
        0.14f, 0.35f, 2.0f, 0.80f, 1.05f, 0.00f, 0.70f,
        15f, 45f, 0.60f,
        0.00f, 0.00f, 0.00f, 0.00f,
        0.002f, 0.005f, 0.06f, 0.02f, 0.04f, 0.55f,
        0f, 0f, 0f, 0f,
        -0.01f, 0.008f, 0.018f, -0.12f, 0.12f
    );

    /** Guard-slot crossbar (small_binding, used in rapier / dagger). MELEE_LIGHT extra. */
    public static final WeaponStatTemplate CROSSBAR = new WeaponStatTemplate(
        0.01f, 0.05f, 0.1f, 0.08f, 0.18f, 0.00f, 0.20f,
        8f, 22f, 0.10f,
        0.00f, 0.02f, 0.04f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Hand guard (longsword guard slot). MELEE_LIGHT extra. Same budget as wide guard. */
    public static final WeaponStatTemplate HAND_GUARD = LIGHT_GUARD;

    /** Full guard (cutlass). Slightly higher stats than wide guard — village-only loot. */
    public static final WeaponStatTemplate FULL_GUARD = new WeaponStatTemplate(
        0.03f, 0.10f, 0.3f, 0.12f, 0.28f, 0.00f, 0.35f,
        12f, 35f, 0.12f,
        0.00f, 0.03f, 0.05f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Board (battlesign head) — very high durability, low damage, defensive. */
    public static final WeaponStatTemplate BOARD = new WeaponStatTemplate(
        0.05f, 0.15f, 1.5f, 0.50f, 0.80f, 0.00f, 0.50f,
        40f, 100f, 0.90f,   // high durability — boards are shields
        0.00f, 0.01f, 0.02f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Pan (frying pan head) — high durability, moderate damage. MELEE_LIGHT. */
    public static final WeaponStatTemplate PAN = new WeaponStatTemplate(
        0.08f, 0.20f, 2.0f, 0.70f, 1.00f, 0.00f, 0.55f,
        35f, 85f, 0.85f,
        -0.02f, 0.01f, 0.03f, -0.02f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Arrow head — contributes ranged velocity and minor damage. RANGED HEAD. */
    public static final WeaponStatTemplate ARROW_HEAD = new WeaponStatTemplate(
        0.10f, 0.25f, 1.5f, 0.60f, 0.90f, 0.00f, 0.60f,
        10f, 28f, 0.50f,
        0.00f, 0.00f, 0.00f, 0.00f,
        0.002f, 0.006f, 0.04f, 0.02f, 0.04f, 0.50f,
        0f, 0f, 0f, 0f,
        -0.01f, 0.008f, 0.018f, -0.10f, 0.10f
    );

    /** Fletching (arrow extra slot) — primary accuracy, minor draw speed. */
    public static final WeaponStatTemplate FLETCHING = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        5f, 14f, 0.15f,
        0.00f, 0.00f, 0.00f, 0.00f,
        0f, 0f, 0f, 0f, 0f, 0f,
        -0.02f, -0.06f, -0.20f, 0.15f,
        0.00f, 0.018f, 0.030f, -0.08f, 0.12f
    );

    // ══════════════════════════════════════════════════════════════════════════
    //  ADVANCED WEAPON templates (Tool Forge)
    // ══════════════════════════════════════════════════════════════════════════

    /** Large blade (cleaver head) — very high damage, very slow. MELEE_HEAVY. */
    public static final WeaponStatTemplate LARGE_BLADE = new WeaponStatTemplate(
        0.30f, 0.70f, 5.0f, 1.50f, 1.80f, 0.15f, 0.85f,
        35f, 100f, 0.70f,
        -0.06f, 0.02f, 0.02f, -0.05f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Hammer head — massive damage, slow, extra vs undead flavour. MELEE_HEAVY. */
    public static final WeaponStatTemplate HAMMER_HEAD = new WeaponStatTemplate(
        0.28f, 0.65f, 4.5f, 1.40f, 1.70f, 0.20f, 0.85f,
        40f, 110f, 0.75f,
        -0.08f, 0.02f, 0.01f, -0.06f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Scythe head — area-feel: moderate damage, good durability. MELEE_HEAVY. */
    public static final WeaponStatTemplate SCYTHE_HEAD = new WeaponStatTemplate(
        0.20f, 0.50f, 3.5f, 1.10f, 1.40f, 0.05f, 0.75f,
        30f, 85f, 0.70f,
        -0.05f, 0.02f, 0.02f, -0.04f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Broad axe head (battleaxe) — high damage + knockback feel. MELEE_HEAVY. */
    public static final WeaponStatTemplate BROAD_AXE_HEAD = new WeaponStatTemplate(
        0.26f, 0.60f, 4.0f, 1.30f, 1.60f, 0.12f, 0.80f,
        32f, 90f, 0.65f,
        -0.06f, 0.02f, 0.02f, -0.05f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Tough binding (scythe / battleaxe extra slot). MELEE_HEAVY extra. */
    public static final WeaponStatTemplate TOUGH_BINDING = new WeaponStatTemplate(
        0.01f, 0.03f, 0.1f, 0.05f, 0.10f, 0.00f, 0.08f,
        15f, 40f, 0.08f,
        0.00f, 0.01f, 0.01f, -0.01f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );

    /** Large plate in advanced weapon context (hammer filler). MELEE_HEAVY extra. */
    public static final WeaponStatTemplate LARGE_PLATE_ADV = new WeaponStatTemplate(
        0.00f, 0.00f, 0.0f, 0.00f, 0.00f, 0.00f, 0.00f,
        25f, 65f, 0.12f,
        0.00f, 0.01f, 0.01f, -0.02f,
        0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f
    );
}
