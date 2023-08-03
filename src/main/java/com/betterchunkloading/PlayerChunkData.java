package com.betterchunkloading;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.betterchunkloading.BetterChunkLoading.TICKET_1min;

public class PlayerChunkData
{
    /**
     * Last chunk pos of the player
     */
    private ChunkPos lastChunk = ChunkPos.ZERO;

    private ResourceKey<Level> lastLevel = null;

    /**
     * Tracking for the last six chunks visited
     */
    private BlockPos[] lastSix            = new BlockPos[6];
    private int        index              = 0;
    private ChunkPos   lastAvgOldChunkPos = ChunkPos.ZERO;
    private ChunkPos   lastAvgNewChunkPos = ChunkPos.ZERO;

    /**
     * Last predictive chunk ticket position and level
     */
    private ChunkPos lastChunkTicket      = ChunkPos.ZERO;
    private int      lastChunkTicketLevel = 0;

    /**
     * Tracking for the last slow average chunk pos
     */
    private BlockPos[] lastSlowAvg   = new BlockPos[6];
    private int        lastSlowIndex = 0;
    private SectionPos lastPosAvg    = null;

    public void tick(ServerPlayer player)
    {
        if (player.chunkPosition().equals(lastChunk))
        {
            return;
        }

        lastChunk = player.chunkPosition();

        if (!player.getLevel().dimension().equals(lastLevel))
        {
            lastLevel = player.getLevel().dimension();
            reset();
        }

        if (BetterChunkLoading.config.getCommonConfig().enablePrediction)
        {
            checkPrediction(player);
        }

        if (BetterChunkLoading.config.getCommonConfig().enableLazyChunkloading)
        {
            updateSlowAvgChunkPos(player);
        }
    }

    /**
     * Resets data
     */
    private void reset()
    {
        lastSix = new BlockPos[6];
        index = 0;
        lastAvgOldChunkPos = ChunkPos.ZERO;
        lastAvgNewChunkPos = ChunkPos.ZERO;

        lastChunkTicket = ChunkPos.ZERO;
        lastChunkTicketLevel = 0;

        lastSlowAvg = new BlockPos[6];
        lastSlowIndex = 0;
        lastPosAvg = null;
    }

    /**
     * Updates the slow changing average chunkpos of the player
     *
     * @param player
     */
    private void updateSlowAvgChunkPos(final ServerPlayer player)
    {
        // Config enable, config slowness factor
        final int cacheSize = Math.max(1, (int) (((ServerChunkCache) player.level.getChunkSource()).chunkMap.viewDistance / 0.6));
        if (lastSlowAvg.length != cacheSize)
        {
            BlockPos[] newArray = new BlockPos[cacheSize];
            for (int i = 0; i < Math.min(cacheSize, lastSlowAvg.length); i++)
            {
                newArray[i] = lastSlowAvg[i];
            }

            lastSlowAvg = newArray;
            lastSlowIndex = lastSlowIndex % cacheSize;
        }

        lastSlowAvg[lastSlowIndex] = new BlockPos(player.getBlockX(), 0, player.getBlockZ());
        lastSlowIndex = (lastSlowIndex + 1) % cacheSize;

        int amount = 0;
        BlockPos posAvg = BlockPos.ZERO;

        for (int i = 0; i < cacheSize; i++)
        {
            final BlockPos pos = lastSlowAvg[i];
            if (pos != null)
            {
                posAvg = posAvg.offset(pos);
                amount++;
            }
        }

        posAvg = new BlockPos(posAvg.getX() / amount, 0, posAvg.getZ() / amount);

        if (BetterChunkLoading.config.getCommonConfig().debugLogging && !SectionPos.of(posAvg).equals(lastPosAvg))
        {
            BetterChunkLoading.LOGGER.info("Set lazy player chunkloading chunk position to: "+new ChunkPos(posAvg)+", player chunk pos:"+ player.chunkPosition());
        }

        lastPosAvg = SectionPos.of(posAvg);
    }

    /**
     * Get the players section pos for chunkloading
     * @param player
     * @return
     */
    public SectionPos getLastPosAvg(final ServerPlayer player)
    {
        if (lastPosAvg == null || !BetterChunkLoading.config.getCommonConfig().enableLazyChunkloading)
        {
            return SectionPos.of(player);
        }

        return lastPosAvg;
    }

    /**
     * Checks the predicted direction and ticket pre-loading
     *
     * @param player
     */
    private void checkPrediction(final ServerPlayer player)
    {
        lastSix[index] = new BlockPos(player.getBlockX(), 0, player.getBlockZ());
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

            final Vec3 direction = Vec3.atBottomCenterOf(avgOldest).subtract(Vec3.atBottomCenterOf(avgNewest)).reverse();
            Vec3 currentpos = Vec3.atBottomCenterOf(avgNewest);
            currentpos = currentpos.add(direction.scale((((ServerChunkCache) player.level.getChunkSource()).chunkMap.getDistanceManager().simulationDistance + BetterChunkLoading.config.getCommonConfig().predictiondidstanceoffset) / 3.0));

            // Current
            ChunkPos currentChunk = new ChunkPos((int) currentpos.x >> 4, (int) currentpos.z >> 4);

            if (lastChunkTicket.equals(currentChunk))
            {
                return;
            }

            if (BetterChunkLoading.config.getCommonConfig().debugLogging)
            {
                BetterChunkLoading.LOGGER.info("Set predictive loading position with area:"+BetterChunkLoading.config.getCommonConfig().predictionarea+" to chunk: "+currentChunk+" player chunk:"+player.chunkPosition());
            }

            ((ServerChunkCache) player.level.getChunkSource()).addRegionTicket(TICKET_1min,
              currentChunk,
              BetterChunkLoading.config.getCommonConfig().predictionarea,
              currentChunk);

            if (!lastChunkTicket.equals(ChunkPos.ZERO))
            {
                ((ServerChunkCache) player.level.getChunkSource()).removeRegionTicket(TICKET_1min, lastChunkTicket, lastChunkTicketLevel, currentChunk);
            }
            lastChunkTicket = currentChunk;
            lastChunkTicketLevel = BetterChunkLoading.config.getCommonConfig().predictionarea;
        }
    }
}
