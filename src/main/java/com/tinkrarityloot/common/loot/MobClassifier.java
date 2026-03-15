package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Classifies mobs for drop-rate adjustment and marks spawner/summoned entities
 * so drop suppression can fire later during LivingDropsEvent.
 *
 * ── Tiers ─────────────────────────────────────────────────────────────────
 *
 *  BOSS   • max-health ≥ config.bossHealthThreshold (default 150)
 *         • known vanilla boss classes (Wither, EnderDragon)
 *         • any entity with an Apotheosis rarity NBT of "legendary" or "mythic"
 *
 *  ELITE  • max-health ≥ half the boss threshold
 *         • Apotheosis affix marker present in persistent NBT
 *
 *  NORMAL • everything else
 *
 * ── Spawn-type tagging ────────────────────────────────────────────────────
 *
 *  A FinalizeSpawn listener writes {@value #KEY_SPAWN_TYPE} into the mob's
 *  persistent NBT (survives entity re-load) so LootDropHandler can read it
 *  without keeping a runtime set.
 *
 *      "spawner"  — block spawner / any MobSpawnType.SPAWNER
 *      "summoned" — MOB_SUMMONED or COMMAND spawn types
 *      (absent)   — natural / chunk_generation / etc.
 *
 * ── Fake-player detection ─────────────────────────────────────────────────
 *
 *  Checks Forge's FakePlayer class. TRL drops are disabled for fake-player
 *  kills to prevent farm automation exploits.
 */
@Mod.EventBusSubscriber(modid = TinkRarityLoot.MODID)
public final class MobClassifier {

    // ── NBT keys written by FinalizeSpawn ─────────────────────────────────────
    public static final String KEY_SPAWN_TYPE = "trl_spawn_type";
    public static final String SPAWN_SPAWNER  = "spawner";
    public static final String SPAWN_SUMMONED = "summoned";

    private MobClassifier() {}

    // ── Mob tier ─────────────────────────────────────────────────────────────

    public enum MobTier { NORMAL, ELITE, BOSS }

    public static MobTier classify(LivingEntity entity) {
        float hp = entity.getMaxHealth();
        float bossThreshold  = TRLConfig.SERVER.bossHealthThreshold.get().floatValue();
        float eliteThreshold = bossThreshold / 2f;

        // Boss: known vanilla boss classes
        if (isVanillaBoss(entity))                    return MobTier.BOSS;
        // Boss: health threshold
        if (hp >= bossThreshold)                      return MobTier.BOSS;
        // Boss: Apotheosis high rarity marker
        if (hasApothRarityAbove(entity, 4))           return MobTier.BOSS;

        // Elite: Apotheosis affix data
        if (hasApotheosisAffix(entity))               return MobTier.ELITE;
        // Elite: health threshold
        if (hp >= eliteThreshold)                     return MobTier.ELITE;

        return MobTier.NORMAL;
    }

    // ── Spawn-type queries ────────────────────────────────────────────────────

    /** True if this mob was produced by a block spawner. */
    public static boolean isSpawnerSpawned(LivingEntity entity) {
        return SPAWN_SPAWNER.equals(entity.getPersistentData().getString(KEY_SPAWN_TYPE));
    }

    /**
     * True if this mob was summoned via command, mob ability, or
     * another entity (not natural spawning).
     */
    public static boolean isSummoned(LivingEntity entity) {
        return SPAWN_SUMMONED.equals(entity.getPersistentData().getString(KEY_SPAWN_TYPE));
    }

    /** True if the player that caused the kill is a Forge FakePlayer. */
    public static boolean isFakePlayer(Player player) {
        return player instanceof FakePlayer;
    }

    // ── FinalizeSpawn hook ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putString(KEY_SPAWN_TYPE, SPAWN_SPAWNER);
        } else if (type == MobSpawnType.MOB_SUMMONED || type == MobSpawnType.COMMAND) {
            event.getEntity().getPersistentData().putString(KEY_SPAWN_TYPE, SPAWN_SUMMONED);
        }
        // MobSpawnType.STRUCTURE, NATURAL, BREEDING, etc. → no tag (normal drops)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean isVanillaBoss(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }

    /**
     * Soft-probe for Apotheosis affix data in persistent NBT.
     * Uses NBT key strings only — no hard class reference.
     */
    private static boolean hasApotheosisAffix(LivingEntity entity) {
        if (!TinkRarityLoot.APOTHEOSIS_LOADED) return false;
        CompoundTag data = entity.getPersistentData();
        return data.contains("apoth_affix_data") || data.contains("apotheosis:affix_data");
    }

    /**
     * Returns true if the Apotheosis rarity ordinal stored on this entity
     * is greater than {@code minOrdinal}.
     * Apotheosis stores rarity as "apoth_rarity" string: common(0), uncommon(1),
     * rare(2), epic(3), legendary(4), mythic(5).
     */
    private static boolean hasApothRarityAbove(LivingEntity entity, int minOrdinal) {
        if (!TinkRarityLoot.APOTHEOSIS_LOADED) return false;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains("apoth_rarity")) return false;
        String raw = data.getString("apoth_rarity").toLowerCase();
        int ord = switch (raw) {
            case "common"    -> 0;
            case "uncommon"  -> 1;
            case "rare"      -> 2;
            case "epic"      -> 3;
            case "legendary" -> 4;
            case "mythic"    -> 5;
            default          -> -1;
        };
        return ord > minOrdinal;
    }
}
