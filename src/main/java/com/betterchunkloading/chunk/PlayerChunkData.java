package com.betterchunkloading.chunk;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.config.CommonConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.betterchunkloading.BetterChunkLoading.TICKET_1min;

public class PlayerChunkData {

    /**
     * Last chunk pos of the player
     */
    private ChunkPos lastChunk = ChunkPos.ZERO;

    private ResourceKey<Level> lastLevel = null;

    /**
     * Tracking for the last six chunks visited
     */
    private BlockPos[] predictionLastChunkpositions = new BlockPos[6];
    private int predictionIndex = 0;
    private ChunkPos predictionOldestPositionsAvg = ChunkPos.ZERO;
    private ChunkPos predictionNewestPositionsAvg = ChunkPos.ZERO;

    /**
     * Last predictive chunk ticket position and level
     */
    private ChunkPos lastChunkTicket = ChunkPos.ZERO;
    private int lastChunkTicketLevel = 0;

    /**
     * Tracking for the last slow average chunk pos
     */
    private BlockPos[] lazyLoadingLastChunkPositions = new BlockPos[6];
    private int lazyLoadingIndex = 0;
    private ChunkPos lazyLoadingAvgChunkpos = null;
    private ChunkPos lazyLoadingLastTicketPos = null;

    public void onChunkChanged(ServerPlayer player) {
        if (!player.level.dimension().equals(lastLevel)) {
            lastLevel = player.level.dimension();

            predictionLastChunkpositions = new BlockPos[6];
            predictionIndex = 0;
            predictionOldestPositionsAvg = ChunkPos.ZERO;
            predictionNewestPositionsAvg = ChunkPos.ZERO;

            lastChunkTicket = ChunkPos.ZERO;
            lastChunkTicketLevel = 0;

            lazyLoadingLastChunkPositions = new BlockPos[6];

            lastChunk = null;
        }


        if (player.chunkPosition().equals(lastChunk)) {
            return;
        }

        if (lastChunk != null && player.chunkPosition().getChessboardDistance(lastChunk) > 2) {
            // Reset prediction & slowavg regions
            predictionLastChunkpositions = new BlockPos[6];
            predictionIndex = 0;
            predictionOldestPositionsAvg = ChunkPos.ZERO;
            predictionNewestPositionsAvg = ChunkPos.ZERO;

            lazyLoadingLastChunkPositions = new BlockPos[6];
        }

        lastChunk = player.chunkPosition();

        if (CommonConfiguration.config.getCommonConfig().enableLazyChunkloading) {
            updateSlowAvgChunkPos(player);
        }

        if (CommonConfiguration.config.getCommonConfig().enablePrediction) {
            checkPrediction(player);
        }
    }

    /**
     * Updates the slow changing average chunkpos of the player
     *
     * @param player
     */
    private void updateSlowAvgChunkPos(final ServerPlayer player) {
        final int cacheSize =
                Math.max(1, (int) (((ServerChunkCache) player.level.getChunkSource()).chunkMap.viewDistance / CommonConfiguration.config.getCommonConfig().lazyloadingspeed));
        if (lazyLoadingLastChunkPositions.length != cacheSize) {
            BlockPos[] newArray = new BlockPos[cacheSize];
            for (int i = 0; i < Math.min(cacheSize, lazyLoadingLastChunkPositions.length); i++) {
                newArray[i] = lazyLoadingLastChunkPositions[i];
            }

            lazyLoadingLastChunkPositions = newArray;
            lazyLoadingIndex = lazyLoadingIndex % cacheSize;
        }

        lazyLoadingIndex = (lazyLoadingIndex + 1) % lazyLoadingLastChunkPositions.length;
        lazyLoadingLastChunkPositions[lazyLoadingIndex] = new BlockPos(player.getBlockX(), 0, player.getBlockZ());

        int amount = 0;
        BlockPos posAvg = BlockPos.ZERO;

        for (int i = 0; i < cacheSize; i++) {
            final BlockPos pos = lazyLoadingLastChunkPositions[i];
            if (pos != null) {
                posAvg = posAvg.offset(pos);
                amount++;
            }
        }

        posAvg = new BlockPos(posAvg.getX() / amount, 0, posAvg.getZ() / amount);

        if (CommonConfiguration.config.getCommonConfig().debugLogging && !(new ChunkPos(posAvg).equals(lazyLoadingAvgChunkpos))) {
            BetterChunkLoading.LOGGER.info("Set lazy player chunkloading chunk position to: " + new ChunkPos(posAvg) + ", player chunk pos:" + player.chunkPosition());
        }

        lazyLoadingAvgChunkpos = new ChunkPos(posAvg);
    }

