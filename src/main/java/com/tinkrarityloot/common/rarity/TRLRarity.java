package com.tinkrarityloot.common.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Random;

/**
 * Canonical rarity tiers owned entirely by TinkRarityLoot.
 *
 * This enum is the single source of truth for:
 *   • Roll weights           (used by ConfiguredRarityRoller)
 *   • Stat multipliers       (used by StatRoller)
 *   • All visual data        (used by TooltipRenderer and GlintRenderer)
 *   • NBT serialisation key  (used everywhere persistence is needed)
 *
 * Mine and Slash and Apotheosis receive translations of this rarity ONLY at
 * tool-assembly time. They never drive TRL's own rendering.
 *
 * Glint colours (packed ARGB, alpha = FF):
 *   Common    – no glint
 *   Uncommon  – soft green  0xFF22BB22
 *   Rare      – cyan        0xFF00CCDD
 *   Epic      – purple      0xFF9900FF
 *   Unique    – amber       0xFFFF8800
 *   Legendary – gold        0xFFFFD700
 *   Mythic    – crimson     0xFFFF1133
 */
public enum TRLRarity {

    //           display       weight  mul    nameColour              bold   glintARGB
    COMMON   ("Common",    55.0, 1.00, ChatFormatting.WHITE,       false, 0x00000000),
    UNCOMMON ("Uncommon",  25.0, 1.25, ChatFormatting.GREEN,       false, 0xFF22BB22),
    RARE     ("Rare",      10.0, 1.60, ChatFormatting.AQUA,        false, 0xFF00CCDD),
    EPIC     ("Epic",       3.5, 2.20, ChatFormatting.LIGHT_PURPLE, true, 0xFF9900FF),
    UNIQUE   ("Unique",     3.5, 2.50, ChatFormatting.GOLD,         true, 0xFFFF8800),
    LEGENDARY("Legendary",  2.0, 3.50, ChatFormatting.YELLOW,       true, 0xFFFFD700),
    MYTHIC   ("Mythic",     1.0, 5.00, ChatFormatting.RED,          true, 0xFFFF1133);

    public final String displayName;
    public final double weight;
    public final double multiplier;
    /** Primary name colour, used for the rarity label and item name tint. */
    public final ChatFormatting colour;
    /** Whether the rarity name should be rendered bold in tooltips. */
    public final boolean bold;
    /**
     * Packed ARGB glint colour used by GlintRenderer.
     * Alpha = 0x00 means no glint (COMMON only).
     */
    public final int glintARGB;

    TRLRarity(String displayName, double weight, double multiplier,
              ChatFormatting colour, boolean bold, int glintARGB) {
        this.displayName = displayName;
        this.weight      = weight;
        this.multiplier  = multiplier;
        this.colour      = colour;
        this.bold        = bold;
        this.glintARGB   = glintARGB;
    }

    // ── Derived display helpers ───────────────────────────────────────────────

    /** True if this rarity should have an enchantment-style glint. */
    public boolean hasGlint() {
        return (glintARGB >>> 24) != 0; // alpha byte non-zero
    }

    /**
     * The rarity label component — used as the very first tooltip line after
     * the item name and as the assembled-tool gear-tier badge.
     *
     * Format:  ★ Legendary   (bold for EPIC+, plain for lower tiers)
     */
    public Component nameComponent() {
        Style style = Style.EMPTY.withColor(colour).withBold(bold);
        String star  = ordinal() >= EPIC.ordinal() ? "✦ " : "◆ ";
        return Component.literal(star + displayName).setStyle(style);
    }

    /**
     * A compact badge suitable for the assembled-tool tooltip footer.
     * Format:  [Legendary]
     */
    public Component badgeComponent() {
        Style style = Style.EMPTY.withColor(colour).withBold(bold);
        return Component.literal("[" + displayName + "]").setStyle(style);
    }

    /**
     * The item name prefix pushed onto assembled tools to visually identify tier.
     * Format:  Legendary
     * (caller concatenates the actual tool name after)
     */
    public Component toolNamePrefix() {
        return Component.literal(displayName + " ").setStyle(
                Style.EMPTY.withColor(colour).withBold(bold));
    }

    // ── Roll table ────────────────────────────────────────────────────────────

    private static final double TOTAL_WEIGHT;
    static {
        double s = 0; for (TRLRarity r : values()) s += r.weight;
        TOTAL_WEIGHT = s;
    }

    /**
     * Roll using the hard-coded weights (before config loads).
     * Game-time rolls should use {@link com.tinkrarityloot.common.rarity.ConfiguredRarityRoller}.
     */
    public static TRLRarity roll(Random random) {
        double roll = random.nextDouble() * TOTAL_WEIGHT, cum = 0;
        for (TRLRarity r : values()) { cum += r.weight; if (roll < cum) return r; }
        return COMMON;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /** Lower-case name stored in NBT and packets. */
    public String getSerialKey() { return name().toLowerCase(); }

    public static TRLRarity fromKey(String key) {
        if (key == null) return COMMON;
        for (TRLRarity r : values())
            if (r.getSerialKey().equalsIgnoreCase(key)) return r;
        return COMMON;
    }

    // ── Legacy compat ─────────────────────────────────────────────────────────

    /** @deprecated Use {@link #nameComponent()} */
    @Deprecated
    public Component toComponent() { return nameComponent(); }
}
