package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.compat.mineandslash.MineAndSlashCompat;
import com.tinkrarityloot.compat.mineandslash.RegionLevelHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

/**
 * Resolves a mob's effective level for TRL stat and drop-chance scaling.
 *
 * ── Fallback chain (first non-null / > 0 wins) ────────────────────────────
 *
 *  1. Mine and Slash entity level    — capability via soft reflection.
 *  2. Config scoreboard objective    — vanilla scoreboard, if configured.
 *  3. Config entity NBT key          — persistent NBT integer, if configured.
 *  4. Mine and Slash region level    — positional query at entity position.
 *  5. Vanilla heuristic              — health proxy + dim bonus + local difficulty.
 *
 * Result is always clamped to [1, 500].
 */
public final class MobLevelResolver {

    private MobLevelResolver() {}

    private static final int BONUS_NETHER = 10;
    private static final int BONUS_END    = 20;

    /** Return the effective level of {@code entity}. Never throws; always >= 1. */
    public static int resolve(LivingEntity entity) {

        // ── 1. M&S entity level ──────────────────────────────────────────────
        if (TinkRarityLoot.MINE_AND_SLASH_LOADED) {
            try {
                Integer masLevel = MineAndSlashCompat.getMobLevel(entity);
                if (masLevel != null && masLevel > 0) return clamp(masLevel);
            } catch (Exception ignored) {}
        }

        // ── 2. Scoreboard objective (config) ─────────────────────────────────
        String scoreObjName = TRLConfig.SERVER.mobLevelScoreboardObjective.get();
        if (!scoreObjName.isBlank() && entity.level() instanceof ServerLevel sl) {
            try {
                Scoreboard board = sl.getScoreboard();
                Objective obj = board.getObjective(scoreObjName);
                if (obj != null && board.hasPlayerScore(entity.getScoreboardName(), obj)) {
                    int score = board.getOrCreatePlayerScore(
                            entity.getScoreboardName(), obj).getScore();
                    if (score > 0) return clamp(score);
                }
            } catch (Exception ignored) {}
        }

        // ── 3. Persistent entity NBT key (config) ────────────────────────────
        String nbtKey = TRLConfig.SERVER.mobLevelNbtKey.get();
        if (!nbtKey.isBlank()) {
            try {
                var data = entity.getPersistentData();
                if (data.contains(nbtKey)) {
                    int nbtLevel = data.getInt(nbtKey);
                    if (nbtLevel > 0) return clamp(nbtLevel);
                }
            } catch (Exception ignored) {}
        }

        // ── 4. M&S region level at entity position ───────────────────────────
        if (TinkRarityLoot.MINE_AND_SLASH_LOADED && entity.level() instanceof ServerLevel sl) {
            try {
                int regionLevel = RegionLevelHelper.getRegionLevel(sl, entity.position());
                if (regionLevel > 1) return clamp(regionLevel);
            } catch (Exception ignored) {}
        }

        // ── 5. Vanilla heuristic ─────────────────────────────────────────────
        return clamp(vanillaHeuristic(entity));
    }

    private static int vanillaHeuristic(LivingEntity entity) {
        int level = Math.max(1, (int)(entity.getMaxHealth() / 5f));

        String dimPath = entity.level().dimension().location().getPath();
        if (dimPath.contains("nether"))     level += BONUS_NETHER;
        else if (dimPath.contains("end"))   level += BONUS_END;

        if (entity.level() instanceof ServerLevel sl) {
            try {
                float diff = sl.getCurrentDifficultyAt(
                        entity.blockPosition()).getEffectiveDifficulty();
                level += Math.max(0, Math.round(diff));
            } catch (Exception ignored) {}
        }
        return level;
    }

    static int clamp(int v) { return Math.max(1, Math.min(v, 500)); }
}
