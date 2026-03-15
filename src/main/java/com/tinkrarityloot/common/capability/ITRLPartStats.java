package com.tinkrarityloot.common.capability;

import com.tinkrarityloot.common.stat.WeaponStatBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;

/**
 * Capability that attaches TRL ARPG weapon stats to any Tinkers part ItemStack.
 *
 * Holds a {@link WeaponStatBlock} which carries the full weapon-family stat
 * set (damage, durability, attack speed, velocity, draw speed, accuracy)
 * rather than the old simple damage + durability pair.
 */
public interface ITRLPartStats extends INBTSerializable<CompoundTag> {

    Capability<ITRLPartStats> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    @Nullable WeaponStatBlock getStats();
    void setStats(WeaponStatBlock stats);
    boolean hasStats();

    final class Impl implements ITRLPartStats {

        private static final String KEY = "trl_stats";

        @Nullable private WeaponStatBlock stats;

        @Override public @Nullable WeaponStatBlock getStats() { return stats; }
        @Override public void setStats(WeaponStatBlock s)      { this.stats = s; }
        @Override public boolean hasStats()                    { return stats != null; }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            if (stats != null) tag.put(KEY, stats.toNBT());
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            if (tag.contains(KEY)) {
                stats = WeaponStatBlock.fromNBT(tag.getCompound(KEY));
            }
        }
    }
}
