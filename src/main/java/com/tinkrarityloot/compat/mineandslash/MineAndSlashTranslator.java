package com.tinkrarityloot.compat.mineandslash;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Secondary translator: TRL rarity → Mine and Slash gear rarity.
 *
 * ── Responsibility boundary ──────────────────────────────────────────────────
 * Called ONLY from {@link com.tinkrarityloot.common.event.ToolAssemblyHandler}
 * after a tool is assembled.  It writes the M&S rarity tier onto the tool's
 * NBT / capability so M&S knows which stat bracket the tool belongs to.
 *
 * It does NOT render anything.  TRL owns all rendering.
 *
 * ── M&S gear rarity mapping ───────────────────────────────────────────────────
 * Mine and Slash uses a separate gear rarity system for determining which
 * stat brackets an item rolls in. Typical M&S rarity tiers (verify for your
 * version):
 *
 *   NORMAL / MAGIC / RARE / EPIC / LEGENDARY / UNIQUE
 *
 * TRL mapping:
 *   COMMON    → NORMAL
 *   UNCOMMON  → MAGIC
 *   RARE      → RARE
 *   EPIC      → EPIC
 *   UNIQUE    → UNIQUE
 *   LEGENDARY → LEGENDARY
 *   MYTHIC    → LEGENDARY  (no Mythic tier in base M&S; falls to Legendary)
 *
 * Adjust the M&S class/method/enum names below to match your exact M&S build.
 * This class is 100% reflective so it compiles without M&S on the classpath.
 */
public final class MineAndSlashTranslator {

    private static boolean available = false;

    // Cached reflected references
    private static Class<?> gearRarityClass    = null;
    private static Object[] gearRarityValues   = null;
    private static Method   setGearRarityMethod = null;

    // TRL ordinal → M&S GearRarity index
    // Adjust if M&S enum ordering differs
    private static final int[] MAS_INDEX = {
        0,  // COMMON    → NORMAL     (index 0)
        1,  // UNCOMMON  → MAGIC      (index 1)
        2,  // RARE      → RARE       (index 2)
        3,  // EPIC      → EPIC       (index 3)
        4,  // UNIQUE    → UNIQUE     (index 4)
        5,  // LEGENDARY → LEGENDARY  (index 5)
        5   // MYTHIC    → LEGENDARY  (index 5, no Mythic in base M&S)
    };

    private MineAndSlashTranslator() {}

    public static void init() {
        try {
            // Adjust class path to match your M&S version
            gearRarityClass  = Class.forName(
                    "com.verdantartifice.mineandslash.common.items.rarity.GearRarity");
            gearRarityValues = (Object[]) gearRarityClass.getMethod("values").invoke(null);

            // M&S typically stores rarity on item capabilities or NBT
            // This method signature assumes a static utility; adjust as needed
            setGearRarityMethod = Class.forName(
                    "com.verdantartifice.mineandslash.common.items.MASItemHelper")
                    .getMethod("setGearRarity", ItemStack.class, gearRarityClass);

            available = true;
            TinkRarityLoot.LOGGER.info("[TRL] M&S gear-rarity translator ready ({} tiers).",
                    gearRarityValues.length);
        } catch (Exception e) {
            available = false;
            TinkRarityLoot.LOGGER.debug(
                    "[TRL] M&S gear-rarity translator unavailable (API mismatch?): {}",
                    e.getMessage());
        }
    }

    /**
     * Write the M&S gear rarity equivalent of {@code trlRarity} onto
     * {@code toolStack}.  Called once at tool-assembly time.
     *
     * If M&S is absent or the reflection fails, this is a silent no-op.
     */
    public static void translateToStack(ItemStack toolStack, TRLRarity trlRarity) {
        if (!available) return;
        try {
            int idx = Math.min(MAS_INDEX[trlRarity.ordinal()], gearRarityValues.length - 1);
            Object masRarity = gearRarityValues[idx];
            setGearRarityMethod.invoke(null, toolStack, masRarity);
            TinkRarityLoot.LOGGER.debug("[TRL] M&S translation: {} → {} (index {})",
                    trlRarity, masRarity, idx);
        } catch (Exception e) {
            TinkRarityLoot.LOGGER.debug("[TRL] M&S translation failed: {}", e.getMessage());
        }
    }

    /**
     * Fallback: write the TRL rarity key directly into the item's top-level NBT
     * under the key {@code "mas_rarity_override"}.  M&S can be configured to
     * read this key as a rarity hint if the reflective path is unavailable.
     */
    public static void writeNbtFallback(ItemStack toolStack, TRLRarity trlRarity) {
        // M&S rarity name as a plain string (lower-case)
        String masName = switch (trlRarity) {
            case COMMON    -> "normal";
            case UNCOMMON  -> "magic";
            case RARE      -> "rare";
            case EPIC      -> "epic";
            case UNIQUE    -> "unique";
            case LEGENDARY, MYTHIC -> "legendary";
        };
        toolStack.getOrCreateTag().putString("mas_rarity_override", masName);
    }
}
