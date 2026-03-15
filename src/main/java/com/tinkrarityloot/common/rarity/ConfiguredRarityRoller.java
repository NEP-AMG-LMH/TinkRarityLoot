package com.tinkrarityloot.common.rarity;

import com.tinkrarityloot.common.config.TRLConfig;

import java.util.Random;

/**
 * Runtime rarity roller that reads weights and multipliers from the live
 * {@link TRLConfig} rather than the hard-coded enum constants.
 *
 * This lets server operators tune the drop table in the config file without
 * recompiling the mod.
 *
 * The static {@link TRLRarity#roll(Random)} method remains for internal use
 * before config is loaded; all game-time rolls should use this class.
 */
public final class ConfiguredRarityRoller {

    private ConfiguredRarityRoller() {}

    /** Roll a rarity using weights from the live server config. */
    public static TRLRarity roll(Random random) {
        TRLConfig.Server cfg = TRLConfig.SERVER;

        double[] weights = {
            cfg.weightCommon.get(),
            cfg.weightUncommon.get(),
            cfg.weightRare.get(),
            cfg.weightEpic.get(),
            cfg.weightUnique.get(),
            cfg.weightLegendary.get(),
            cfg.weightMythic.get()
        };

        double total = 0;
        for (double w : weights) total += w;

        double roll = random.nextDouble() * total;
        double cum  = 0;
        TRLRarity[] values = TRLRarity.values();
        for (int i = 0; i < values.length; i++) {
            cum += weights[i];
            if (roll < cum) return values[i];
        }
        return TRLRarity.COMMON;
    }

    /** Return the configured multiplier for a given rarity. */
    public static double multiplierFor(TRLRarity rarity) {
        TRLConfig.Server cfg = TRLConfig.SERVER;
        return switch (rarity) {
            case COMMON    -> cfg.mulCommon.get();
            case UNCOMMON  -> cfg.mulUncommon.get();
            case RARE      -> cfg.mulRare.get();
            case EPIC      -> cfg.mulEpic.get();
            case UNIQUE    -> cfg.mulUnique.get();
            case LEGENDARY -> cfg.mulLegendary.get();
            case MYTHIC    -> cfg.mulMythic.get();
        };
    }
}
