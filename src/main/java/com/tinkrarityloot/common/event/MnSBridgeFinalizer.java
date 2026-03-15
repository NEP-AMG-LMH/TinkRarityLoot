package com.tinkrarityloot.common.event;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.rarity.TRLRarity;
import com.tinkrarityloot.common.rpg.TRLAffix;
import com.tinkrarityloot.common.rpg.TRLRequirements;
import com.tinkrarityloot.common.rpg.TRLRPGApplicator;
import com.tinkrarityloot.common.weapon.WeaponFamily;
import com.tinkrarityloot.compat.mineandslash.MnSBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-applies the mmorpg_gear JSON after M&S has had a chance to overwrite it.
 *
 * ── Problem ───────────────────────────────────────────────────────────────────
 *
 * M&S's auto-compatibility code listens to ItemCraftedEvent and rewrites
 * mmorpg_gear on any tool that passes through a crafting grid, stripping our
 * TRL affixes and replacing them with blank M&S data.
 *
 * ── Solution ─────────────────────────────────────────────────────────────────
 *
 * 1. TRLRPGApplicator writes our data to trl_* vanilla NBT keys during assembly
 *    (these survive because M&S doesn't touch them).
 *
 * 2. This class listens to PlayerContainerEvent.Close (LOWEST priority) and
 *    PlayerTickEvent.END (server-only, infrequent check) to re-apply mmorpg_gear
 *    from the stored trl_* keys whenever M&S has reset it.
 *
 * Detection: mmorpg_gear.affixes.pre is empty but trl_affixes is non-empty
 * → M&S overwrote us → re-apply.
 */
public class MnSBridgeFinalizer {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        scanAndReapply(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        reapplyIfNeeded(event.getStack());
    }

    /** Scan entire inventory for TRL tools that need mmorpg_gear re-applied. */
    public static void scanAndReapply(Player player) {
        int fixed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (reapplyIfNeeded(player.getInventory().getItem(i))) fixed++;
        }
        if (fixed > 0)
            TinkRarityLoot.LOGGER.debug("[TRL] MnSBridgeFinalizer: re-applied mmorpg_gear on {} tool(s)", fixed);
    }

    /**
     * Check if mmorpg_gear was overwritten by M&S and re-apply if so.
     * Returns true if a re-apply happened.
     */
    public static boolean reapplyIfNeeded(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return false;
        CompoundTag root = stack.getTag();

        // Only process TRL-assembled tools
        if (root.getByte(TRLRPGApplicator.K_RPG_APPLIED) != 1) return false;

        // Check if our affixes were stripped — trl_affixes present but mmorpg_gear has empty pre[]
        if (!root.contains(TRLAffix.K_LIST)) return false;
        ListTag trlAffixes = root.getList(TRLAffix.K_LIST, Tag.TAG_COMPOUND);

        // Read mmorpg_gear and check if its affixes were blanked
        boolean masStripped = false;
        if (root.contains("mmorpg_gear")) {
            String masJson = root.getString("mmorpg_gear");
            // If M&S blanked our affixes the pre[] array will be empty
            masStripped = masJson.contains("\"pre\":[]") && trlAffixes.size() > 0;
        } else {
            // mmorpg_gear missing entirely — also needs re-apply
            masStripped = trlAffixes.size() > 0 || root.contains(TRLRequirements.K_LEVEL);
        }

        if (!masStripped) return false;

        // Re-build mmorpg_gear from stored trl_* keys
        TRLRequirements reqs = TRLRequirements.readFromTag(root);
        List<TRLAffix> affixes = new ArrayList<>();
        for (int i = 0; i < trlAffixes.size(); i++) {
            affixes.add(TRLAffix.fromNbt(trlAffixes.getCompound(i)));
        }

        // Read stored rarity + family
        TRLRarity rarity = root.contains(ToolAssemblyHandler.TOOL_RARITY_KEY)
                ? TRLRarity.fromKey(root.getString(ToolAssemblyHandler.TOOL_RARITY_KEY))
                : TRLRarity.COMMON;
        WeaponFamily family = readFamily(root);
        int mobLevel = reqs.level();

        MnSBridge.applyToTool(stack, reqs, affixes, rarity, family, mobLevel);
        TinkRarityLoot.LOGGER.info("[TRL] MnSBridgeFinalizer: restored mmorpg_gear (M&S had stripped affixes)");
        return true;
    }

    private static WeaponFamily readFamily(CompoundTag root) {
        // trl_family is written on the parts but not on the tool — infer from tic_materials
        // Default to MELEE_LIGHT; we store trl_tool_family if available
        if (root.contains("trl_tool_family")) {
            String f = root.getString("trl_tool_family");
            try { return WeaponFamily.valueOf(f.toUpperCase()); } catch (Exception ignored) {}
        }
        return WeaponFamily.MELEE_LIGHT;
    }
}
