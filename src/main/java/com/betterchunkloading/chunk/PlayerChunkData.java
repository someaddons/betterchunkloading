package com.betterchunkloading.chunk;

import com.betterchunkloading.BetterChunkLoading;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.betterchunkloading.BetterChunkLoading.*;

public class PlayerChunkData
{
    private static final ChunkPos INVALID = new ChunkPos(0, 0)
    {
        @Override
        public boolean equals(final Object other)
        {
            return other == INVALID;
        }
    };

    /**
     * Last chunk pos of the player
     */
    private ChunkPos lastChunk = INVALID;

    private ResourceKey<Level> lastLevel = null;

    /**
     * The tickets we issued
     */
    private Object2IntOpenHashMap<ChunkPos> lastTickets = new Object2IntOpenHashMap();

    /**
     * Tracking for the view area
     */
    private ChunkPos playerChunkLoadCenter       = null;
    private ChunkPos playerChunkLoadLastPos      = null;
    private int      playerChunkLoadViewDistance = 0;

    /**
     * Tracks player movement and speed
     */
    private BlockPos[] playerMovementTracker      = new BlockPos[6];
    private ChunkPos   playerMovementTrackerAvg   = INVALID;
    private int        playerMovementTrackerIndex = 0;

    private long     lastPlayerMovementUpdate = 0;
    private BlockPos lastPlayerPos            = null;
    private double   playerMovementSpeed      = 0;

    /**
     * Direction of the last 6 chunks, 3 + 3, max length = 3 chunks min = 0
     */
    private Vec3 direction = Vec3.ZERO;

    public boolean isFrozen = false;

    /**
     * Movement/Interval callback
     *
     * @param player
     */
    public void onChunkChanged(ServerPlayer player)
    {
        if (player == null || player.getClass() != ServerPlayer.class)
        {
            return;
        }

        if (!player.level().dimension().equals(lastLevel))
        {
            lastLevel = player.level().dimension();

            setPlayerViewDistTo(null, player.level().getServer().getLevel(lastLevel).getChunkSource(), 4);
            playerChunkLoadViewDistance = 4;
            playerChunkLoadCenter = null;

            playerMovementTracker = new BlockPos[6];
            playerMovementTrackerAvg = INVALID;
            playerMovementTrackerIndex = 0;
            lastPlayerMovementUpdate = 0;
            lastPlayerPos = null;
            playerMovementSpeed = 0;

            lastChunk = null;
        }

        if (lastChunk != null && player.chunkPosition().getChessboardDistance(lastChunk) > 10)
        {
            // Reset tracking
            setPlayerViewDistTo(player.chunkPosition(), (ServerChunkCache) player.level().getChunkSource(), 4);
            playerChunkLoadViewDistance = 4;
            playerChunkLoadCenter = null;

            playerMovementTracker = new BlockPos[6];
            playerMovementTrackerAvg = INVALID;
            playerMovementTrackerIndex = 0;
            lastPlayerMovementUpdate = 0;
            lastPlayerPos = null;
            playerMovementSpeed = 0;
        }

        if (player.chunkPosition().equals(lastChunk))
        {
            if ((System.currentTimeMillis() - lastPlayerMovementUpdate) > 5 * 1000)
            {
                trackPlayerMovement(player);
            }

            if (isFrozen)
            {
                if (player.level()
                  .hasChunk((int) (player.blockPosition().getX() + direction.normalize().multiply(32, 32, 32).x) >> 4,
                    (int) (player.blockPosition().getZ() + direction.normalize().multiply(32, 32, 32).z) >> 4))
                {
                    isFrozen = false;
                }
            }

            return;
        }

        trackPlayerMovement(player);
        lastChunk = player.chunkPosition();
    }

