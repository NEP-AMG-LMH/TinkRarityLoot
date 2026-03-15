package com.tinkrarityloot.common.event;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.loot.ComponentLootboxItem;
import com.tinkrarityloot.common.loot.DropRateEngine;
import com.tinkrarityloot.common.loot.MobClassifier;
import com.tinkrarityloot.common.loot.MobLevelResolver;
import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Intercepts mob death and drops a {@link ComponentLootboxItem}.
 *
 * All chance math is delegated to {@link DropRateEngine#compute}, which
 * applies the full modifier stack:
 *
 *   base + level×bonus + looting×bonus
 *   × tierMultiplier  (boss / elite / normal)
 *   × dimensionMultiplier
 *   × spawnerFraction
 *   × lootBudgetMultiplier
 *   × chunkAntiFarmMultiplier
 *   × killAttributionMultiplier
 *   clamped to absoluteMaxDropChance
 *
 * Rarity resolution is delegated to {@link DropRateEngine#resolveRarity},
 * which handles M&S inheritance, boss double-roll, and level locking.
 *
 * All rolls happen SERVER-SIDE.  The client receives the final ItemStack
 * via normal entity sync; the NBT predicate drives visual selection there.
 */
public class LootDropHandler {

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!TRLConfig.SERVER.enableMobDrops.get()) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // FakePlayer check (all other exploit gates are inside DropRateEngine)
        if (TRLConfig.SERVER.suppressFakePlayerDrops.get()) {
            Player killer = event.getSource().getEntity() instanceof Player p ? p : null;
            if (killer != null && MobClassifier.isFakePlayer(killer)) return;
        }

        // ── Tier classification ───────────────────────────────────────────────
        MobClassifier.MobTier tier = MobClassifier.classify(entity);

        // ── Full chance computation ───────────────────────────────────────────
        DropRateEngine.Result result = DropRateEngine.compute(
                entity, tier, event.getSource(),
                event.getLootingLevel(), event.getDrops());

        if (!result.shouldDrop()) return;

        // ── Random roll ───────────────────────────────────────────────────────
        // Seed: entity UUID XOR game time — server-deterministic, not client-guessable
        Random random = new Random(
                entity.getUUID().getLeastSignificantBits() ^ entity.level().getGameTime());

        if (random.nextDouble() >= result.finalChance()) return;

        // ── Build lootbox ─────────────────────────────────────────────────────
        int mobLevel = MobLevelResolver.resolve(entity);
        TRLRarity rarity = DropRateEngine.resolveRarity(entity, tier, mobLevel, random,
                event.getLootingLevel());

        String source = tier == MobClassifier.MobTier.BOSS
                ? ComponentLootboxItem.SOURCE_BOSS
                : ComponentLootboxItem.SOURCE_KILL;

        // Category selection — driven by config:
        //   mob_category_lock = false (default): random MELEE or RANGED
        //   mob_category_lock = true:  mob-type-based (skeleton→RANGED, etc.)
        //   enable_tool_lootbox = false (default): TOOL never drops from mobs
        com.tinkrarityloot.common.loot.LootboxCategory category;
        if (TRLConfig.SERVER.mobCategoryLock.get()) {
            category = resolveCategory(entity);
        } else {
            // Random between MELEE and RANGED (equal weight)
            // TOOL only included if explicitly enabled
            int bound = TRLConfig.SERVER.enableToolLootbox.get() ? 3 : 2;
            int roll  = random.nextInt(bound);
            category = switch (roll) {
                case 1 -> com.tinkrarityloot.common.loot.LootboxCategory.RANGED;
                case 2 -> com.tinkrarityloot.common.loot.LootboxCategory.TOOL;
                default -> com.tinkrarityloot.common.loot.LootboxCategory.MELEE;
            };
        }
        ItemStack lootbox = ComponentLootboxItem.create(mobLevel, rarity, source, category);

        // ── Spawn ─────────────────────────────────────────────────────────────
        ItemEntity drop = new ItemEntity(
                entity.level(), entity.getX(), entity.getY(), entity.getZ(), lootbox);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);

        TinkRarityLoot.LOGGER.debug(
                "[TRL] Lootbox drop: rarity={} level={} tier={} chance={:.3f} source={}",
                rarity, mobLevel, tier, result.finalChance(), source);
    }
    private static com.tinkrarityloot.common.loot.LootboxCategory resolveCategory(
            net.minecraft.world.entity.LivingEntity entity) {
        String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(entity.getType()) != null
                ? net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                        .getKey(entity.getType()).getPath()
                : "";

        // Ranged mobs
        if (id.contains("skeleton") || id.contains("stray") || id.contains("wither_skeleton")
                || id.contains("drowned") || id.contains("piglin") && entity.isHolding(
                        stack -> stack.getItem() instanceof net.minecraft.world.item.BowItem
                              || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem)
                || entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                || entity instanceof net.minecraft.world.entity.monster.Drowned) {
            return com.tinkrarityloot.common.loot.LootboxCategory.RANGED;
        }

        // Mining / tool mobs
        if (id.contains("silverfish") || id.contains("endermite") || id.contains("shulker")
                || id.contains("cave_spider") || id.contains("creeper")) {
            return com.tinkrarityloot.common.loot.LootboxCategory.TOOL;
        }

        // Default
        return com.tinkrarityloot.common.loot.LootboxCategory.MELEE;
    }

}
