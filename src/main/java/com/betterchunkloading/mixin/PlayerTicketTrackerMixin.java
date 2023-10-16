package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IDistanceManager;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.PlayerTicketTracker.class)
public abstract class PlayerTicketTrackerMixin extends ChunkTracker {

    /**
     * References the outer class this
     */
    @Unique
    IDistanceManager distManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(final DistanceManager distanceManager, final int i, final CallbackInfo ci) {
        distManager = (IDistanceManager) distanceManager;
    }


    protected PlayerTicketTrackerMixin(final int p_140701_, final int p_140702_, final int p_140703_) {
        super(p_140701_, p_140702_, p_140703_);
    }

    /**
     * Needs to be remapped, so override is necessary
     *
     * @param p_140899_
     * @return
     */
    @Override
    protected int getLevelFromSource(long p_140899_) {
        return this.havePlayer(p_140899_) ? 0 : Integer.MAX_VALUE;
    }

    /**
     * Our own custom players per chunk
     *
     * @param p_140903_
     * @return
     */
    @Unique
    private boolean havePlayer(long p_140903_) {
        return distManager.getPlayerCountPerChunk().get(p_140903_) > 0;
    }
}
