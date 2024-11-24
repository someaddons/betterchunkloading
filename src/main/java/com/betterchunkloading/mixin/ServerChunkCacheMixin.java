package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
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
    protected abstract ChunkHolder getVisibleChunkIfPresent(final long p_8365_);

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFutureMainThread(
        final int p_8457_,
        final int p_8458_,
        final ChunkStatus p_8459_,
        final boolean p_8460_);

    @Redirect(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> prioWaiting(
        final ServerChunkCache instance,
        final int x,
        final int z,
        final ChunkStatus status,
        final boolean load)
    {
        final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = getChunkFutureMainThread(x, z, status, load);
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
                var newFuture = chunkholder.getOrScheduleFuture(status, this.chunkMap);
                chunkholder.futures.set(status.getIndex(), future);

                // Trigger resort
                chunkholder.onLevelChange.onLevelChange(chunkholder.getPos(), () -> {
                    return chunkholder.getQueueLevel();
                }, 1, chunkholder::setQueueLevel);

                return (CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>) (Object) CompletableFuture.anyOf(future, newFuture).whenComplete((a, b) -> {
                    if (!future.isDone() || future.getNow(ChunkHolder.UNLOADED_CHUNK) != a)
                    {
                        future.complete((Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>) a);
                        chunkholder.updateChunkToSave(future, "temp");
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
