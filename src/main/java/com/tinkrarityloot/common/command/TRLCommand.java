package com.tinkrarityloot.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.event.ToolAssemblyHandler;
import com.tinkrarityloot.common.loot.TRLDropTable;
import com.tinkrarityloot.common.material.MaterialSelector;
import com.tinkrarityloot.common.material.PartStatType;
import com.tinkrarityloot.common.rarity.ConfiguredRarityRoller;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import com.tinkrarityloot.common.weapon.WeaponStatRoller;
import com.tinkrarityloot.common.weapon.WeaponStatTemplate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * /trl – developer / admin debug command family (op level 2).
 *
 * Subcommands:
 *   /trl inspect               – show TRL WeaponStatBlock on held item
 *   /trl simulate <level>      – roll a LIGHT_BLADE at given level and print result
 *   /trl simulateFamily <family> <level> – roll chosen family at given level
 *   /trl mobLevel              – print Mine and Slash level of nearest mob
 *   /trl dropTable             – list weighted drop pool entries
 *   /trl rarityTable           – list configured rarity weights + multipliers
 *   /trl materials <slot>      – list valid TCon materials for HEAD/HANDLE/EXTRA/LIMB
 */
public class TRLCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("trl")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("inspect")
                        .executes(TRLCommand::inspect))

                .then(Commands.literal("simulate")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 500))
                                .executes(TRLCommand::simulate)))

                .then(Commands.literal("simulateFamily")
                        .then(Commands.argument("family",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (WeaponFamily f : WeaponFamily.values()) b.suggest(f.name().toLowerCase());
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 500))
                                        .executes(TRLCommand::simulateFamily))))

                .then(Commands.literal("mobLevel")
                        .executes(TRLCommand::mobLevel))

                .then(Commands.literal("dropTable")
                        .executes(TRLCommand::dropTable))

                .then(Commands.literal("rarityTable")
                        .executes(TRLCommand::rarityTable))

                .then(Commands.literal("materials")
                        .then(Commands.argument("slot",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (PartStatType t : PartStatType.values()) b.suggest(t.name().toLowerCase());
                                    return b.buildFuture();
                                })
                                .executes(TRLCommand::materials)))

                .then(Commands.literal("dumptool")
                        .executes(TRLCommand::dumpTool))

                .then(Commands.literal("repatch")
                        .executes(TRLCommand::repatch))
        );
    }

    // ── /trl inspect ─────────────────────────────────────────────────────────

    private static int inspect(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = getPlayer(src);
        if (player == null) return 0;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("Hold a Tinkers part or assembled tool.")); return 0;
        }

        src.sendSuccess(() -> header("=== TRL Inspect: " + held.getHoverName().getString() + " ==="), false);

        // ── Part stats via capability ─────────────────────────────────────────
        boolean[] hadStats = { false };
        held.getCapability(ITRLPartStats.CAPABILITY).ifPresent(cap -> {
            if (!cap.hasStats()) return;
            hadStats[0] = true;
            WeaponStatBlock s = cap.getStats();
            send(src, "  Rarity:      " + s.rarity.displayName + " (×" + String.format("%.2f", s.rarity.multiplier) + ")", s.rarity.colour);
            send(src, "  Family:      " + s.family.name(), ChatFormatting.GRAY);
            send(src, "  Mob Level:   " + s.mobLevel, ChatFormatting.GRAY);
            send(src, "  Durability:  " + String.format("%,d", s.durability), ChatFormatting.WHITE);

            switch (s.family) {
                case MELEE_LIGHT, MELEE_HEAVY -> {
                    send(src, "  Damage:      " + String.format("+%.2f", s.damage), ChatFormatting.WHITE);
                    if (Math.abs(s.attackSpeedDelta) > 0.001f)
                        send(src, "  Atk Speed:   " + String.format("%+.3f", s.attackSpeedDelta),
                                s.attackSpeedDelta >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
                }
                case THROWN -> {
                    send(src, "  Throw Dmg:   " + String.format("+%.2f", s.damage), ChatFormatting.WHITE);
                    if (s.velocity > 0.001f)
                        send(src, "  Velocity:    " + String.format("+%.3f", s.velocity), ChatFormatting.AQUA);
                    if (Math.abs(s.accuracy) > 0.001f)
                        send(src, "  Accuracy:    " + String.format("%+.3f", s.accuracy), ChatFormatting.GREEN);
                }
                case RANGED -> {
                    if (s.velocity > 0.001f)
                        send(src, "  Velocity:    " + String.format("+%.3f", s.velocity), ChatFormatting.AQUA);
                    if (Math.abs(s.drawSpeed) > 0.001f)
                        send(src, "  Draw Speed:  " + String.format("%+.3f", s.drawSpeed),
                                s.drawSpeed <= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
                    if (Math.abs(s.accuracy) > 0.001f)
                        send(src, "  Accuracy:    " + String.format("%+.3f", s.accuracy), ChatFormatting.GREEN);
                }
            }
        });

        // ── Fallback: flat NBT ────────────────────────────────────────────────
        if (!hadStats[0] && held.hasTag() && WeaponStatBlock.isPresent(held.getTag())) {
            WeaponStatBlock s = WeaponStatBlock.fromNBT(held.getTag());
            send(src, "  [From NBT fallback]", ChatFormatting.DARK_GRAY);
            send(src, "  Rarity: " + s.rarity.displayName, s.rarity.colour);
            send(src, "  Family: " + s.family.name(), ChatFormatting.GRAY);
        } else if (!hadStats[0]) {
            send(src, "  [No TRL part stats attached]", ChatFormatting.DARK_GRAY);
        }

        // ── Assembled tool rarity ─────────────────────────────────────────────
        if (held.hasTag() && held.getTag().contains(ToolAssemblyHandler.TOOL_RARITY_KEY)) {
            TRLRarity r = TRLRarity.fromKey(held.getTag().getString(ToolAssemblyHandler.TOOL_RARITY_KEY));
            send(src, "  Gear Tier: " + r.displayName, r.colour);
        }

        TinkRarityLoot.LOGGER.debug("[TRL inspect] NBT: {}", held.getTag());
        return 1;
    }

    // ── /trl simulate <level> ─────────────────────────────────────────────────

    private static int simulate(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        WeaponStatBlock stats = WeaponStatRoller.roll(
                level, WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_BLADE, new Random());
        printSimResult(ctx.getSource(), stats, "Simulate LIGHT_BLADE");
        return 1;
    }

    // ── /trl simulateFamily <family> <level> ──────────────────────────────────

    private static int simulateFamily(CommandContext<CommandSourceStack> ctx) {
        String familyArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "family");
        int    level     = IntegerArgumentType.getInteger(ctx, "level");

        WeaponFamily family;
        WeaponStatTemplate template;
        try {
            family = WeaponFamily.valueOf(familyArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown family '" + familyArg + "'. Use: melee_light, melee_heavy, thrown, ranged"));
            return 0;
        }

        // Default templates per family for simulation
        template = switch (family) {
            case MELEE_LIGHT -> WeaponStatTemplate.LIGHT_BLADE;
            case MELEE_HEAVY -> WeaponStatTemplate.HEAVY_BLADE;
            case THROWN      -> WeaponStatTemplate.THROWN_HEAD;
            case RANGED      -> WeaponStatTemplate.RANGED_LIMB;
        };

        WeaponStatBlock stats = WeaponStatRoller.roll(level, family, template, new Random());
        printSimResult(ctx.getSource(), stats, "Simulate " + family.name() + " @ " + level);
        return 1;
    }

    private static void printSimResult(CommandSourceStack src, WeaponStatBlock s, String title) {
        src.sendSuccess(() -> header("=== TRL " + title + " ==="), false);
        send(src, "  Rarity:     " + s.rarity.displayName + " (×" + String.format("%.2f", s.rarity.multiplier) + ")", s.rarity.colour);
        send(src, "  Durability: " + String.format("%,d", s.durability), ChatFormatting.WHITE);
        switch (s.family) {
            case MELEE_LIGHT, MELEE_HEAVY -> {
                send(src, "  Damage:     " + String.format("+%.2f", s.damage), ChatFormatting.WHITE);
                if (Math.abs(s.attackSpeedDelta) > 0.001f)
                    send(src, "  Atk Speed:  " + String.format("%+.3f", s.attackSpeedDelta), ChatFormatting.GRAY);
            }
            case THROWN -> {
                send(src, "  Throw Dmg:  " + String.format("+%.2f", s.damage), ChatFormatting.WHITE);
                send(src, "  Velocity:   " + String.format("+%.3f", s.velocity), ChatFormatting.AQUA);
                send(src, "  Accuracy:   " + String.format("%+.3f", s.accuracy), ChatFormatting.GRAY);
            }
            case RANGED -> {
                send(src, "  Velocity:   " + String.format("+%.3f", s.velocity), ChatFormatting.AQUA);
                send(src, "  Draw Speed: " + String.format("%+.3f", s.drawSpeed), ChatFormatting.GRAY);
                send(src, "  Accuracy:   " + String.format("%+.3f", s.accuracy), ChatFormatting.GRAY);
            }
        }
    }

    // ── /trl mobLevel ─────────────────────────────────────────────────────────

    private static int mobLevel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = getPlayer(src);
        if (player == null) return 0;

        var nearby = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(10),
                e -> e != player);

        if (nearby.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No mobs within 10 blocks.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        var mob = nearby.get(0);
        int resolved = com.tinkrarityloot.common.loot.MobLevelResolver.resolve(mob);
        com.tinkrarityloot.common.loot.MobClassifier.MobTier tier =
                com.tinkrarityloot.common.loot.MobClassifier.classify(mob);

        src.sendSuccess(() -> header("=== TRL Mob Level: " + mob.getName().getString() + " ==="), false);
        send(src, "  Resolved level: " + resolved, ChatFormatting.GREEN);
        send(src, "  Tier:           " + tier.name(), tier == com.tinkrarityloot.common.loot.MobClassifier.MobTier.BOSS
                ? ChatFormatting.RED : tier == com.tinkrarityloot.common.loot.MobClassifier.MobTier.ELITE
                ? ChatFormatting.GOLD : ChatFormatting.WHITE);
        send(src, "  Max health:     " + String.format("%.0f", mob.getMaxHealth()), ChatFormatting.GRAY);

        if (TinkRarityLoot.MINE_AND_SLASH_LOADED) {
            Integer masLevel = com.tinkrarityloot.compat.mineandslash.MineAndSlashCompat.getMobLevel(mob);
            send(src, "  M&S level:      " + (masLevel != null ? masLevel : "n/a"), ChatFormatting.AQUA);
            Integer masTier = com.tinkrarityloot.compat.mineandslash.MineAndSlashCompat.getMobTier(mob);
            send(src, "  M&S tier:       " + (masTier != null ? masTier : "n/a"), ChatFormatting.AQUA);
        } else {
            send(src, "  [Mine and Slash not loaded]", ChatFormatting.DARK_GRAY);
        }
        return 1;
    }

    // ── /trl dropTable ────────────────────────────────────────────────────────

    private static int dropTable(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> header("=== TRL Drop Table ==="), false);
        for (TRLDropTable.Entry e : TRLDropTable.getEntries()) {
            String name = e.item().get().getDescriptionId();
            src.sendSuccess(() -> Component.literal(
                    String.format("  %-42s  family=%-12s  slot=%-6s  w=%.0f",
                            name, e.family().name(), e.statType().name(), e.weight()))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ── /trl rarityTable ──────────────────────────────────────────────────────

    private static int rarityTable(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> header("=== TRL Rarity Weights (configured) ==="), false);
        for (TRLRarity r : TRLRarity.values()) {
            double mul = ConfiguredRarityRoller.multiplierFor(r);
            src.sendSuccess(() -> Component.literal(
                    String.format("  %-10s  mul=×%.2f  glint=%s",
                            r.displayName, mul, r.hasGlint() ? "yes" : "none"))
                    .withStyle(r.colour), false);
        }
        return 1;
    }

    // ── /trl materials <slot> ─────────────────────────────────────────────────

    private static int materials(CommandContext<CommandSourceStack> ctx) {
        String slotArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "slot");
        PartStatType statType;
        try {
            statType = PartStatType.valueOf(slotArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown slot '" + slotArg + "'. Use: HEAD, HANDLE, EXTRA, LIMB"));
            return 0;
        }

        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> header("=== TRL Material Pool: " + statType.name() + " ==="), false);

        var pool = MaterialSelector.debugPool(statType);
        if (pool.isEmpty()) {
            send(src, "  (empty – world may not be loaded yet)", ChatFormatting.DARK_GRAY);
        } else {
            for (var id : pool)
                send(src, "  " + id.getId(), ChatFormatting.GRAY);
            send(src, "  Total: " + pool.size() + " materials", ChatFormatting.GOLD);
        }
        return 1;
    }

    // ── /trl repatch ──────────────────────────────────────────────────────────
    // Force-clears the fingerprint and re-patches the held tool's stats.
    // Use when you suspect a patcher timing issue.

    private static int repatch(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx.getSource());
        if (player == null) return 0;

        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.hasTag()) {
            ctx.getSource().sendFailure(Component.literal("Hold a TRL tool."));
            return 0;
        }

        // Clear fingerprint to force re-patch on next call
        held.getTag().remove("trl_stat_fp");
        held.getTag().remove("trl_final_dur");

        boolean patched = com.tinkrarityloot.common.event.ToolStatPatcher.patch(held);
        ctx.getSource().sendSuccess(() -> Component.literal(
                patched ? "Repatched successfully." : "Nothing to patch (trl_applied not set)."),
                false);
        return 1;
    }

    // ── /trl dumptool ─────────────────────────────────────────────────────────
    // Dumps the raw NBT of the held item to chat so we can see exactly what
    // TCon writes and where the Stats compound lives.

    private static int dumpTool(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx.getSource());
        if (player == null) return 0;

        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold a tool in your main hand."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> header("=== TRL Tool NBT Dump ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Item: " + held.getItem().getDescriptionId())
                .withStyle(ChatFormatting.AQUA), false);

        if (!held.hasTag()) {
            ctx.getSource().sendFailure(Component.literal("No NBT tag on this item."));
            return 0;
        }

        net.minecraft.nbt.CompoundTag root = held.getTag();

        // Print top-level keys
        send(ctx.getSource(), "Root keys: " + root.getAllKeys(), ChatFormatting.YELLOW);

        // Print each top-level compound's keys (one level deep)
        for (String key : root.getAllKeys()) {
            byte type = root.getTagType(key);
            if (type == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                net.minecraft.nbt.CompoundTag sub = root.getCompound(key);
                send(ctx.getSource(), "  [" + key + "]: " + sub.getAllKeys(), ChatFormatting.GRAY);
                // One more level for nested compounds
                for (String subKey : sub.getAllKeys()) {
                    if (sub.getTagType(subKey) == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                        send(ctx.getSource(), "    [" + key + "." + subKey + "]: "
                                + sub.getCompound(subKey).getAllKeys(), ChatFormatting.DARK_GRAY);
                    }
                }
            } else {
                send(ctx.getSource(), "  " + key + " = " + root.get(key), ChatFormatting.GRAY);
            }
        }

        // Show tic_stats actual float values (critical for diagnosing patcher)
        if (root.contains("tic_stats", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag ts = root.getCompound("tic_stats");
            send(ctx.getSource(), "tic_stats.dur=" + ts.getFloat("tconstruct:durability")
                    + " dmg=" + ts.getFloat("tconstruct:attack_damage")
                    + " aspd=" + ts.getFloat("tconstruct:attack_speed"), ChatFormatting.YELLOW);
        }

        // Also check TRL applied flag
        send(ctx.getSource(), "trl_applied: " + root.getByte("trl_applied"), ChatFormatting.GREEN);
        send(ctx.getSource(), "trl_b_dur: " + root.getFloat("trl_b_dur"), ChatFormatting.GREEN);
        send(ctx.getSource(), "trl_b_dmg: " + root.getFloat("trl_b_dmg"), ChatFormatting.GREEN);
        send(ctx.getSource(), "trl_b_atkspd: " + root.getFloat("trl_b_atkspd"), ChatFormatting.GREEN);

        // Show ALL tic_volatile_data subkeys and their values
        if (root.contains("tic_volatile_data", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag vd = root.getCompound("tic_volatile_data");
            for (String vKey : vd.getAllKeys()) {
                send(ctx.getSource(),
                     "tic_volatile_data." + vKey + " [type=" + vd.getTagType(vKey) + "]: " + vd.get(vKey),
                     ChatFormatting.AQUA);
            }
        }
        // Show tic_persistent values
        if (root.contains("tic_persistent", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag pd = root.getCompound("tic_persistent");
            for (String key : pd.getAllKeys()) {
                send(ctx.getSource(), "  tic_persistent." + key + " = " + pd.get(key), ChatFormatting.LIGHT_PURPLE);
            }
        }

        return 1;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static Component header(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static void send(CommandSourceStack src, String text, ChatFormatting colour) {
        src.sendSuccess(() -> Component.literal(text).withStyle(colour), false);
    }

    private static ServerPlayer getPlayer(CommandSourceStack src) {
        try { return src.getPlayerOrException(); }
        catch (Exception e) { src.sendFailure(Component.literal("Must be run by a player.")); return null; }
    }
}
