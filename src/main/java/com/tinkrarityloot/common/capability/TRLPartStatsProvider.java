package com.tinkrarityloot.common.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides and serializes {@link ITRLPartStats} for an ItemStack.
 */
public class TRLPartStatsProvider implements ICapabilitySerializable<CompoundTag> {

    private final ITRLPartStats.Impl impl = new ITRLPartStats.Impl();
    private final LazyOptional<ITRLPartStats> optional = LazyOptional.of(() -> impl);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return ITRLPartStats.CAPABILITY.orEmpty(cap, optional);
    }

    @Override public CompoundTag serializeNBT()              { return impl.serializeNBT(); }
    @Override public void deserializeNBT(CompoundTag tag)    { impl.deserializeNBT(tag); }
}
