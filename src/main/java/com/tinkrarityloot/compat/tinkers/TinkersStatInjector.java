package com.tinkrarityloot.compat.tinkers;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.event.ToolAssemblyHandler;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Stores TRL stat bonuses in vanilla NBT and triggers the initial patch.
 *
 * ── Architecture ──────────────────────────────────────────────────────────
 *
 * Single path: ToolStatPatcher is the sole authority for writing tic_stats.
 * This class only:
 *   1. Sums bonuses from part stat blocks
 *   2. Writes them to vanilla root NBT (trl_b_* keys) for persistence
 *   3. Calls ToolStatPatcher.patch() once for the initial application
 *
 * TRLRarityModifier is kept only for the tooltip badge — it does NOT inject
 * stats (its addToolStats hook is empty). The modifier path was abandoned
 * because TCon 3.11 rebuilds tic_modifiers from tic_materials on every
 * rebuildStats() call, wiping any injected entries.
 *
 * ── Storage keys (vanilla root tag) ──────────────────────────────────────
 *
 *   trl_applied   byte 1  — set once at assembly; guards against re-application
 *   trl_b_dur     float   — durability bonus
 *   trl_b_dmg     float   — damage bonus
 *   trl_b_atkspd  float   — attack speed delta
 *   trl_b_vel     float   — velocity bonus (ranged/thrown)
 *   trl_b_draw    float   — draw speed delta (ranged)
 *   trl_b_acc     float   — accuracy delta (ranged/thrown)
 */
public final class TinkersStatInjector {

    public static final String K_APPLIED  = "trl_applied";
    public static final String K_B_DUR    = "trl_b_dur";
    public static final String K_B_DMG    = "trl_b_dmg";
    public static final String K_B_ATKSPD = "trl_b_atkspd";
    public static final String K_B_VEL    = "trl_b_vel";
    public static final String K_B_DRAW   = "trl_b_draw";
    public static final String K_B_ACC    = "trl_b_acc";

    private static final float MAX_ATK_SPD =  0.18f;
    private static final float MIN_ATK_SPD = -0.20f;

    private TinkersStatInjector() {}

    /**
     * Called at tool assembly. Stores bonuses in vanilla NBT and applies
     * the initial stat patch via ToolStatPatcher.
     */
    public static void applyBonuses(ItemStack toolStack, List<WeaponStatBlock> partStats) {
        if (!TRLConfig.SERVER.enableTinkersStatInjection.get()) return;
        if (partStats.isEmpty()) return;

        CompoundTag root = toolStack.getOrCreateTag();
        if (root.getByte(K_APPLIED) == 1) {
            // Already applied (repair path) — just re-patch stats
            com.tinkrarityloot.common.event.ToolStatPatcher.patch(toolStack);
            return;
        }

        // Sum bonuses
        float totalDur = (float) partStats.stream().mapToDouble(s -> s.durability).sum();
        float maxDmg   = (float) partStats.stream().mapToDouble(s -> s.damage).max().orElse(0);
        float atkSpd   = clamp(
                (float) partStats.stream().mapToDouble(s -> s.attackSpeedDelta).sum(),
                MIN_ATK_SPD, MAX_ATK_SPD);
        float vel      = (float) partStats.stream().mapToDouble(s -> s.velocity).sum();
        float draw     = (float) partStats.stream().mapToDouble(s -> s.drawSpeed).sum();
        float acc      = (float) partStats.stream().mapToDouble(s -> s.accuracy).sum();
        WeaponFamily family = partStats.get(0).family;

        // Write to vanilla root NBT
        root.putByte(K_APPLIED, (byte) 1);
        root.putFloat(K_B_DUR, Math.max(0f, totalDur));
        switch (family) {
            case MELEE_LIGHT, MELEE_HEAVY -> {
                root.putFloat(K_B_DMG,    Math.max(0f, maxDmg));
                root.putFloat(K_B_ATKSPD, atkSpd);
            }
            case THROWN -> {
                root.putFloat(K_B_DMG, Math.max(0f, maxDmg));
                root.putFloat(K_B_VEL, Math.max(0f, vel));
                root.putFloat(K_B_ACC, acc);
            }
            case RANGED -> {
                root.putFloat(K_B_VEL,  Math.max(0f, vel));
                root.putFloat(K_B_DRAW, draw);
                root.putFloat(K_B_ACC,  acc);
            }
        }

        // Also store rarity index for tooltip badge
        if (toolStack.getTag() != null
                && toolStack.getTag().contains(ToolAssemblyHandler.TOOL_RARITY_KEY)) {
            root.putInt("trl_rarity_idx", rarityOrdinal(
                    toolStack.getTag().getString(ToolAssemblyHandler.TOOL_RARITY_KEY)));
        }

        // Apply initial patch — ToolStatPatcher is the sole writer of tic_stats
        com.tinkrarityloot.common.event.ToolStatPatcher.patch(toolStack);

        TinkRarityLoot.LOGGER.info(
                "[TRL] applyBonuses complete: family={} dur={} dmg={} atkSpd={}",
                family, (int) totalDur, maxDmg, atkSpd);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int rarityOrdinal(String key) {
        return switch (key) {
            case "uncommon"  -> 1;
            case "rare"      -> 2;
            case "epic"      -> 3;
            case "unique"    -> 4;
            case "legendary" -> 5;
            case "mythic"    -> 6;
            default          -> 0;
        };
    }
}
