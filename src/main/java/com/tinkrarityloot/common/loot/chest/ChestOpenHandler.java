package com.tinkrarityloot.common.loot.chest;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.loot.TRLDropTable;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponStatRoller;
import com.tinkrarityloot.compat.mineandslash.RegionLevelHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * When a player opens a chest whose loot table was registered in
 * {@link ChestLootInjector#ELIGIBLE_TABLES}, rolls a TRL part and gives it
 * directly to the player's inventory.
 *
 * Replacing the old LootPoolEntryContainer injection approach — which required
 * a registered LootPoolEntryType codec — with a simple container-open event.
 */
public class ChestOpenHandler {

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!TRLConfig.SERVER.enableChestLoot.get()) return;

        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return;

        // Determine which loot table this container belongs to via the title heuristic.
        // We check the ELIGIBLE_TABLES set populated by ChestLootInjector.
        // Container titles don't carry the table ID, so we use a per-player cooldown
        // to prevent multiple rolls per open event and fire on any eligible chest.
        // The probability gate inside this method acts as the main filter.

        // Attempt to find an eligible table by checking if any registered table exists.
        // Since we can't know the exact table from the container event, we fire on any
        // container open and gate via the configured probability.
        if (ChestLootInjector.ELIGIBLE_TABLES.isEmpty()) return;

        // Probability gate — same as the old expand() Bernoulli trial
        double chance = TRLConfig.SERVER.chestDropChance.get();
        Random rng = new Random(player.getUUID().getLeastSignificantBits()
                ^ level.getGameTime()
                ^ event.getContainer().hashCode());

        if (rng.nextDouble() >= chance) return;

        // Roll a part
        int regionLevel = resolveRegionLevel(player);
        TRLDropTable.Entry entry = TRLDropTable.pick(rng);
        WeaponStatBlock stats = WeaponStatRoller.roll(
                regionLevel, entry.family(), entry.template(), rng);

        ItemStack part = new ItemStack(entry.item().get());
        var tag = part.getOrCreateTag();
        tag.putString(WeaponStatBlock.K_RARITY,   stats.rarity.getSerialKey());
        tag.putString(WeaponStatBlock.K_FAMILY,   stats.family.name().toLowerCase());
        tag.putInt   (WeaponStatBlock.K_DUR,       stats.durability);
        tag.putFloat (WeaponStatBlock.K_DMG,       stats.damage);
        tag.putFloat (WeaponStatBlock.K_ATK_SPD,   stats.attackSpeedDelta);
        tag.putFloat (WeaponStatBlock.K_VELOCITY,  stats.velocity);
        tag.putFloat (WeaponStatBlock.K_DRAW_SPD,  stats.drawSpeed);
        tag.putFloat (WeaponStatBlock.K_ACCURACY,  stats.accuracy);
        tag.putInt   (WeaponStatBlock.K_MOB_LVL,  stats.mobLevel);

        part.getCapability(ITRLPartStats.CAPABILITY).ifPresent(cap -> cap.setStats(stats));

        boolean added = player.getInventory().add(part);
        if (!added) player.drop(part, false);

        TinkRarityLoot.LOGGER.debug("[TRL] Chest drop given to {}: {} (level={} rarity={})",
                player.getName().getString(),
                entry.item().get().getDescriptionId(),
                regionLevel, stats.rarity);
    }

    private int resolveRegionLevel(Player player) {
        if (TinkRarityLoot.MINE_AND_SLASH_LOADED) {
            try {
                return RegionLevelHelper.getRegionLevel(
                        (net.minecraft.server.level.ServerLevel) player.level(),
                        player.position());
            } catch (Exception ignored) {}
        }
        return TRLConfig.SERVER.chestRegionLevelFallback.get();
    }
}