    /**
     * Tracks player movement, both location and speed indirectly
     *
     * @param player
     */
    private void trackPlayerMovement(final ServerPlayer player)
    {
        final long currentTime = System.currentTimeMillis();
        if (lastPlayerPos != null)
        {
            playerMovementSpeed -= playerMovementSpeed / 5;

            final int x = player.getBlockX() - lastPlayerPos.getX();
            final int z = player.getBlockZ() - lastPlayerPos.getZ();
            playerMovementSpeed += (Math.sqrt(x * x + z * z) / ((currentTime - lastPlayerMovementUpdate) / 1000.0)) / 5;
        }

        lastPlayerPos = player.blockPosition();
        lastPlayerMovementUpdate = currentTime;

        if (!player.chunkPosition().equals(lastChunk))
        {
            playerMovementTrackerIndex = (playerMovementTrackerIndex + 1) % playerMovementTracker.length;
            playerMovementTracker[playerMovementTrackerIndex] = player.blockPosition();

            int x = 0;
            int z = 0;
            int count = 0;

            for (int i = 0; i < playerMovementTracker.length; i++)
            {
                final BlockPos pos = playerMovementTracker[i];
                if (pos != null)
                {
                    count++;
                    x += pos.getX();
                    z += pos.getZ();
                }
            }

            final ChunkPos newPos = new ChunkPos((x / count) >> 4, (z / count) >> 4);

            playerMovementTrackerAvg = newPos;
            checkDirection(player);
        }

        chunkLoadForPlayer(player, playerMovementTrackerAvg);
    }

    /**
     * Calculates a movement vector from the tracked player positions, min lenght 0, max lenght 48 (6 chunks tracked)
     *
     * @return
     */
    private Vec3 calculatePlayerMovementVec(final ServerPlayer player)
    {
        int xOld = 0;
        int zOld = 0;
        int oldCounter = 0;

        int xNew = 0;
        int zNew = 0;
        int newCounter = 0;

        for (int i = 0; i < playerMovementTracker.length; i++)
        {
            final BlockPos pos = (playerMovementTracker[(playerMovementTrackerIndex + 1 + i) % playerMovementTracker.length]);
            if (pos == null)
            {
                continue;
            }

            if (i < playerMovementTracker.length / 2)
            {
                xOld += pos.getX();
                zOld += pos.getZ();
                oldCounter++;
            }
            else
            {
                xNew += pos.getX();
                zNew += pos.getZ();
                newCounter++;
            }
        }

        if (oldCounter == 0 || newCounter == 0)
        {
            return new Vec3(48, 0, 0);
        }

        xOld /= oldCounter;
        zOld /= oldCounter;
        xNew /= newCounter;
        zNew /= newCounter;

        return new Vec3(xNew - xOld, 0, zNew - zOld);
    }

    /**
     * Does chunkloading around the player
     *
     * @param player   player this is for
     * @param newChunk chunk position to load around, avg of player movement
     */
    private void chunkLoadForPlayer(final ServerPlayer player, final ChunkPos newChunk)
    {
        if (!config.getCommonConfig().enableSmartChunkLoading)
        {
            return;
        }

        final int viewDistance = calculateViewDistance(player);

        if (newChunk.equals(playerChunkLoadCenter))
        {
            if (viewDistance == playerChunkLoadViewDistance)
            {
                return;
            }
        }

        playerChunkLoadCenter = newChunk;
        setPlayerViewDistTo(playerChunkLoadCenter, ((ServerLevel) player.level()).getChunkSource(), viewDistance);
    }

    /**
     * Calculates the view distance based on player movement
     *
     * @param player
     * @return
     */
    private int calculateViewDistance(final ServerPlayer player)
    {
        final Vec3 playerMovement = calculatePlayerMovementVec(player).multiply(playerMovementSpeed / 4.3, 0, playerMovementSpeed / 4.3);
        final ServerChunkCache chunkSource = ((ServerLevel) player.level()).getChunkSource();

        // Normal movement: playermovement length: <=40, max creative flight speed length: 220
        // <= 40: chunkSource.chunkMap.viewDistance
        // (1.0 - (playermovement length - 40)/(80 * config.increaseformoreviewdistancewhilemoving)) * chunkSource.chunkMap.viewDistance
        // 120+: minmum view dist of 4

        int viewDistance = 4;
        if (playerMovement.length() < 120 * config.getCommonConfig().smartChunkLoadModifier)
        {
            if (playerMovement.length() <= 40 * config.getCommonConfig().smartChunkLoadModifier)
            {
                viewDistance = chunkSource.chunkMap.serverViewDistance;
            }
            else
            {
                viewDistance = (int) ((1.0 - (playerMovement.length() - 40) / (80 * config.getCommonConfig().smartChunkLoadModifier)) * chunkSource.chunkMap.serverViewDistance);
            }
        }

        return Math.max(5, viewDistance);
    }

