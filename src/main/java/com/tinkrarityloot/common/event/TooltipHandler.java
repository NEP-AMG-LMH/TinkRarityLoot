package com.tinkrarityloot.common.event;

import com.tinkrarityloot.client.tooltip.TooltipRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/**
 * Event handler that routes tooltip requests to {@link TooltipRenderer}.
 *
 * Priority is HIGHEST so TRL's lines always appear before anything Mine and
 * Slash or Apotheosis inject into the same event.
 *
 * TRL is the sole visual authority:
 *   • We read only our own NBT / capability.
 *   • We never consult M&S or Apotheosis APIs here.
 *   • M&S and Apotheosis add their own lines at lower priority afterward.
 */
@OnlyIn(Dist.CLIENT)
public class TooltipHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        if (stack.getItem() instanceof IToolPart) {
            TooltipRenderer.appendPartLines(stack, event.getToolTip());
        } else {
            // Assembled tools (TCon tool items that are not IToolPart)
            TooltipRenderer.appendToolLines(stack, event.getToolTip());
        }
    }
}
