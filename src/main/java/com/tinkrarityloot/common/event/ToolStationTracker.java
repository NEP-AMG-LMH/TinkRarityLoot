package com.tinkrarityloot.common.event;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks TRL parts placed into a Tool Station / Tool Forge BEFORE TCon
 * consumes them.
 *
 * Problem: PlayerEvent.ItemCraftedEvent fires AFTER TCon removes the input
 * parts from the station slots and clears their NBT. By the time our handler
 * reads the container slots, they are empty or contain stripped items.
 *
 * Solution: When a player OPENS a container, we start watching it.
 * We snapshot the TRL stat blocks from any TRL parts in the station on a
 * periodic slot scan. When ItemCraftedEvent fires for that player, we use
 * the cached stats instead of re-reading the (now-empty) slots.
 *
 * Cache is per-player-UUID, cleared when the container is closed.
 */
public class ToolStationTracker {

    // Player UUID → cached list of WeaponStatBlocks from TRL parts in station
    private static final Map<UUID, List<WeaponStatBlock>> CACHE =
            new ConcurrentHashMap<>();

    // Track which container each player has open (by system identity)
    private static final Map<UUID, AbstractContainerMenu> OPEN_CONTAINERS =
            new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        AbstractContainerMenu menu = event.getContainer();
        UUID uuid = player.getUUID();
        OPEN_CONTAINERS.put(uuid, menu);
        // Clear any stale cache from a previous session
        CACHE.remove(uuid);
        TinkRarityLoot.LOGGER.debug("[TRL] ToolStationTracker: container opened for {}", player.getName().getString());
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        OPEN_CONTAINERS.remove(uuid);
        CACHE.remove(uuid);
        TinkRarityLoot.LOGGER.debug("[TRL] ToolStationTracker: container closed for {}", player.getName().getString());
    }

    /**
     * Called by ToolAssemblyHandler when ItemCraftedEvent fires.
     * Scans the current container slots for TRL parts RIGHT NOW (before our
     * handler previously read them — but actually this fires from the same event,
     * so we need to have pre-scanned).
     *
     * This method scans ALL slots in the currently open container and caches
     * any TRL stat blocks found. Must be called eagerly.
     */
    public static void scanAndCache(Player player) {
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        List<WeaponStatBlock> found = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            WeaponStatBlock sb = readStatBlock(stack);
            if (sb != null) {
                found.add(sb);
                TinkRarityLoot.LOGGER.info("[TRL] Pre-scan found TRL part in slot {}: rarity={} family={}", i, sb.rarity, sb.family);
            }
        }
        if (!found.isEmpty()) {
            CACHE.put(uuid, found);
            TinkRarityLoot.LOGGER.info("[TRL] Cached {} TRL stat blocks for {}", found.size(), player.getName().getString());
        }
    }

    /**
     * Store stat blocks directly (called from onItemCrafted's early scan).
     */
    public static void storeCache(Player player, List<WeaponStatBlock> blocks) {
        CACHE.put(player.getUUID(), new ArrayList<>(blocks));
        TinkRarityLoot.LOGGER.info("[TRL] Stored {} TRL blocks in cache for {}",
                blocks.size(), player.getName().getString());
    }

    /**
     * Retrieve and clear the cached stat blocks for this player.
     * Returns empty list if nothing was cached.
     */
    public static List<WeaponStatBlock> consumeCache(Player player) {
        List<WeaponStatBlock> cached = CACHE.remove(player.getUUID());
        return cached != null ? cached : List.of();
    }

    private static WeaponStatBlock readStatBlock(ItemStack stack) {
        // Try capability first
        try {
            WeaponStatBlock fromCap = stack.getCapability(ITRLPartStats.CAPABILITY)
                    .filter(c -> c.hasStats() && c.getStats() != null).map(ITRLPartStats::getStats)
                    .orElse(null);
            if (fromCap != null) return fromCap;
        } catch (Exception ignored) {}

        // Fall back to flat NBT
        if (stack.hasTag() && WeaponStatBlock.isPresent(stack.getTag())) {
            return WeaponStatBlock.fromNBT(stack.getTag());
        }
        return null;
    }
}
