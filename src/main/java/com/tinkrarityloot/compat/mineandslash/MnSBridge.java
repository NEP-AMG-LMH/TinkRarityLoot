package com.tinkrarityloot.compat.mineandslash;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.rpg.TRLAffix;
import com.tinkrarityloot.common.rpg.TRLRequirements;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Writes TRL RPG data into Mine and Slash 6.3.14's native NBT structure.
 *
 * ── Confirmed M&S NBT structure (from /data get entity @p) ───────────────────
 *
 * Gear items carry a JSON string in tag key "mmorpg_gear":
 * {
 *   "baseStats": {"p": 92},
 *   "imp":       {"p": 52, "imp": "cleric_staff"},
 *   "affixes":   {
 *     "pre": [{"p": 8, "id": "crit_prefix", "rar": "common", "ty": "prefix"}],
 *     "suf": [],
 *     "cor": []
 *   },
 *   "sockets":   {"so": [], "sl": 0, "rw": "", "rp": 0},
 *   "rar":       "common",
 *   "lvl":       1,
 *   "gtype":     "staff"
 * }
 *
 * Player level:  mmorpg:entity_data → level (int)
 * Player stats:  mmorpg:player_data → stats → {"map":{"dexterity":1,"strength":1}}
 *
 * ── M&S stat IDs (confirmed from player data) ─────────────────────────────────
 *   strength, dexterity, critical_hit, critical_damage, mana_regen,
 *   health, health_regen, dodge, armor, energy_regen, weapon_damage, etc.
 *
 * ── Level requirement ─────────────────────────────────────────────────────────
 * M&S enforces gear level via the "lvl" field in mmorpg_gear. Items with
 * lvl > player level are blocked from use. We write the lootbox mob level
 * as the gear level so M&S naturally enforces it.
 *
 * ── Stat requirements ─────────────────────────────────────────────────────────
 * M&S 6.3.14 does not appear to have explicit "str_req" gate (checked from
 * NBT and changelog). Requirements are soft-enforced via gear level.
 * We write them to TRL keys (trl_req_*) for our own tooltip display.
 * If M&S adds stat gates in a future version, the keys are already there.
 *
 * ── Affix mapping ─────────────────────────────────────────────────────────────
 * TRL affixes are written into mmorpg_gear.affixes so M&S's HUD displays them.
 * We use M&S's known affix IDs where they exist, TRL IDs otherwise.
 */
public final class MnSBridge {

    private static final Gson GSON = new Gson();

    private static boolean masLoaded = false;

    /** M&S player level reader — for requirement checking in ToolAssemblyHandler */
    private static Method getMasLevelMethod = null;
    private static Object masEntityDataCap  = null;

    private MnSBridge() {}

    public static void init() {
        try {
            Class<?> caps = Class.forName("com.robertx22.mine_and_slash.mmorpg.Ref");
            masLoaded = true;
            TinkRarityLoot.LOGGER.info("[TRL] MnSBridge: M&S 6.x detected");
        } catch (ClassNotFoundException e) {
            // Try older package
            try {
                Class.forName("com.robertx22.age_of_exile.mmorpg.Ref");
                masLoaded = true;
                TinkRarityLoot.LOGGER.info("[TRL] MnSBridge: M&S (age_of_exile pkg) detected");
            } catch (ClassNotFoundException e2) {
                masLoaded = false;
                TinkRarityLoot.LOGGER.debug("[TRL] MnSBridge: M&S not found — NBT fallback only");
            }
        }
    }

    /**
     * Write all TRL RPG data to an assembled tool stack.
     *
     * @param stack    The assembled TCon tool
     * @param reqs     Level + stat requirements
     * @param affixes  Rolled prefix/suffix affixes
     * @param rarity   TRL rarity (mapped to M&S rarity string)
     * @param family   Weapon family (drives M&S gtype)
     * @param mobLevel Mob level from the lootbox
     */
    public static void applyToTool(ItemStack stack, TRLRequirements reqs,
                                    List<TRLAffix> affixes, TRLRarity rarity,
                                    WeaponFamily family, int mobLevel) {
        if (stack.isEmpty()) return;
        CompoundTag root = stack.getOrCreateTag();

        // ── 1. TRL requirement keys (for our tooltip + future M&S gate compat) ──
        reqs.writeToTag(root);
        // Also write with M&S-style names in case M&S reads them directly
        root.putInt("level_req", reqs.level());

        // ── 2. TRL affix list (for our tooltip renderer) ──────────────────────
        var affixList = new net.minecraft.nbt.ListTag();
        for (TRLAffix affix : affixes) affixList.add(affix.toNbt());
        root.put(TRLAffix.K_LIST, affixList);

        // ── 3. Write mmorpg_gear JSON (M&S reads this for HUD + level gate) ───
        root.putString("mmorpg_gear", buildMasGearJson(
                affixes, rarity, family, mobLevel));

        // ── 4. M&S potential (controls how many currency modifies are allowed) ─
        // Higher rarity = more potential
        int potential = switch (rarity) {
            case COMMON    -> 3;
            case UNCOMMON  -> 4;
            case RARE      -> 5;
            case EPIC      -> 6;
            case UNIQUE    -> 7;
            case LEGENDARY -> 8;
            case MYTHIC    -> 10;
        };
        root.putString("mmorpg_potential", "{\"potential\":" + potential + "}");

        TinkRarityLoot.LOGGER.debug(
                "[TRL] MnSBridge.applyToTool: lvl={} rar={} affixes={} potential={}",
                mobLevel, rarity.displayName, affixes.size(), potential);
    }

