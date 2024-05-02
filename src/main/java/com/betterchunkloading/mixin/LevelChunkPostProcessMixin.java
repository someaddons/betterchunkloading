package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

import static com.betterchunkloading.BetterChunkLoading.TICKET_POST_PROCESS;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin extends ChunkAccess {

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
            @Nullable final BlendingData p_187627_) {
        super(p_187621_, p_187622_, p_187623_, p_187624_, p_187625_, p_187626_, p_187627_);
    }

    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void onPost(final CallbackInfo ci) {
        if (BetterChunkLoading.config.getCommonConfig().enableSmartPostProcessing && postProcessing.length != 0 && level.getServer() != null) {
            boolean needsSurroundingChunks = false;

            outer:
            for (int i = 0; i < this.postProcessing.length; ++i) {
                if (this.postProcessing[i] != null) {
                    for (Short oshort : this.postProcessing[i]) {
                        BlockPos blockpos = ProtoChunk.unpackOffsetCoordinates(oshort, this.getSectionYFromSectionIndex(i), chunkPos);
                        BlockState blockstate = this.getBlockState(blockpos);
                        FluidState fluidstate = blockstate.getFluidState();
                        if (!fluidstate.isEmpty()) {
                            needsSurroundingChunks = true;
                            break outer;
                        }

                        if (!(blockstate.getBlock() instanceof LiquidBlock)) {
                            if ((blockpos.getX() + 1) >> 4 != chunkPos.x || (blockpos.getX() - 1) >> 4 != chunkPos.x
                                    || (blockpos.getZ() + 1) >> 4 != chunkPos.z || (blockpos.getX() - 1) >> 4 != chunkPos.z) {
                                needsSurroundingChunks = true;
                                break outer;
                            }
                        }
                    }
                }
            }

            if (!needsSurroundingChunks) {
                return;
            }

            for (final it.unimi.dsi.fastutil.shorts.ShortList shorts : postProcessing) {
                if (shorts != null && !shorts.isEmpty()) {
                    ((ServerChunkCache) level.getChunkSource()).distanceManager.addTicket(TICKET_POST_PROCESS,
                            chunkPos,
                            33 - 1,
                            chunkPos);

                    EventHandler.ChunkInfo info = new EventHandler.ChunkInfo(level.getServer().getTickCount(),
                      chunkPos,
                      level,
                      Arrays.copyOf(postProcessing, postProcessing.length));
                    EventHandler.addChunkToQueue(info);
                    Arrays.fill(this.postProcessing, null);
                    break;
                }
            }
        }
    }
}
