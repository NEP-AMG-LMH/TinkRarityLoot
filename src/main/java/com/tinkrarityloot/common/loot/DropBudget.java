package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Loot budgeting: prevents drop flooding when Mine and Slash or Apotheosis
 * have already contributed gear to the same kill event.
 *
 * ── Strategy ──────────────────────────────────────────────────────────────
 *
 *  Additive drops (no budgeting, config option) — simplest; can feel excessive
 *  when both TRL and M&S drop items from the same mob.
 *
 *  Budgeted drops (default) — for each "premium competing item" already in the
 *  drop list, reduce TRL's base chance by {@code config.budgetedDropReduction}
 *  (default 0.40 = -40% per item). The floor is 10% of the original chance.
 *
 * ── What counts as a competing premium item ───────────────────────────────
 *
 *  Detection is NBT-based (no hard mod dependency):
 *   • Mine and Slash gear:      contains "mas_item_level" OR "mas_rarity"
 *   • Apotheosis affix items:   contains "apoth_affix_data" or "apoth_gem_data"
 *   • Apotheosis gems:          contains "apoth_rarity"     (socket gems)
 *
 *  Vanilla drops (XP orbs, bones, arrows, etc.) are never counted.
 *
 * ── Returns ───────────────────────────────────────────────────────────────
 *
 *  multiplier(drops) → double in [0.10, 1.0]
 *     1.0 = no competitors detected, proceed at full chance
 *     < 1.0 = reduce proportionally per competitor found
 */
public final class DropBudget {

    private DropBudget() {}

    /**
     * Compute a multiplier [0.10, 1.0] to apply to the final drop chance.
     *
     * @param existingDrops The drop list from the current {@code LivingDropsEvent}
     *                      (may include items added by other event listeners
     *                       that fired before TRL's).
     * @return 1.0 when budgeting is disabled or no competitors found.
     */
    public static double multiplier(Collection<ItemEntity> existingDrops) {
        if (!TRLConfig.SERVER.enableLootBudgeting.get()) return 1.0;

        int competitors = 0;
        for (ItemEntity ie : existingDrops) {
            ItemStack stack = ie.getItem();
            if (stack.isEmpty() || !stack.hasTag()) continue;
            var tag = stack.getTag();
            if (tag.contains("mas_item_level")
                    || tag.contains("mas_rarity")
                    || tag.contains("apoth_affix_data")
                    || tag.contains("apoth_gem_data")
                    || tag.contains("apoth_rarity")) {
                competitors++;
            }
        }

        if (competitors == 0) return 1.0;

        double reductionPerItem = TRLConfig.SERVER.budgetedDropReduction.get();
        double multiplier = Math.max(0.10, 1.0 - competitors * reductionPerItem);

        TinkRarityLoot.LOGGER.debug(
                "[TRL] Budget: {} competing item(s) → chance ×{:.2f}", competitors, multiplier);
        return multiplier;
    }
}
