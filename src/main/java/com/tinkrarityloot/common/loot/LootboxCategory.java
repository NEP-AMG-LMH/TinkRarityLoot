package com.tinkrarityloot.common.loot;

/**
 * The three lootbox categories — each drops a distinct sealed box containing
 * components for one weapon/tool archetype.
 *
 *   MELEE  — sword, cleaver, axe, hammer parts
 *   RANGED — bow, crossbow, javelin, arrow parts
 *   TOOL   — pickaxe, mattock, excavator, hand-axe parts
 *
 * Mobs drop a category based on their classification:
 *   Skeletons/ranged → RANGED
 *   Mining/cave mobs  → TOOL
 *   All others        → MELEE (default)
 */
public enum LootboxCategory {
    MELEE  ("melee",  "Melee Weapon"),
    RANGED ("ranged", "Ranged Weapon"),
    TOOL   ("tool",   "Tool");

    public final String key;
    public final String displayName;

    LootboxCategory(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public static LootboxCategory fromKey(String key) {
        for (LootboxCategory c : values()) if (c.key.equals(key)) return c;
        return MELEE;
    }
}
