package com.betterchunkloading.chunk;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.config.CommonConfiguration;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.betterchunkloading.BetterChunkLoading.TICKET_15s;
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
     * The tickets we issued
     */
    private Object2IntOpenHashMap<ChunkPos> lastTickets = new Object2IntOpenHashMap();

    /**
     * Tracking for the last slow average chunk pos
     */
    private BlockPos[] lazyLoadingLastChunkPositions = new BlockPos[6];
    private int lazyLoadingIndex = 0;
    private ChunkPos lazyLoadingAvgChunkpos = null;
    private ChunkPos lazyLoadingLastTicketPos = null;

    public void onChunkChanged(ServerPlayer player) {
        if (!player.level().dimension().equals(lastLevel)) {
            lastLevel = player.level().dimension();

            predictionLastChunkpositions = new BlockPos[6];
            predictionIndex = 0;
            predictionOldestPositionsAvg = ChunkPos.ZERO;
            predictionNewestPositionsAvg = ChunkPos.ZERO;

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

        checkDirection(player);
    }

    /**
     * Updates the slow changing average chunkpos of the player
     *
     * @param player
     */
    private void updateSlowAvgChunkPos(final ServerPlayer player) {
        final int cacheSize =
                Math.max(1, (int) (((ServerChunkCache) player.level().getChunkSource()).chunkMap.viewDistance / CommonConfiguration.config.getCommonConfig().lazyloadingspeed));
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
    private void checkDirection(final ServerPlayer player)
    {
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

        if (!newNewest.equals(predictionNewestPositionsAvg) || !newOldest.equals(predictionOldestPositionsAvg))
        {
            predictionNewestPositionsAvg = newNewest;
            predictionOldestPositionsAvg = newOldest;

            final Vec3 direction = Vec3.atBottomCenterOf(avgOldest).subtract(Vec3.atBottomCenterOf(avgNewest)).reverse();
            Vec3 currentpos = player.position();

            if (CommonConfiguration.config.getCommonConfig().enablePrediction)
            {
                checkPrediction(direction, currentpos, player);
            }

            if (CommonConfiguration.config.getCommonConfig().enablePreGen)
            {
                checkPregen(direction, currentpos, player);
            }
        }
    }

    List<BlockPos> goldPos = new ArrayList<>();

    /**
     * Add chunk tickets in the predicted area
     *
     * @param direction
     * @param currentPos
     * @param player
     */
    private void checkPrediction(final Vec3 direction, final Vec3 currentPos, final ServerPlayer player)
    {
        Vec3 predictedPos;
        if (lazyLoadingLastTicketPos != null)
        {
            predictedPos = Vec3.atBottomCenterOf(lazyLoadingLastTicketPos.getWorldPosition());
            predictedPos = predictedPos.add(direction.normalize().scale(16 * ((ServerChunkCache) player.level().getChunkSource()).chunkMap.viewDistance));
        }
        else
        {
            final double distFromPlayer =
              (((ServerChunkCache) player.level().getChunkSource()).chunkMap.viewDistance - 2)
                / (CommonConfiguration.config.getCommonConfig().enableLazyChunkloading ? 3.0 : 1.0) * 16;
            predictedPos = currentPos.add(direction.normalize().scale(distFromPlayer));
        }

        Vec3 previousChunk = predictedPos.add(direction.normalize().reverse().scale(16));

        for (int i = 0; i < 20 && !player.level().hasChunk((int) previousChunk.x >> 4, (int) previousChunk.z >> 4); i++)
        {
            previousChunk = previousChunk.add(direction.normalize().reverse().scale(16));
        }

        predictedPos = previousChunk;

        if (CommonConfiguration.config.getCommonConfig().debugLogging)
        {
            final ChunkPos nextPredictedStartChunk = new ChunkPos((int) predictedPos.x >> 4, (int) predictedPos.z >> 4);
            BetterChunkLoading.LOGGER.info(
              "Set predictive loading position with area:" + CommonConfiguration.config.getCommonConfig().predictionarea + " to chunk: " + nextPredictedStartChunk + " player chunk:"
                + player.chunkPosition());
        }

        for (final BlockPos oldGold : goldPos)
        {
            player.level().setBlock(oldGold, Blocks.AIR.defaultBlockState(), 3);
        }

        goldPos = new ArrayList<>();
        final ServerChunkCache chunkSource = ((ServerLevel) player.level()).getChunkSource();
        final Object2IntOpenHashMap<ChunkPos> oldTickets = lastTickets;
        lastTickets = new Object2IntOpenHashMap<>();

        int repetition = (int) (Math.abs(direction.x) + Math.abs(direction.z)) / 16;

        // Forward tickets

        Vec3 forwardPredictedPos = predictedPos;

        for (int ticketArea = CommonConfiguration.config.getCommonConfig().predictionarea; ticketArea > 0; ticketArea = ticketArea - 1)
        {
            for (int i = 0; i < repetition; i++)
            {
                final ChunkPos nextPredictedStartChunk = new ChunkPos((int) forwardPredictedPos.x >> 4, (int) forwardPredictedPos.z >> 4);
                addpredictionChunkTicket(nextPredictedStartChunk, ticketArea, chunkSource);
                forwardPredictedPos = forwardPredictedPos.add(direction.normalize().scale(16));

                /*
                for (int area = ticketArea; area > 0; area--)
                {
                    goldPos.add(nextPredictedStartChunk.getWorldPosition().atY(player.getBlockY() - area));
                    player.level().setBlock(nextPredictedStartChunk.getWorldPosition().atY(player.getBlockY() - area), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                }
                 */
            }

            forwardPredictedPos = forwardPredictedPos.add(direction.normalize().scale(16));
        }

        // Sideways tickets
        for (int ticketArea = 1; ticketArea < CommonConfiguration.config.getCommonConfig().predictionarea; ticketArea = ticketArea + 1)
        {
            final Vec3 rotated1 = predictedPos.add(BetterChunkLoading.rotateLeft(direction.normalize()).scale(16 * ticketArea * 2));
            final Vec3 rotated2 = predictedPos.add(BetterChunkLoading.rotateRight(direction.normalize()).scale(16 * ticketArea * 2));

            final ChunkPos leftChunk = new ChunkPos((int) rotated1.x >> 4, (int) rotated1.z >> 4);
            final ChunkPos rightChunk = new ChunkPos((int) rotated2.x >> 4, (int) rotated2.z >> 4);
            addpredictionChunkTicket(leftChunk, CommonConfiguration.config.getCommonConfig().predictionarea - ticketArea, chunkSource);
            addpredictionChunkTicket(rightChunk, CommonConfiguration.config.getCommonConfig().predictionarea - ticketArea, chunkSource);

            /*
            for (int i = BetterChunkLoading.config.getCommonConfig().predictionarea - ticketArea; i > 0; i--)
            {
                goldPos.add(leftChunk.getWorldPosition().atY(player.getBlockY() - i));
                goldPos.add(rightChunk.getWorldPosition().atY(player.getBlockY() - i));
                player.level().setBlock(leftChunk.getWorldPosition().atY(player.getBlockY() - i), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                player.level().setBlock(rightChunk.getWorldPosition().atY(player.getBlockY() - i), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            }*/
        }

        for (final Object2IntMap.Entry<ChunkPos> ticketEntry : oldTickets.object2IntEntrySet())
        {
            chunkSource.removeRegionTicket(TICKET_1min, ticketEntry.getKey(), ticketEntry.getIntValue(), ticketEntry.getKey());
        }
    }

    /**
     * Adds a chunk ticket
     *
     * @param pos
     * @param level
     * @param chunkSource
     */
    private void addpredictionChunkTicket(final ChunkPos pos, final int level, ServerChunkCache chunkSource)
    {
        chunkSource.addRegionTicket(TICKET_1min,
          pos,
          level,
          pos);
        lastTickets.put(pos, level);
    }

    /**
     * Checks the predicted area further ahead for chunk pregeneration
     *
     * @param direction
     * @param currentPos
     * @param player
     */
    private void checkPregen(final Vec3 direction, final Vec3 currentPos, final ServerPlayer player)
    {
        int repetition = (int) (Math.abs(direction.x) + Math.abs(direction.z)) / 16;
        int predictionDistance = CommonConfiguration.config.getCommonConfig().predictionarea * (repetition + 1) - 2;
        final int pregenSize = CommonConfiguration.config.getCommonConfig().preGenArea;

        Vec3 predictedPos;
        if (lazyLoadingLastTicketPos != null)
        {
            predictedPos = Vec3.atBottomCenterOf(lazyLoadingLastTicketPos.getWorldPosition());
            predictedPos = predictedPos.add(direction.normalize().scale(16 * (((ServerChunkCache) player.level().getChunkSource()).chunkMap.viewDistance + predictionDistance + pregenSize)));
        }
        else
        {
            final double distFromPlayer =
              ((((ServerChunkCache) player.level().getChunkSource()).chunkMap.viewDistance - 2) / (CommonConfiguration.config.getCommonConfig().enableLazyChunkloading ? 3.0 : 1.0)
                + predictionDistance) * 16;
            predictedPos = currentPos.add(direction.normalize().scale(distFromPlayer));
        }

        ServerChunkCache chunkSource = ((ServerLevel) player.level()).getChunkSource();
        final ChunkPos predictedChunk = new ChunkPos((int) predictedPos.x >> 4, (int) predictedPos.z >> 4);
        final BlockPos playerPos = player.blockPosition();
        final int playerPredictionDistance = (int) Math.sqrt(predictedChunk.getWorldPosition().distSqr(playerPos));

        final RegionFileStorage storage = ((IOWorker) chunkSource.chunkMap.chunkScanner()).storage;
        CompletableFuture<List<ChunkPos>> future = ((IOWorker) chunkSource.chunkMap.chunkScanner()).submitTask(() -> {
            List<ChunkPos> missing = new ArrayList<>();
            for (int i = -pregenSize; i < pregenSize; i++)
            {
                for (int j = -pregenSize; j < pregenSize; j++)
                {
                    ChunkPos current = new ChunkPos(predictedChunk.x + i, predictedChunk.z + j);

                    if (Math.sqrt(i * i + j * j) > pregenSize)
                    {
                        continue;
                    }

                    if (Math.sqrt(current.getWorldPosition().distSqr(playerPos)) < playerPredictionDistance)
                    {
                        continue;
                    }

                    try
                    {
                        if (!storage.getRegionFile(current).hasChunk(current))
                        {
                            missing.add(current);
                        }
                    }
                    catch (IOException e)
                    {
                        missing.add(current);
                    }
                }
            }
            return Either.left(missing);
        });
        future.thenApplyAsync(list -> {
            for (int i = 0; i < list.size() && i < 20; i++)
            {
                final ChunkPos pos = list.get(i);
                chunkSource.distanceManager.addTicket(TICKET_15s,
                  pos,
                  ChunkLevel.byStatus(FullChunkStatus.FULL),
                  pos);
            }

            if (CommonConfiguration.config.getCommonConfig().debugLogging)
            {
                BetterChunkLoading.LOGGER.warn("Preloading " + (Math.min(list.size(), 20)) + " chunks around:" + predictedChunk);
            }
            return null;
        }, player.level().getServer());
    }
}
