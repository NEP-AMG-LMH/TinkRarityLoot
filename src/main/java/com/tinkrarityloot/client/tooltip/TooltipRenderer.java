package com.tinkrarityloot.client.tooltip;

import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.event.ToolAssemblyHandler;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Owns the complete TRL tooltip layout for parts and assembled tools.
 *
 * TRL is the single visual authority. We render at HIGHEST priority
 * so M&S and Apotheosis always appear below our block.
 *
 * ── Part tooltip layout (melee example) ──────────────────────────────────────
 *
 *   ✦ Epic                               ← nameComponent()
 *   ─────────────────────
 *     Durability    2,240                ← all families
 *     Damage        +9.80               ← melee / thrown
 *     Atk Speed    +0.04               ← melee only (omitted if 0)
 *   ─────────────────────
 *     Source level  18
 *
 * ── Part tooltip (ranged limb) ────────────────────────────────────────────────
 *
 *   ◆ Rare
 *   ─────────────────────
 *     Durability     960
 *     Velocity      +0.28               ← ranged + thrown
 *     Draw Speed    -0.14               ← ranged only (negative = faster)
 *     Accuracy      +0.06               ← ranged + thrown
 *   ─────────────────────
 *     Source level   9
 *
 * ── Assembled tool tooltip ────────────────────────────────────────────────────
 *
 *   [TCon tool name / stat lines]
 *   ─────────────────────
 *   Gear Tier  [Legendary]
 */
@OnlyIn(Dist.CLIENT)
public final class TooltipRenderer {

    private TooltipRenderer() {}

