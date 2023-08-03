package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import net.minecraft.core.Registry;
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

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin extends ChunkAccess
{
    @Shadow
    @Final
    private Level level;

    public LevelChunkMixin(
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
        if (BetterChunkLoading.config.getCommonConfig().enableFasterChunkLoading)
        {
            EventHandler.delayedLoading.put(new EventHandler.ChunkInfo(level.getServer().getTickCount(), chunkPos, level), postProcessing.clone());
            Arrays.fill(this.postProcessing, null);
        }
    }
}
