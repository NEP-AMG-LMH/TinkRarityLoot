package com.tinkrarityloot.common.util;

import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Static helpers used throughout TinkRarityLoot.
 */
public final class TRLUtil {

    private TRLUtil() {}

    // ── Capability helper ─────────────────────────────────────────────────────

    /**
     * Safe capability query — returns null instead of throwing if the
     * capability has not been registered yet (e.g. during early tooltip events
     * before RegisterCapabilitiesEvent fires on the client).
     */
    @Nullable
    private static ITRLPartStats getPartStats(ItemStack stack) {
        try {
            return stack.getCapability(ITRLPartStats.CAPABILITY)
                    .map(c -> c.hasStats() ? c : null)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Stack inspection ─────────────────────────────────────────────────────

    /**
     * Read the TRL rarity of any stack — part or assembled tool.
     * Priority: capability → flat part NBT → tool rarity key → COMMON.
     */
    public static TRLRarity rarityOf(ItemStack stack) {
        if (stack.isEmpty()) return TRLRarity.COMMON;

        ITRLPartStats cap = getPartStats(stack);
        if (cap != null && cap.getStats() != null) return cap.getStats().rarity;

        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag.contains(WeaponStatBlock.K_RARITY))
                return TRLRarity.fromKey(tag.getString(WeaponStatBlock.K_RARITY));
            if (tag.contains(com.tinkrarityloot.common.event.ToolAssemblyHandler.TOOL_RARITY_KEY))
                return TRLRarity.fromKey(
                        tag.getString(com.tinkrarityloot.common.event.ToolAssemblyHandler.TOOL_RARITY_KEY));
        }
        return TRLRarity.COMMON;
    }

    /**
     * Returns the {@link WeaponStatBlock} of a stack, or {@code null} if none.
     */
    @Nullable
    public static WeaponStatBlock statsOf(ItemStack stack) {
        if (stack.isEmpty()) return null;

        ITRLPartStats cap = getPartStats(stack);
        if (cap != null) return cap.getStats();

        if (stack.hasTag() && WeaponStatBlock.isPresent(stack.getTag()))
            return WeaponStatBlock.fromNBT(stack.getTag());
        return null;
    }

    /** True if the stack is a TRL-rolled Tinkers part (has any rarity stamped). */
    public static boolean isTRLPart(ItemStack stack) {
        return statsOf(stack) != null
                || (stack.hasTag() && stack.getTag().contains(WeaponStatBlock.K_RARITY));
    }

    /** True if the stack is an assembled Tinkers tool with a TRL gear tier. */
    public static boolean isTRLTool(ItemStack stack) {
        return stack.hasTag()
                && stack.getTag().contains(
                        com.tinkrarityloot.common.event.ToolAssemblyHandler.TOOL_RARITY_KEY);
    }

    // ── Level helpers ─────────────────────────────────────────────────────────

    /** Clamp mob level to the range the stat formulas are designed for. */
    public static int clampMobLevel(int raw) {
        return Math.max(1, Math.min(raw, 500));
    }
}