    /**
     * Sets the player view distance area to the given position
     *
     * @param pos
     * @param chunkSource
     */
    private void setPlayerViewDistTo(final ChunkPos pos, ServerChunkCache chunkSource, int viewDistance)
    {
        if (!BetterChunkLoading.config.getCommonConfig().enableSmartChunkLoading)
        {
            return;
        }

        if (playerChunkLoadLastPos != null)
        {
            chunkSource.removeRegionTicket(TICKET_PLAYER_CHUNK_AREA,
              playerChunkLoadLastPos,
              playerChunkLoadViewDistance,
              playerChunkLoadLastPos);
            playerChunkLoadLastPos = null;
        }

        if (pos == null)
        {
            return;
        }

        playerChunkLoadViewDistance = viewDistance;
        playerChunkLoadLastPos = pos;
        chunkSource.addRegionTicket(TICKET_PLAYER_CHUNK_AREA, pos, viewDistance, pos);

        chunkSource.runDistanceManagerUpdates();

        if (BetterChunkLoading.config.getCommonConfig().debugLogging)
        {
            BetterChunkLoading.LOGGER.info("Set player chunkloading chunk position to: " + pos + " size:" + viewDistance);
        }
    }

    /**
     * On logout reset tickets
     *
     * @param player
     */
    public void onLogout(final ServerPlayer player)
    {
        setPlayerViewDistTo(null, ((ServerLevel) player.level()).getChunkSource(), 0);
    }

    /**
     * Checks the predicted direction and ticket pre-loading
     *
     * @param player
     */
    private void checkDirection(final ServerPlayer player)
    {
        direction = calculatePlayerMovementVec(player);
        Vec3 currentpos = player.position();

        if (BetterChunkLoading.config.getCommonConfig().enablePrediction)
        {
            checkPrediction(direction, currentpos, player);
        }

        if (!player.level()
          .hasChunk((int) (player.blockPosition().getX() + direction.normalize().multiply(32, 32, 32).x) >> 4,
            (int) (player.blockPosition().getZ() + direction.normalize().multiply(32, 32, 32).z) >> 4))
        {
            isFrozen = true;
        }
    }

    /**
     * Add chunk tickets in the predicted area
     *
     * @param direction
     * @param currentPos
     * @param player
     */
    private void checkPrediction(final Vec3 direction, final Vec3 currentPos, final ServerPlayer player)
    {
        final int viewDist =
          config.getCommonConfig().enableSmartChunkLoading ? playerChunkLoadViewDistance : ((ServerChunkCache) player.level().getChunkSource()).chunkMap.serverViewDistance;
        Vec3 predictedPos = currentPos.add(direction.normalize().scale(16 * (viewDist * 0.7)));

        for (int i = 0; i < 30 && !player.level().hasChunk((int) predictedPos.x >> 4, (int) predictedPos.z >> 4); i++)
        {
            predictedPos = predictedPos.add(direction.normalize().reverse().scale(16));
        }

        if (BetterChunkLoading.config.getCommonConfig().debugLogging)
        {
            final ChunkPos nextPredictedStartChunk = new ChunkPos((int) predictedPos.x >> 4, (int) predictedPos.z >> 4);
            BetterChunkLoading.LOGGER.info(
              "Set predictive loading position with area:" + Math.min(config.getCommonConfig().predictionarea, viewDist + 1) + " to chunk: " + nextPredictedStartChunk
                + " player chunk:"
                + player.chunkPosition());
        }

        final Object2IntOpenHashMap<ChunkPos> oldTickets = lastTickets;
        lastTickets = new Object2IntOpenHashMap<>();

        final int repetition = (int) (Math.abs(direction.x) + Math.abs(direction.z)) / 16;
        for (int i = 0; i < repetition; i++)
        {
            addpredictionChunkTicket(new ChunkPos(((int) predictedPos.x >> 4), ((int) predictedPos.z >> 4)),
              Math.min(config.getCommonConfig().predictionarea, viewDist + 1),
              ((ServerLevel) player.level()).getChunkSource());

            predictedPos = predictedPos.add(direction.normalize().scale(16));
        }

        for (final Object2IntMap.Entry<ChunkPos> ticketEntry : oldTickets.object2IntEntrySet())
        {
            ((ServerChunkCache) player.level().getChunkSource()).removeRegionTicket(TICKET_PREDICTION, ticketEntry.getKey(), ticketEntry.getIntValue(), ticketEntry.getKey());
        }

        ((ServerChunkCache) player.level().getChunkSource()).runDistanceManagerUpdates();
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
        chunkSource.addRegionTicket(TICKET_PREDICTION,
          pos,
          level,
          pos);
        lastTickets.put(pos, level);
    }
}
