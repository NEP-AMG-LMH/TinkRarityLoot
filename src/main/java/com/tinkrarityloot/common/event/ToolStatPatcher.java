package com.tinkrarityloot.common.event;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.compat.tinkers.TinkersStatInjector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Single authority for writing TRL bonuses into tic_stats.
 *
 * ── Why server-only with forced sync ─────────────────────────────────────
 *
 * Running patch() on both client and server causes oscillation: TCon rebuilds
 * tic_stats independently on each side, and the server's item sync packets
 * overwrite the client's patched copy. Running on both sides creates a
 * race between the two copies.
 *
 * The correct approach: patch ONLY on the server, then immediately call
 * broadcastChanges() to push the patched NBT to the client. The client
 * never patches — it always receives the authoritative patched copy from
 * the server. This eliminates oscillation entirely.
 *
 * For the station UI display (slot 0), we patch server-side in the tick
 * and immediately broadcast so the client sees updated stat bars without
 * needing a menu reopen.
 *
 * ── Idempotency ──────────────────────────────────────────────────────────
 *
 * Full fingerprint of all six tic_stats values. patch() only writes when
 * the fingerprint differs from stored trl_stat_fp.
 */
public class ToolStatPatcher {

    private static final String K_BASE_DUR   = "trl_base_dur";
    private static final String K_BASE_DMG   = "trl_base_dmg";
    private static final String K_BASE_ASPD  = "trl_base_aspd";
    private static final String K_BASE_VEL   = "trl_base_vel";
    private static final String K_BASE_DRAW  = "trl_base_draw";
    private static final String K_BASE_ACC   = "trl_base_acc";
    private static final String K_FINAL_DUR  = "trl_final_dur";
    private static final String K_FINAL_DMG  = "trl_final_dmg";
    private static final String K_FINAL_ASPD = "trl_final_aspd";
    private static final String K_FINAL_VEL  = "trl_final_vel";
    private static final String K_FINAL_DRAW = "trl_final_draw";
    private static final String K_FINAL_ACC  = "trl_final_acc";
    private static final String K_STAT_FP    = "trl_stat_fp";

    private static final String TC_DUR  = "tconstruct:durability";
    private static final String TC_DMG  = "tconstruct:attack_damage";
    private static final String TC_ASPD = "tconstruct:attack_speed";
    private static final String TC_VEL  = "tconstruct:velocity";
    private static final String TC_DRAW = "tconstruct:draw_speed";
    private static final String TC_ACC  = "tconstruct:accuracy";

    // ── Server-only tick (cursor + station output) ────────────────────────────

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Server-only — client patches cause oscillation because the client's
        // TCon rebuild overwrites our stats, and broadcastChanges() then sends
        // that client state back to the server, clearing our fingerprint.
        // Server maintains the canonical patched state; client renders via sync.
        if (!(event.player instanceof ServerPlayer sp)) return;

        AbstractContainerMenu menu = sp.containerMenu;
        if (menu == null) return;

        boolean changed = false;

