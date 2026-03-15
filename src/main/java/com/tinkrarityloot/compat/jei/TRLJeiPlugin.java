package com.tinkrarityloot.compat.jei;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional JEI integration — adds a rarity info box to the TCon Sword Blade page.
 *
 * This class is only loaded when JEI is present. The @JeiPlugin annotation
 * causes JEI to discover and instantiate it via its plugin loading system;
 * if JEI is absent the class is simply never loaded and causes no errors.
 *
 * We look up the sword blade item from the Forge registry by resource location
 * rather than referencing TinkerToolParts fields directly, so the plugin
 * compiles regardless of TCon field-name changes between versions.
 */
@JeiPlugin
public class TRLJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(TinkRarityLoot.MODID, "jei_plugin");

    // Known TCon part resource locations — in order of preference.
    // TCon 3.8 uses snake_case registry names.
    private static final String[] BLADE_CANDIDATES = {
            "tconstruct:sword_blade",
            "tconstruct:small_sword_blade",
            "tconstruct:small_blade",
    };

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        ItemStack blade = resolveBlade();
        if (blade.isEmpty()) {
            TinkRarityLoot.LOGGER.warn("[TRL] JEI: could not find a TCon blade item — skipping info registration.");
            return;
        }

        List<Component> info = new ArrayList<>();
        info.add(Component.literal("TinkRarityLoot — Drop Info")
                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
        info.add(Component.literal(" "));
        info.add(Component.literal("Mobs drop this part with randomized ARPG stats."));
        info.add(Component.literal("Stats scale with Mine & Slash mob / region level."));
        info.add(Component.literal(" "));
        info.add(Component.literal("Rarity tiers:").withStyle(net.minecraft.ChatFormatting.GRAY));

        for (TRLRarity r : TRLRarity.values()) {
            info.add(Component.literal(String.format("  %-10s  ×%.2f stat mult",
                    r.displayName, r.multiplier)).withStyle(r.colour));
        }

        info.add(Component.literal(" "));
        info.add(Component.literal("Base drop chance: 3 %  (cap 50 % with high level + Looting)")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        reg.addIngredientInfo(List.of(blade), VanillaTypes.ITEM_STACK,
                info.toArray(new Component[0]));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        TinkRarityLoot.LOGGER.debug("[TinkRarityLoot] JEI plugin loaded.");
    }

    /** Resolves the first available TCon blade item from the Forge registry. */
    private static ItemStack resolveBlade() {
        for (String candidate : BLADE_CANDIDATES) {
            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation(candidate.split(":")[0], candidate.split(":")[1]));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
}
