package com.tinkrarityloot.common.weapon;

/**
 * The budget role a component plays within a weapon family.
 *
 * Each role carries budget multipliers that scale how much of the weapon's
 * total stat budget this component contributes. Roles are orthogonal to
 * {@link WeaponFamily}: the same role (e.g. GUARD) receives different
 * budget multipliers depending on the family.
 *
 * ── Budget multiplier semantics ───────────────────────────────────────────────
 *
 *  dmgBudget (0.0–1.0)  – fraction of the weapon's damage budget this part owns
 *  durBudget (0.0–1.0)  – fraction of the durability budget this part owns
 *
 * These are used in {@link WeaponStatTemplate} to scale the base formula
 * output before writing to the part's stat block.
 *
 * BLADE  is the dominant primary part — it holds most damage and a large share of durability.
 * HANDLE holds a significant share of durability, no raw damage.
 * GUARD  holds a minor share of both.
 * LIMB   is the ranged equivalent of a blade — holds velocity + draw speed.
 * STRING is a ranged secondary — mostly draw speed.
 * GRIP   is a ranged tertiary — accuracy tweaks.
 */
public enum ComponentRole {

    // ── Melee roles ──────────────────────────────────────────────────────────
    BLADE  ("Blade / Head"),
    HANDLE ("Handle / Rod"),
    GUARD  ("Guard / Binding"),

    // ── Ranged roles ─────────────────────────────────────────────────────────
    LIMB   ("Bow Limb"),
    STRING ("Bowstring"),
    GRIP   ("Grip");

    public final String displayName;

    ComponentRole(String displayName) {
        this.displayName = displayName;
    }
}