        // Cursor item — fixes stat reset when dragging
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) changed |= patch(carried);

        // Station output slot 0 — fixes stat bars in the TCon UI
        if (menu.slots.size() > 0) {
            ItemStack output = menu.slots.get(0).getItem();
            if (!output.isEmpty()
                    && output.hasTag()
                    && output.getTag().contains("tic_stats")
                    && output.getTag().getByte(TinkersStatInjector.K_APPLIED) == 1) {
                changed |= patch(output);
            }
        }

        // Only broadcast when we actually changed something — avoids flooding
        if (changed) menu.broadcastChanges();
    }

    // ── Server-only inventory coverage ───────────────────────────────────────

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        EquipmentSlot slot = event.getSlot();
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        LivingEntity entity = event.getEntity();
        // Patch on both sides — client patches for display, server patches for combat
        // Both use identical fingerprint logic so they never compound
        boolean changed = patch(event.getTo());
        if (changed && !entity.level().isClientSide() && entity instanceof ServerPlayer sp)
            sp.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (patch(event.getStack()) && event.getEntity() instanceof ServerPlayer sp)
            sp.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        patchInventory(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        patchInventory(player);
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        patchInventory(player);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        patchInventory(player);
    }

    // ── Core ─────────────────────────────────────────────────────────────────

    public static int patchInventory(Player player) {
        int patched = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (patch(player.getInventory().getItem(i))) patched++;
        }
        if (patched > 0)
            TinkRarityLoot.LOGGER.debug("[TRL] patchInventory: {} tool(s) for {}",
                    patched, player.getName().getString());
        return patched;
    }

    /**
     * Idempotent patch. Server-side only. Returns true if tic_stats was written.
     * Uses full fingerprint to detect TCon rebuilds.
     */
    public static boolean patch(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return false;
        CompoundTag root = stack.getTag();
        if (root.getByte(TinkersStatInjector.K_APPLIED) != 1) return false;
        if (!root.contains("tic_stats", Tag.TAG_COMPOUND)) return false;

        CompoundTag stats = root.getCompound("tic_stats");
        if (!stats.contains(TC_DUR)) return false;

        float bonusDur  = root.getFloat(TinkersStatInjector.K_B_DUR);
        float bonusDmg  = root.getFloat(TinkersStatInjector.K_B_DMG);
        float bonusVel  = root.getFloat(TinkersStatInjector.K_B_VEL);
        float bonusDraw = root.getFloat(TinkersStatInjector.K_B_DRAW);
        float bonusAcc  = root.getFloat(TinkersStatInjector.K_B_ACC);
        // Attack speed bonus only injected at EPIC+ (rarity_idx >= 3).
        // The value is stored on all parts for tooltip display, but the tool
        // stat only gains it once the assembled rarity reaches EPIC tier.
        int rarityIdx = root.getInt("trl_rarity_idx");
        float bonusAspd = (rarityIdx >= 3) ? root.getFloat(TinkersStatInjector.K_B_ATKSPD) : 0f;

        int currentFP = fingerprint(stats);
        if (root.contains(K_STAT_FP) && root.getInt(K_STAT_FP) == currentFP) {
            return false; // Already patched
        }

        float baseDur  = stats.getFloat(TC_DUR);
        float baseDmg  = stats.contains(TC_DMG)  ? stats.getFloat(TC_DMG)  : 0f;
        float baseAspd = stats.contains(TC_ASPD) ? stats.getFloat(TC_ASPD) : 0f;
        float baseVel  = stats.contains(TC_VEL)  ? stats.getFloat(TC_VEL)  : 0f;
        float baseDraw = stats.contains(TC_DRAW) ? stats.getFloat(TC_DRAW) : 0f;
        float baseAcc  = stats.contains(TC_ACC)  ? stats.getFloat(TC_ACC)  : 0f;

        float finalDur  = baseDur  + bonusDur;
        float finalDmg  = baseDmg  + bonusDmg;
        float finalAspd = baseAspd + bonusAspd;
        float finalVel  = baseVel  + bonusVel;
        float finalDraw = baseDraw + bonusDraw;
        float finalAcc  = baseAcc  + bonusAcc;

        stats.putFloat(TC_DUR, finalDur);
        if (bonusDmg  != 0f && stats.contains(TC_DMG))  stats.putFloat(TC_DMG,  finalDmg);
        if (bonusAspd != 0f && stats.contains(TC_ASPD)) stats.putFloat(TC_ASPD, finalAspd);
        if (bonusVel  != 0f && stats.contains(TC_VEL))  stats.putFloat(TC_VEL,  finalVel);
        if (bonusDraw != 0f && stats.contains(TC_DRAW)) stats.putFloat(TC_DRAW, finalDraw);
        if (bonusAcc  != 0f && stats.contains(TC_ACC))  stats.putFloat(TC_ACC,  finalAcc);

        root.putFloat(K_BASE_DUR,  baseDur);   root.putFloat(K_FINAL_DUR,  finalDur);
        root.putFloat(K_BASE_DMG,  baseDmg);   root.putFloat(K_FINAL_DMG,  finalDmg);
        root.putFloat(K_BASE_ASPD, baseAspd);  root.putFloat(K_FINAL_ASPD, finalAspd);
        root.putFloat(K_BASE_VEL,  baseVel);   root.putFloat(K_FINAL_VEL,  finalVel);
        root.putFloat(K_BASE_DRAW, baseDraw);  root.putFloat(K_FINAL_DRAW, finalDraw);
        root.putFloat(K_BASE_ACC,  baseAcc);   root.putFloat(K_FINAL_ACC,  finalAcc);
        root.putInt(K_STAT_FP, fingerprint(stats));

        // Only log on server (Render thread = client side)
        if (!Thread.currentThread().getName().startsWith("Render")) {
            TinkRarityLoot.LOGGER.info("[TRL] Patched: dur {}+{}={} dmg {}+{}={} aspd {}+{}={}",
                    (int)baseDur, (int)bonusDur, (int)finalDur,
                    f(baseDmg), f(bonusDmg), f(finalDmg),
                    f(baseAspd), f(bonusAspd), f(finalAspd));
        }
        return true;
    }

    private static int fingerprint(CompoundTag stats) {
        int h = Float.floatToIntBits(stats.getFloat(TC_DUR));
        h = h * 31 + Float.floatToIntBits(stats.getFloat(TC_DMG));
        h = h * 31 + Float.floatToIntBits(stats.getFloat(TC_ASPD));
        h = h * 31 + Float.floatToIntBits(stats.getFloat(TC_VEL));
        h = h * 31 + Float.floatToIntBits(stats.getFloat(TC_DRAW));
        h = h * 31 + Float.floatToIntBits(stats.getFloat(TC_ACC));
        return h;
    }

    private static String f(float v) { return String.format("%.2f", v); }
}
