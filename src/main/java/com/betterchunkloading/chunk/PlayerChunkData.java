package com.betterchunkloading.chunk;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.event.EventHandler;
import com.betterchunkloading.event.ITickingTask;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.betterchunkloading.BetterChunkLoading.LOGGER;
import static com.betterchunkloading.BetterChunkLoading.config;

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
     * Ticket types per player
     */
    private final TicketType<ChunkPos> chunkloadTicketType;
    private final TicketType<ChunkPos> predictionTicketType;

    public PlayerChunkData(final ServerPlayer player)
    {
        chunkloadTicketType = TicketType.create("bcl_player_" + player.getName().getString(), Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 20);
        predictionTicketType = TicketType.create("bcl_pred_player_" + player.getName().getString(), Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 1);
    }

    /**
     * Last chunk pos of the player
     */
    private ChunkPos lastChunk = INVALID;
    private ResourceKey<Level> lastLevel = null;

    /**
     * Tracking for the view area
     */
    private ChunkLoadingTask viewDistLoadTask            = null;
    private int              playerChunkLoadViewDistance = 0;

    /**
     * Chunk loading task for predicion area
     */
    ChunkLoadingTask predictionTask = null;

    /**
     * Tracks player movement and speed
     */
    private BlockPos[] playerMovementTracker      = new BlockPos[6];
    private int        playerMovementTrackerIndex = 0;

    private long     lastPlayerMovementUpdate = 0;
    private BlockPos lastPlayerPos            = null;
    private double   playerMovementSpeed      = 0;

    /**
     * Direction of the last 6 chunks, 3 + 3, max length = 3 chunks min = 0
     */
    private Vec3 direction = Vec3.ZERO;

    /**
     * Movement/Interval callback
     *
     * @param player
     */
    public void onChunkChanged(ServerPlayer player, ChunkPos chunkPos)
    {
        if (player == null || player.getClass() != ServerPlayer.class)
        {
            return;
        }

        if (chunkPos == null)
        {
            chunkPos = player.chunkPosition();
        }

        if (!player.level().dimension().equals(lastLevel))
        {
            if (lastLevel != null)
            {
                if (predictionTask != null)
                {
                    predictionTask.cancel();
                    predictionTask = null;
                }
            }

            // Low view distance for starting in new place
            updatePlayerViewDistance(chunkPos, (ServerChunkCache) player.level().getChunkSource(), 4, chunkloadTicketType);

            playerChunkLoadViewDistance = 4;

            playerMovementTracker = new BlockPos[6];
            playerMovementTrackerIndex = 0;
            lastPlayerMovementUpdate = 0;
            lastPlayerPos = null;
            playerMovementSpeed = 0;
            direction = Vec3.ZERO;

            lastLevel = player.level().dimension();
            lastChunk = null;
            return;
        }
        else if (lastChunk != null && chunkPos.getChessboardDistance(lastChunk) > 10)
        {
            // Reset tracking
            // Tickets are one task, as thus large tickets can stall the server for a while, e.g. when teleporting and a smaller ticket is issued -> less prio. Instead use small ticks and add them via ChunkLoadingTasks
            // Low view distance for starting in new place

            playerMovementTracker = new BlockPos[6];
            playerMovementTrackerIndex = 0;
            lastPlayerMovementUpdate = 0;
            lastPlayerPos = null;
            playerMovementSpeed = 0;
            direction = Vec3.ZERO;

            if (predictionTask != null)
            {
                predictionTask.cancel();
                predictionTask = null;
            }

            updatePlayerViewDistance(chunkPos, (ServerChunkCache) player.level().getChunkSource(), 4, chunkloadTicketType);

            lastLevel = player.level().dimension();
            lastChunk = null;
            /* Dont think this is needed on tp
            ((ServerChunkCache) player.level().getChunkSource()).distanceManager.playerTicketManager.runAllUpdates();
            ((ServerChunkCache) player.level().getChunkSource()).distanceManager.ticketTracker.runDistanceUpdates(Integer.MAX_VALUE);
            ((ServerChunkCache) player.level().getChunkSource()).distanceManager.runAllUpdates(((ServerChunkCache) player.level().getChunkSource()).chunkMap);
            */
            return;
        }

        if (chunkPos.equals(lastChunk))
        {
            if ((System.currentTimeMillis() - lastPlayerMovementUpdate) > 5 * 1000)
            {
                trackPlayerMovement(player);
            }

            return;
        }

        trackPlayerMovement(player);
        lastChunk = chunkPos;
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
            checkDirection(player);
        }

        doChunkLoadForPlayer(player, lastChunk);
    }

    /**
     * Calculates a movement vector from the tracked player positions, min lenght 0, max lenght 48 (6 chunks tracked)
     *
     * @return
     */
    private Vec3 calculatePlayerMovementVec()
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
     * @param player    player this is for
     * @param lastChunk chunk position to load around, avg of player movement
     */
    private void doChunkLoadForPlayer(final ServerPlayer player, final ChunkPos lastChunk)
    {
        if (!config.getCommonConfig().enableSmartChunkLoading)
        {
            return;
        }

        final int viewDistance = ((ServerChunkCache) player.level().getChunkSource()).chunkMap.serverViewDistance;

        if (player.chunkPosition().equals(lastChunk))
        {
            if (viewDistance == playerChunkLoadViewDistance)
            {
                return;
            }
        }
        updatePlayerViewDistance(player.chunkPosition(), ((ServerLevel) player.level()).getChunkSource(), viewDistance, chunkloadTicketType);
    }

    // TODO: For slower updating: Just compare distance of player to pos we're loading around and if its bigger than e.g. 3 chunks reset then players moving in small areas dont trigger anything,
    //  could make it relative to viewdist and scale with movementspeed? slow movement = lazier updating.
    //  May not be needed though, since the outer chunks load much slower.

    /**
     * Sets the player view distance area to the given position
     *
     * @param pos
     * @param chunkSource
     */
    private void updatePlayerViewDistance(final ChunkPos pos, ServerChunkCache chunkSource, int viewDistance, TicketType ticketType)
    {
        if (!BetterChunkLoading.config.getCommonConfig().enableSmartChunkLoading)
        {
            return;
        }
        if (pos == null)
        {
            if (viewDistLoadTask != null)
            {
                viewDistLoadTask.cancel();
            }
            viewDistLoadTask = null;
            return;
        }

        playerChunkLoadViewDistance = viewDistance;

        final ChunkPos predictionPos = new ChunkPos(pos.x + (int) (direction.normalize().multiply(3, 3, 3).x), pos.z + (int) (direction.normalize().multiply(3, 3, 3).z));

        List<ChunkTicketPos> toLoad = new ArrayList<>();
        for (int x = pos.x - viewDistance; x < pos.x + viewDistance; x++)
        {
            for (int z = pos.z - viewDistance; z < pos.z + viewDistance; z++)
            {
                var xDiff = pos.x - x;
                var zDiff = pos.z - z;

                var distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

                if (distance < viewDistance)
                {
                    var ticketPos = new ChunkTicketPos(new ChunkPos(x, z), ticketType, 2);

                    xDiff = predictionPos.x - x;
                    zDiff = predictionPos.z - z;

                    ticketPos.distanceToPlayer = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
                    ticketPos.ticking = true;
                    toLoad.add(ticketPos);
                }
            }
        }

        toLoad.sort(Comparator.comparingDouble(ChunkTicketPos::getDistanceToPlayer));

        ChunkLoadingTask newTask = new ChunkLoadingTask(pos, chunkSource, new ArrayDeque<>(toLoad));
        EventHandler.addTickingTask(chunkSource.getLevel().dimension(), newTask);

        checkExisting(chunkSource.level);
        if (viewDistLoadTask != null)
        {
            newTask.syncWithLastTask(viewDistLoadTask);
            // Delay cancel to after sync, so the chunk level does not change if not needed
            viewDistLoadTask.cancel();
        }

        newTask.loadSpeedModifier = 7 * config.getCommonConfig().smartChunkLoadingSpeed;
        viewDistLoadTask = newTask;
        checkExisting(chunkSource.level);

        if (BetterChunkLoading.config.getCommonConfig().debugLogging)
        {
            BetterChunkLoading.LOGGER.info("Set player chunkloading chunk position to: " + pos + " viewdist:" + viewDistance);
        }
    }

    /**
     * Prefer small gradual ticking updates with low ticket levels, similar to vanilla player chunkloading
     */
    private class ChunkLoadingTask implements ITickingTask
    {
        private final ChunkPos                      center;
        private       ServerChunkCache              chunkSource;
        private       Queue<ChunkTicketPos>         chunksToTicket;
        private       Map<ChunkPos, ChunkTicketPos> loadedChunks      = new HashMap<>();
        private       double                        loadSpeedModifier = 1.0;
        private       double                        cooldownCounter   = 0;

        private ChunkLoadingTask(final ChunkPos center, final ServerChunkCache chunkSource, final Queue<ChunkTicketPos> chunksToTicket)
        {
            this.center = center;
            this.chunkSource = chunkSource;
            this.chunksToTicket = chunksToTicket;
        }

        @Override
        public boolean tick()
        {
            if (chunksToTicket == null || chunksToTicket.isEmpty())
            {
                return true;
            }

            if (cooldownCounter > 1)
            {
                cooldownCounter--;
                return false;
            }


            // 100 MSTP -> 10 TPS -> 0.5
            // 40 MSTP -> 20 TPS -> 4
            // range: 60
            int clampedMSTP = Math.min(100, Math.max(EventHandler.MSTP, 40));
            double tpsMod = 1.0;
            if (clampedMSTP <= 40)
            {
                tpsMod = 4;
            }
            else if (clampedMSTP <= 60)
            {
                // 4 - 1
                tpsMod = (((clampedMSTP - 40) / 20.0) * -3.0) + 4;
            }
            else
            {
                // 1 - 0.2
                tpsMod = (((clampedMSTP - 60) / 40.0) * -0.8) + 1;
            }

            cooldownCounter += (loadedChunks.size() / (100 * loadSpeedModifier * tpsMod));
            while (!chunksToTicket.isEmpty())
            {
                // Note: If prediction chunks are lower ticket level, this will slow down the area a lot since we are re-ticketing the lower chunks to load to entity
                final ChunkTicketPos ticketToAdd = chunksToTicket.poll();
                SortedArraySet<Ticket<?>> ticketsAtPos = chunkSource.distanceManager.tickets.get(ticketToAdd.pos.toLong());
                Ticket<?> firstTicket = ticketsAtPos != null ? ticketsAtPos.first() : null;
                if (firstTicket != null && firstTicket.getTicketLevel() <= (ChunkLevel.byStatus(FullChunkStatus.FULL)) - 1)
                {
                    addTicketfor(ticketToAdd);
                    continue;
                }

                addTicketfor(ticketToAdd);
                break;
            }

            return chunksToTicket.isEmpty();
        }

        @Override
        public void cancel()
        {
            for (final ChunkTicketPos pos : loadedChunks.values())
            {
                removeTicketfor(pos);
            }

            chunksToTicket = null;
            loadedChunks = null;
            chunkSource = null;
        }

        /**
         * Re-adds tickets before they get removed for matching positions
         *
         * @param oldTask
         */
        private void syncWithLastTask(final ChunkLoadingTask oldTask)
        {
            for (Iterator<ChunkTicketPos> iterator = chunksToTicket.iterator(); iterator.hasNext(); )
            {
                final ChunkTicketPos ticketToAdd = iterator.next();

                final ChunkTicketPos oldPos = oldTask.loadedChunks.get(ticketToAdd.pos);
                if (ticketToAdd.equals(oldPos) && chunkSource == oldTask.chunkSource)
                {
                    SortedArraySet<Ticket<?>> ticketsAtPos = chunkSource.distanceManager.tickets.get(ticketToAdd.pos.toLong());

                    if (ticketsAtPos != null && !ticketsAtPos.isEmpty())
                    {
                        for (final Ticket ticket : ticketsAtPos)
                        {
                            if (ticket.getType() == ticketToAdd.type && ticket.getTicketLevel() == getTicketLevelForArea(ticketToAdd.ticketArea))
                            {
                                ticket.setCreatedTick(chunkSource.distanceManager.ticketTickCounter);
                                loadedChunks.put(oldPos.pos, ticketToAdd);
                                oldTask.loadedChunks.remove(oldPos.pos);
                                iterator.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }

        private void addTicketfor(final ChunkTicketPos ticketPos)
        {
            chunkSource.distanceManager.addTicket(ticketPos.type, ticketPos.pos, getTicketLevelForArea(ticketPos.ticketArea), ticketPos.pos);

            final ChunkTicketPos prev = loadedChunks.put(ticketPos.pos, ticketPos);
            if (prev != null && BetterChunkLoading.IN_DEV)
            {
                LOGGER.warn("Error, re-adding ticket twice!");
            }
        }

        private void removeTicketfor(final ChunkTicketPos ticketPos)
        {
            final SortedArraySet<Ticket<?>> ticketsAtPos = chunkSource.distanceManager.tickets.get(ticketPos.pos.toLong());
            if (ticketsAtPos != null && !ticketsAtPos.isEmpty())
            {
                if (chunkSource.getVisibleChunkIfPresent(ticketPos.pos.toLong()).getChunkIfPresent(ChunkStatus.FULL) == null)
                {
                    chunkSource.distanceManager.removeTicket(ticketPos.type, ticketPos.pos, getTicketLevelForArea(ticketPos.ticketArea), ticketPos.pos);
                    return;
                }

                int counter = 0;
                for (final Ticket ticket : ticketsAtPos)
                {
                    counter++;
                    if (ticket.getType() == ticketPos.type && ticket.getTicketLevel() == getTicketLevelForArea(ticketPos.ticketArea))
                    {
                        if (BetterChunkLoading.IN_DEV)
                        {
                            expiringTicketsMap.computeIfAbsent(ticketPos.pos.toLong(), t -> new HashSet<>()).add(ticket);
                        }
                        // Use the timer to unload one each tick, with a bit of delay giving the player a chance to refresh them.
                        ticket.setCreatedTick(chunkSource.distanceManager.ticketTickCounter - ticket.getType().timeout() + 30 + counter);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Region Tickets do not need this, they already do it by default.
     * We use nonregion tickets since they are not supposed to do simulation distance tickets
     *
     * @param ticketArea
     * @return
     */
    public static int getTicketLevelForArea(final int ticketArea)
    {
        return ChunkLevel.byStatus(FullChunkStatus.FULL) - ticketArea;
    }

    /**
     * On logout reset tickets
     *
     * @param player
     */
    public void onLogout(final ServerPlayer player)
    {
        updatePlayerViewDistance(null, ((ServerLevel) player.level()).getChunkSource(), 0, chunkloadTicketType);
        if (predictionTask != null)
        {
            predictionTask.cancel();
        }
    }

    /**
     * Checks the predicted direction and ticket pre-loading
     *
     * @param player
     */
    private void checkDirection(final ServerPlayer player)
    {
        direction = calculatePlayerMovementVec();
        Vec3 currentpos = player.position();

        if (BetterChunkLoading.config.getCommonConfig().enablePrediction)
        {
            checkPrediction(direction, currentpos, player);
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
        Vec3 predictedPos = currentPos.add(direction.normalize()
            .scale(16 * Math.max(3, ((ServerChunkCache) player.level().getChunkSource()).chunkMap.serverViewDistance - config.getCommonConfig().predictionarea * 2)));

        for (int i = 0; i < 30 && !player.level().hasChunk((int) predictedPos.x >> 4, (int) predictedPos.z >> 4); i++)
        {
            predictedPos = predictedPos.add(direction.normalize().reverse().scale(16));
        }

        if (BetterChunkLoading.config.getCommonConfig().debugLogging)
        {
            final ChunkPos nextPredictedStartChunk = new ChunkPos((int) predictedPos.x >> 4, (int) predictedPos.z >> 4);
            BetterChunkLoading.LOGGER.info(
                "Set predictive loading position with area:" + config.getCommonConfig().predictionarea + " to chunk: " + nextPredictedStartChunk
                    + " player chunk:"
                    + player.chunkPosition());
        }

        final ChunkPos center = new ChunkPos(((int) predictedPos.x >> 4), ((int) predictedPos.z >> 4));
        final ChunkPos predictionPos = new ChunkPos(player.chunkPosition().x + (int) (direction.normalize().multiply(3, 3, 3).x),
            player.chunkPosition().z + (int) (direction.normalize().multiply(3, 3, 3).z));
        final int areaRadius = config.getCommonConfig().predictionarea;

        List<ChunkTicketPos> toLoad = new ArrayList<>();
        for (int x = center.x - areaRadius; x < center.x + areaRadius; x++)
        {
            for (int z = center.z - areaRadius; z < center.z + areaRadius; z++)
            {
                var xDiff = center.x - x;
                var zDiff = center.z - z;

                var distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
                if (distance < areaRadius)
                {
                    var ticketPos = new ChunkTicketPos(new ChunkPos(x, z), predictionTicketType, 1);

                    // Skip places already loaded by the view distance
                    if (viewDistLoadTask != null)
                    {
                        final ChunkTicketPos chunkTicketPos = viewDistLoadTask.loadedChunks.get(ticketPos.pos);
                        if (ticketPos.equals(chunkTicketPos))
                        {
                            continue;
                        }
                    }

                    xDiff = predictionPos.x - x;
                    zDiff = predictionPos.z - z;

                    ticketPos.distanceToPlayer = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
                    toLoad.add(ticketPos);
                }
            }
        }

        toLoad.sort(Comparator.comparingDouble(ChunkTicketPos::getDistanceToPlayer));
        ChunkLoadingTask newTask = new ChunkLoadingTask(center, ((ServerLevel) player.level()).getChunkSource(), new ArrayDeque<>(toLoad));
        EventHandler.addTickingTask(player.level().dimension(), newTask);

        checkExisting(((ServerLevel) player.level()));
        if (predictionTask != null)
        {
            newTask.syncWithLastTask(predictionTask);

            // Delay cancel to after sync, so the chunk level does not change if not needed
            predictionTask.cancel();
        }
        predictionTask = newTask;
        predictionTask.loadSpeedModifier = 10 * config.getCommonConfig().predictionLoadingSpeed;
        checkExisting(((ServerLevel) player.level()));
        predictionTask.tick();
    }

    private static class ChunkTicketPos
    {
        private       double               distanceToPlayer = 100;
        private final ChunkPos             pos;
        private final TicketType<ChunkPos> type;
        private final int                  ticketArea;
        private       boolean              ticking          = false;

        ChunkTicketPos(ChunkPos pos, TicketType<ChunkPos> type, int ticketLevel)
        {
            this.pos = pos;
            this.type = type;
            this.ticketArea = ticketLevel;
        }

        public double getDistanceToPlayer()
        {
            return distanceToPlayer;
        }

        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }

            if (obj instanceof ChunkTicketPos ticketPos)
            {
                return ticketPos.ticketArea == ticketArea && ticketPos.pos.equals(pos) && ticketPos.type.equals(type) && ticketPos.ticking == ticking;
            }
            else
            {
                return false;
            }
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(pos, type, ticketArea, ticking);
        }
    }

    /**
     * Debug tracking, to make sure no tickets are lost
     */
    private static Long2ObjectOpenHashMap<Set<Ticket>> expiringTicketsMap = new Long2ObjectOpenHashMap();

    public void checkExisting(final ServerLevel level)
    {
        if (!BetterChunkLoading.IN_DEV || lastChunk == null)
        {
            return;
        }

        for (final Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> ticketEntry : level.getChunkSource().distanceManager.tickets.long2ObjectEntrySet())
        {
            final ChunkPos chunk = new ChunkPos(ticketEntry.getLongKey());
            final double distance = chunk.getChessboardDistance(lastChunk);
            for (final Ticket<?> ticket : ticketEntry.getValue())
            {
                if (ticket.getType() == chunkloadTicketType || ticket.getType() == predictionTicketType)
                {
                    boolean tracked = false;

                    if (predictionTask != null && ticket.getType() == predictionTicketType)
                    {
                        if (predictionTask.loadedChunks.get(chunk) != null)
                        {
                            if (distance > playerChunkLoadViewDistance * 3)
                            {
                                LOGGER.warn("Far distance ticket: " + chunk + " ticket:" + ticket);
                            }

                            tracked = true;
                        }
                    }

                    if (viewDistLoadTask != null && ticket.getType() == chunkloadTicketType)
                    {
                        if (viewDistLoadTask.loadedChunks.get(chunk) != null)
                        {
                            if (distance > playerChunkLoadViewDistance * 3)
                            {
                                LOGGER.warn("Far distance ticket: " + chunk + " ticket:" + ticket);
                            }

                            tracked = true;
                        }
                    }

                    if (!tracked)
                    {
                        if (expiringTicketsMap.containsKey(ticketEntry.getLongKey())
                            && ((level.getChunkSource().distanceManager.ticketTickCounter - ticket.createdTick) + 30 + 100) > ticket.getType().timeout())
                        {
                            tracked = true;
                        }
                    }

                    if (!tracked)
                    {
                        LOGGER.warn("Lost ticket reference at: " + chunk + " ticket:" + ticket);
                    }
                }
            }
        }
    }
}
