package com.tinkrarityloot.common.network;

import com.tinkrarityloot.TinkRarityLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * TinkRarityLoot network channel.
 *
 * Used to sync {@link com.tinkrarityloot.common.capability.ITRLPartStats} data
 * from the server to clients so tooltips render correctly in multiplayer.
 *
 * Registered and initialised from {@link TinkRarityLoot#commonSetup}.
 */
public final class TRLNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TinkRarityLoot.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                nextId++,
                SyncPartStatsPacket.class,
                SyncPartStatsPacket::encode,
                SyncPartStatsPacket::decode,
                SyncPartStatsPacket::handle
        );
    }
}
