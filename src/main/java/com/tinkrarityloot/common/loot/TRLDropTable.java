package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.material.PartStatType;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import com.tinkrarityloot.common.weapon.WeaponStatTemplate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

/**
 * Three separate weighted pools — one per LootboxCategory.
 *
 * MELEE pool  — every part needed for: sword, cleaver, hand axe, hammer, broad axe
 * RANGED pool — every part needed for: shortbow, longbow, crossbow, javelin, arrow
 * TOOL pool   — every part needed for: pickaxe, hand axe (tool), hammer (tool),
 *               excavator, mattock
 *
 * All TCon 3.11 item IDs confirmed from in-game JEI.
 */
public class TRLDropTable {

    public record Entry(
            Supplier<Item>     item,
            PartStatType       statType,
            WeaponFamily       family,
            WeaponStatTemplate template,
            LootboxCategory    category,
            double             weight) {}

    private static final Map<LootboxCategory, List<Entry>> POOLS  = new EnumMap<>(LootboxCategory.class);
    private static final Map<LootboxCategory, Double>      TOTALS = new EnumMap<>(LootboxCategory.class);
    private static boolean initialized = false;

    private static Supplier<Item> part(String path) {
        return () -> {
            var rl = new net.minecraft.resources.ResourceLocation("tconstruct", path);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == Items.AIR) {
                TinkRarityLoot.LOGGER.warn("[TRL] TCon part not found in registry: {}", rl);
                return Items.AIR;
            }
            return item;
        };
    }

    // ── Initialization ────────────────────────────────────────────────────────

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        for (LootboxCategory cat : LootboxCategory.values()) {
            POOLS.put(cat, new ArrayList<>());
            TOTALS.put(cat, 0.0);
        }

        boolean basic    = TRLConfig.SERVER.enableBasicWeaponParts.get();
        boolean advanced = TRLConfig.SERVER.enableAdvancedWeaponParts.get();

        TinkRarityLoot.LOGGER.info("[TRL] Initialising drop table — basic={} advanced={}", basic, advanced);

        // ── MELEE POOL ────────────────────────────────────────────────────────
        // Sword parts — tool_binding uses non-material system → excluded.
        // small_blade and tool_handle both have real material pools.
        reg(LootboxCategory.MELEE, part("small_blade"),
                PartStatType.HEAD,   WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_BLADE,   35);
        reg(LootboxCategory.MELEE, part("tool_handle"),
                PartStatType.HANDLE, WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_HANDLE,  30);

        // Cleaver (broad_blade + tough_handle)
        reg(LootboxCategory.MELEE, part("broad_blade"),
                PartStatType.HEAD,   WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.HEAVY_BLADE,   15);
        reg(LootboxCategory.MELEE, part("tough_handle"),
                PartStatType.HANDLE, WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.HEAVY_HANDLE,  12);

        if (basic) {
            // Hand axe (small_axe_head + tool_handle + tool_binding — same as sword)
            reg(LootboxCategory.MELEE, part("small_axe_head"),
                    PartStatType.HEAD,   WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_BLADE,  10);
            // Tough binding (swasher, war pick)
            reg(LootboxCategory.MELEE, part("tough_binding"),
                    PartStatType.EXTRA,  WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.CROSSBAR,      8);
        }

        if (advanced) {
            // Hammer — large_plate uses non-material system → excluded.
            reg(LootboxCategory.MELEE, part("hammer_head"),
                    PartStatType.HEAD,   WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.HAMMER_HEAD,   10);
            // Broad axe (broad_axe_head + tough_handle + large_plate)
            reg(LootboxCategory.MELEE, part("broad_axe_head"),
                    PartStatType.HEAD,   WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.BROAD_AXE_HEAD,  8);
        }

        // ── RANGED POOL ───────────────────────────────────────────────────────
        // Shortbow / Longbow — bow_grip uses HANDLE stat type which has real materials.
        // bowstring uses a non-material system → always resolves to tconstruct:unknown → excluded.
        reg(LootboxCategory.RANGED, part("bow_grip"),
                PartStatType.HANDLE, WeaponFamily.RANGED, WeaponStatTemplate.RANGED_GRIP,    30);

        if (basic) {
            // Arrow / Javelin — arrow_head has real materials (metal heads).
            // fletching uses non-material system → always tconstruct:unknown → excluded.
            reg(LootboxCategory.RANGED, part("arrow_head"),
                    PartStatType.HEAD,   WeaponFamily.THROWN, WeaponStatTemplate.THROWN_HEAD,  20);
            reg(LootboxCategory.RANGED, part("tool_handle"),
                    PartStatType.HANDLE, WeaponFamily.RANGED, WeaponStatTemplate.RANGED_GRIP,  15);
        }

        // ── TOOL POOL ─────────────────────────────────────────────────────────
        // Pickaxe parts — tool_binding excluded (non-material system → always unknown).
        reg(LootboxCategory.TOOL, part("pick_head"),
                PartStatType.HEAD,   WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_BLADE,   30);
        reg(LootboxCategory.TOOL, part("tool_handle"),
                PartStatType.HANDLE, WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_HANDLE,  30);

        if (basic) {
            // Hand axe tool (small_axe_head + tool_handle + tool_binding)
            reg(LootboxCategory.TOOL, part("small_axe_head"),
                    PartStatType.HEAD,   WeaponFamily.MELEE_LIGHT, WeaponStatTemplate.LIGHT_BLADE,  12);
            // tough_binding excluded — non-material system → always tconstruct:unknown
        }

        if (advanced) {
            // Hammer/Excavator — large_plate excluded (non-material system → always unknown).
            reg(LootboxCategory.TOOL, part("hammer_head"),
                    PartStatType.HEAD,   WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.HAMMER_HEAD,     9);
            reg(LootboxCategory.TOOL, part("tough_handle"),
                    PartStatType.HANDLE, WeaponFamily.MELEE_HEAVY, WeaponStatTemplate.HEAVY_HANDLE,    8);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void reg(LootboxCategory cat, Supplier<Item> item,
                             PartStatType statType, WeaponFamily family,
                             WeaponStatTemplate template, double weight) {
        POOLS.get(cat).add(new Entry(item, statType, family, template, cat, weight));
        TOTALS.merge(cat, weight, Double::sum);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void register(Supplier<Item> item, PartStatType statType,
                                WeaponFamily family, WeaponStatTemplate template,
                                double weight) {
        // Legacy API — adds to MELEE pool only
        reg(LootboxCategory.MELEE, item, statType, family, template, weight);
    }

    /** Pick a weighted random entry from the given category's pool.
     *  Skips entries whose item is AIR or whose stat type has no valid materials. */
    public static Entry pick(Random random, LootboxCategory category) {
        if (!initialized) init();
        List<Entry> pool = POOLS.get(category);
        double total = TOTALS.get(category);
        if (pool.isEmpty()) return pickAny(random);

        double roll = random.nextDouble() * total;
        double cum  = 0;
        for (Entry e : pool) {
            cum += e.weight();
            if (roll < cum && isEligible(e)) return e;
        }
        // Fallback: first eligible entry
        for (Entry e : pool) if (isEligible(e)) return e;
        return pool.get(0);
    }

    /**
     * An entry is eligible if its item exists in the registry AND its stat type
     * has at least one valid (non-unknown) material registered.
     * Parts like bowstring, fletching, large_plate use non-standard material
     * systems and always resolve to tconstruct:unknown — exclude them.
     */
    private static boolean isEligible(Entry e) {
        if (e.item().get() == Items.AIR) return false;
        // Check material pool — if empty, this part type has no renderable materials
        try {
            java.util.List<slimeknights.tconstruct.library.materials.definition.MaterialVariantId> pool =
                com.tinkrarityloot.common.material.MaterialSelector.debugPool(e.statType());
            // Also exclude if the only available material is tconstruct:unknown
            if (pool.isEmpty()) return false;
            boolean hasReal = pool.stream().anyMatch(m ->
                !m.getId().toString().contains("unknown") &&
                !m.getId().toString().contains("hidden"));
            return hasReal;
        } catch (Exception ex) {
            return true; // if we can't check, allow it
        }
    }

    /** Legacy pick — uses MELEE pool (for backward compat). */
    public static Entry pick(Random random) {
        return pick(random, LootboxCategory.MELEE);
    }

    private static Entry pickAny(Random random) {
        for (LootboxCategory cat : LootboxCategory.values()) {
            List<Entry> pool = POOLS.get(cat);
            if (!pool.isEmpty()) return pick(random, cat);
        }
        return POOLS.get(LootboxCategory.MELEE).get(0);
    }

    public static List<Entry> getEntries(LootboxCategory category) {
        if (!initialized) init();
        return Collections.unmodifiableList(POOLS.getOrDefault(category, List.of()));
    }

    public static List<Entry> getEntries() {
        return getEntries(LootboxCategory.MELEE);
    }
}
