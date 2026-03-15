package com.tinkrarityloot.common.material;

import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

/**
 * Maps every supported Tinkers part type to its required {@link MaterialStatsId}.
 *
 * ── TCon 3.8 stat type names ──────────────────────────────────────────────
 *
 *   HEAD   – HeadMaterialStats    → damage, attack speed, durability
 *   HANDLE – HandleMaterialStats  → durability/speed multipliers
 *   EXTRA  – guards / bindings    → durability bonus
 *   LIMB   – LimbMaterialStats    → bow/crossbow draw parts
 *
 * EXTRA note: in TCon 3.6 this was ExtraMaterialStats; in 3.8 it was renamed
 * to BindingMaterialStats in some builds.  We resolve the correct ID at class
 * load using the static initialiser below so the code compiles against either.
 */
public enum PartStatType {

    HEAD   (HeadMaterialStats.ID),
    HANDLE (HandleMaterialStats.ID),
    EXTRA  (resolveExtraId()),
    LIMB   (LimbMaterialStats.ID);

    public final MaterialStatsId statsId;

    PartStatType(MaterialStatsId statsId) {
        this.statsId = statsId;
    }

    /**
     * Resolves the "extra" (guard/binding) stat ID, which was renamed between
     * TCon 3.6 and 3.8.  Tries ExtraMaterialStats first; falls back to
     * constructing the ID directly from the known string if the class is absent.
     */
    private static MaterialStatsId resolveExtraId() {
        try {
            // TCon 3.6 / early 3.8
            Class<?> cls = Class.forName(
                    "slimeknights.tconstruct.tools.stats.ExtraMaterialStats");
            return (MaterialStatsId) cls.getField("ID").get(null);
        } catch (Exception e1) {
            try {
                // TCon 3.8 renamed class
                Class<?> cls = Class.forName(
                        "slimeknights.tconstruct.tools.stats.BindingMaterialStats");
                return (MaterialStatsId) cls.getField("ID").get(null);
            } catch (Exception e2) {
                // Absolute fallback — MaterialStatsId extends ResourceLocation,
                // so we can construct it directly with (namespace, path).
                return new MaterialStatsId("tconstruct", "extra");
            }
        }
    }
}