    // ── mmorpg_gear JSON builder ──────────────────────────────────────────────

    private static String buildMasGearJson(List<TRLAffix> affixes, TRLRarity rarity,
                                            WeaponFamily family, int mobLevel) {
        JsonObject gear = new JsonObject();

        // Base stats — points derived from mob level
        JsonObject baseStats = new JsonObject();
        baseStats.addProperty("p", mobLevel * 5);
        gear.add("baseStats", baseStats);

        // Implicit stat (weapon type flavour — not mechanically significant)
        JsonObject imp = new JsonObject();
        imp.addProperty("p", mobLevel * 3);
        imp.addProperty("imp", masImplicit(family));
        gear.add("imp", imp);

        // Affixes
        JsonObject affixObj = new JsonObject();
        JsonArray pre = new JsonArray();
        JsonArray suf = new JsonArray();
        JsonArray cor = new JsonArray();

        for (TRLAffix affix : affixes) {
            JsonObject a = new JsonObject();
            a.addProperty("p",   (int)(affix.value() * 10)); // M&S points scale
            a.addProperty("id",  toMasAffixId(affix.type()));
            a.addProperty("rar", toMasRarity(rarity));
            a.addProperty("ty",  affix.isPrefix() ? "prefix" : "suffix");
            if (affix.isPrefix()) pre.add(a); else suf.add(a);
        }

        affixObj.add("pre", pre);
        affixObj.add("suf", suf);
        affixObj.add("cor", cor);
        gear.add("affixes", affixObj);

        // Sockets
        JsonObject sockets = new JsonObject();
        sockets.add("so", new JsonArray());
        sockets.addProperty("sl", rarity.ordinal() >= TRLRarity.RARE.ordinal() ? 1 : 0);
        sockets.addProperty("rw", "");
        sockets.addProperty("rp", 0);
        gear.add("sockets", sockets);

        // Core fields
        gear.addProperty("rar",   toMasRarity(rarity));
        gear.addProperty("lvl",   mobLevel);     // ← M&S enforces level gate here
        gear.addProperty("gtype", masGearType(family));

        return GSON.toJson(gear);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /** Map TRL rarity to M&S rarity string (confirmed from player data). */
    public static String toMasRarity(TRLRarity rarity) {
        return switch (rarity) {
            case COMMON    -> "common";
            case UNCOMMON  -> "uncommon";
            case RARE      -> "rare";
            case EPIC      -> "epic";
            case UNIQUE    -> "unique";
            case LEGENDARY, MYTHIC -> "legendary";
        };
    }

    /** Map TRL affix type to M&S affix ID. */
    private static String toMasAffixId(String trlType) {
        return switch (trlType) {
            case TRLAffix.PHYS_DMG_PCT  -> "physical_prefix";
            case TRLAffix.FIRE_DMG      -> "fire_prefix";
            case TRLAffix.ICE_DMG       -> "ice_prefix";
            case TRLAffix.LIGHTNING_DMG -> "lightning_prefix";
            case TRLAffix.POISON_DMG    -> "poison_prefix";
            case TRLAffix.CRIT_CHANCE   -> "crit_prefix";
            case TRLAffix.CRIT_DMG      -> "crit_dmg_suffix";
            case TRLAffix.LIFE_STEAL    -> "life_steal_suffix";
            case TRLAffix.ATK_SPEED_PCT -> "attack_speed_suffix";
            case TRLAffix.THORNS        -> "thorns_suffix";
            case TRLAffix.PROJ_SPEED    -> "projectile_speed_suffix";
            case TRLAffix.PIERCE_CHANCE -> "pierce_suffix";
            case TRLAffix.MAX_HP_PCT    -> "health_suffix";
            case TRLAffix.MANA_REGEN    -> "mana_regen_suffix";
            case TRLAffix.ENERGY_REGEN  -> "energy_regen_suffix";
            case TRLAffix.EXP_BONUS     -> "exp_suffix";
            default -> trlType + "_suffix";
        };
    }

    private static String masImplicit(WeaponFamily family) {
        return switch (family) {
            case MELEE_HEAVY -> "two_handed_sword";
            case MELEE_LIGHT -> "one_handed_sword";
            case RANGED      -> "bow";
            case THROWN      -> "javelin";
        };
    }

    private static String masGearType(WeaponFamily family) {
        return switch (family) {
            case MELEE_HEAVY -> "two_hand";
            case MELEE_LIGHT -> "one_hand";
            case RANGED      -> "bow";
            case THROWN      -> "thrown";
        };
    }

    /**
     * Read the M&S player level from entity capability data.
     * Used to display "Requires Level X" correctly relative to current level.
     * Returns -1 if M&S is not loaded or capability unavailable.
     */
    public static int getPlayerLevel(Player player) {
        if (!masLoaded) return -1;
        try {
            // M&S stores level in ForgeCaps: "mmorpg:entity_data" → level (int)
            // We read it from the player's persistent data compound
            CompoundTag forgeCaps = player.getPersistentData();
            // Try direct NBT path first (fastest)
            if (forgeCaps.contains("mmorpg:entity_data")) {
                return forgeCaps.getCompound("mmorpg:entity_data").getInt("level");
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isMasLoaded() { return masLoaded; }
}
