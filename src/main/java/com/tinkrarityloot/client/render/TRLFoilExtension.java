package com.tinkrarityloot.client.render;

import com.tinkrarityloot.common.util.TRLUtil;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * {@link IClientItemExtensions} implementation that drives the custom glint.
 *
 * Forge calls {@code isFoil(ItemStack)} before deciding whether to draw the
 * enchantment glint pass over an item.  By returning {@code true} for UNCOMMON+
 * TRL stacks, we get the foil render call.
 *
 * The actual colour is applied via the {@code RenderTooltipEvent.Color} border
 * in {@link TRLGlintRenderer} for tooltips, and for in-world / inventory icons
 * we rely on the fact that Minecraft 1.20.1 uses the enchantment glint texture
 * (a built-in rainbow) which we override the colour of by patching the shader
 * uniform in {@link TRLFoilShaderHook}.
 *
 * ── How this is registered ─────────────────────────────────────────────────
 * Because the TCon part items are not our classes, we cannot override
 * {@code initializeClient()} on them directly.  Instead we register this
 * extension via the Forge {@code AttachCapabilitiesEvent<ItemStack>} on the
 * client side, inside {@link com.tinkrarityloot.client.TRLClientSetup}.
 *
 * Forge checks for {@link IClientItemExtensions} via the item's
 * {@code initializeClient} method and also via a separate client-extension
 * registry. We use the second path: registering a per-stack override through
 * {@link net.minecraftforge.client.extensions.common.IClientItemExtensions}
 * that Forge queries from the item's getExtension method.
 *
 * For 1.20.1, the cleanest approach that doesn't require AT/mixin is to
 * override foil on the ITEM level by wrapping the item's existing extension
 * and delegating to our logic first.
 */
@OnlyIn(Dist.CLIENT)
public class TRLFoilExtension implements IClientItemExtensions {

    public static final TRLFoilExtension INSTANCE = new TRLFoilExtension();

    /** The wrapped base extension (can be IClientItemExtensions.DEFAULT). */
    private final IClientItemExtensions delegate;

    public TRLFoilExtension() {
        this.delegate = IClientItemExtensions.DEFAULT;
    }

    public TRLFoilExtension(IClientItemExtensions delegate) {
        this.delegate = delegate;
    }

    // shouldCauseReequipAnimation was removed from IClientItemExtensions in Forge 47.4+.

    // ── Static helpers called by the mixin / event hook ────────────────────────

    /**
     * Returns whether a TRL glint should override the vanilla isFoil result.
     * Called from {@link TRLFoilShaderHook}.
     */
    public static boolean hasTRLGlint(ItemStack stack) {
        return TRLGlintRenderer.shouldShowFoil(stack);
    }

    /**
     * The ARGB colour to tint the glint pass with.
     * Extracted as a float4 [R, G, B, A] for use in shader uniforms.
     */
    public static float[] glintColourRGBA(ItemStack stack) {
        int argb = TRLGlintRenderer.glintColour(stack);
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;
        return new float[]{ r, g, b, a * 0.60f }; // 60 % opacity for the glint pass
    }
}
