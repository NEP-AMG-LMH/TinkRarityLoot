package com.tinkrarityloot.common.rpg;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import com.tinkrarityloot.compat.mineandslash.MnSBridge;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Entry point: roll and apply all RPG data to an assembled TRL tool.
 *
 * Called from ToolAssemblyHandler after a tool is crafted from TRL parts.
 * The mob level comes from the lootbox NBT (trl_mob_level on the dominant part).
 *
 * ── What happens ─────────────────────────────────────────────────────────────
 *   1. Roll requirements (level gate + stat requirements)
 *   2. Roll affixes (prefix/suffix pool, count by rarity)
 *   3. Write everything to vanilla NBT via MnSBridge
 *   4. Mark trl_rpg_applied = 1 so this never runs twice
 */
public final class TRLRPGApplicator {

    public static final String K_RPG_APPLIED = "trl_rpg_applied";

    private TRLRPGApplicator() {}

    public static void apply(ItemStack tool, TRLRarity rarity,
                              WeaponFamily family, int mobLevel, Random random) {
        if (tool.isEmpty()) return;
        if (tool.hasTag() && tool.getTag().getByte(K_RPG_APPLIED) == 1) return;

        // 1. Requirements
        TRLRequirements reqs = TRLRequirementsRoller.roll(mobLevel, rarity, family);

        // 2. Affixes
        List<TRLAffix> affixes = TRLAffixRoller.roll(rarity, family, mobLevel, random);

        // 3. Write to tool via bridge (vanilla NBT + M&S API)
        MnSBridge.applyToTool(tool, reqs, affixes, rarity, family, mobLevel);

        // 4. Mark applied
        tool.getOrCreateTag().putByte(K_RPG_APPLIED, (byte) 1);

        TinkRarityLoot.LOGGER.info(
                "[TRL] RPG applied: rarity={} level={} req_lvl={} str={} dex={} int={} affixes={}",
                rarity.displayName, mobLevel,
                reqs.level(), reqs.str(), reqs.dex(), reqs.intel(),
                affixes.size());
    }
}
