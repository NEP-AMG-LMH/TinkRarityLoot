package com.tinkrarityloot.client;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.client.render.LootboxRarityProperty;
import com.tinkrarityloot.client.render.TRLFoilShaderHook;
import com.tinkrarityloot.client.render.TRLGlintRenderer;
import com.tinkrarityloot.common.registry.TRLItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only initialisation.
 *
 * Registered on the MOD bus via {@code @Mod.EventBusSubscriber(Dist.CLIENT)}.
 * Subscribes the rendering and tooltip event handlers to the FORGE bus.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = TinkRarityLoot.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TRLClientSetup {

    /** ResourceLocation used in the model JSON override predicate key. */
    public static final ResourceLocation LOOTBOX_RARITY_PROP =
            new ResourceLocation(TinkRarityLoot.MODID, "rarity");

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {

        // ── Register lootbox rarity item property ─────────────────────────────
        // Must run on the main thread; enqueueWork guarantees that.
        event.enqueueWork(() -> {
            ItemProperties.register(
                TRLItems.LOOTBOX_MELEE.get(),
                LOOTBOX_RARITY_PROP,
                LootboxRarityProperty.INSTANCE
            );
            ItemProperties.register(
                TRLItems.LOOTBOX_RANGED.get(),
                LOOTBOX_RARITY_PROP,
                LootboxRarityProperty.INSTANCE
            );
            ItemProperties.register(
                TRLItems.LOOTBOX_TOOL.get(),
                LOOTBOX_RARITY_PROP,
                LootboxRarityProperty.INSTANCE
            );
        });

        // ── Register client-side Forge bus event listeners ────────────────────
        var forgeBus = MinecraftForge.EVENT_BUS;

        // Tooltip border colouring + glint enable check
        forgeBus.register(new TRLGlintRenderer());

        // Item-in-frame overlay / shader hook
        forgeBus.register(new TRLFoilShaderHook());
    }
}

