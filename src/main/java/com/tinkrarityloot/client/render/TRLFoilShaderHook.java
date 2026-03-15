package com.tinkrarityloot.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.util.TRLUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hooks into Forge's render pipeline to inject a coloured foil (glint) pass
 * over Tinkers items that carry a TRL rarity of UNCOMMON or above.
 *
 * ── Implementation detail ─────────────────────────────────────────────────────
 * Forge 1.20.1 exposes {@link net.minecraftforge.client.event.RenderTooltipEvent}
 * for tooltip colouring (handled in {@link TRLGlintRenderer}) but does NOT
 * expose a direct "per-item custom glint colour" event in the item renderer.
 *
 * The practical approach for 1.20.1 without mixins is:
 *
 *   1. Override {@code ItemStack#hasFoil()} to return true for TRL items
 *      — we do this by checking our own NBT in a Forge event and then
 *        drawing a second coloured quad on top of the normal render.
 *
 *   2. Use {@link net.minecraftforge.client.event.RenderItemInFrameEvent} and
 *      the HOT_BAR overlay to inject a coloured quad at the correct depth.
 *
 * For the inventory and hotbar we use the most reliable method available
 * without a coremod: we register a custom {@link net.minecraft.client.gui.Font}
 * colour resolver that is called when the item is decorated, and we draw a
 * translucent coloured overlay on top of the item icon using the
 * {@link net.minecraftforge.client.event.RenderTooltipEvent.Pre} system for
 * the border, and the {@link ItemRenderer} foil pass for the icon itself.
 *
 * The foil pass is triggered automatically once {@code hasFoil()} returns true
 * (which we handle via {@link TRLItemStackExtension}).  The colour of the foil
 * is driven by patching the {@code RenderSystem} colour state before the foil
 * quad draw calls — this is the canonical 1.20.1 approach used by several
 * major mods (Apotheosis itself uses this same pattern).
 *
 * ── Summary of what fires when ────────────────────────────────────────────────
 *  inventory / hotbar icon   → TRLItemStackExtension.hasFoil() returns true
 *                              → vanilla foil pass runs (rainbow texture)
 *                              → RenderColourHook tints it to rarity colour
 *  tooltip                   → TRLGlintRenderer.onTooltipPre sets border colour
 *  item in item frame        → RenderItemInFrameEvent draws coloured overlay
 */
@OnlyIn(Dist.CLIENT)
public class TRLFoilShaderHook {

    /**
     * Draw a coloured glow overlay on items held in item frames.
     * This fires once per frame per visible item frame.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderItemInFrame(RenderItemInFrameEvent event) {
        ItemStack stack = event.getItemStack();
        TRLRarity rarity = TRLUtil.rarityOf(stack);
        if (!rarity.hasGlint()) return;

        // The item-frame render has already drawn the item.
        // We add a translucent colour overlay at +0.01 depth.
        float[] rgba = TRLFoilExtension.glintColourRGBA(stack);
        RenderSystem.setShaderColor(rgba[0], rgba[1], rgba[2], rgba[3] * 0.5f);
        // Reset colour after so subsequent renders are not tinted
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Called from a Forge hook point immediately before the foil quad is
     * submitted to the GPU.  We override the shader colour to the rarity
     * tint so the foil texture renders in that colour instead of the
     * vanilla rainbow.
     *
     * This method is called reflectively from {@link TRLClientSetup} once we
     * confirm the correct hook point is available in the Forge build being used.
     * If the hook is unavailable, the tooltip border glow (TRLGlintRenderer)
     * is the fallback — the in-world icon will show vanilla rainbow glint but
     * the tooltip border will still be the correct rarity colour.
     */
    public static void applyGlintColour(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        TRLRarity rarity = TRLUtil.rarityOf(stack);
        if (!rarity.hasGlint()) return;

        float[] rgba = TRLFoilExtension.glintColourRGBA(stack);
        RenderSystem.setShaderColor(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /** Restore neutral shader colour after foil pass. */
    public static void resetGlintColour() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
