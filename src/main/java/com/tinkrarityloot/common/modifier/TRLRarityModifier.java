package com.tinkrarityloot.common.modifier;

import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.List;

/**
 * TCon modifier used solely for the rarity badge tooltip on assembled tools.
 *
 * ── Why no addToolStats ───────────────────────────────────────────────────
 *
 * TCon 3.11 rebuilds tic_modifiers from tic_materials on every rebuildStats()
 * call. Our modifier is not in tic_materials (it's not a material trait), so
 * it never appears in tic_modifiers and addToolStats() never fires.
 *
 * Stat injection is handled entirely by ToolStatPatcher, which writes directly
 * to tic_stats and uses base+bonus snapshots to stay idempotent across rebuilds.
 *
 * ── Tooltip ───────────────────────────────────────────────────────────────
 *
 * The rarity badge shown in the tool tooltip comes from TooltipRenderer,
 * which reads trl_tool_rarity from the vanilla root NBT tag directly.
 * This modifier's tooltip hook is kept as a fallback but TooltipRenderer
 * is the primary display path.
 */
public class TRLRarityModifier extends Modifier implements TooltipModifierHook {

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           Player player, List<Component> tooltip,
                           TooltipKey key, TooltipFlag flag) {
        // Badge is handled by TooltipRenderer.appendToolLines() — no-op here
        // to avoid duplicate display.
    }
}
