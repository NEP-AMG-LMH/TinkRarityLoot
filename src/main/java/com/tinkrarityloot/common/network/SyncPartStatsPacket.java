package com.tinkrarityloot.common.network;

import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet that syncs a {@link WeaponStatBlock} for a specific
 * inventory slot so the client capability stays populated.
 *
 * Payload:  int slotIndex + CompoundTag (WeaponStatBlock.toNBT())
 */
public class SyncPartStatsPacket {

    private final int        slotIndex;
    private final CompoundTag statNBT;

    public SyncPartStatsPacket(int slotIndex, CompoundTag statNBT) {
        this.slotIndex = slotIndex;
        this.statNBT   = statNBT;
    }

    public static void encode(SyncPartStatsPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.slotIndex);
        buf.writeNbt(pkt.statNBT);
    }

    public static SyncPartStatsPacket decode(FriendlyByteBuf buf) {
        int          slot = buf.readInt();
        CompoundTag  nbt  = buf.readNbt();
        return new SyncPartStatsPacket(slot, nbt == null ? new CompoundTag() : nbt);
    }

    public static void handle(SyncPartStatsPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            Inventory inv   = player.getInventory();
            ItemStack stack = inv.getItem(pkt.slotIndex);
            if (stack.isEmpty()) return;

            stack.getCapability(ITRLPartStats.CAPABILITY).ifPresent(cap -> {
                if (WeaponStatBlock.isPresent(pkt.statNBT)) {
                    cap.setStats(WeaponStatBlock.fromNBT(pkt.statNBT));
                }
            });
        });
        ctx.setPacketHandled(true);
    }

    /** Build a sync packet for a given slot. Returns null if no TRL stats present. */
    public static SyncPartStatsPacket forStack(int slotIndex, ItemStack stack) {
        CompoundTag nbt = stack.getCapability(ITRLPartStats.CAPABILITY)
                .map(cap -> cap.hasStats() ? cap.getStats().toNBT() : null)
                .orElse(null);
        // NBT fallback: if capability not populated server-side, read from flat item NBT
        if (nbt == null && stack.hasTag() && WeaponStatBlock.isPresent(stack.getTag()))
            nbt = stack.getTag();
        return nbt != null ? new SyncPartStatsPacket(slotIndex, nbt) : null;
    }
}
