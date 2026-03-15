package com.tinkrarityloot.common.event;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.event.ToolStationTracker;
import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import com.tinkrarityloot.compat.apotheosis.ApotheosisCompat;
import com.tinkrarityloot.compat.tinkers.TinkersStatInjector;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects when a player takes a completed Tinkers tool out of the station
 * and applies TRL rarity + stat bonuses.
 *
 * ── Why ItemCraftedEvent instead of TinkerToolEvent ──────────────────────
 *
 * TinkerToolEvent.ToolBuilt moved / restructured between TCon 3.x versions.
 * PlayerEvent.ItemCraftedEvent is a stable Forge event that fires reliably
 * whenever the player takes an item from any crafting output slot, including
 * the Tinker Station.  We gate on the item being a TCon tool via ToolStack.
 *
 * For repairs we listen to the same event — a repaired tool is also an
 * ItemCraftedEvent from the station's output slot.
 */
public class ToolAssemblyHandler {

    public static final String TOOL_RARITY_KEY = "trl_tool_rarity";

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!TRLConfig.SERVER.enableToolAssemblyRarity.get()) return;
        if (event.getEntity().level().isClientSide()) return;

        net.minecraft.world.entity.player.Player player = event.getEntity();

        // SCAN IMMEDIATELY — before any other check — to capture parts before TCon clears them.
        // ItemCraftedEvent fires synchronously during the slot-take action; the input slots
        // may still be populated at this exact moment.
        AbstractContainerMenu menu = player.containerMenu;
        List<ItemStack> currentParts = collectInputParts(menu);
        List<WeaponStatBlock> earlyBlocks = collectStatBlocks(currentParts);
        if (!earlyBlocks.isEmpty()) {
            ToolStationTracker.storeCache(player, earlyBlocks);
        }

        ItemStack result = event.getCrafting();
        if (result.isEmpty()) return;

        // Only process TCon tools
        if (!isTinkersToolStack(result)) return;

        // Step 1: use early scan if available, else check slots again
        List<WeaponStatBlock> statBlocks = earlyBlocks.isEmpty()
                ? collectStatBlocks(currentParts)
                : earlyBlocks;

        // Step 2: if slot scan found nothing, use the pre-cached stats
        if (statBlocks.isEmpty()) {
            statBlocks = ToolStationTracker.consumeCache(player);
            if (!statBlocks.isEmpty()) {
                TinkRarityLoot.LOGGER.info("[TRL] Using pre-cached TRL stats ({} blocks)", statBlocks.size());
            }
        }

        // Step 3: determine dominant rarity (from current parts or cached)
        TRLRarity dominant = !currentParts.isEmpty()
                ? dominantRarity(currentParts)
                : statBlocks.isEmpty() ? TRLRarity.COMMON
                : statBlocks.stream().map(s -> s.rarity).max(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(TRLRarity.COMMON);
        applyRarity(result, dominant);

        if (!statBlocks.isEmpty()) {
            TinkersStatInjector.applyBonuses(result, statBlocks);

            // RPG layer: requirements + affixes (mob level from dominant part)
            int mobLevel = statBlocks.stream().mapToInt(s -> s.mobLevel).max().orElse(1);
            WeaponFamily family = dominant == TRLRarity.COMMON
                    ? statBlocks.get(0).family
                    : statBlocks.stream()
                            .filter(s -> s.rarity == dominant)
                            .findFirst()
                            .map(s -> s.family)
                            .orElse(statBlocks.get(0).family);
            // Store family on tool so MnSBridgeFinalizer can re-apply mmorpg_gear
            result.getOrCreateTag().putString("trl_tool_family", family.name().toLowerCase());

            com.tinkrarityloot.common.rpg.TRLRPGApplicator.apply(
                    result, dominant, family, mobLevel, new java.util.Random(
                            result.hashCode() ^ System.nanoTime()));

            TinkRarityLoot.LOGGER.info("[TRL] Tool assembled — rarity={} parts={} level={}",
                    dominant.displayName, statBlocks.size(), mobLevel);
            // Force immediate sync so client sees final stats at craft time
            if (player instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.containerMenu.broadcastChanges();
        } else if (result.hasTag() && result.getTag().getByte(TinkersStatInjector.K_APPLIED) == 1) {
            // Component replacement or repair — clear fingerprint so patcher
            // re-captures the new TCon base (new material may have different stats)
            result.getTag().remove("trl_stat_fp");
            com.tinkrarityloot.common.event.ToolStatPatcher.patch(result);
            TinkRarityLoot.LOGGER.info("[TRL] Tool modified/repaired — repatched with new base");
        } else {
            TinkRarityLoot.LOGGER.info("[TRL] Tool assembled but no TRL parts found — {} slots checked", currentParts.size());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True if the stack carries TCon's tool NBT (has the "ToolData" or "TinkerData" tag). */
    private boolean isTinkersToolStack(ItemStack stack) {
        if (!stack.hasTag()) return false;
        try {
            ToolStack ts = ToolStack.from(stack);
            if (ts == null) return false;
            // ToolStack.from succeeded — verify by checking for any known TCon compound tag.
            // Key names vary across 3.x builds, so we check all known variants.
            var tag = stack.getTag();
            return tag.contains("ToolData")    // 3.6 compound
                || tag.contains("TinkerData")  // 3.8 compound
                || tag.contains("Modifiers")   // inner key, older versions
                || tag.contains("Stats")       // inner key, older versions
                || tag.contains("tic_stats")   // TCon 3.11
                || tag.contains("tic_modifiers"); // TCon 3.11
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Pull only IToolPart items from the current container.
     * Restricting to IToolPart prevents accidentally picking up the output
     * tool, player inventory contents, or unrelated items in the menu.
     */
    private List<ItemStack> collectInputParts(AbstractContainerMenu menu) {
        List<ItemStack> parts = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty() && s.getItem() instanceof slimeknights.tconstruct.library.tools.part.IToolPart)
                parts.add(s);
        }
        return parts;
    }

    private List<WeaponStatBlock> collectStatBlocks(List<ItemStack> parts) {
        List<WeaponStatBlock> out = new ArrayList<>();
        for (ItemStack part : parts) {
            if (part.isEmpty()) continue;
            WeaponStatBlock sb = null;

            // Safe capability read (may not be registered on server during early events)
            try {
                sb = part.getCapability(ITRLPartStats.CAPABILITY)
                        .filter(c -> c.hasStats() && c.getStats() != null)
                        .map(ITRLPartStats::getStats)
                        .orElse(null);
            } catch (Exception ignored) {}

            // NBT fallback — parts stamped by lootbox always have flat NBT
            if (sb == null && part.hasTag() && WeaponStatBlock.isPresent(part.getTag()))
                sb = WeaponStatBlock.fromNBT(part.getTag());

            if (sb != null) {
                out.add(sb);
                TinkRarityLoot.LOGGER.info("[TRL] Found TRL part in station: rarity={} family={}",
                        sb.rarity, sb.family);
            }
        }
        if (out.isEmpty())
            TinkRarityLoot.LOGGER.info("[TRL] No TRL stat blocks found in station — {} parts checked", parts.size());
        return out;
    }

    private TRLRarity dominantRarity(List<ItemStack> parts) {
        TRLRarity best = TRLRarity.COMMON;
        for (ItemStack part : parts) {
            TRLRarity r = rarityFromStack(part);
            if (r.ordinal() > best.ordinal()) best = r;
        }
        return best;
    }

    private TRLRarity rarityFromStack(ItemStack stack) {
        // LazyOptional.map() calls Optional.of() internally — must not return null.
        // Use filter+map pattern instead of ternary that returns null.
        WeaponStatBlock fromCap = stack.getCapability(ITRLPartStats.CAPABILITY)
                .filter(c -> c.hasStats() && c.getStats() != null)
                .map(ITRLPartStats::getStats)
                .orElse(null);
        if (fromCap != null) return fromCap.rarity;

        if (stack.hasTag() && stack.getTag().contains(WeaponStatBlock.K_RARITY))
            return TRLRarity.fromKey(stack.getTag().getString(WeaponStatBlock.K_RARITY));

        return TRLRarity.COMMON;
    }

    private void applyRarity(ItemStack toolStack, TRLRarity rarity) {
        toolStack.getOrCreateTag().putString(TOOL_RARITY_KEY, rarity.getSerialKey());

        if (TinkRarityLoot.APOTHEOSIS_LOADED && TRLConfig.SERVER.enableApotheosisSync.get())
            ApotheosisCompat.translateToStack(toolStack, rarity);

        if (TinkRarityLoot.MINE_AND_SLASH_LOADED) {
            com.tinkrarityloot.compat.mineandslash.MineAndSlashTranslator.translateToStack(toolStack, rarity);
            com.tinkrarityloot.compat.mineandslash.MineAndSlashTranslator.writeNbtFallback(toolStack, rarity);
        }
    }

    @SuppressWarnings("unused")
    private TRLRarity best(@Nullable TRLRarity a, @Nullable TRLRarity b) {
        if (a == null) return b == null ? TRLRarity.COMMON : b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
