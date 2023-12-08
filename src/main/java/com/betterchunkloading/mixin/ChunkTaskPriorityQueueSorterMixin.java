package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

/**
 * Runs additional tasks to adapt to player count and increased task amount for better async processing
 */
@Mixin(ChunkTaskPriorityQueueSorter.class)
public abstract class ChunkTaskPriorityQueueSorterMixin
{
    @Shadow
    protected abstract <T> void pollTask(final ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> p_140646_, final ProcessorHandle<T> p_140647_);

    @Shadow @Final private Map<ProcessorHandle<?>, ChunkTaskPriorityQueue<? extends Function<ProcessorHandle<Unit>, ?>>> queues;
    @Unique
    boolean adjusting = false;

    @Inject(method = "pollTask", at = @At("RETURN"))
    private <T> void lagebegone$polltask(
      final ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> functionChunkTaskPriorityQueue,
      final ProcessorHandle<T> processorHandle,
      final CallbackInfo ci)
    {
        if (!adjusting)
        {
            adjusting = true;
            for (int i = 0; i < BetterChunkLoading.player_modifier; i++)
            {
               pollTask(functionChunkTaskPriorityQueue, processorHandle);
            }
            adjusting = false;
        }
    }
}
