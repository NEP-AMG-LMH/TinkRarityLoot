package com.tinkrarityloot.common.rpg;

/**
 * A single rolled affix on an assembled TRL tool.
 * Stored as vanilla NBT — no M&S dependency required.
 */
public record TRLAffix(String type, float value, int tier, boolean isPrefix) {

    public static final String K_LIST   = "trl_affixes";
    public static final String K_TYPE   = "type";
    public static final String K_VALUE  = "value";
    public static final String K_TIER   = "tier";
    public static final String K_PREFIX = "prefix";

    // Stat type IDs
    public static final String PHYS_DMG_PCT  = "physical_dmg_pct";
    public static final String FIRE_DMG      = "fire_dmg";
    public static final String ICE_DMG       = "ice_dmg";
    public static final String LIGHTNING_DMG = "lightning_dmg";
    public static final String POISON_DMG    = "poison_dmg";
    public static final String CRIT_CHANCE   = "crit_chance";
    public static final String CRIT_DMG      = "crit_dmg";
    public static final String LIFE_STEAL    = "life_steal";
    public static final String ATK_SPEED_PCT = "atk_speed_pct";
    public static final String THORNS        = "thorns";
    public static final String PROJ_SPEED    = "projectile_speed";
    public static final String PIERCE_CHANCE = "pierce_chance";
    public static final String MAX_HP_PCT    = "max_hp_pct";
    public static final String MANA_REGEN    = "mana_regen";
    public static final String ENERGY_REGEN  = "energy_regen";
    public static final String EXP_BONUS     = "exp_bonus";

    public String displayName() {
        return switch (type) {
            case PHYS_DMG_PCT  -> "Physical Damage";
            case FIRE_DMG      -> "Fire Damage";
            case ICE_DMG       -> "Ice Damage";
            case LIGHTNING_DMG -> "Lightning Damage";
            case POISON_DMG    -> "Poison Damage";
            case CRIT_CHANCE   -> "Crit Chance";
            case CRIT_DMG      -> "Crit Damage";
            case LIFE_STEAL    -> "Life Steal";
            case ATK_SPEED_PCT -> "Attack Speed";
            case THORNS        -> "Thorns";
            case PROJ_SPEED    -> "Projectile Speed";
            case PIERCE_CHANCE -> "Pierce Chance";
            case MAX_HP_PCT    -> "Max Health";
            case MANA_REGEN    -> "Mana Regen";
            case ENERGY_REGEN  -> "Energy Regen";
            case EXP_BONUS     -> "EXP Bonus";
            default            -> type;
        };
    }

    public boolean isPercent() {
        return switch (type) {
            case PHYS_DMG_PCT, CRIT_CHANCE, CRIT_DMG, LIFE_STEAL,
                 ATK_SPEED_PCT, PIERCE_CHANCE, MAX_HP_PCT, EXP_BONUS -> true;
            default -> false;
        };
    }

    public net.minecraft.nbt.CompoundTag toNbt() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString (K_TYPE,   type);
        tag.putFloat  (K_VALUE,  value);
        tag.putInt    (K_TIER,   tier);
        tag.putBoolean(K_PREFIX, isPrefix);
        return tag;
    }

    public static TRLAffix fromNbt(net.minecraft.nbt.CompoundTag tag) {
        return new TRLAffix(
                tag.getString(K_TYPE),
                tag.getFloat(K_VALUE),
                tag.getInt(K_TIER),
                tag.getBoolean(K_PREFIX));
    }
}
