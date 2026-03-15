package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side anti-farm tracking.
 *
 * ── Chunk kill window ─────────────────────────────────────────────────────
 *
 *  Each server-level chunk has a rolling kill counter.  When a mob dies:
 *   1. The counter for that chunk (level + ChunkPos) is incremented.
 *   2. The counter decays toward 0 over {@code antiFarmDecaySeconds} seconds
 *      (implemented as: stamp the reset time on first increment, re-zero when
 *      the window expires).
 *   3. If the counter exceeds {@code antiFarmChunkKillLimit}, the drop chance
 *      multiplier for that chunk is scaled by {@code antiFarmChunkPenalty}.
 *
 *  Effect: killing 10 mobs in the same chunk in 60 seconds reduces chance by
 *  the configured penalty per excess kill; naturally-explored areas are
 *  unaffected because players move chunk-to-chunk.
 *
 * ── Named mob cooldown ────────────────────────────────────────────────────
 *
 *  Named mobs (CustomName set) are popular farm targets.  Once a named mob
 *  drops a lootbox its UUID is recorded; subsequent kills within
 *  {@code namedMobCooldownSeconds} return a 0.0 multiplier (no drop).
 *
 *  The cooldown map is bounded to {@code MAX_NAMED_ENTRIES} to prevent
 *  unbounded memory growth in large worlds.
 *
 * ── Kill attribution ──────────────────────────────────────────────────────
 *
 *  Mobs killed by non-player sources (environment, fire, drowning, other
 *  mobs, player pets) use a configurable reduced multiplier.  The calling
 *  code checks this before computing the full drop chance.
 *
 * ── Lifecycle ─────────────────────────────────────────────────────────────
 *
 *  All state is cleared on world unload so singleplayer restarts don't carry
 *  stale data.  This class is registered on the Forge bus in
 *  {@link TinkRarityLoot}.
 */
@Mod.EventBusSubscriber(modid = TinkRarityLoot.MODID)
public final class AntiFarmTracker {

    // ── State ─────────────────────────────────────────────────────────────────

    /** Key = "dimension_key/chunkX,chunkZ" → kill record */
    private static final Map<String, ChunkRecord> CHUNK_KILLS = new ConcurrentHashMap<>();

    /** Named mob UUID → game-time of first kill in the cooldown window */
    private static final Map<UUID, Long> NAMED_MOB_COOLDOWN = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
            return size() > MAX_NAMED_ENTRIES;
        }
    };

    private static final int MAX_NAMED_ENTRIES = 1024;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a drop-chance multiplier for this entity's home chunk.
     * Call this AFTER the normal tier/budget math; multiply the result in.
     *
     * @return 1.0 if below the kill limit; penalty fraction if above.
     *         0.0 if the named-mob cooldown is active.
     */
    public static double chunkMultiplier(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel sl)) return 1.0;

        long now = sl.getGameTime();

        // ── Named mob cooldown ────────────────────────────────────────────────
        if (entity.hasCustomName()) {
            int cooldownTicks = TRLConfig.SERVER.namedMobCooldownSeconds.get() * 20;
            Long firstKill = NAMED_MOB_COOLDOWN.get(entity.getUUID());
            if (firstKill != null && now - firstKill < cooldownTicks) {
                TinkRarityLoot.LOGGER.debug("[TRL] Named mob {} on cooldown.", entity.getUUID());
                return 0.0;
            }
            // Record or refresh
            NAMED_MOB_COOLDOWN.put(entity.getUUID(), now);
        }

        // ── Chunk kill window ─────────────────────────────────────────────────
        if (!TRLConfig.SERVER.enableAntiFarm.get()) return 1.0;

        String key = chunkKey(sl, entity);
        int windowTicks = TRLConfig.SERVER.antiFarmWindowSeconds.get() * 20;
        int limit       = TRLConfig.SERVER.antiFarmKillLimit.get();

        ChunkRecord record = CHUNK_KILLS.computeIfAbsent(key, k -> new ChunkRecord());
        record.increment(now, windowTicks);

        if (record.count <= limit) return 1.0;

        // Each excess kill reduces chance by the penalty factor, floored at minFraction
        int   excess      = record.count - limit;
        double penalty    = TRLConfig.SERVER.antiFarmPenaltyPerKill.get();
        double minFrac    = TRLConfig.SERVER.antiFarmMinFraction.get();
        double multiplier = Math.max(minFrac, 1.0 - excess * penalty);

        TinkRarityLoot.LOGGER.debug(
                "[TRL] Anti-farm: chunk={} kills={} excess={} mul={:.2f}",
                key, record.count, excess, multiplier);
        return multiplier;
    }

    /**
     * Returns the drop-chance multiplier for a kill whose source is not a
     * direct player attack (environment, pet, other mob, etc.).
     * Returns 1.0 if the feature is disabled.
     */
    public static double indirectKillMultiplier() {
        if (!TRLConfig.SERVER.enableIndirectKillReduction.get()) return 1.0;
        return TRLConfig.SERVER.indirectKillFraction.get();
    }

    /** Clear all state on world unload (singleplayer session end). */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        CHUNK_KILLS.clear();
        NAMED_MOB_COOLDOWN.clear();
        TinkRarityLoot.LOGGER.debug("[TRL] AntiFarmTracker cleared on world unload.");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String chunkKey(ServerLevel level, LivingEntity entity) {
        ResourceLocation dim = level.dimension().location();
        ChunkPos pos = new ChunkPos(entity.blockPosition());
        return dim.getNamespace() + ":" + dim.getPath() + "/" + pos.x + "," + pos.z;
    }

    // ── Inner record type ─────────────────────────────────────────────────────

    /** Mutable kill counter with rolling window reset. */
    private static final class ChunkRecord {
        int  count     = 0;
        long windowEnd = 0;

        /** Increment counter; resets to 1 if the window has expired. */
        void increment(long now, int windowTicks) {
            if (now >= windowEnd) {
                count     = 1;
                windowEnd = now + windowTicks;
            } else {
                count++;
            }
        }
    }
}
