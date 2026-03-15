package com.tinkrarityloot.common.stat;

import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import net.minecraft.nbt.CompoundTag;

/**
 * Complete rolled stat snapshot for a single TRL-dropped Tinkers part.
 *
 * Replaces the old {@link PartStatBlock} (which only held damage + durability)
 * with the full weapon-family stat set. Fields not relevant to a given family
 * are set to their neutral / zero value and are excluded from the tooltip.
 *
 * ── Stat fields by family ─────────────────────────────────────────────────────
 *
 *  ALL families:
 *    rarity, durability, mobLevel
 *
 *  MELEE_LIGHT / MELEE_HEAVY:
 *    damage, attackSpeedDelta
 *
 *  THROWN:
 *    damage (throw damage), velocity, accuracy
 *
 *  RANGED:
 *    velocity, drawSpeed, accuracy
 *    (damage is 0 – ranged damage comes from arrow heads, not launcher parts)
 *
 * ── NBT keys ─────────────────────────────────────────────────────────────────
 *  trl_rarity          String
 *  trl_family          String
 *  trl_durability      Int
 *  trl_damage          Float
 *  trl_atk_speed       Float
 *  trl_velocity        Float
 *  trl_draw_speed      Float
 *  trl_accuracy        Float
 *  trl_mob_level       Int
 */
public final class WeaponStatBlock {

    // NBT keys
    public static final String K_RARITY    = "trl_rarity";
    public static final String K_FAMILY    = "trl_family";
    public static final String K_DUR       = "trl_durability";
    public static final String K_DMG       = "trl_damage";
    public static final String K_ATK_SPD   = "trl_atk_speed";
    public static final String K_VELOCITY  = "trl_velocity";
    public static final String K_DRAW_SPD  = "trl_draw_speed";
    public static final String K_ACCURACY  = "trl_accuracy";
    public static final String K_MOB_LVL  = "trl_mob_level";

    // ── Core fields (all families) ────────────────────────────────────────────
    public final TRLRarity   rarity;
    public final WeaponFamily family;
    public final int         durability;
    public final int         mobLevel;

    // ── Melee / thrown ────────────────────────────────────────────────────────
    /** Rolled attack / throw damage. 0 for pure ranged launcher parts. */
    public final float damage;
    /**
     * Attack speed delta relative to the material baseline.
     * Negative = slower (heavy weapons), positive = faster (light weapons).
     * Magnitude is always very small (±0.18 max).
     * 0 for thrown / ranged — their timing is cooldown-driven.
     */
    public final float attackSpeedDelta;

    // ── Ranged / thrown ───────────────────────────────────────────────────────
    /** Arrow / projectile flight speed modifier. 0 for non-ranged parts. */
    public final float velocity;
    /**
     * Draw speed modifier (negative = faster draw in TCon's convention).
     * 0 for melee.
     */
    public final float drawSpeed;
    /**
     * Accuracy modifier (positive = tighter shot spread).
     * Used by thrown (minor) and ranged (primary).
     */
    public final float accuracy;

    private WeaponStatBlock(Builder b) {
        this.rarity          = b.rarity;
        this.family          = b.family;
        this.durability      = b.durability;
        this.mobLevel        = b.mobLevel;
        this.damage          = b.damage;
        this.attackSpeedDelta = b.attackSpeedDelta;
        this.velocity        = b.velocity;
        this.drawSpeed       = b.drawSpeed;
        this.accuracy        = b.accuracy;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(K_RARITY,   rarity.getSerialKey());
        tag.putString(K_FAMILY,   family.name().toLowerCase());
        tag.putInt   (K_DUR,      durability);
        tag.putFloat (K_DMG,      damage);
        tag.putFloat (K_ATK_SPD,  attackSpeedDelta);
        tag.putFloat (K_VELOCITY, velocity);
        tag.putFloat (K_DRAW_SPD, drawSpeed);
        tag.putFloat (K_ACCURACY, accuracy);
        tag.putInt   (K_MOB_LVL, mobLevel);
        return tag;
    }

    public static WeaponStatBlock fromNBT(CompoundTag tag) {
        return new Builder()
                .rarity   (TRLRarity.fromKey(tag.getString(K_RARITY)))
                .family   (familyFromKey(tag.getString(K_FAMILY)))
                .durability(tag.getInt(K_DUR))
                .damage   (tag.getFloat(K_DMG))
                .atkSpeed (tag.getFloat(K_ATK_SPD))
                .velocity (tag.getFloat(K_VELOCITY))
                .drawSpeed(tag.getFloat(K_DRAW_SPD))
                .accuracy (tag.getFloat(K_ACCURACY))
                .mobLevel (tag.getInt(K_MOB_LVL))
                .build();
    }

    public static boolean isPresent(CompoundTag tag) {
        return tag != null && tag.contains(K_RARITY);
    }

    private static WeaponFamily familyFromKey(String key) {
        if (key == null) return WeaponFamily.MELEE_LIGHT;
        for (WeaponFamily f : WeaponFamily.values())
            if (f.name().equalsIgnoreCase(key)) return f;
        return WeaponFamily.MELEE_LIGHT;
    }

    @Override
    public String toString() {
        return String.format(
                "WeaponStatBlock{%s %s dur=%d dmg=%.2f spdΔ=%.3f vel=%.3f draw=%.3f acc=%.3f lvl=%d}",
                rarity.displayName, family, durability, damage,
                attackSpeedDelta, velocity, drawSpeed, accuracy, mobLevel);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TRLRarity    rarity    = TRLRarity.COMMON;
        private WeaponFamily family    = WeaponFamily.MELEE_LIGHT;
        private int          durability = 1;
        private int          mobLevel   = 1;
        private float        damage     = 0f;
        private float        attackSpeedDelta = 0f;
        private float        velocity   = 0f;
        private float        drawSpeed  = 0f;
        private float        accuracy   = 0f;

        public Builder rarity   (TRLRarity v)    { this.rarity    = v; return this; }
        public Builder family   (WeaponFamily v)  { this.family    = v; return this; }
        public Builder durability(int v)          { this.durability = v; return this; }
        public Builder mobLevel (int v)           { this.mobLevel  = v; return this; }
        public Builder damage   (float v)         { this.damage    = v; return this; }
        public Builder atkSpeed (float v)         { this.attackSpeedDelta = v; return this; }
        public Builder velocity (float v)         { this.velocity  = v; return this; }
        public Builder drawSpeed(float v)         { this.drawSpeed = v; return this; }
        public Builder accuracy (float v)         { this.accuracy  = v; return this; }

        public WeaponStatBlock build() { return new WeaponStatBlock(this); }
    }
}
