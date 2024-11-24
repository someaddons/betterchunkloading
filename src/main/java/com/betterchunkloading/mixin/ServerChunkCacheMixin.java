package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin
{
    @Shadow
    public abstract ChunkHolder getVisibleChunkIfPresent(final long p_8365_);

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(
        final int p_8457_,
        final int p_8458_,
        final ChunkStatus p_331599_,
        final boolean p_8460_);

    @Redirect(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> prioWaiting(
        final ServerChunkCache instance, final int x, final int z, final ChunkStatus status, final boolean load)
    {
        final CompletableFuture<ChunkResult<ChunkAccess>> future = getChunkFutureMainThread(x, z, status, load);
        if (future == ChunkHolder.UNLOADED_CHUNK_FUTURE)
        {
            return future;
        }

        if (!future.isDone() && BetterChunkLoading.config.getCommonConfig().optimizeWaiting)
        {
            if (load)
            {
                var chunkholder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
                EventHandler.loadingChunk = chunkholder.getPos();

                // Generate an additional load, to make sure the future is not stuck in some queue
                chunkholder.futures.set(status.getIndex(), null);
                var newFuture = chunkholder.scheduleChunkGenerationTask(status, this.chunkMap);
                chunkholder.futures.set(status.getIndex(), future);

                // Trigger resort
                chunkholder.onLevelChange.onLevelChange(chunkholder.getPos(), () -> {
                    return chunkholder.getQueueLevel();
                }, 1, chunkholder::setQueueLevel);

                return (CompletableFuture<ChunkResult<ChunkAccess>>) (Object) CompletableFuture.anyOf(future, newFuture).whenComplete((a, b) -> {
                    if (!future.isDone() || future.getNow(ChunkHolder.UNLOADED_CHUNK) != a)
                    {
                        future.complete((ChunkResult<ChunkAccess>) a);
                        chunkholder.addSaveDependency(future);
                    }
                });
            }
            else
            {
                return ChunkHolder.UNLOADED_CHUNK_FUTURE;
            }
        }
        return future;
    }
}
