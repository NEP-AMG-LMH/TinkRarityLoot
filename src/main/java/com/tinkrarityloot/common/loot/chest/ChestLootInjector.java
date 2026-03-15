package com.tinkrarityloot.common.loot.chest;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers chest-type loot table IDs so that {@link ChestOpenHandler} knows
 * which chests are eligible for TRL part injection.
 *
 * ── Why no LootPool injection ─────────────────────────────────────────────
 *
 * Vanilla's LootPool.Builder.add() expects a LootPoolEntryContainer.Builder<?>,
 * not a bare LootPoolEntryContainer instance.  Subclassing the builder hierarchy
 * requires a registered LootPoolEntryType with a codec — a significant amount of
 * data-pack plumbing for a runtime-only injection.
 *
 * Simpler approach:
 *   1. Here we record which table IDs are chest tables in ELIGIBLE_TABLES.
 *   2. ChestOpenHandler listens for the chest-open event, checks this set,
 *      rolls the drop, and injects the part directly into the output list.
 */
public class ChestLootInjector {

    /**
     * Table IDs marked eligible for TRL chest drops.
     * Populated during data-pack load; read by ChestOpenHandler.
     */
    public static final Set<ResourceLocation> ELIGIBLE_TABLES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Set<ResourceLocation> BLOCKLIST = Set.of(
            new ResourceLocation("minecraft", "gameplay/fishing"),
            new ResourceLocation("minecraft", "entities/player"),
            new ResourceLocation("minecraft", "chests/village/village_cartographer")
    );

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!TRLConfig.SERVER.enableChestLoot.get()) return;

        ResourceLocation id = event.getName();
        if (!isChestTable(id)) return;

        ELIGIBLE_TABLES.add(id);
        TinkRarityLoot.LOGGER.debug("[TRL] Registered chest table for TRL drops: {}", id);
    }

    private boolean isChestTable(ResourceLocation id) {
        if (BLOCKLIST.contains(id)) return false;
        String path = id.getPath();
        return path.startsWith("chests/") || path.contains("/chests/");
    }
}
