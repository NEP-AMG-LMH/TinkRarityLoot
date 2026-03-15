package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.rarity.ConfiguredRarityRoller;
import com.tinkrarityloot.common.rarity.RarityLockTable;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.compat.mineandslash.MineAndSlashCompat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Single authoritative source for lootbox drop-chance calculation.
 *
 * ── Full modifier stack ───────────────────────────────────────────────────
 *
 *   base_chance
 *   + level × levelDropBonus
 *   + looting × lootingDropBonus
 *   = scaledBase
 *
 *   scaledBase
 *   × tierMultiplier           (boss ×3.0  elite ×2.0  normal ×1.0)
 *   = tieredChance
 *
 *   min(tieredChance, tierCap)
 *   × dimensionMultiplier      (Nether ×1.5  End ×2.0  other dims configurable)
 *   × spawnerFraction          (default 0.05 — spawners still can drop, rarely)
 *   × budgetMultiplier         (reduced when M&S/Apoth already dropped gear)
 *   × chunkAntiFarmMultiplier  (decays when same chunk farmed aggressively)
 *   × indirectKillMultiplier   (player's pet / env kill = reduced)
 *   = finalChance
 *
 *   finalChance clamp [0, absoluteMax]  (default 0.50; safety ceiling)
 *
 * ── Kill attribution ──────────────────────────────────────────────────────
 *
 *  We distinguish three kill categories:
 *
 *   PLAYER_DIRECT   — a Player entity is the immediate source.
 *                     Full drops apply.
 *
 *   PLAYER_INDIRECT — player's tamed pet / Arrow fired by player killed it.
 *                     indirectKillFraction applies (default 0.50).
 *
 *   NO_PLAYER       — environment, fire, drowning, other non-player mob.
 *                     If config.requirePlayerKill = true → multiply by 0.
 *                     Otherwise indirectKillFraction applies.
 *
 * ── Rarity resolution ─────────────────────────────────────────────────────
 *
 *  1. M&S entity rarity  (inherited if M&S is loaded and mob has a tier)
 *  2. Boss double-roll   (roll twice, take higher; for BOSS tier only)
 *  3. Normal roll        (ConfiguredRarityRoller)
 *  Then:
 *  4. RarityLockTable.clamp(rolled, mobLevel)
 *     → re-maps to highest tier the level permits (e.g. no Mythic below lvl 75)
 */
public final class DropRateEngine {

    private DropRateEngine() {}

    // ── Kill-source categories ────────────────────────────────────────────────

    public enum KillSource { PLAYER_DIRECT, PLAYER_INDIRECT, NO_PLAYER }

    /**
     * Classify who killed the entity from the {@link DamageSource}.
     *
     *  PLAYER_DIRECT    → damageSource.getEntity() instanceof Player
     *  PLAYER_INDIRECT  → damageSource.getDirectEntity() != Entity but
     *                     damageSource.getEntity() instanceof Player
     *                     (covers arrows, tridents, pets)
     *  NO_PLAYER        → everything else
     */
    public static KillSource classifyKill(DamageSource source) {
        var direct = source.getDirectEntity();
        var root   = source.getEntity();

        if (direct instanceof Player) return KillSource.PLAYER_DIRECT;
        if (root   instanceof Player) return KillSource.PLAYER_INDIRECT;
        return KillSource.NO_PLAYER;
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Result object returned by {@link #compute}.
     *
     * @param finalChance  The computed probability [0, 1] to roll against.
     * @param shouldDrop   False if any hard gate killed the drop early
     *                     (summoned suppression, fake player, etc.).
     */
    public record Result(double finalChance, boolean shouldDrop) {}

    /**
     * Compute the final lootbox drop probability for a mob death.
     *
     * @param entity       The dying mob.
     * @param tier         Pre-classified mob tier (boss / elite / normal).
     * @param source       Kill damage source (for attribution).
     * @param lootingLevel Looting enchantment level on the killing weapon.
     * @param existingDrops Current drop list (for loot-budget probe).
     * @return             {@link Result} — check {@code shouldDrop} first.
     */
    public static Result compute(LivingEntity entity, MobClassifier.MobTier tier,
                                 DamageSource source, int lootingLevel,
                                 Collection<ItemEntity> existingDrops) {

        // ── Hard suppression gates ─────────────────────────────────────────
        if (MobClassifier.isSummoned(entity) && !TRLConfig.SERVER.allowSummonedDrops.get())
            return new Result(0, false);

        double spawnerFraction = MobClassifier.isSpawnerSpawned(entity)
                ? TRLConfig.SERVER.spawnerDropFraction.get()
                : 1.0;
        if (spawnerFraction <= 0.0)
            return new Result(0, false);

        // Kill attribution
        KillSource killSrc = classifyKill(source);
        if (killSrc == KillSource.NO_PLAYER && TRLConfig.SERVER.requirePlayerKill.get())
            return new Result(0, false);

        // ── Base + scaling ────────────────────────────────────────────────
        int mobLevel = MobLevelResolver.resolve(entity);
        double base = TRLConfig.SERVER.baseDropChance.get()
                + mobLevel * TRLConfig.SERVER.levelDropBonus.get()
                + lootingLevel * TRLConfig.SERVER.lootingDropBonus.get();

        // Tier multiplier + per-tier cap
        double tierMul = switch (tier) {
            case BOSS   -> TRLConfig.SERVER.bossDropMultiplier.get();
            case ELITE  -> TRLConfig.SERVER.eliteDropMultiplier.get();
            case NORMAL -> 1.0;
        };
        double tierCap = tier == MobClassifier.MobTier.BOSS
                ? TRLConfig.SERVER.bossMaxDropChance.get()
                : TRLConfig.SERVER.maxDropChance.get();

        double chance = Math.min(tierCap, base * tierMul);

        // ── Additive modifiers ────────────────────────────────────────────
        chance *= dimensionMultiplier(entity);
        chance *= spawnerFraction;
        chance *= DropBudget.multiplier(existingDrops);
        chance *= AntiFarmTracker.chunkMultiplier(entity);
        chance *= killAttributionMultiplier(killSrc);

        // Safety ceiling — absolute maximum regardless of stacked bonuses
        double absoluteMax = TRLConfig.SERVER.absoluteMaxDropChance.get();
        chance = Math.min(absoluteMax, Math.max(0.0, chance));

        return new Result(chance, true);
    }

    // ── Rarity resolution ─────────────────────────────────────────────────────

    /**
     * Resolve the rarity for the lootbox, then enforce level locks.
     *
     * Priority:
     *  1. M&S entity rarity (inherited)
     *  2. Boss double-roll
     *  3. Normal roll
     *  → Always clamped through RarityLockTable
     */
    public static TRLRarity resolveRarity(LivingEntity entity, MobClassifier.MobTier tier,
                                          int mobLevel, Random random) {
        return resolveRarity(entity, tier, mobLevel, random, 0);
    }

    public static TRLRarity resolveRarity(LivingEntity entity, MobClassifier.MobTier tier,
                                          int mobLevel, Random random, int lootingLevel) {
        TRLRarity rolled;

        // 1. M&S inheritance
        if (TinkRarityLoot.MINE_AND_SLASH_LOADED) {
            try {
                TRLRarity masRarity = MineAndSlashCompat.getMobRarity(entity);
                if (masRarity != null) {
                    rolled = masRarity;
                    return RarityLockTable.clamp(rolled, mobLevel);
                }
            } catch (Exception ignored) {}
        }

        // 2. Boss double-roll
        if (tier == MobClassifier.MobTier.BOSS) {
            TRLRarity r1 = ConfiguredRarityRoller.roll(random);
            TRLRarity r2 = ConfiguredRarityRoller.roll(random);
            rolled = r1.ordinal() >= r2.ordinal() ? r1 : r2;
        } else {
            // 3. Normal roll
            rolled = ConfiguredRarityRoller.roll(random);
        }

        // 4. Looting rarity boost — each looting level bumps ordinal by 1 (probabilistic)
        if (lootingLevel > 0 && TRLConfig.SERVER.lootingAffectsRarity.get()) {
            TRLRarity[] vals = TRLRarity.values();
            int ord = rolled.ordinal();
            for (int i = 0; i < lootingLevel; i++) {
                // 40% chance per looting level to step up one tier
                if (random.nextFloat() < 0.40f && ord < vals.length - 1) ord++;
            }
            rolled = vals[ord];
        }

        // 5. Level gate
        return RarityLockTable.clamp(rolled, mobLevel);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Multiplier based on the dimension the mob died in.
     *
     *   Overworld: 1.0
     *   Nether:    config.netherDropMultiplier   (default 1.5)
     *   End:       config.endDropMultiplier       (default 2.0)
     *   Other:     1.0  (mod dimensions inherit neutral; can be tuned later)
     */
    private static double dimensionMultiplier(LivingEntity entity) {
        String path = entity.level().dimension().location().getPath();
        if (path.contains("nether") || path.equals("the_nether"))
            return TRLConfig.SERVER.netherDropMultiplier.get();
        if (path.contains("end") || path.equals("the_end"))
            return TRLConfig.SERVER.endDropMultiplier.get();
        return 1.0;
    }

    /** Multiplier for indirect (pet/arrow) or no-player kills. */
    private static double killAttributionMultiplier(KillSource src) {
        return switch (src) {
            case PLAYER_DIRECT   -> 1.0;
            case PLAYER_INDIRECT -> TRLConfig.SERVER.indirectKillFraction.get();
            case NO_PLAYER       -> TRLConfig.SERVER.indirectKillFraction.get();
        };
    }
}
