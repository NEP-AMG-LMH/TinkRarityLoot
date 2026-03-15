package com.tinkrarityloot.common.event;

import com.tinkrarityloot.common.material.MaterialSelector;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Invalidates the {@link MaterialSelector} material pool cache at the right moments:
 *
 *  • {@link ServerStartedEvent}    – after the first data-pack load on world open.
 *  • {@link AddReloadListenerEvent} – registers a reload listener so /reload also
 *    invalidates the cache (new materials from data packs become visible immediately).
 *
 * The cache itself is lazy: it is rebuilt on the first drop after invalidation,
 * not eagerly on world load, which keeps startup fast.
 */
public class WorldLoadHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Data packs are fully loaded by the time ServerStartedEvent fires.
        // Clear any stale cache from a previous world session.
        MaterialSelector.invalidateCache();
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        // Register a simple reload listener that invalidates the cache.
        // The actual pool rebuild is deferred to the next drop event.
        event.addListener(
            new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(net.minecraft.server.packs.resources.ResourceManager mgr,
                                       net.minecraft.util.profiling.ProfilerFiller profiler) {
                    return null; // nothing to prepare
                }

                @Override
                protected void apply(Void obj,
                                     net.minecraft.server.packs.resources.ResourceManager mgr,
                                     net.minecraft.util.profiling.ProfilerFiller profiler) {
                    MaterialSelector.invalidateCache();
                }
            }
        );
    }
}
