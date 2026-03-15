package com.tinkrarityloot.common.rpg;

import net.minecraft.nbt.CompoundTag;

/**
 * Level and stat requirements for an assembled TRL tool.
 *
 * ── NBT keys ──────────────────────────────────────────────────────────────────
 *   trl_req_level   int   — minimum player level to equip
 *   trl_req_str     int   — minimum Strength (melee heavy weapons)
 *   trl_req_dex     int   — minimum Dexterity (melee light / ranged)
 *   trl_req_int     int   — minimum Intelligence (magical / ranged)
 *
 * Values are set at assembly time from the lootbox's mob level and rarity.
 * The MnSRequirementsBridge also writes these to M&S's native NBT keys
 * (if M&S is present) so the game engine enforces them automatically.
 */
public record TRLRequirements(int level, int str, int dex, int intel) {

    public static final String K_LEVEL = "trl_req_level";
    public static final String K_STR   = "trl_req_str";
    public static final String K_DEX   = "trl_req_dex";
    public static final String K_INT   = "trl_req_int";

    public void writeToTag(CompoundTag tag) {
        tag.putInt(K_LEVEL, level);
        tag.putInt(K_STR,   str);
        tag.putInt(K_DEX,   dex);
        tag.putInt(K_INT,   intel);
    }

    public static TRLRequirements readFromTag(CompoundTag tag) {
        return new TRLRequirements(
                tag.getInt(K_LEVEL),
                tag.getInt(K_STR),
                tag.getInt(K_DEX),
                tag.getInt(K_INT));
    }

    public static TRLRequirements none() {
        return new TRLRequirements(1, 0, 0, 0);
    }
}
