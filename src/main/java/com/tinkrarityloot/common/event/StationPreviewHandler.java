package com.tinkrarityloot.common.event;

import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.event.ToolStatPatcher;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.part.IToolPart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a TRL stat preview on the Tool Station output slot tooltip.
 *
 * Only fires when:
 *   • The hovered item is an assembled IModifiable TCon tool
 *   • trl_applied is NOT set (station preview, not an inventory tool)
 *   • The open container contains at least one IToolPart with TRL data
 *
 * Shows base + bonus = final for each relevant stat.
 */
@OnlyIn(Dist.CLIENT)
public class StationPreviewHandler {

    private static final Component SEP = Component.literal("─────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Must be an assembled TCon tool, not a part
        if (!(stack.getItem() instanceof IModifiable)) return;
        if (stack.getItem() instanceof IToolPart) return;

        // Must NOT already have TRL bonuses applied (station preview, not inventory)
        if (stack.hasTag() && stack.getTag().getByte("trl_applied") == 1) return;

        Player player = event.getEntity();
        if (player == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        // Scan only IToolPart slots for TRL data
        List<WeaponStatBlock> statBlocks = new ArrayList<>();
        TRLRarity bestRarity = TRLRarity.COMMON;

        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack slot = menu.slots.get(i).getItem();
            if (slot.isEmpty() || !(slot.getItem() instanceof IToolPart)) continue;
            WeaponStatBlock sb = readStatBlock(slot);
            if (sb != null) {
                statBlocks.add(sb);
                if (sb.rarity.ordinal() > bestRarity.ordinal()) bestRarity = sb.rarity;
            }
        }

        if (statBlocks.isEmpty()) return;

        // Compute expected bonuses from parts
        float bonusDur = (float) statBlocks.stream().mapToDouble(s -> s.durability).sum();
        float bonusDmg = (float) statBlocks.stream().mapToDouble(s -> s.damage).max().orElse(0);
        float bonusAtk = (float) statBlocks.stream().mapToDouble(s -> s.attackSpeedDelta).sum();

        // Try to read the station output's projected base stats from tic_stats
        float baseDur = 0f, baseDmg = 0f;
        if (stack.hasTag() && stack.getTag().contains("tic_stats",
                net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag ts = stack.getTag().getCompound("tic_stats");
            baseDur = ts.getFloat("tconstruct:durability");
            baseDmg = ts.getFloat("tconstruct:attack_damage");
        }

        List<Component> lines = event.getToolTip();
        lines.add(Component.empty());
        lines.add(SEP);
        lines.add(Component.literal("  TRL Preview  ")
                .withStyle(ChatFormatting.GRAY)
                .append(bestRarity.badgeComponent()));

        // Show base + bonus = final
        if (bonusDur > 0) {
            lines.add(statLine("Durability",
                    baseDur > 0 ? String.format("%,d + %,d = %,d", (int)baseDur, (int)bonusDur, (int)(baseDur+bonusDur))
                                : String.format("+ %,d", (int)bonusDur)));
        }
        if (bonusDmg > 0) {
            lines.add(statLine("Attack Damage",
                    baseDmg > 0 ? String.format("%.2f + %.2f = %.2f", baseDmg, bonusDmg, baseDmg+bonusDmg)
                                : String.format("+ %.2f", bonusDmg)));
        }
        if (Math.abs(bonusAtk) > 0.001f) {
            String sign = bonusAtk > 0 ? "+" : "";
            lines.add(statLine("Attack Speed", String.format("%s%.3f", sign, bonusAtk)));
        }
    }

    private static Component statLine(String label, String value) {
        return Component.literal("  " + label + "  ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GREEN));
    }

    @Nullable
    private static WeaponStatBlock readStatBlock(ItemStack stack) {
        try {
            WeaponStatBlock fromCap = stack.getCapability(ITRLPartStats.CAPABILITY)
                    .filter(c -> c.hasStats() && c.getStats() != null)
                    .map(ITRLPartStats::getStats)
                    .orElse(null);
            if (fromCap != null) return fromCap;
        } catch (Exception ignored) {}
        if (stack.hasTag() && WeaponStatBlock.isPresent(stack.getTag()))
            return WeaponStatBlock.fromNBT(stack.getTag());
        return null;
    }
}
