package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.betterchunkloading.BetterChunkLoading.TICKET_1min;
import static com.betterchunkloading.BetterChunkLoading.TICKET_5min;

@Mixin(ServerPlayer.class)
public abstract class PlayerTickMixin extends Player
{
    @Shadow
    public abstract void die(final DamageSource p_9035_);

    @Unique
    private ChunkPos lastChunk = ChunkPos.ZERO;

    @Unique
    private int index = 0;

    @Unique
    private ChunkPos lastAvgOldChunkPos   = ChunkPos.ZERO;
    @Unique
    private ChunkPos lastAvgNewChunkPos   = ChunkPos.ZERO;
    @Unique
    private ChunkPos lastChunkTicket      = ChunkPos.ZERO;
    @Unique
    private int      lastChunkTicketLevel = 0;

    @Unique
    private final BlockPos[] lastSix = new BlockPos[6];

    public PlayerTickMixin(final Level p_250508_, final BlockPos p_250289_, final float p_251702_, final GameProfile p_252153_)
    {
        super(p_250508_, p_250289_, p_251702_, p_252153_);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(final CallbackInfo ci)
    {
        // Exclude fake players and the likes
        if (((Object) this).getClass() == ServerPlayer.class)
        {
            if (!this.chunkPosition().equals(lastChunk))
            {
                lastChunk = chunkPosition();
                ((ServerChunkCache) level().getChunkSource()).addRegionTicket(TICKET_5min,
                  lastChunk,
                  ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - ((ServerChunkCache) level().getChunkSource()).distanceManager.simulationDistance,
                  lastChunk);
                lastSix[index] = new BlockPos(getBlockX(), 0, getBlockZ());
                index = (index + 1) % 6;

                BlockPos avgOldest = BlockPos.ZERO;
                for (int i = 0; i < 3; i++)
                {
                    if (lastSix[(index + i) % 6] == null)
                    {
                        return;
                    }
                    avgOldest = avgOldest.offset(lastSix[(index + i) % 6]);
                }
                avgOldest = new BlockPos(avgOldest.getX() / 3, 0, avgOldest.getZ() / 3);

                BlockPos avgNewest = BlockPos.ZERO;
                for (int i = 3; i < 6; i++)
                {
                    if (lastSix[(index + i) % 6] == null)
                    {
                        return;
                    }
                    avgNewest = avgNewest.offset(lastSix[(index + i) % 6]);
                }
                avgNewest = new BlockPos(avgNewest.getX() / 3, 0, avgNewest.getZ() / 3);

                ChunkPos newOldest = new ChunkPos(avgOldest);
                ChunkPos newNewest = new ChunkPos(avgNewest);

                if (!newNewest.equals(lastAvgNewChunkPos) || !newOldest.equals(lastAvgOldChunkPos))
                {
                    lastAvgNewChunkPos = newNewest;
                    lastAvgOldChunkPos = newOldest;

                    final Vec3 direction = Vec3.atBottomCenterOf(avgOldest).subtract(Vec3.atBottomCenterOf(avgNewest)).normalize().reverse();

                    final int viewdistanceBlocks = ((ServerChunkCache) level().getChunkSource()).chunkMap.viewDistance * 16;

                    Vec3 currentpos = Vec3.atBottomCenterOf(avgNewest);

                    currentpos = currentpos.add(direction.scale(BetterChunkLoading.config.getCommonConfig().predictiondistance * 16));

                    // Current
                    ChunkPos currentChunk = new ChunkPos((int) currentpos.x >> 4, (int) currentpos.z >> 4);

                    if (lastChunkTicket.equals(currentChunk))
                    {
                        return;
                    }

                    if (!lastChunkTicket.equals(ChunkPos.ZERO))
                    {
                        ((ServerChunkCache) level().getChunkSource()).removeRegionTicket(TICKET_1min, lastChunkTicket, lastChunkTicketLevel, currentChunk);
                    }
                    ((ServerChunkCache) level().getChunkSource()).addRegionTicket(TICKET_1min,
                      currentChunk,
                      11,
                      currentChunk);
                    lastChunkTicket = currentChunk;
                    lastChunkTicketLevel = 11;
                }
            }
        }
    }
}
