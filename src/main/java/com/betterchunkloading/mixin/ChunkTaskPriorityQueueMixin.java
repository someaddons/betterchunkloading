package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Mixin(ChunkTaskPriorityQueue.class)
public abstract class ChunkTaskPriorityQueueMixin<T>
{
    @Redirect(method = "resortChunkTasks", at = @At(value = "INVOKE", target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"))
    private boolean newTasksFirst(final List instance, final Collection es, int p_140522_, ChunkPos p_140523_, int p_140524_)
    {
        // Make sure newer tasks are ran first
        return instance.addAll(0, es);
    }

    @Inject(method = "resortChunkTasks", at = @At("RETURN"))
    private void checkStalled(final int p_140522_, final ChunkPos chunkPos, final int p_140524_, final CallbackInfo ci)
    {
        if (EventHandler.loadingChunk != null && firstQueue > 1 && BetterChunkLoading.config.getCommonConfig().optimizeWaiting)
        {
            // Search n sort it to first
            final long longPos = chunkPos.toLong();
            List<Optional<T>> newTaskList = null;
            for (int index = 0; index < taskQueue.size(); index++)
            {
                final Long2ObjectLinkedOpenHashMap<List<Optional<T>>> map = taskQueue.get(index);
                List<Optional<T>> data = map.get(longPos);
                if (data != null && !data.isEmpty())
                {
                    if (newTaskList == null)
                    {
                        newTaskList = new ArrayList<>();
                    }

                    newTaskList.addAll(data);
                    map.remove(longPos);
                }
            }

            if (newTaskList != null)
            {
                firstQueue = 1;
                taskQueue.get(1).computeIfAbsent(longPos, s -> new ArrayList<>()).addAll(0, newTaskList);
            }
        }
    }

    @Shadow
    @Final
    private List<Long2ObjectLinkedOpenHashMap<List<Optional<T>>>> taskQueue;

    @Shadow
    private volatile int firstQueue;

    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean submitNewTasksFirst(final List instance, final Object e, Optional<T> p_140536_, long pos, int p_140538_)
    {
        instance.add(0, e);
        return true;
    }
}
