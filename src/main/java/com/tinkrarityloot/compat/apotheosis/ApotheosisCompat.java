package com.tinkrarityloot.compat.apotheosis;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Secondary translator: TRL rarity → Apotheosis rarity.
 *
 * ── Responsibility boundary ──────────────────────────────────────────────────
 * This class does ONE thing only: when a tool is assembled, it writes the
 * Apotheosis-compatible rarity data onto the tool's NBT so that Apotheosis
 * systems (gem sockets, affix quality brackets) honour the same tier.
 *
 * It does NOT:
 *   • Drive tooltip rendering  (owned by TooltipRenderer)
 *   • Drive glint rendering    (owned by TRLGlintRenderer)
 *   • Roll or modify the TRL rarity (owned by StatRoller / ConfiguredRarityRoller)
 *
 * Call site: {@link com.tinkrarityloot.common.event.ToolAssemblyHandler#applyRarity}
 * which is the only place this class is invoked.
 *
 * ── Apotheosis rarity mapping ─────────────────────────────────────────────────
 * TRL          → Apotheosis LootRarity
 * COMMON       → common    (index 0)
 * UNCOMMON     → uncommon  (index 1)
 * RARE         → rare      (index 2)
 * EPIC         → epic      (index 3)
 * UNIQUE       → epic      (index 3, no direct Unique in base Apoth)
 * LEGENDARY    → ancient   (index 4)
 * MYTHIC       → mythic    (index 5)
 *
 * Adjust indices if your Apotheosis version has a different enum ordering.
 */
public final class ApotheosisCompat {

    private static boolean available = false;
    private static Class<?> lootRarityClass  = null;
    private static Object[] rarityConstants  = null;
    private static Method   getNameMethod    = null;

    // TRL ordinal → Apotheosis LootRarity index
    private static final int[] APOTH_INDEX = { 0, 1, 2, 3, 3, 4, 5 };

    private ApotheosisCompat() {}

    public static void init() {
        try {
            lootRarityClass = Class.forName(
                    "dev.shadowsoffire.apotheosis.adventure.loot.LootRarity");
            rarityConstants = (Object[]) lootRarityClass.getMethod("values").invoke(null);
            getNameMethod   = lootRarityClass.getMethod("getName");
            available = true;
            TinkRarityLoot.LOGGER.info(
                    "[TRL] Apotheosis translator ready ({} rarity constants).",
                    rarityConstants.length);
        } catch (Exception e) {
            available = false;
            TinkRarityLoot.LOGGER.warn("[TRL] Apotheosis translator unavailable: {}", e.getMessage());
        }
    }

    /**
     * Translate {@code trlRarity} and write the Apotheosis rarity key onto
     * {@code toolStack}.  Called once at tool-assembly time.
     *
     * Apotheosis reads the {@code "apoth_rarity"} NBT string when deciding
     * gem-socket quality and affix roll brackets.
     */
    public static void translateToStack(ItemStack toolStack, TRLRarity trlRarity) {
        if (!available) return;
        try {
            int idx = Math.min(APOTH_INDEX[trlRarity.ordinal()], rarityConstants.length - 1);
            String apothName = (String) getNameMethod.invoke(rarityConstants[idx]);
            toolStack.getOrCreateTag().putString("apoth_rarity", apothName);
            TinkRarityLoot.LOGGER.debug("[TRL] Apotheosis translation: {} → {}", trlRarity, apothName);
        } catch (Exception e) {
            TinkRarityLoot.LOGGER.debug("[TRL] Apotheosis translation failed: {}", e.getMessage());
        }
    }

    /** @deprecated Use {@link #translateToStack} — old name kept for callsite compat. */
    @Deprecated
    public static void stampRarityOnStack(ItemStack stack, TRLRarity rarity) {
        translateToStack(stack, rarity);
    }
}

