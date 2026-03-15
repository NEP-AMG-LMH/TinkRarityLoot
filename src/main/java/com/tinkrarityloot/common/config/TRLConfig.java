package com.tinkrarityloot.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * TinkRarityLoot server-side configuration.
 *
 * Config file: saves/world/serverconfig/tinkrarityloot-server.toml
 *
 * All values can be changed live by editing the file and running
 *   /reload   (or restarting the server).
 */
public class TRLConfig {

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER      = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    public static class Server {

        // ── Drop chances ─────────────────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue baseDropChance;
        public final ForgeConfigSpec.DoubleValue levelDropBonus;
        public final ForgeConfigSpec.DoubleValue lootingDropBonus;
        public final ForgeConfigSpec.DoubleValue maxDropChance;
        /** Multiplier applied to drop chance for boss-tier entities. */
        public final ForgeConfigSpec.DoubleValue bossDropMultiplier;
        /** Multiplier applied to drop chance for elite-tier entities. */
        public final ForgeConfigSpec.DoubleValue eliteDropMultiplier;
        /** Separate hard cap applied after the boss multiplier. */
        public final ForgeConfigSpec.DoubleValue bossMaxDropChance;

        // ── Boss / elite detection ────────────────────────────────────────────
        /** Max-health threshold above which an entity is classified as BOSS. 0 = disabled. */
        public final ForgeConfigSpec.DoubleValue bossHealthThreshold;
        /** Max-health threshold above which an entity is classified as ELITE. 0 = disabled. */
        public final ForgeConfigSpec.DoubleValue eliteHealthThreshold;

        // ── Mob-level resolution ──────────────────────────────────────────────
        /**
         * Name of a scoreboard objective that stores mob level.
         * Leave blank to skip the scoreboard source.
         * Example: "level" (Mine & Slash writes mob levels to scoreboard in some versions).
         */
        public final ForgeConfigSpec.ConfigValue<String> mobLevelScoreboardObjective;
        /**
         * Persistent entity NBT key that holds an integer mob level.
         * Leave blank to skip the NBT source.
         * Example: "AttributeLevel"  or  "trl_level"
         */
        public final ForgeConfigSpec.ConfigValue<String> mobLevelNbtKey;
        /** Enable the spawn-distance fallback heuristic. */
        public final ForgeConfigSpec.BooleanValue useSpawnDistanceFallback;
        /** Level per block of distance from world origin (Overworld). */
        public final ForgeConfigSpec.DoubleValue spawnDistanceScale;
        /** Maximum level the distance heuristic can produce. */
        public final ForgeConfigSpec.IntValue maxDistanceLevel;
        /** Level multiplier in the Nether (default 2.0 — Nether coords are ×8 real dist). */
        public final ForgeConfigSpec.DoubleValue netherLevelMultiplier;
        /** Level multiplier in the End (default 3.0 — End is a late-game dimension). */
        public final ForgeConfigSpec.DoubleValue endLevelMultiplier;

        // ── Rarity weights ───────────────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue weightCommon;
        public final ForgeConfigSpec.DoubleValue weightUncommon;
        public final ForgeConfigSpec.DoubleValue weightRare;
        public final ForgeConfigSpec.DoubleValue weightEpic;
        public final ForgeConfigSpec.DoubleValue weightUnique;
        public final ForgeConfigSpec.DoubleValue weightLegendary;
        public final ForgeConfigSpec.DoubleValue weightMythic;

        // ── Rarity stat multipliers ───────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue mulCommon;
        public final ForgeConfigSpec.DoubleValue mulUncommon;
        public final ForgeConfigSpec.DoubleValue mulRare;
        public final ForgeConfigSpec.DoubleValue mulEpic;
        public final ForgeConfigSpec.DoubleValue mulUnique;
        public final ForgeConfigSpec.DoubleValue mulLegendary;
        public final ForgeConfigSpec.DoubleValue mulMythic;

        // ── Stat formula overrides ────────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue bladeDurScaleLo;
        public final ForgeConfigSpec.DoubleValue bladeDurScaleHi;
        public final ForgeConfigSpec.DoubleValue bladeDmgLevelLo;
        public final ForgeConfigSpec.DoubleValue bladeDmgLevelHi;
        public final ForgeConfigSpec.DoubleValue bladeDmgBase;
        public final ForgeConfigSpec.DoubleValue bladeDmgRarityLo;
        public final ForgeConfigSpec.DoubleValue bladeDmgRarityHi;

        // ── Chest loot ────────────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue enableChestLoot;
        public final ForgeConfigSpec.DoubleValue  chestDropChance;
        public final ForgeConfigSpec.IntValue     chestRegionLevelFallback;

        // ── Feature flags ─────────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue enableMobDrops;
        public final ForgeConfigSpec.BooleanValue enableToolAssemblyRarity;
        public final ForgeConfigSpec.BooleanValue enableApotheosisSync;
        public final ForgeConfigSpec.BooleanValue enableTinkersStatInjection;

        // ── Drop table category flags ─────────────────────────────────────────
        /** Basic weapon parts: swords, guards, rods, bows, arrows, dagger, battlesign, frying pan, cutlass */
        public final ForgeConfigSpec.BooleanValue enableBasicWeaponParts;
        /** Advanced weapon parts: hammer, scythe, cleaver, battleaxe (requires Tool Forge) */
        public final ForgeConfigSpec.BooleanValue enableAdvancedWeaponParts;
        /** Lock lootbox category to mob type. False = random MELEE/RANGED. */
        public final ForgeConfigSpec.BooleanValue mobCategoryLock;
        /** Include TOOL lootboxes in mob drops. Off by default. */
        public final ForgeConfigSpec.BooleanValue enableToolLootbox;
        /** Looting enchantment also improves rarity odds. */
        public final ForgeConfigSpec.BooleanValue lootingAffectsRarity;

        // ── Exploit prevention ────────────────────────────────────────────────
        /** Fraction of base drop chance kept for spawner-spawned mobs (0 = fully suppressed). */
        public final ForgeConfigSpec.DoubleValue spawnerDropFraction;
        /** If false, mobs summoned via command or mob ability never drop TRL parts. */
        public final ForgeConfigSpec.BooleanValue allowSummonedDrops;
        /** If true, drops from FakePlayer kills (automation mods) are suppressed. */
        public final ForgeConfigSpec.BooleanValue suppressFakePlayerDrops;

        // ── Loot budgeting ────────────────────────────────────────────────────
        /** Enable loot budgeting: reduce TRL chance when M&S/Apotheosis already dropped gear. */
        public final ForgeConfigSpec.BooleanValue enableLootBudgeting;
        /**
         * Per competing-item drop reduction factor.
         * 0.40 = each M&S/Apotheosis gear item found in the drop list reduces
         * TRL's chance by 40%, floored at 10% of the original chance.
         */
        public final ForgeConfigSpec.DoubleValue budgetedDropReduction;

        // ── Material level gates ──────────────────────────────────────────────
        /**
         * Mob level below which only tier-1/2 materials can drop.
         * Prevents low-level players from getting endgame-looking materials immediately.
         */
        public final ForgeConfigSpec.IntValue materialLevelGate1;
        /**
         * Mob level below which only tier-1/2/3 materials can drop.
         * Above this level all registered materials are eligible.
         */
        public final ForgeConfigSpec.IntValue materialLevelGate2;

        // ── Rarity level locks ────────────────────────────────────────────────
        /** Minimum mob level to roll UNCOMMON. 0 = no lock. */
        public final ForgeConfigSpec.IntValue rarityLockUncommon;
        public final ForgeConfigSpec.IntValue rarityLockRare;
        public final ForgeConfigSpec.IntValue rarityLockEpic;
        public final ForgeConfigSpec.IntValue rarityLockUnique;
        public final ForgeConfigSpec.IntValue rarityLockLegendary;
        public final ForgeConfigSpec.IntValue rarityLockMythic;

        // ── Dimension drop multipliers ────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue netherDropMultiplier;
        public final ForgeConfigSpec.DoubleValue endDropMultiplier;

        // ── Kill attribution ──────────────────────────────────────────────────
        /** If true, only direct player kills produce lootboxes. Env/pet kills = 0 chance. */
        public final ForgeConfigSpec.BooleanValue requirePlayerKill;
        /** Fraction of base chance kept for indirect kills (pet, arrow, etc.). */
        public final ForgeConfigSpec.DoubleValue indirectKillFraction;

        // ── Anti-farm ─────────────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue enableAntiFarm;
        /** Kills per chunk within the window before the penalty kicks in. */
        public final ForgeConfigSpec.IntValue antiFarmKillLimit;
        /** Rolling window duration in seconds. */
        public final ForgeConfigSpec.IntValue antiFarmWindowSeconds;
        /** Per-excess-kill chance reduction factor. 0.10 = −10% per extra kill. */
        public final ForgeConfigSpec.DoubleValue antiFarmPenaltyPerKill;
        /** Floor: drop chance never falls below this fraction due to chunk farming. */
        public final ForgeConfigSpec.DoubleValue antiFarmMinFraction;
        /** Seconds before a named mob's UUID is eligible to drop again. */
        public final ForgeConfigSpec.IntValue namedMobCooldownSeconds;

        /** Hard safety ceiling on the final computed drop chance. */
        public final ForgeConfigSpec.DoubleValue absoluteMaxDropChance;
        /** Enable indirect kill reduction (enableIndirectKillReduction flag). */
        public final ForgeConfigSpec.BooleanValue enableIndirectKillReduction;

        public Server(ForgeConfigSpec.Builder b) {
            b.comment("TinkRarityLoot Configuration").push("tinkrarityloot");

            // ── Drop chances ─────────────────────────────────────────────────
            b.comment("Drop chance settings").push("drop_chance");
            baseDropChance   = b.comment("Base probability (0–1) a mob drops a Tinkers part. Recommended: 0.03 (3%)")
                    .defineInRange("base_drop_chance",   0.03, 0.0, 1.0);
            levelDropBonus   = b.comment("Extra probability per mob level (+0.03% per level = +1.5% at level 50)")
                    .defineInRange("level_drop_bonus",   0.0003, 0.0, 0.1);
            lootingDropBonus = b.comment("Extra probability per looting enchantment level")
                    .defineInRange("looting_drop_bonus", 0.03,  0.0, 0.2);
            maxDropChance    = b.comment("Hard cap on drop probability for normal mobs")
                    .defineInRange("max_drop_chance",    0.12,  0.0, 1.0);
            bossDropMultiplier = b.comment("Multiplier applied to drop chance for boss-tier entities (×3 → max 35%)")
                    .defineInRange("boss_drop_multiplier",  3.0, 1.0, 20.0);
            eliteDropMultiplier = b.comment("Multiplier applied to drop chance for elite-tier entities")
                    .defineInRange("elite_drop_multiplier", 2.0, 1.0, 10.0);
            bossMaxDropChance = b.comment("Separate hard cap applied after the boss multiplier")
                    .defineInRange("boss_max_drop_chance",  0.35, 0.0, 1.0);
            b.pop();

            // ── Boss / elite detection ────────────────────────────────────────
            b.comment("Boss and elite entity classification").push("boss_detection");
            bossHealthThreshold  = b.comment("Max health at or above which an entity is BOSS. 0 = disabled.")
                    .defineInRange("boss_health_threshold",  500.0, 0.0, 100000.0);
            eliteHealthThreshold = b.comment("Max health at or above which an entity is ELITE. 0 = disabled.")
                    .defineInRange("elite_health_threshold", 100.0, 0.0, 100000.0);
            b.pop();

            // ── Mob-level resolution ──────────────────────────────────────────
            b.comment("Mob-level resolution fallback chain").push("mob_level");
            mobLevelScoreboardObjective = b.comment(
                    "Name of a scoreboard objective that stores mob level. Leave blank to skip.",
                    "Example: \"level\"  (Mine & Slash writes mob levels to scoreboard in some versions)")
                    .define("scoreboard_objective", "");
            mobLevelNbtKey = b.comment(
                    "Persistent entity NBT key holding an integer mob level. Leave blank to skip.",
                    "Example: \"AttributeLevel\"  or  \"trl_level\"")
                    .define("nbt_level_key", "");
            useSpawnDistanceFallback = b.comment("Enable the spawn-distance heuristic fallback")
                    .define("use_spawn_distance", true);
            spawnDistanceScale = b.comment("Level per block of distance from world origin (Overworld).",
                    "level = sqrt(x²+z²) × scale × dim_multiplier   Default: 0.05 → level 50 at 1000 blocks")
                    .defineInRange("spawn_distance_scale", 0.05, 0.0, 10.0);
            maxDistanceLevel = b.comment("Maximum level the distance heuristic can produce")
                    .defineInRange("max_distance_level", 200, 1, 500);
            netherLevelMultiplier = b.comment("Dimension multiplier in the Nether (default 2.0)")
                    .defineInRange("nether_multiplier", 2.0, 1.0, 10.0);
            endLevelMultiplier = b.comment("Dimension multiplier in the End (default 3.0)")
                    .defineInRange("end_multiplier", 3.0, 1.0, 10.0);
            b.pop();

            // ── Rarity weights ───────────────────────────────────────────────
            b.comment("Roll weights – do not need to sum to 100; relative ratios matter")
             .push("rarity_weights");
            weightCommon    = b.defineInRange("common",    55.0, 0.0, 10000.0);
            weightUncommon  = b.defineInRange("uncommon",  25.0, 0.0, 10000.0);
            weightRare      = b.defineInRange("rare",      10.0, 0.0, 10000.0);
            weightEpic      = b.defineInRange("epic",       3.5, 0.0, 10000.0);
            weightUnique    = b.defineInRange("unique",     3.5, 0.0, 10000.0);
            weightLegendary = b.defineInRange("legendary",  2.0, 0.0, 10000.0);
            weightMythic    = b.defineInRange("mythic",     1.0, 0.0, 10000.0);
            b.pop();

            // ── Rarity multipliers ───────────────────────────────────────────
            b.comment("Stat multipliers applied per rarity tier").push("rarity_multipliers");
            mulCommon    = b.defineInRange("common",    1.00, 0.01, 100.0);
            mulUncommon  = b.defineInRange("uncommon",  1.25, 0.01, 100.0);
            mulRare      = b.defineInRange("rare",      1.60, 0.01, 100.0);
            mulEpic      = b.defineInRange("epic",      2.20, 0.01, 100.0);
            mulUnique    = b.defineInRange("unique",    2.50, 0.01, 100.0);
            mulLegendary = b.defineInRange("legendary", 3.50, 0.01, 100.0);
            mulMythic    = b.defineInRange("mythic",    5.00, 0.01, 100.0);
            b.pop();

            // ── Blade formula ────────────────────────────────────────────────
            b.comment("Sword Blade stat formula coefficients").push("blade_formula");
            bladeDurScaleLo  = b.defineInRange("dur_scale_lo",  25.0, 0.0, 10000.0);
            bladeDurScaleHi  = b.defineInRange("dur_scale_hi",  75.0, 0.0, 10000.0);
            bladeDmgLevelLo  = b.defineInRange("dmg_level_lo",  0.20, 0.0, 100.0);
            bladeDmgLevelHi  = b.defineInRange("dmg_level_hi",  0.50, 0.0, 100.0);
            bladeDmgBase     = b.defineInRange("dmg_base",      3.0,  0.0, 1000.0);
            bladeDmgRarityLo = b.defineInRange("dmg_rarity_lo", 1.00, 0.0, 100.0);
            bladeDmgRarityHi = b.defineInRange("dmg_rarity_hi", 1.25, 0.0, 100.0);
            b.pop();

            // ── Chest loot ───────────────────────────────────────────────────
            b.comment("Chest / structure loot injection").push("chest_loot");
            enableChestLoot          = b.comment("Inject TRL parts into chest loot tables")
                    .define("enable", true);
            chestDropChance          = b.comment(
                    "Probability a chest loot table roll also yields a TRL part.",
                    "Kept lower than mob drops: chests are already curated loot.")
                    .defineInRange("drop_chance", 0.10, 0.0, 1.0);
            chestRegionLevelFallback = b.comment("Region level used when Mine and Slash region level is unavailable")
                    .defineInRange("region_level_fallback", 5, 1, 500);
            b.pop();

            // ── Feature flags ────────────────────────────────────────────────
            b.comment("Enable / disable individual features").push("features");
            enableMobDrops            = b.define("mob_drops", true);
            enableToolAssemblyRarity  = b.define("tool_assembly_rarity", true);
            enableApotheosisSync      = b.define("apotheosis_sync", true);
            enableTinkersStatInjection = b.define("tinkers_stat_injection", true);
            b.pop();

            // ── Drop table categories ────────────────────────────────────────
            b.comment("Control which weapon part categories can drop from mobs and chests.").push("drop_categories");
            enableBasicWeaponParts = b.comment(
                    "Include basic weapon parts in the drop pool:",
                    "  Swords (blade, guard, rod), Dagger (knife blade, crossbar),",
                    "  Shortbow + arrows, Battlesign, Frying Pan, Cutlass (full guard).",
                    "  These cover Tool Station weapons.")
                    .define("basic_weapon_parts", true);
            enableAdvancedWeaponParts = b.comment(
                    "Include advanced weapon parts in the drop pool:",
                    "  Hammer (hammer head, large plate, tough handle),",
                    "  Scythe (scythe head, tough binding),",
                    "  Cleaver (large blade), Battleaxe (broad axe head).",
                    "  These cover Tool Forge weapons.")
                    .define("advanced_weapon_parts", true);

            b.comment("─── Category & Looting ─────────────────────────────────────────────────────────────").define("_cat_sep", true);
            mobCategoryLock = b.comment(
                    "Lock lootbox category to mob type (skeleton→ranged, creeper→tool, etc.).",
                    "Set false to drop random MELEE or RANGED boxes from all mobs.")
                    .define("mob_category_lock", false);
            enableToolLootbox = b.comment(
                    "Include TOOL lootboxes in mob drops.",
                    "Disabled by default — tool parts have fewer useful stat slots.",
                    "Enable if you want pickaxe/hammer parts to drop from mobs.")
                    .define("enable_tool_lootbox", false);
            lootingAffectsRarity = b.comment(
                    "Each level of Looting increases the chance of a higher rarity roll.",
                    "Looting I: +1 rarity weight tier. Looting III: +3 tiers.")
                    .define("looting_affects_rarity", true);
            b.pop();

            // ── Exploit prevention ───────────────────────────────────────────
            b.comment("Drop suppression for automated or non-natural kills").push("exploit_prevention");
            spawnerDropFraction = b.comment(
                    "Fraction of the normal drop chance kept for block-spawner mobs.",
                    "0.0 = fully suppressed.  0.05 = 5% of normal (still possible but rare).")
                    .defineInRange("spawner_drop_fraction",   0.05, 0.0, 1.0);
            allowSummonedDrops  = b.comment(
                    "If false, mobs spawned by command or mob summon abilities never drop TRL parts.")
                    .define("allow_summoned_drops", false);
            suppressFakePlayerDrops = b.comment(
                    "If true, kills made by Forge FakePlayer instances (automation mods, pipes, etc.) "
                    + "do not produce TRL drops.")
                    .define("suppress_fake_player_drops", true);
            b.pop();

            // ── Loot budgeting ───────────────────────────────────────────────
            b.comment("Reduce TRL drop chance when other mods already dropped gear").push("loot_budget");
            enableLootBudgeting = b.comment(
                    "When true, TRL checks the existing drop list for M&S gear or Apotheosis affix items "
                    + "and reduces its own drop chance proportionally.")
                    .define("enabled", true);
            budgetedDropReduction = b.comment(
                    "Drop-chance reduction factor per competing item found.",
                    "0.40 = −40% per item, floored at 10% of base.  0.0 = disable.")
                    .defineInRange("reduction_per_item", 0.40, 0.0, 1.0);
            b.pop();

            // ── Material level gates ─────────────────────────────────────────
            b.comment("Gate high-tier materials behind mob level to preserve progression").push("material_gates");
            materialLevelGate1 = b.comment(
                    "Below this mob level, only tier-1 and tier-2 materials can be chosen.",
                    "Prevents new players from getting obsidian / netherite-looking parts immediately.")
                    .defineInRange("tier_1_2_max_level", 20, 1, 500);
            materialLevelGate2 = b.comment(
                    "Below this mob level, only tier-1, 2, and 3 materials can be chosen.",
                    "Above this level all registered materials are eligible.")
                    .defineInRange("tier_3_max_level", 50, 1, 500);
            b.pop();

            // ── Rarity level locks ───────────────────────────────────────────
            b.comment("Minimum mob level required to roll each rarity tier.",
                    "0 = no lock. Rolled rarity is stepped down if mob is below threshold.")
             .push("rarity_locks");
            rarityLockUncommon  = b.defineInRange("uncommon",  5,  0, 500);
            rarityLockRare      = b.defineInRange("rare",      15, 0, 500);
            rarityLockEpic      = b.defineInRange("epic",      30, 0, 500);
            rarityLockUnique    = b.defineInRange("unique",    40, 0, 500);
            rarityLockLegendary = b.defineInRange("legendary", 55, 0, 500);
            rarityLockMythic    = b.defineInRange("mythic",    75, 0, 500);
            b.pop();

            // ── Dimension multipliers ────────────────────────────────────────
            b.comment("Drop-chance multipliers per dimension (stacks with tier multiplier)")
             .push("dimension_multipliers");
            netherDropMultiplier = b.comment("Nether drop chance multiplier (default 1.5 — hostile progression tier)")
                    .defineInRange("nether", 1.5, 0.0, 10.0);
            endDropMultiplier    = b.comment("End drop chance multiplier (default 2.0 — late-game dimension)")
                    .defineInRange("end",    2.0, 0.0, 10.0);
            b.pop();

            // ── Kill attribution ─────────────────────────────────────────────
            b.comment("Lootbox drop rules based on who killed the mob").push("kill_attribution");
            requirePlayerKill = b.comment(
                    "If true, only direct player kills can produce lootboxes.",
                    "Env kills (fire, lava, falling), other mob kills → 0 chance.")
                    .define("require_player_kill", false);
            enableIndirectKillReduction = b.comment(
                    "If true, indirect kills (player's pet, arrow) use indirectKillFraction.")
                    .define("enable_indirect_reduction", true);
            indirectKillFraction = b.comment(
                    "Fraction of normal drop chance kept for indirect player kills.",
                    "0.5 = pet/arrow kills have 50% the normal chance.")
                    .defineInRange("indirect_kill_fraction", 0.5, 0.0, 1.0);
            b.pop();

            // ── Anti-farm ────────────────────────────────────────────────────
            b.comment("Anti-farm: reduce drops when a chunk is being killed repeatedly")
             .push("anti_farm");
            enableAntiFarm = b.comment("Enable chunk-kill-window anti-farm system.")
                    .define("enabled", true);
            antiFarmKillLimit = b.comment(
                    "Max kills in a chunk within the window before the penalty starts.")
                    .defineInRange("chunk_kill_limit",  8, 1, 500);
            antiFarmWindowSeconds = b.comment(
                    "Rolling window size in seconds. Counter resets after this time.")
                    .defineInRange("window_seconds",   60, 5, 3600);
            antiFarmPenaltyPerKill = b.comment(
                    "Per-excess-kill drop chance reduction factor.",
                    "0.10 = each kill above the limit reduces chance by 10%.")
                    .defineInRange("penalty_per_kill", 0.10, 0.0, 1.0);
            antiFarmMinFraction = b.comment(
                    "Floor: chunk farming cannot reduce drop chance below this fraction.",
                    "0.10 = even a heavily farmed chunk keeps 10% of normal chance.")
                    .defineInRange("min_fraction",     0.10, 0.0, 1.0);
            namedMobCooldownSeconds = b.comment(
                    "Seconds before a named mob (CustomName) can drop a lootbox again.",
                    "0 = no cooldown.")
                    .defineInRange("named_mob_cooldown_seconds", 300, 0, 86400);
            b.pop();

            // ── Safety ceiling ───────────────────────────────────────────────
            absoluteMaxDropChance = b.comment(
                    "Hard ceiling: final computed chance is never higher than this,",
                    "regardless of stacked boss/dim/looting bonuses. Safety valve.")
                    .defineInRange("absolute_max_drop_chance", 0.50, 0.0, 1.0);

            b.pop(); // tinkrarityloot
        }
    }
}
