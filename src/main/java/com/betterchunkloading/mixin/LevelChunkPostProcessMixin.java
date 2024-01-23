package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

import static com.betterchunkloading.BetterChunkLoading.TICKET_1min;
import static com.betterchunkloading.BetterChunkLoading.TICKET_2min;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin extends ChunkAccess
{
    @Shadow
    @Final
    public Level level;

    public LevelChunkPostProcessMixin(
      final ChunkPos p_187621_,
      final UpgradeData p_187622_,
      final LevelHeightAccessor p_187623_,
      final Registry<Biome> p_187624_,
      final long p_187625_,
      @Nullable final LevelChunkSection[] p_187626_,
      @Nullable final BlendingData p_187627_)
    {
        super(p_187621_, p_187622_, p_187623_, p_187624_, p_187625_, p_187626_, p_187627_);
    }

    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void onPost(final CallbackInfo ci)
    {
        if (BetterChunkLoading.config.getCommonConfig().enableFasterChunkLoading && postProcessing.length != 0 && level.getServer() != null)
        {
            for (final it.unimi.dsi.fastutil.shorts.ShortList shorts : postProcessing)
            {
                if (shorts != null && !shorts.isEmpty())
                {
                    ((ServerChunkCache) level.getChunkSource()).distanceManager.addTicket(TICKET_2min,
                      chunkPos,
                      ChunkLevel.byStatus(FullChunkStatus.FULL),
                      chunkPos);
                    EventHandler.delayedLoading.put(new EventHandler.ChunkInfo(level.getServer().getTickCount(), chunkPos, level), postProcessing.clone());
                    Arrays.fill(this.postProcessing, null);
                    break;
                }
            }
        }
    }
}
