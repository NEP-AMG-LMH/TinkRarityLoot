package com.tinkrarityloot.common.loot.chest;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Thread-local carrier used to pass a capability-bearing ItemStack from
 * {@link ChestLootInjector.TRLChestLootEntry#expand} to
 * {@link ChestOpenHandler#onLootingFill} on the same tick.
 *
 * Forge's loot system doesn't provide a clean way to inject a pre-built
 * ItemStack with an attached capability through the LootPoolEntry API, so we
 * use this short-lived carrier instead.  It is always cleared after consumption.
 */
public final class PendingChestDrop {

    private static final ThreadLocal<ItemStack> PENDING = new ThreadLocal<>();

    private PendingChestDrop() {}

    public static void set(ItemStack stack) {
        PENDING.set(stack);
    }

    @Nullable
    public static ItemStack take() {
        ItemStack s = PENDING.get();
        PENDING.remove();
        return s;
    }

    public static boolean hasPending() {
        return PENDING.get() != null;
    }
}
