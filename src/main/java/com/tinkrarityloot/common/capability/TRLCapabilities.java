package com.tinkrarityloot.common.capability;

import com.tinkrarityloot.TinkRarityLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/**
 * Registers the {@link ITRLPartStats} capability and attaches it to every
 * Tinkers' Construct tool-part item stack.
 */
public class TRLCapabilities {

    private static final ResourceLocation CAP_KEY =
            new ResourceLocation(TinkRarityLoot.MODID, "part_stats");

    /** Called from the mod bus during common setup. */
    public static void register() {
        // Registration itself happens via @SubscribeEvent below on the MOD bus
        // (RegisterCapabilitiesEvent).  This method is kept for future
        // one-time setup work.
    }

    // ── Mod bus ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ITRLPartStats.class);
    }

    // ── Forge bus ─────────────────────────────────────────────────────────────

    /**
     * Attach the capability to any ItemStack whose item implements
     * Tinkers' {@link IToolPart}.  This covers ToolRod, SwordBlade, WideGuard,
     * and every other part out of the box.
     */
    @SubscribeEvent
    public static void onAttachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof IToolPart) {
            event.addCapability(CAP_KEY, new TRLPartStatsProvider());
        }
    }
}
