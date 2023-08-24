package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IDistanceManager;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DistanceManager.PlayerTicketTracker.class)
public abstract class PlayerTicketTrackerMixin extends ChunkTracker
{
    /**
     * References the outer class this
     */
    @Shadow
    @Final
    DistanceManager this$0;

    protected PlayerTicketTrackerMixin(final int p_140701_, final int p_140702_, final int p_140703_)
    {
        super(p_140701_, p_140702_, p_140703_);
    }

    /**
     * Needs to be remapped, so override is necessary
     * @param p_140899_
     * @return
     */
    @Override
    protected int getLevelFromSource(long p_140899_)
    {
        return this.havePlayer(p_140899_) ? 0 : Integer.MAX_VALUE;
    }

    /**
     * Our own custom players per chunk
     * @param p_140903_
     * @return
     */
    @Unique
    private boolean havePlayer(long p_140903_)
    {
        return ((IDistanceManager) this$0).getPlayerCountPerChunk().get(p_140903_) > 0;
    }
}
