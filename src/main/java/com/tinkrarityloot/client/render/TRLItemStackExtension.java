package com.tinkrarityloot.client.render;

import com.tinkrarityloot.common.util.TRLUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Static hook that overrides whether a TRL-stamped ItemStack shows the
 * enchantment foil (glint) pass in the item renderer.
 *
 * ── How Forge drives hasFoil ─────────────────────────────────────────────────
 * In 1.20.1, {@code ItemStack.hasFoil()} calls {@code Item.isFoil(ItemStack)}
 * first, then checks {@code stack.isEnchanted()}.  Forge patches this to also
 * call {@link net.minecraftforge.client.extensions.common.IClientItemExtensions#isFoil}.
 *
 * Since TCon items implement their own {@code IClientItemExtensions}, we must
 * wrap them rather than replace them.  The wrapping happens in
 * {@link TRLClientCapabilityHandler} which fires on the client-side
 * {@code AttachCapabilitiesEvent<ItemStack>}.
 *
 * This class provides the shared logic so both the wrapper and the event
 * handler can call the same code.
 */
@OnlyIn(Dist.CLIENT)
public final class TRLItemStackExtension {

    private TRLItemStackExtension() {}

    /**
     * Returns true if the stack should display a TRL glint, overriding the
     * item's own foil decision.
     *
     * For assembled tools we still want the glint even though they are not
     * {@link slimeknights.tconstruct.library.tools.part.IToolPart} — the
     * {@link TRLUtil#rarityOf} helper reads the {@code trl_tool_rarity} key.
     */
    public static boolean hasTRLFoil(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return TRLGlintRenderer.shouldShowFoil(stack);
    }
}
