package com.tinkrarityloot.common.material;

import com.tinkrarityloot.TinkRarityLoot;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * Writes the chosen {@link MaterialVariantId} onto a Tinkers part {@link ItemStack}
 * using TCon's own {@link IMaterialItem} API.
 *
 * Why this works:
 *   Every TCon part item (SwordBlade, ToolRod, WideGuard, …) implements
 *   {@link IMaterialItem}.  That interface exposes:
 *
 *     ItemStack withMaterial(MaterialVariantId)
 *       – returns a new ItemStack that has the material's ID baked into its NBT
 *         under the key TCon expects ("Material" inside the tconstruct compound).
 *
 *   Once the material is baked in, TCon's renderer, tool station, and stat
 *   system all see it as a fully legitimate material part:
 *     • Inventory icon    = correct material texture via TCon's material render
 *     • In-world model    = correct material tint/texture
 *     • Tool station UI   = correct part preview
 *     • Tool assembly     = material stats contribute normally
 *
 * We then transplant TRL's own NBT keys (trl_rarity, trl_durability, …) from
 * the original plain stack onto the materialised stack, preserving our ARPG data.
 */
public final class PartMaterialApplicator {

    private PartMaterialApplicator() {}

    /**
     * Apply {@code material} to {@code partStack} and return the resulting stack.
     *
     * If the item does not implement {@link IMaterialItem} (shouldn't happen for
     * any TCon part, but defensive), the original stack is returned unchanged.
     *
     * @param partStack  A freshly created ItemStack of a TCon part item.
     * @param material   The material to apply (from {@link MaterialSelector}).
     * @return           A new ItemStack with the material baked in, carrying TRL NBT.
     */
    public static ItemStack apply(ItemStack partStack, MaterialVariantId material) {
        if (!(partStack.getItem() instanceof IMaterialItem materialItem)) {
            TinkRarityLoot.LOGGER.warn("[TRL] {} does not implement IMaterialItem — skipping material apply.",
                    partStack.getItem().getDescriptionId());
            return partStack;
        }

        // Let TCon build the materialised stack (sets the "Material" NBT the renderer reads)
        ItemStack materialised = materialItem.withMaterial(material);

        // Transplant any TRL NBT already written on the plain stack
        transplantTrlNbt(partStack, materialised);

        TinkRarityLoot.LOGGER.debug("[TRL] Applied material {} to {}",
                material.getId(), partStack.getItem().getDescriptionId());

        return materialised;
    }

    /**
     * Copy TRL-specific NBT keys from {@code source} to {@code dest}.
     * Called after {@link IMaterialItem#withMaterial} so our stat data is not lost.
     */
    /**
     * Copy ALL TRL-specific NBT keys from {@code source} to {@code dest}.
     *
     * Covers every key that {@link com.tinkrarityloot.common.stat.WeaponStatBlock#fromNBT}
     * reads so that {@link com.tinkrarityloot.common.event.ToolAssemblyHandler#collectStatBlocks}
     * can reconstruct a full WeaponStatBlock from flat NBT when the capability
     * is unavailable (e.g. after item serialisation / client-side stacks).
     *
     * Previous version only copied 5 of 9 keys; trl_family, trl_atk_speed,
     * trl_velocity, trl_draw_speed, and trl_accuracy were silently lost, causing
     * WeaponStatBlock.fromNBT to return null and ToolAssemblyHandler to skip
     * stat injection entirely.
     */
    private static void transplantTrlNbt(ItemStack source, ItemStack dest) {
        if (!source.hasTag()) return;
        var s = source.getTag();
        var d = dest.getOrCreateTag();

        // ── All 9 WeaponStatBlock keys ────────────────────────────────────────
        copyS(s, d, "trl_rarity");
        copyS(s, d, "trl_family");
        copyI(s, d, "trl_durability");
        copyF(s, d, "trl_damage");
        copyF(s, d, "trl_atk_speed");
        copyF(s, d, "trl_velocity");
        copyF(s, d, "trl_draw_speed");
        copyF(s, d, "trl_accuracy");
        copyI(s, d, "trl_mob_level");
    }

    private static void copyS(net.minecraft.nbt.CompoundTag s,
                               net.minecraft.nbt.CompoundTag d, String k) {
        if (s.contains(k)) d.putString(k, s.getString(k));
    }
    private static void copyI(net.minecraft.nbt.CompoundTag s,
                               net.minecraft.nbt.CompoundTag d, String k) {
        if (s.contains(k)) d.putInt(k, s.getInt(k));
    }
    private static void copyF(net.minecraft.nbt.CompoundTag s,
                               net.minecraft.nbt.CompoundTag d, String k) {
        if (s.contains(k)) d.putFloat(k, s.getFloat(k));
    }
}
