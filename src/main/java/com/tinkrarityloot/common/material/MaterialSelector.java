package com.tinkrarityloot.common.material;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.config.TRLConfig;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queries the live TCon {@link MaterialRegistry} to build per-stat-type pools
 * of valid materials, then picks one at random.
 *
 * "Valid" means the material:
 *   1. Is registered and not hidden (not the "unknown" sentinel).
 *   2. Provides the required {@link MaterialStatsId} (so it has textures + stats
 *      for the target part slot).
 *   3. Is not in the configured exclusion list.
 *
 * Pools are built lazily and cached per stat type; the cache is cleared on
 * world load so data-pack reloads are respected.
 *
 * Thread safety: pools are built once on the server thread (first drop after
 * world load) and then only read — safe for concurrent reads.
 */
public final class MaterialSelector {

    private MaterialSelector() {}

    /**
     * Materials we never want to pick regardless of stat support.
     * "tconstruct:unknown" is the internal fallback and has no textures.
     * Add modpack-specific exclusions here or via config (future work).
     */
    /**
     * Safe fallback MaterialVariantId used when no valid materials are found.
     * MaterialVariantId.UNKNOWN was removed in TCon 3.8 and the static factory
     * method names changed between versions.  We resolve it reflectively so
     * the code compiles against any 3.x jar.
     */
    private static final MaterialVariantId FALLBACK_MATERIAL = buildFallbackMaterial();

    private static MaterialVariantId buildFallbackMaterial() {
        var rl = new ResourceLocation("tconstruct", "unknown");
        // Try known static factory signatures across TCon 3.x versions.
        // IMPORTANT: never throw here — a failed fallback is handled by callers.
        // TCon 3.6: create(ResourceLocation)
        try {
            var m = MaterialVariantId.class.getMethod("create", ResourceLocation.class);
            return (MaterialVariantId) m.invoke(null, rl);
        } catch (Exception ignored) {}
        // TCon 3.8: of(ResourceLocation)
        try {
            var m = MaterialVariantId.class.getMethod("of", ResourceLocation.class);
            return (MaterialVariantId) m.invoke(null, rl);
        } catch (Exception ignored) {}
        // TCon 3.11: of(String) — accepts "namespace:path" string
        try {
            var m = MaterialVariantId.class.getMethod("of", String.class);
            return (MaterialVariantId) m.invoke(null, "tconstruct:unknown");
        } catch (Exception ignored) {}
        // TCon 3.11: tryParse or parse
        try {
            var m = MaterialVariantId.class.getMethod("tryParse", String.class);
            return (MaterialVariantId) m.invoke(null, "tconstruct:unknown");
        } catch (Exception ignored) {}
        // Constructor fallback: new MaterialVariantId(ResourceLocation)
        try {
            var c = MaterialVariantId.class.getConstructor(ResourceLocation.class);
            return (MaterialVariantId) c.newInstance(rl);
        } catch (Exception ignored) {}
        // All attempts failed — log a warning but do NOT throw.
        // Callers fall back to skipping the material entirely.
        TinkRarityLoot.LOGGER.warn("[TRL] Could not construct MaterialVariantId fallback — " +
                "material selection will use pool fallback instead.");
        return null;
    }

    private static final Set<ResourceLocation> EXCLUDED = Set.of(
            new ResourceLocation("tconstruct", "unknown"),
            new ResourceLocation("tconstruct", "wood")    // wood parts look odd on ARPG drops
    );

