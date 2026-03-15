package com.tinkrarityloot.common.event;

import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.network.SyncPartStatsPacket;
import com.tinkrarityloot.common.network.TRLNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/**
 * Sends {@link SyncPartStatsPacket} to the owning client whenever a TRL-stamped
 * Tinkers part lands in the player's inventory (pickup or login).
 *
 * This keeps the client capability mirror in sync so tooltips display correctly
 * in multiplayer without requiring the client to hold the full capability state.
 */
public class InventorySyncHandler {

    // ── Item pickup ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem().getItem();
        if (!(stack.getItem() instanceof IToolPart)) return;

        // Find the slot it was placed into and sync
        syncStackToPlayer(stack, player);
    }

    // ── Player login – re-sync all TRL parts already in inventory ─────────────

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof IToolPart)) continue;

            SyncPartStatsPacket pkt = SyncPartStatsPacket.forStack(i, stack);
            if (pkt != null) {
                TRLNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void syncStackToPlayer(ItemStack stack, ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                SyncPartStatsPacket pkt = SyncPartStatsPacket.forStack(i, stack);
                if (pkt != null) {
                    TRLNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
                }
                return;
            }
        }
    }
}
