package com.tinkrarityloot.common.loot;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.capability.ITRLPartStats;
import com.tinkrarityloot.common.material.MaterialSelector;
import com.tinkrarityloot.common.material.PartMaterialApplicator;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.stat.WeaponStatBlock;
import com.tinkrarityloot.common.weapon.WeaponStatRoller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * tinkrarityloot:lootbox
 *
 * A sealed box that stores only a mob/region level and a rarity tier.  The
 * actual Tinkers part is generated at open-time (right-click), keeping NBT
 * minimal and allowing same-rarity + same-level boxes to stack.
 *
 * ── NBT schema ────────────────────────────────────────────────────────────
 *
 *   trl_rarity  (String) — serialised TRLRarity key, e.g. "epic"
 *   trl_level   (int)    — resolved mob / region level at time of drop
 *   trl_source  (String) — "kill" | "chest" | "boss"  (tuning metadata)
 *
 * ── Stacking ──────────────────────────────────────────────────────────────
 *
 *  Vanilla stacks items with IDENTICAL NBT tags.  Since the only meaningful
 *  NBT on a fresh lootbox is trl_rarity + trl_level (+trl_source), two boxes
 *  of the same rarity and level share identical tags and stack automatically
 *  up to {@link #MAX_STACK_SIZE}.  No custom stack-merging logic required.
 *
 * ── Open pipeline ─────────────────────────────────────────────────────────
 *
 *  Right-click (server side):
 *   1. Read level + rarity from NBT
 *   2. Pick a TRLDropTable entry (weighted random)
 *   3. Roll WeaponStatBlock via WeaponStatRoller.rollWithRarity
 *   4. Pick material via MaterialSelector.pickForLevel (level-gated)
 *   5. Build plain stack → stamp NBT → attach capability
 *   6. Apply material (PartMaterialApplicator)
 *   7. Re-attach capability on materialised stack
 *   8. Give part to player (drop at feet if inventory full)
 *   9. Play sound, shrink lootbox count by 1
 *
 * ── Visuals ───────────────────────────────────────────────────────────────
 *
 *  • isFoil()         → true for UNCOMMON+; drives vanilla enchantment shimmer
 *  • Tooltip border   → coloured by rarity via TRLGlintRenderer (reads trl_rarity)
 *  • Item name        → rarity-tinted in the same colour as the tier
 *  • Tooltip lines    → rarity badge · level · preview parts · open hint
 */
public class ComponentLootboxItem extends Item {

    // ── NBT keys ──────────────────────────────────────────────────────────────

    /** Reuses the existing global rarity key so TRLUtil.rarityOf() works automatically. */
    public static final String K_RARITY   = WeaponStatBlock.K_RARITY;   // "trl_rarity"
    public static final String K_LEVEL    = "trl_level";
    public static final String K_SOURCE   = "trl_source";
    public static final String K_CATEGORY = "trl_category";

    public static final String SOURCE_KILL  = "kill";
    public static final String SOURCE_CHEST = "chest";
    public static final String SOURCE_BOSS  = "boss";

    private static final int MAX_STACK_SIZE = 16;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ComponentLootboxItem() {
        super(new Item.Properties().stacksTo(MAX_STACK_SIZE));
    }

    // ── NBT factory + accessors ───────────────────────────────────────────────

    /**
     * Create a ready-to-drop lootbox ItemStack.
     *
     * @param mobLevel  The resolved mob or region level (clamped externally).
     * @param rarity    The rolled or inherited rarity.
     * @param source    One of {@link #SOURCE_KILL}, {@link #SOURCE_CHEST}, {@link #SOURCE_BOSS}.
     */
    public static ItemStack create(int mobLevel, TRLRarity rarity, String source) {
        return create(mobLevel, rarity, source, LootboxCategory.MELEE);
    }

    public static ItemStack create(int mobLevel, TRLRarity rarity, String source,
                                   LootboxCategory category) {
        net.minecraft.world.item.Item item = switch (category) {
            case MELEE  -> com.tinkrarityloot.common.registry.TRLItems.LOOTBOX_MELEE.get();
            case RANGED -> com.tinkrarityloot.common.registry.TRLItems.LOOTBOX_RANGED.get();
            case TOOL   -> com.tinkrarityloot.common.registry.TRLItems.LOOTBOX_TOOL.get();
        };
        ItemStack stack = new ItemStack(item);
        var tag = stack.getOrCreateTag();
        tag.putString(K_RARITY,   rarity.getSerialKey());
        tag.putInt   (K_LEVEL,    mobLevel);
        tag.putString(K_SOURCE,   source);
        tag.putString(K_CATEGORY, category.key);
        return stack;
    }

    public static LootboxCategory getCategory(ItemStack stack) {
        // Infer from registry ID first — this works for /give and crafted items
        // even if trl_category NBT is absent.
        String regId = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getKey(stack.getItem()) != null
                ? net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(stack.getItem()).getPath()
                : "";
        if (regId.contains("ranged")) return LootboxCategory.RANGED;
        if (regId.contains("tool"))   return LootboxCategory.TOOL;
        if (regId.contains("melee"))  return LootboxCategory.MELEE;
        // Fallback to NBT for legacy items
        if (stack.hasTag()) {
            String key = stack.getTag().getString(K_CATEGORY);
            if (!key.isEmpty()) return LootboxCategory.fromKey(key);
        }
        return LootboxCategory.MELEE;
    }

    public static TRLRarity getTRLRarity(ItemStack stack) {
        if (!stack.hasTag()) return TRLRarity.COMMON;
        return TRLRarity.fromKey(stack.getTag().getString(K_RARITY));
    }

    public static int getLevel(ItemStack stack) {
        if (!stack.hasTag()) return 1;
        return Math.max(1, stack.getTag().getInt(K_LEVEL));
    }

    public static String getSource(ItemStack stack) {
        if (!stack.hasTag()) return SOURCE_KILL;
        return stack.getTag().getString(K_SOURCE);
    }

    // ── Item overrides ────────────────────────────────────────────────────────

    /**
     * Rarity-tinted item name:
     *   §6Component Lootbox   (gold = Unique)
     *   §5Component Lootbox   (purple = Epic)
     *   etc.
     */
    @Override
    public Component getName(ItemStack stack) {
        TRLRarity rarity = getTRLRarity(stack);
        LootboxCategory category = getCategory(stack);
        Style style = Style.EMPTY.withColor(rarity.colour).withBold(rarity.bold);
        String label = switch (category) {
            case RANGED -> "Ranged Lootbox";
            case TOOL   -> "Tool Lootbox";
            default     -> "Melee Lootbox";
        };
        return Component.literal(label).setStyle(style);
    }

    /**
     * Show enchantment glint for EPIC tier and above only.
     * COMMON / UNCOMMON / RARE get colored tooltip borders instead
     * (handled by TRLGlintRenderer) but no inventory shimmer.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getTRLRarity(stack).ordinal() >= TRLRarity.EPIC.ordinal();
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    private static final Component SEP = Component.literal("─────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        TRLRarity rarity   = getTRLRarity(stack);
        int       mobLevel = getLevel(stack);
        String    source   = getSource(stack);

        // ── Rarity badge ──────────────────────────────────────────────────────
        tooltip.add(rarity.nameComponent());
        tooltip.add(SEP);

        // ── Stats ──────────────────────────────────────────────────────────────
        tooltip.add(labelValue("Rarity", rarity.displayName, rarity.colour));
        tooltip.add(labelValue("Level",  String.valueOf(mobLevel), ChatFormatting.WHITE));
        if (!source.isEmpty()) {
            tooltip.add(labelValue("Source",
                    capitalize(source), ChatFormatting.DARK_GRAY));
        }
        tooltip.add(SEP);

        // ── Contents preview ──────────────────────────────────────────────────
        tooltip.add(Component.literal("  Contains: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Random Tinkers Component")
                        .withStyle(ChatFormatting.WHITE)));

        tooltip.add(SEP);
        tooltip.add(Component.literal("  Possible:").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("    Blade · Rod · Guard  (Melee)")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("    Bow Limb · Bowstring · Grip  (Ranged)")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("    Javelin Head · Shaft · Knife  (Thrown)")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(SEP);

        // ── Open hint ─────────────────────────────────────────────────────────
        tooltip.add(Component.literal("  Right-click to open")
                .withStyle(ChatFormatting.ITALIC)
                .withStyle(ChatFormatting.GRAY));
    }

    // ── Right-click to open ───────────────────────────────────────────────────

    /**
     * Server-authoritative open flow.
     *
     * Client side: play the use-animation (triggers the "swing" arm motion).
     * Server side:
     *   1. Roll + build the Tinkers part (WeaponStatRoller → PartMaterialApplicator)
     *   2. Give to player's inventory; drop at feet if full
     *   3. Send chat notification if inventory was full
     *   4. Two sounds: chest-open on use · enchant/chime on receive
     *   5. Particle burst at player position (rarity-count matches tier)
     *   6. Consume 1 box (skipped in creative)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // ── Client side: arm swing + early return ─────────────────────────────
        if (world.isClientSide()) {
            // Return success so the arm-swing animation plays
            return InteractionResultHolder.success(stack);
        }

        // ── Server side ───────────────────────────────────────────────────────
        int       mobLevel = getLevel(stack);
        TRLRarity rarity   = getTRLRarity(stack);

        // Seed: combine UUID, game time, AND nanoTime so that opening multiple boxes
        // in rapid succession (even in the same tick) produces independent results.
        // Without nanoTime, seeds differ by only 1 per tick, and Java's LCG RNG
        // produces nearly identical first values for seeds that differ by 1 —
        // causing the same part type to appear in a run before cycling.
        Random rng = new Random(
                player.getUUID().getLeastSignificantBits()
                ^ world.getGameTime()
                ^ System.nanoTime());

        // ── 1. Roll part ──────────────────────────────────────────────────────
        LootboxCategory category = getCategory(stack);

        // Re-roll up to 8 times if we land on an entry with no real material.
        // isEligible() in TRLDropTable should prevent this, but belt-and-suspenders.
        TRLDropTable.Entry entry = null;
        MaterialVariantId material = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            TRLDropTable.Entry candidate = TRLDropTable.pick(rng, category);
            MaterialVariantId mat = MaterialSelector.pickForLevel(
                    candidate.statType(), rarity, mobLevel, rng);
            // Reject if material is unknown/unresolved
            if (mat != null && !mat.getId().toString().contains("unknown")) {
                entry    = candidate;
                material = mat;
                break;
            }
            TinkRarityLoot.LOGGER.debug(
                    "[TRL] Skipping part {} — no valid material (attempt {})",
                    candidate.item().get().getDescriptionId(), attempt + 1);
        }
        if (entry == null || material == null) {
            TinkRarityLoot.LOGGER.warn(
                    "[TRL] Could not find a part with a valid material after 8 attempts — giving common sword blade");
            // Safe fallback: small_blade is always present and always has materials
            entry    = TRLDropTable.pick(rng, LootboxCategory.MELEE);
            material = MaterialSelector.pickForLevel(entry.statType(), rarity, mobLevel, rng);
        }

        WeaponStatBlock stats = WeaponStatRoller.rollWithRarity(
                mobLevel, entry.family(), entry.template(), rarity, rng);

        ItemStack plain = new ItemStack(entry.item().get());
        stampPartNbt(plain, stats);
        plain.getCapability(ITRLPartStats.CAPABILITY).ifPresent(cap -> cap.setStats(stats));

        ItemStack part = PartMaterialApplicator.apply(plain, material);
        part.getCapability(ITRLPartStats.CAPABILITY).ifPresent(cap -> cap.setStats(stats));

        // ── 2. Give to player ─────────────────────────────────────────────────
        boolean addedToInventory = player.addItem(part);
        if (!addedToInventory) {
            // Drop at player's feet
            player.drop(part, false);
            // Chat nudge so the player notices
            player.sendSystemMessage(
                    Component.literal("Your inventory is full — the part dropped at your feet!")
                            .withStyle(ChatFormatting.YELLOW));
        }

        // ── 3. Layered rarity sounds ──────────────────────────────────────────
        com.tinkrarityloot.common.registry.TRLSounds.playOpen(world, player, rarity);

        // ── 5. Particle burst (server → client via ServerLevel.sendParticles) ──
        if (world instanceof ServerLevel sl) {
            spawnOpenParticles(sl, player, rarity);
        }

        TinkRarityLoot.LOGGER.info(
                "[TRL] Lootbox opened by {}: item={} mat={} level={} rarity={}",
                player.getName().getString(),
                entry.item().get().getDescriptionId(), material.getId(), mobLevel, rarity);

        // ── 6. Consume (skipped in creative) ──────────────────────────────────
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * Particle burst at the player's position.
     *
     * Rarity maps to particle type + count:
     *   COMMON    — SMOKE_NORMAL  ×4
     *   UNCOMMON  — HAPPY_VILLAGER ×6   (green sparks)
     *   RARE      — DRIPPING_WATER ×8   (cyan feel)
     *   EPIC      — ENCHANT ×12         (purple magic)
     *   UNIQUE    — FLAME ×14           (amber/orange)
     *   LEGENDARY — TOTEM_OF_UNDYING ×20 (gold shower)
     *   MYTHIC    — SOUL_FIRE_FLAME ×24  (crimson)
     *
     * sendParticles(type, x, y, z, count, offsetX, offsetY, offsetZ, speed)
     */
    private static void spawnOpenParticles(ServerLevel sl, Player player, TRLRarity rarity) {
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        switch (rarity) {
            case COMMON    -> sl.sendParticles(ParticleTypes.SMOKE,              x,y,z,  4, 0.3,0.3,0.3, 0.02);
            case UNCOMMON  -> sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,     x,y,z,  8, 0.4,0.4,0.4, 0.05);
            case RARE      -> sl.sendParticles(ParticleTypes.DRIPPING_WATER,     x,y,z, 10, 0.4,0.5,0.4, 0.05);
            case EPIC      -> sl.sendParticles(ParticleTypes.ENCHANT,            x,y,z, 14, 0.5,0.6,0.5, 0.10);
            case UNIQUE    -> sl.sendParticles(ParticleTypes.FLAME,              x,y,z, 16, 0.5,0.5,0.5, 0.08);
            case LEGENDARY -> sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,   x,y,z, 22, 0.6,0.7,0.6, 0.12);
            case MYTHIC    -> sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,    x,y,z, 28, 0.6,0.8,0.6, 0.15);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Stamp the rolled WeaponStatBlock into flat NBT on the part stack. */
    private static void stampPartNbt(ItemStack stack, WeaponStatBlock stats) {
        var tag = stack.getOrCreateTag();
        tag.putString(WeaponStatBlock.K_RARITY,  stats.rarity.getSerialKey());
        tag.putString(WeaponStatBlock.K_FAMILY,  stats.family.name().toLowerCase());
        tag.putInt   (WeaponStatBlock.K_DUR,     stats.durability);
        tag.putFloat (WeaponStatBlock.K_DMG,     stats.damage);
        tag.putFloat (WeaponStatBlock.K_ATK_SPD, stats.attackSpeedDelta);
        tag.putFloat (WeaponStatBlock.K_VELOCITY,stats.velocity);
        tag.putFloat (WeaponStatBlock.K_DRAW_SPD,stats.drawSpeed);
        tag.putFloat (WeaponStatBlock.K_ACCURACY,stats.accuracy);
        tag.putInt   (WeaponStatBlock.K_MOB_LVL, stats.mobLevel);
    }

    private static Component labelValue(String label, String value, ChatFormatting valueColour) {
        return Component.literal("  " + label + "  ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColour));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