    /** Cache: stat type → immutable list of valid MaterialVariantIds. */
    private static final Map<PartStatType, List<MaterialVariantId>> POOL_CACHE
            = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Pick a random material valid for {@code statType}.
     *
     * @param statType The stat category the part requires.
     * @param random   RNG instance (seeded per-drop for determinism).
     * @return A {@link MaterialVariantId} guaranteed to be renderable and stat-valid,
     *         or {@link MaterialVariantId#UNKNOWN} if no valid materials are registered.
     */
    public static MaterialVariantId pickRandom(PartStatType statType, Random random) {
        List<MaterialVariantId> pool = getPool(statType);
        if (pool.isEmpty()) {
            TinkRarityLoot.LOGGER.warn(
                    "[TRL] No valid materials found for stat type {}. Using UNKNOWN.", statType);
            return FALLBACK_MATERIAL;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Pick a material valid for {@code statType}, biased by rarity tier.
     * For RARE+, draws from a "premium" sub-pool of tier-2+ materials.
     */
    public static MaterialVariantId pickForRarity(
            PartStatType statType,
            com.tinkrarityloot.common.rarity.TRLRarity rarity,
            Random random) {
        if (rarity.ordinal() >= com.tinkrarityloot.common.rarity.TRLRarity.RARE.ordinal()) {
            List<MaterialVariantId> premium = getPremiumPool(statType);
            if (!premium.isEmpty()) return premium.get(random.nextInt(premium.size()));
        }
        return pickRandom(statType, random);
    }

    /**
     * Pick a material valid for {@code statType}, gated by both rarity and mob level.
     *
     * Mob level gates prevent high-tier materials from appearing in early-game
     * drops, preserving the feeling of progression:
     *
     *   level < materialLevelGate1 → tier-1/2 materials only
     *   level < materialLevelGate2 → tier-1/2/3 materials only
     *   level ≥ materialLevelGate2 → all tiers eligible (rarity bias still applies)
     *
     * @param statType  The TCon part stat slot.
     * @param rarity    Resolved rarity for this drop (drives premium-pool bias).
     * @param mobLevel  Resolved mob level (from MobLevelResolver).
     * @param random    Per-drop seeded RNG.
     */
    public static MaterialVariantId pickForLevel(
            PartStatType statType,
            com.tinkrarityloot.common.rarity.TRLRarity rarity,
            int mobLevel,
            Random random) {

        int gate1 = TRLConfig.SERVER.materialLevelGate1.get();
        int gate2 = TRLConfig.SERVER.materialLevelGate2.get();

        List<MaterialVariantId> pool;

        if (mobLevel < gate1) {
            // Tier 1–2 only
            pool = getTieredPool(statType, 2);
        } else if (mobLevel < gate2) {
            // Tier 1–3 only
            pool = getTieredPool(statType, 3);
        } else {
            // All tiers available; apply rarity premium bias
            if (rarity.ordinal() >= com.tinkrarityloot.common.rarity.TRLRarity.RARE.ordinal()) {
                pool = getPremiumPool(statType);
            } else {
                pool = getPool(statType);
            }
        }

        if (pool.isEmpty()) pool = getPool(statType);  // ultimate fallback
        if (pool.isEmpty()) return FALLBACK_MATERIAL;
        return pool.get(random.nextInt(pool.size()));
    }

    /** Evict all cached pools (call on world load / data-pack reload). */
    /** Reflection-safe factory for MaterialVariantId — handles TCon 3.6/3.8 rename. */
    private static MaterialVariantId toVariantId(ResourceLocation id) {
        // TCon 3.6
        try {
            var m = MaterialVariantId.class.getMethod("create", ResourceLocation.class);
            return (MaterialVariantId) m.invoke(null, id);
        } catch (Exception ignored) {}
        // TCon 3.8
        try {
            var m = MaterialVariantId.class.getMethod("of", ResourceLocation.class);
            return (MaterialVariantId) m.invoke(null, id);
        } catch (Exception ignored) {}
        // TCon 3.11: of(String)
        try {
            var m = MaterialVariantId.class.getMethod("of", String.class);
            return (MaterialVariantId) m.invoke(null, id.toString());
        } catch (Exception ignored) {}
        // TCon 3.11: tryParse(String)
        try {
            var m = MaterialVariantId.class.getMethod("tryParse", String.class);
            return (MaterialVariantId) m.invoke(null, id.toString());
        } catch (Exception ignored) {}
        // Constructor fallback
        try {
            var c = MaterialVariantId.class.getConstructor(ResourceLocation.class);
            return (MaterialVariantId) c.newInstance(id);
        } catch (Exception e) {
            TinkRarityLoot.LOGGER.error("[TRL] Cannot create MaterialVariantId for {}: {}", id, e.getMessage());
            return null;
        }
    }

    public static void invalidateCache() {
        POOL_CACHE.clear();
        PREMIUM_CACHE.clear();
        TIERED_CACHE.clear();
        TinkRarityLoot.LOGGER.debug("[TRL] Material pool cache cleared.");
    }

    /**
     * Returns the full material pool for a stat type (builds it if needed).
     * Safe to call from commands — returns empty list if the registry isn't ready.
     */
    public static List<MaterialVariantId> debugPool(PartStatType statType) {
        try {
            return getPool(statType);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Pool building ─────────────────────────────────────────────────────────

    private static List<MaterialVariantId> getPool(PartStatType statType) {
        return POOL_CACHE.computeIfAbsent(statType, MaterialSelector::buildPool);
    }

    /**
     * Build the full valid-material list for a stat type by querying the live
     * TCon MaterialRegistry.
     *
     * The registry is only fully populated after data packs load (server-side),
     * so this must never be called before the world is ready.
     */
    private static List<MaterialVariantId> buildPool(PartStatType statType) {
        MaterialStatsId required = statType.statsId;
        List<MaterialVariantId> pool = new ArrayList<>();

        try {
            for (IMaterial material : MaterialRegistry.getInstance().getVisibleMaterials()) {
                ResourceLocation id = material.getIdentifier().getId();

                // Skip exclusions
                if (EXCLUDED.contains(id)) continue;

                // Skip hidden / internal materials (TCon marks these with tier 0)
                if (material.getTier() < 0) continue;

                // Check that this material actually provides the required stat type
                if (!MaterialRegistry.getInstance()
                        .getMaterialStats(material.getIdentifier(), required)
                        .isPresent()) continue;

                // All variants of this material are valid; for most materials there
                // is only the default variant. We use the default variant ID.
                MaterialVariantId varId = toVariantId(id);
                if (varId != null) pool.add(varId);
            }
        } catch (Exception e) {
            TinkRarityLoot.LOGGER.error(
                    "[TRL] Failed to build material pool for {}: {}", statType, e.getMessage());
        }

        TinkRarityLoot.LOGGER.debug("[TRL] Built material pool for {}: {} materials found.",
                statType, pool.size());

        // Shuffle for unpredictable ordering (the .get(random.nextInt(size)) call
        // produces uniform distribution regardless, but shuffling ensures no index bias)
        Collections.shuffle(pool);
        return Collections.unmodifiableList(pool);
    }

    /** Cache: (statType, maxTier) → filtered pool.  Key = statType.ordinal() * 10 + maxTier. */
    private static final Map<Integer, List<MaterialVariantId>> TIERED_CACHE
            = new ConcurrentHashMap<>();

    private static List<MaterialVariantId> getTieredPool(PartStatType statType, int maxTier) {
        int key = statType.ordinal() * 10 + maxTier;
        return TIERED_CACHE.computeIfAbsent(key, k -> buildTieredPool(statType, maxTier));
    }

    private static List<MaterialVariantId> buildTieredPool(PartStatType statType, int maxTier) {
        List<MaterialVariantId> base = getPool(statType);
        List<MaterialVariantId> filtered = new ArrayList<>();
        for (MaterialVariantId varId : base) {
            try {
                IMaterial mat = MaterialRegistry.getInstance().getMaterial(varId.getId());
                if (mat != null && mat.getTier() <= maxTier) filtered.add(varId);
            } catch (Exception ignored) {}
        }
        if (filtered.isEmpty()) return base;  // no materials of that tier → use all
        TinkRarityLoot.LOGGER.debug("[TRL] Tiered pool ({}≤t{}): {} materials.",
                statType, maxTier, filtered.size());
        return Collections.unmodifiableList(filtered);
    }

    /**
     * "Premium" pool: tier-2+ materials for RARE+ drops.
     * Used for RARE+ drops to give them a sense of quality material origin.
     */
    private static final Map<PartStatType, List<MaterialVariantId>> PREMIUM_CACHE
            = new ConcurrentHashMap<>();

    private static List<MaterialVariantId> getPremiumPool(PartStatType statType) {
        return PREMIUM_CACHE.computeIfAbsent(statType, MaterialSelector::buildPremiumPool);
    }

    private static List<MaterialVariantId> buildPremiumPool(PartStatType statType) {
        int durThreshold = switch (statType) {
            case HEAD   -> 350;
            case HANDLE -> 150;
            case EXTRA  -> 100;
            case LIMB   -> 200;
        };

        List<MaterialVariantId> base = getPool(statType);
        List<MaterialVariantId> premium = new ArrayList<>();

        for (MaterialVariantId varId : base) {
            try {
                var stats = MaterialRegistry.getInstance()
                        .getMaterialStats(varId.getId(), statType.statsId);

                stats.ifPresent(ms -> {
                    // HeadMaterialStats exposes getDurability()
                    if (ms instanceof slimeknights.tconstruct.tools.stats.HeadMaterialStats head) {
                        if (head.durability() >= durThreshold) premium.add(varId);
                    } else if (ms instanceof slimeknights.tconstruct.tools.stats.HandleMaterialStats handle) {
                        // Handle durability is a multiplier; use tier as proxy
                        IMaterial mat = MaterialRegistry.getInstance()
                                .getMaterial(varId.getId());
                        if (mat != null && mat.getTier() >= 2) premium.add(varId);
                    } else {
                        // For EXTRA / LIMB, any tier-2+ material qualifies
                        IMaterial mat = MaterialRegistry.getInstance()
                                .getMaterial(varId.getId());
                        if (mat != null && mat.getTier() >= 2) premium.add(varId);
                    }
                });
            } catch (Exception ignored) {}
        }

        if (premium.isEmpty()) return base; // fallback: use full pool
        TinkRarityLoot.LOGGER.debug("[TRL] Premium pool for {}: {} materials.", statType, premium.size());
        return Collections.unmodifiableList(premium);
    }
}
