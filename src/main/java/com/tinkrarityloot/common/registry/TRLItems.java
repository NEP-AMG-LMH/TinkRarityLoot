package com.tinkrarityloot.common.registry;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.loot.ComponentLootboxItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registry for TinkRarityLoot custom items.
 */
public class TRLItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TinkRarityLoot.MODID);

    /**
     * A sealed lootbox dropped from mobs/chests.
     *
     * Registry ID: {@code tinkrarityloot:lootbox}
     *
     * Stores only mob level and rarity tier in NBT.  The visual appearance is
     * driven by the {@code tinkrarityloot:rarity} item property function which
     * returns the rarity ordinal (0–6); the root model {@code lootbox.json}
     * selects the matching per-rarity sub-model via predicate overrides.
     *
     * Right-clicking the box rolls and awards the actual Tinkers part.
     * Boxes of the same rarity + level share identical NBT and stack to 16.
     */
    public static final RegistryObject<Item> LOOTBOX_MELEE =
            ITEMS.register("lootbox_melee",  ComponentLootboxItem::new);
    public static final RegistryObject<Item> LOOTBOX_RANGED =
            ITEMS.register("lootbox_ranged", ComponentLootboxItem::new);
    public static final RegistryObject<Item> LOOTBOX_TOOL =
            ITEMS.register("lootbox_tool",   ComponentLootboxItem::new);

    /** Backward-compat alias → melee lootbox. */
    public static RegistryObject<Item> LOOTBOX = LOOTBOX_MELEE;
}