    private static final Component SEP = Component.literal("─────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY);

    // ── Part tooltip ──────────────────────────────────────────────────────────

    public static void appendPartLines(ItemStack stack, List<Component> lines) {
        WeaponStatBlock stats = getStats(stack);
        if (stats == null) return;

        TRLRarity rarity = stats.rarity;
        int at = Math.min(1, lines.size());

        // Rarity name
        ins(lines, at++, rarity.nameComponent());
        ins(lines, at++, SEP);

        // ── Durability (all families) ─────────────────────────────────────
        ins(lines, at++, lv("Durability",
                String.format("%,d", stats.durability), rarityColour(rarity)));

        // ── Family-specific stat lines ────────────────────────────────────
        switch (stats.family) {
            case MELEE_LIGHT, MELEE_HEAVY -> {
                ins(lines, at++, lv("Damage",
                        fmt("+%.2f", stats.damage), rarityColour(rarity)));
                if (Math.abs(stats.attackSpeedDelta) > 0.001f) {
                    ins(lines, at++, lv("Atk Speed",
                            fmt("%+.3f", stats.attackSpeedDelta), atkSpdColour(stats.attackSpeedDelta)));
                }
            }
            case THROWN -> {
                ins(lines, at++, lv("Throw Damage",
                        fmt("+%.2f", stats.damage), rarityColour(rarity)));
                if (stats.velocity > 0.001f)
                    ins(lines, at++, lv("Velocity",
                            fmt("+%.3f", stats.velocity), ChatFormatting.AQUA));
                if (Math.abs(stats.accuracy) > 0.001f)
                    ins(lines, at++, lv("Accuracy",
                            fmt("%+.3f", stats.accuracy), ChatFormatting.GREEN));
            }
            case RANGED -> {
                if (stats.velocity > 0.001f)
                    ins(lines, at++, lv("Velocity",
                            fmt("+%.3f", stats.velocity), ChatFormatting.AQUA));
                if (Math.abs(stats.drawSpeed) > 0.001f)
                    ins(lines, at++, lv("Draw Speed",
                            fmt("%+.3f", stats.drawSpeed), drawSpdColour(stats.drawSpeed)));
                if (Math.abs(stats.accuracy) > 0.001f)
                    ins(lines, at++, lv("Accuracy",
                            fmt("%+.3f", stats.accuracy), ChatFormatting.GREEN));
            }
        }

        // Source level footer
        ins(lines, at++, SEP);
        ins(lines, at, Component.literal("  Source level  ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(String.valueOf(stats.mobLevel))
                        .withStyle(ChatFormatting.GRAY)));
    }

    // ── Assembled tool tooltip ─────────────────────────────────────────────────

    public static void appendToolLines(ItemStack stack, List<Component> lines) {
        if (!stack.hasTag()) return;
        if (!stack.getTag().contains(ToolAssemblyHandler.TOOL_RARITY_KEY)) return;

        net.minecraft.nbt.CompoundTag root = stack.getTag();
        TRLRarity rarity = TRLRarity.fromKey(root.getString(ToolAssemblyHandler.TOOL_RARITY_KEY));

        lines.add(Component.empty());
        lines.add(SEP);
        lines.add(Component.literal("  Gear Tier  ")
                .withStyle(ChatFormatting.GRAY)
                .append(rarity.badgeComponent()));

        // ── Requirements ──────────────────────────────────────────────────────
        if (root.contains(com.tinkrarityloot.common.rpg.TRLRequirements.K_LEVEL)) {
            int lvl = root.getInt(com.tinkrarityloot.common.rpg.TRLRequirements.K_LEVEL);
            int str = root.getInt(com.tinkrarityloot.common.rpg.TRLRequirements.K_STR);
            int dex = root.getInt(com.tinkrarityloot.common.rpg.TRLRequirements.K_DEX);
            int intel = root.getInt(com.tinkrarityloot.common.rpg.TRLRequirements.K_INT);

            lines.add(Component.literal("  Requires  ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Level " + lvl)
                            .withStyle(ChatFormatting.RED)));
            StringBuilder statReqs = new StringBuilder();
            if (str   > 0) statReqs.append("STR ").append(str).append("  ");
            if (dex   > 0) statReqs.append("DEX ").append(dex).append("  ");
            if (intel > 0) statReqs.append("INT ").append(intel);
            if (!statReqs.isEmpty()) {
                lines.add(Component.literal("  ")
                        .append(Component.literal(statReqs.toString().trim())
                                .withStyle(ChatFormatting.RED)));
            }
        }

        // Show TRL bonus breakdown if the tool has been assembled with TRL parts
        if (root.getByte(com.tinkrarityloot.compat.tinkers.TinkersStatInjector.K_APPLIED) == 1) {
            float bonusDur  = root.getFloat(com.tinkrarityloot.compat.tinkers.TinkersStatInjector.K_B_DUR);
            float bonusDmg  = root.getFloat(com.tinkrarityloot.compat.tinkers.TinkersStatInjector.K_B_DMG);
            float bonusAspd = root.getFloat(com.tinkrarityloot.compat.tinkers.TinkersStatInjector.K_B_ATKSPD);

            // Base + bonus breakdown from stored snapshots
            float baseDur = root.contains("trl_base_dur") ? root.getFloat("trl_base_dur") : 0f;
            float baseDmg = root.contains("trl_base_dmg") ? root.getFloat("trl_base_dmg") : 0f;

            ChatFormatting bonusColour = ChatFormatting.GREEN;

            // Use symmetric snapshots for accurate base/final display
            float finalDur  = root.contains("trl_final_dur")  ? root.getFloat("trl_final_dur")  : baseDur + bonusDur;
            float finalDmg  = root.contains("trl_final_dmg")  ? root.getFloat("trl_final_dmg")  : baseDmg + bonusDmg;
            float finalAspd = root.contains("trl_final_aspd") ? root.getFloat("trl_final_aspd") : baseDmg + bonusAspd;

            if (bonusDur > 0) {
                lines.add(Component.literal("  Durability  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%,d + %,d = %,d",
                                (int)baseDur, (int)bonusDur, (int)finalDur))
                                .withStyle(bonusColour)));
            }
            if (bonusDmg > 0) {
                lines.add(Component.literal("  Attack Damage  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.2f + %.2f = %.2f",
                                baseDmg, bonusDmg, finalDmg))
                                .withStyle(bonusColour)));
            }
            if (Math.abs(bonusAspd) > 0.001f) {
                String sign = bonusAspd > 0 ? "+" : "";
                lines.add(Component.literal("  Attack Speed  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%s%.3f", sign, bonusAspd))
                                .withStyle(bonusAspd > 0 ? bonusColour : ChatFormatting.RED)));
            }
        }

        // ── Affixes ───────────────────────────────────────────────────────────
        if (root.contains(com.tinkrarityloot.common.rpg.TRLAffix.K_LIST)) {
            net.minecraft.nbt.ListTag affixList = root.getList(
                    com.tinkrarityloot.common.rpg.TRLAffix.K_LIST,
                    net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (!affixList.isEmpty()) {
                lines.add(Component.empty());
                // Prefixes first
                for (int i = 0; i < affixList.size(); i++) {
                    com.tinkrarityloot.common.rpg.TRLAffix affix =
                            com.tinkrarityloot.common.rpg.TRLAffix.fromNbt(
                                    affixList.getCompound(i));
                    if (!affix.isPrefix()) continue;
                    String valStr = affix.isPercent()
                            ? String.format("+%.1f%%", affix.value())
                            : String.format("+%.1f", affix.value());
                    lines.add(Component.literal("  ✦ ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(valStr + " " + affix.displayName()
                                    + " [T" + affix.tier() + "]")
                                    .withStyle(ChatFormatting.AQUA)));
                }
                // Suffixes
                for (int i = 0; i < affixList.size(); i++) {
                    com.tinkrarityloot.common.rpg.TRLAffix affix =
                            com.tinkrarityloot.common.rpg.TRLAffix.fromNbt(
                                    affixList.getCompound(i));
                    if (affix.isPrefix()) continue;
                    String valStr = affix.isPercent()
                            ? String.format("+%.1f%%", affix.value())
                            : String.format("+%.1f", affix.value());
                    lines.add(Component.literal("  ◆ ")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal(valStr + " " + affix.displayName()
                                    + " [T" + affix.tier() + "]")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
                }
            }
        }
    }

    // ── Capability / NBT reader ────────────────────────────────────────────────

    @Nullable
    public static WeaponStatBlock getStats(ItemStack stack) {
        // Guard: CAPABILITY may be unregistered on the client during early tooltip events
        if (stack.isEmpty()) return null;
        try {
            WeaponStatBlock fromCap = stack.getCapability(ITRLPartStats.CAPABILITY)
                    .filter(c -> c.hasStats() && c.getStats() != null).map(ITRLPartStats::getStats).orElse(null);
            if (fromCap != null) return fromCap;
        } catch (Exception ignored) {}
        if (stack.hasTag() && WeaponStatBlock.isPresent(stack.getTag()))
            return WeaponStatBlock.fromNBT(stack.getTag());
        return null;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /** Label + value line: "  Label    value" */
    private static Component lv(String label, String value, ChatFormatting valueColour) {
        return Component.literal("  " + label + "  ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColour));
    }

    private static String fmt(String pattern, float val) {
        return String.format(pattern, val);
    }

    private static void ins(List<Component> lines, int index, Component c) {
        if (index <= lines.size()) lines.add(index, c);
        else lines.add(c);
    }

    /** Single colour function — value colour matches rarity tier. */
    private static ChatFormatting rarityColour(TRLRarity r) {
        return switch (r) {
            case COMMON, UNCOMMON -> ChatFormatting.WHITE;
            case RARE             -> ChatFormatting.AQUA;
            case EPIC             -> ChatFormatting.LIGHT_PURPLE;
            case UNIQUE           -> ChatFormatting.GOLD;
            case LEGENDARY        -> ChatFormatting.YELLOW;
            case MYTHIC           -> ChatFormatting.RED;
        };
    }

    /** Attack speed: green if positive (faster), red if negative (slower). */
    private static ChatFormatting atkSpdColour(float delta) {
        return delta >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    /** Draw speed: in TCon convention negative = faster = good (green). */
    private static ChatFormatting drawSpdColour(float delta) {
        return delta <= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
    }
}