    /**
     * Get the players section pos for chunkloading
     *
     * @return
     */
    public ChunkPos getSlowAvgPos() {
        return lazyLoadingAvgChunkpos;
    }

    public ChunkPos getLazyLoadingLastTicketPos() {
        return lazyLoadingLastTicketPos;
    }

    public void setLazyLoadingLastTicketPos(final ChunkPos lastChunk) {
        if (lastChunk != null && lazyLoadingLastTicketPos != null) {
            BetterChunkLoading.LOGGER.error("Did not unload previous position!", new Exception());
        }

        lazyLoadingLastTicketPos = lastChunk;
    }

    /**
     * Checks the predicted direction and ticket pre-loading
     *
     * @param player
     */
    private void checkPrediction(final ServerPlayer player) {
        predictionLastChunkpositions[predictionIndex] = new BlockPos(player.getBlockX(), 0, player.getBlockZ());
        predictionIndex = (predictionIndex + 1) % 6;

        BlockPos avgOldest = BlockPos.ZERO;
        for (int i = 0; i < 3; i++) {
            if (predictionLastChunkpositions[(predictionIndex + i) % 6] == null) {
                return;
            }
            avgOldest = avgOldest.offset(predictionLastChunkpositions[(predictionIndex + i) % 6]);
        }
        avgOldest = new BlockPos(avgOldest.getX() / 3, 0, avgOldest.getZ() / 3);

        BlockPos avgNewest = BlockPos.ZERO;
        for (int i = 3; i < 6; i++) {
            if (predictionLastChunkpositions[(predictionIndex + i) % 6] == null) {
                return;
            }
            avgNewest = avgNewest.offset(predictionLastChunkpositions[(predictionIndex + i) % 6]);
        }

        avgNewest = new BlockPos(avgNewest.getX() / 3, 0, avgNewest.getZ() / 3);

        ChunkPos newOldest = new ChunkPos(avgOldest);
        ChunkPos newNewest = new ChunkPos(avgNewest);

        // TODO: Test if lazy update is often enough for prediction loading too, to reduce frequency even more
        /* if (lazyLoadingAvgChunkpos != null && lazyLoadingAvgChunkpos.equals(lazyLoadingLastTicketPos))
        {
            return;
        }*/

        if (!newNewest.equals(predictionNewestPositionsAvg) || !newOldest.equals(predictionOldestPositionsAvg)) {
            predictionNewestPositionsAvg = newNewest;
            predictionOldestPositionsAvg = newOldest;

            final Vec3 direction = Vec3.atBottomCenterOf(avgOldest).subtract(Vec3.atBottomCenterOf(avgNewest)).reverse();
            Vec3 currentpos = Vec3.atBottomCenterOf(avgNewest);
            currentpos = currentpos.add(direction.scale((((ServerChunkCache) player.level.getChunkSource()).chunkMap.getDistanceManager().simulationDistance
                    + CommonConfiguration.config.getCommonConfig().predictiondidstanceoffset) / 3.0));

            // Current
            ChunkPos currentChunk = new ChunkPos((int) currentpos.x >> 4, (int) currentpos.z >> 4);

            if (lastChunkTicket.equals(currentChunk)) {
                return;
            }

            if (CommonConfiguration.config.getCommonConfig().debugLogging) {
                BetterChunkLoading.LOGGER.info(
                        "Set predictive loading position with area:" + CommonConfiguration.config.getCommonConfig().predictionarea + " to chunk: " + currentChunk + " player chunk:"
                                + player.chunkPosition());
            }

            ((ServerChunkCache) player.level.getChunkSource()).addRegionTicket(TICKET_1min,
                    currentChunk, CommonConfiguration.config.getCommonConfig().predictionarea,
                    currentChunk);

            if (!lastChunkTicket.equals(ChunkPos.ZERO)) {
                ((ServerChunkCache) player.level.getChunkSource()).removeRegionTicket(TICKET_1min, lastChunkTicket, lastChunkTicketLevel, lastChunkTicket);
            }
            lastChunkTicket = currentChunk;
            lastChunkTicketLevel = CommonConfiguration.config.getCommonConfig().predictionarea;
        }
    }
}
