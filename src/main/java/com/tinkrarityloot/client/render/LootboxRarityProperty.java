package com.tinkrarityloot.client.render;

import com.tinkrarityloot.common.loot.ComponentLootboxItem;
import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Item property function registered as {@code tinkrarityloot:rarity}.
 *
 * Returns a float value equal to {@link TRLRarity#ordinal()} for the rarity
 * stored in the lootbox's {@code trl_rarity} NBT tag:
 *
 *   COMMON    → 0.0
 *   UNCOMMON  → 1.0
 *   RARE      → 2.0
 *   EPIC      → 3.0
 *   UNIQUE    → 4.0
 *   LEGENDARY → 5.0
 *   MYTHIC    → 6.0
 *
 * The root model {@code lootbox.json} declares predicate overrides that match
 * these values to per-rarity sub-models (lootbox_epic.json etc.), which each
 * reference their own coloured texture.
 *
 * The predicate system fires every frame for items in the inventory and in the
 * world, so the correct texture is shown immediately after the NBT is set —
 * no custom rendering needed.
 *
 * ── Registration ─────────────────────────────────────────────────────────
 *
 *  Called from {@link com.tinkrarityloot.client.TRLClientSetup} inside
 *  {@code event.enqueueWork()} to satisfy the requirement that
 *  {@code ItemProperties.register} runs on the main thread.
 */
@OnlyIn(Dist.CLIENT)
public final class LootboxRarityProperty implements ItemPropertyFunction {

    public static final LootboxRarityProperty INSTANCE = new LootboxRarityProperty();

    private LootboxRarityProperty() {}

    @Override
    public float call(ItemStack stack, @Nullable ClientLevel level,
                      @Nullable LivingEntity entity, int seed) {
        TRLRarity rarity = ComponentLootboxItem.getTRLRarity(stack);
        return (float) rarity.ordinal();   // 0.0 – 6.0
    }
}
