package com.tinkrarityloot.common.registry;

import com.tinkrarityloot.common.rarity.TRLRarity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Plays layered vanilla sounds for lootbox opens.
 *
 * Minecraft's sounds.json picks ONE sound from a list randomly — it does not
 * layer them simultaneously.  To layer multiple sounds we call world.playSound()
 * multiple times in code.  No custom SoundEvent registration is needed; we use
 * vanilla sound events directly.
 */
public final class TRLSounds {

    private TRLSounds() {}

    /**
     * Play the rarity-appropriate layered open sound at the player's position.
     * Called server-side only (world.isClientSide() == false).
     */
    public static void playOpen(Level world, Player player, TRLRarity rarity) {
        double x = player.getX(), y = player.getY(), z = player.getZ();

        // All rarities: chest open as the base click
        boolean isEnder = rarity.ordinal() >= TRLRarity.RARE.ordinal();
        world.playSound(null, x, y, z,
                isEnder ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN,
                SoundSource.PLAYERS,
                0.75f, 0.95f + rarity.ordinal() * 0.02f);

        // Common: just the chest click — nothing magical
        if (rarity == TRLRarity.COMMON) return;

        // Uncommon+: chime note
        world.playSound(null, x, y, z,
                SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS,
                0.55f, 1.0f + rarity.ordinal() * 0.08f);

        // Rare+: level-up jingle
        if (rarity.ordinal() >= TRLRarity.RARE.ordinal()) {
            world.playSound(null, x, y, z,
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    0.45f + rarity.ordinal() * 0.06f,
                    0.8f + rarity.ordinal() * 0.06f);
        }

        // Epic+: bell strike
        if (rarity.ordinal() >= TRLRarity.EPIC.ordinal()) {
            world.playSound(null, x, y, z,
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS,
                    0.7f, 0.85f + rarity.ordinal() * 0.04f);
        }

        // Unique+: totem shimmer
        if (rarity.ordinal() >= TRLRarity.UNIQUE.ordinal()) {
            world.playSound(null, x, y, z,
                    SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS,
                    0.4f, 1.2f + rarity.ordinal() * 0.05f);
        }

        // Legendary+: dragon growl at low volume — epic reveal
        if (rarity.ordinal() >= TRLRarity.LEGENDARY.ordinal()) {
            world.playSound(null, x, y, z,
                    SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.PLAYERS,
                    0.22f, 1.4f + (rarity == TRLRarity.MYTHIC ? 0.1f : 0f));
        }
    }
}
