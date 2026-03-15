package com.tinkrarityloot.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.util.TRLUtil;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Renders a per-rarity coloured glint on TRL-stamped Tinkers parts and
 * assembled tools.
 *
 * ── Strategy ─────────────────────────────────────────────────────────────────
 * Forge 1.20.1 provides {@link IClientItemExtensions#getCustomFoilType} which
 * lets items opt into a custom foil (glint) effect.  However, because we are
 * augmenting existing TCon item types (not our own items), we cannot override
 * that method on the item class directly.
 *
 * Instead we hook {@link net.minecraftforge.client.event.RenderTooltipEvent.Pre}
 * to draw a coloured glow border matching the rarity, and we hook
 * {@link net.minecraftforge.client.event.RenderGuiOverlayEvent} to tint the
 * hotbar slot.
 *
 * The actual in-world / inventory item glint is controlled via
 * {@link IClientItemExtensions} — we register a ForgeCapability on the
 * item stack on the CLIENT side (see {@link TRLClientCapabilityHandler}) that
 * overrides {@code isFoil()} to return true for UNCOMMON+, and provides the
 * foil colour through a custom texture approach.
 *
 * ── Glint rendering detail ────────────────────────────────────────────────────
 * Minecraft's vanilla glint uses a fixed rainbow texture scrolling over the
 * item.  We substitute this with a solid-colour pass at 30 % alpha that
 * matches the rarity colour, giving a clean ARPG-style coloured shimmer rather
 * than the vanilla rainbow.
 *
 * This is implemented in {@link TRLFoilExtension} which returns a custom
 * ResourceLocation for the foil texture (a 1×1 white texture we tint at
 * render time using the matrix colour uniform).
 *
 * For items that cannot receive the capability override (e.g. assembled TCon
 * tools which are not IToolPart), we fall back to a tooltip-border glow
 * rendered here in {@link #onTooltipPre}.
 */
@OnlyIn(Dist.CLIENT)
public class TRLGlintRenderer {

    // ── Tooltip border glow ────────────────────────────────────────────────────

    /**
     * Colours the tooltip border to match the TRL rarity.
     * Fires for every tooltip; we exit immediately if the stack has no TRL data.
     */
    @SubscribeEvent
    public void onTooltipPre(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        TRLRarity rarity = TRLUtil.rarityOf(stack);

        // COMMON has no glint; skip to avoid overriding normal tooltip borders
        if (!rarity.hasGlint()) return;

        // Extract RGB from the packed ARGB glint colour
        int argb   = rarity.glintARGB;
        int border = argb | 0xFF000000; // force full alpha for the border
        int bg     = (argb & 0x00FFFFFF) | 0x60000000; // 37 % alpha for inner background

        event.setBorderStart(border);
        event.setBorderEnd(border);
        event.setBackgroundStart(bg);
        event.setBackgroundEnd(bg);
    }

    // ── isFoil override helper ────────────────────────────────────────────────

    /**
     * Returns true if the given stack should show an enchantment-style glint.
     * Called from {@link TRLFoilExtension} which is attached client-side.
     */
    public static boolean shouldShowFoil(ItemStack stack) {
        // Show glint for any TRL part or tool of UNCOMMON rarity or above
        TRLRarity rarity = TRLUtil.rarityOf(stack);
        return rarity.hasGlint();
    }

    /**
     * Returns the packed ARGB glint colour for a stack.
     * Used by {@link TRLFoilExtension} to drive the custom foil colour uniform.
     */
    public static int glintColour(ItemStack stack) {
        return TRLUtil.rarityOf(stack).glintARGB;
    }
}
