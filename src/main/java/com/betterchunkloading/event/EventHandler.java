package com.betterchunkloading.event;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.chunk.IPlayerDataPlayer;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.function.Predicate;

import static com.betterchunkloading.BetterChunkLoading.TICKET_POST_PROCESS;

public class EventHandler
{
    /**
     * Data storage for later post processing of chunk load data
     */
    private static ArrayDeque<ChunkInfo>    delayedLoading    = new ArrayDeque<>();
    private static  Map<ChunkPos, ChunkInfo> delayedLoadingMap = new HashMap<>();
    private static List<ChunkInfo> toadd = new ArrayList<>();

    private static int tickTimer = 0;
    public static int      MSTP         = 0;
    public volatile static ChunkPos loadingChunk = null;

    /**
     * Adds or queues to add a chunk info
     *
     * @param info
     */
    public static void addChunkToQueue(final ChunkInfo info)
    {
        if (info.level.getServer() != null && !info.level.getServer().isSameThread())
        {
            info.level.getServer().submit(() -> addChunkToQueue(info));
        }
        else
        {
            if (BetterChunkLoading.IN_DEV && EventHandler.delayedLoadingMap.containsKey(info.pos))
            {
                BetterChunkLoading.LOGGER.error("processing chunk twice!", new Exception());
            }
            toadd.add(info);
        }
    }

    @SubscribeEvent()
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            tickTimer++;
            if (tickTimer >= 40)
            {
                tickTimer = 0;
                 MSTP = (int) (average(event.getServer().tickTimes) * 1.0E-6D);
            }

            for(final ChunkInfo info: toadd)
            {
                delayedLoadingMap.put(info.pos, info);
                delayedLoading.offer(info);
            }

            if (!toadd.isEmpty())
            {
                toadd = new ArrayList<>();
            }

            long serverTime = event.getServer().getTickCount();

            int amount = 0;
            for (Iterator<ChunkInfo> iterator = delayedLoading.iterator(); iterator.hasNext(); )
            {
                final ChunkInfo chunkInfo = iterator.next();
                if (serverTime - chunkInfo.originalTime > 20
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x, chunkInfo.pos.z)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x + 1, chunkInfo.pos.z + 1)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x + 1, chunkInfo.pos.z)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x + 1, chunkInfo.pos.z - 1)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x, chunkInfo.pos.z + 1)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x, chunkInfo.pos.z - 1)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x - 1, chunkInfo.pos.z + 1)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x - 1, chunkInfo.pos.z)
                      && chunkInfo.level.hasChunk(chunkInfo.pos.x - 1, chunkInfo.pos.z - 1))
                {
                    applyToChunk(chunkInfo);
                    delayedLoadingMap.remove(chunkInfo.pos);
                    iterator.remove();

                    amount++;
                    if (amount > 10)
                    {
                        return;
                    }
                }
                else if (serverTime - chunkInfo.originalTime > 20 * 60)
                {
                    if (BetterChunkLoading.IN_DEV && ((ServerLevel) chunkInfo.level).getChunkSource().distanceManager.tickets.get(chunkInfo.pos.toLong()) == null)
                    {
                        BetterChunkLoading.LOGGER.warn("Missing ticket!!!");

                        ((ServerLevel) chunkInfo.level).getChunkSource().distanceManager.runAllUpdates(((ServerLevel) chunkInfo.level).getChunkSource().chunkMap);
                        if (((ServerLevel) chunkInfo.level).getChunkSource().distanceManager.tickets.get(chunkInfo.pos.toLong()) == null)
                        {
                            BetterChunkLoading.LOGGER.warn(
                              "Really! Missing ticket!!! time since ticket:" + (chunkInfo.level.getServer().getTickCount() - chunkInfo.originalTime));
                        }
                    }

                    applyToChunk(chunkInfo);
                    iterator.remove();
                    delayedLoadingMap.remove(chunkInfo.pos);
                    return;
                }
                else
                {
                    break;
                }
            }
        }
    }

    /**
     * Re-apply of postprocessing logic of level chunks, copy from vanilla
     *
     * @param chunkInfo
     */
    private static void applyToChunk(final ChunkInfo chunkInfo)
    {
        final LevelChunk chunk = chunkInfo.level.getChunk(chunkInfo.pos.x, chunkInfo.pos.z);
        for (int i = 0; i < chunkInfo.data.length; ++i)
        {
            if (chunkInfo.data[i] != null)
            {
                for (Short oshort : chunkInfo.data[i])
                {
                    BlockPos blockpos = ProtoChunk.unpackOffsetCoordinates(oshort, chunk.getSectionYFromSectionIndex(i), chunkInfo.pos);
                    BlockState blockstate = chunk.getBlockState(blockpos);
                    FluidState fluidstate = blockstate.getFluidState();
                    if (!fluidstate.isEmpty())
                    {
                        fluidstate.tick(chunkInfo.level, blockpos);
                    }

                    if (!(blockstate.getBlock() instanceof LiquidBlock))
                    {
                        BlockState blockstate1 = Block.updateFromNeighbourShapes(blockstate, chunkInfo.level, blockpos);
                        chunkInfo.level.setBlock(blockpos, blockstate1, 20);

                        final ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(blockpos, chunk.getBlockState(blockpos));
                        for(final ServerPlayer player: ((ServerChunkCache)chunkInfo.level.getChunkSource()).chunkMap.getPlayers(chunkInfo.pos, false))
                        {
                            player.connection.send(packet);
                        }
                    }
                }
            }
        }

        ((ServerChunkCache) chunkInfo.level.getChunkSource()).distanceManager.removeTicket(TICKET_POST_PROCESS,
          chunkInfo.pos,
          ChunkLevel.byStatus(FullChunkStatus.FULL) - 1,
          chunkInfo.pos);
    }

    /**
     * Data class for delayed post-processing
     */
    public static class ChunkInfo
    {
        private final long        originalTime;
        private final ChunkPos    pos;
        private final Level       level;
        private final ShortList[] data;

        public ChunkInfo(final long originalTime, final ChunkPos pos, final Level level, ShortList[] data)
        {
            this.originalTime = originalTime;
            this.pos = pos;
            this.level = level;
            this.data = data;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }
            final ChunkInfo chunkInfo = (ChunkInfo) o;
            return Objects.equals(pos, chunkInfo.pos);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(pos);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (!event.player.level().isClientSide && event.phase == TickEvent.Phase.END)
        {
            if (event.player.tickCount % 3 == 0)
            {
                if (event.player instanceof IPlayerDataPlayer dataPlayer && event.player.getClass() == ServerPlayer.class)
                {
                    dataPlayer.betterchunkloading$getPlayerChunkData().onChunkChanged((ServerPlayer) event.player, null);
                }
            }

            if (!BetterChunkLoading.IN_DEV)
            {
                return;
            }

            if (event.player.level().getGameTime() % (20 * 10) == 0)
            {
                BetterChunkLoading.LOGGER.warn("Loaded chunks: " + loadedChunks + " unloaded: " + unloadedChunks+" total:"+ event.player.level().getChunkSource().getLoadedChunksCount());
                loadedChunks = 0;
                unloadedChunks = 0;
            }
        }
    }

    private static int loadedChunks = 0;
    private static int unloadedChunks = 0;

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (!event.getEntity().level().isClientSide)
        {
            if (event.getEntity() instanceof IPlayerDataPlayer dataPlayer && event.getEntity() instanceof ServerPlayer)
            {
                dataPlayer.betterchunkloading$getPlayerChunkData().onLogout((ServerPlayer) event.getEntity());
            }
        }
        recentlyLoadedTimes = new HashMap<>();
        recentlyUnLoadedTimes = new HashMap<>();
    }

    /**
     * Debugging Utils
     */

    static Map<ChunkPos, Integer> recentlyLoadedTimes   = new HashMap<>();
    static Map<ChunkPos, Integer> recentlyUnLoadedTimes = new HashMap<>();

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event)
    {
        if (!BetterChunkLoading.IN_DEV)
        {
            return;
        }

        if (event.getLevel().isClientSide())
        {
            return;
        }

        loadedChunks++;
        if (loadingChunk != null)
        {
            BetterChunkLoading.LOGGER.warn("Loading chunk while stalled:" + event.getChunk().getPos());
            EventHandler.loadingChunk = null;
        }

        boolean sr = false;

        sr |= event.getLevel().hasChunk(event.getChunk().getPos().x + 1, event.getChunk().getPos().z);
        sr |= event.getLevel().hasChunk(event.getChunk().getPos().x, event.getChunk().getPos().z + 1);
        sr |= event.getLevel().hasChunk(event.getChunk().getPos().x - 1, event.getChunk().getPos().z);
        sr |= event.getLevel().hasChunk(event.getChunk().getPos().x, event.getChunk().getPos().z - 1);

        if (!sr)
        {
            BetterChunkLoading.LOGGER.warn("no surrounding chunk!");
        }

        Integer prev = recentlyLoadedTimes.put(event.getChunk().getPos(), event.getLevel().getServer().getTickCount());

        if (prev != null && event.getLevel().getServer().getTickCount() - prev < 100)
        {
            BetterChunkLoading.LOGGER.warn("Loaded shortly again:" + event.getChunk().getPos());
        }

        if (recentlyUnLoadedTimes.containsKey(event.getChunk().getPos())
              && event.getLevel().getServer().getTickCount() - recentlyUnLoadedTimes.get(event.getChunk().getPos()) < 100)
        {
            BetterChunkLoading.LOGGER.warn("Loaded shortly after unload:" + event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnLoad(final ChunkEvent.Unload event)
    {
        if (!BetterChunkLoading.IN_DEV)
        {
            return;
        }

        if (event.getLevel().isClientSide())
        {
            return;
        }

        unloadedChunks++;
        recentlyUnLoadedTimes.put(event.getChunk().getPos(), event.getLevel().getServer().getTickCount());

        if (recentlyLoadedTimes.containsKey(event.getChunk().getPos())
              && event.getLevel().getServer().getTickCount() - recentlyLoadedTimes.get(event.getChunk().getPos()) < 100)
        {
            BetterChunkLoading.LOGGER.warn("UnLoaded shortly after load:" + event.getChunk().getPos());
        }
    }

    private static Map<ResourceKey<Level>, List<ITickingTask>> taskMap = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(final TickEvent.LevelTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide())
        {
            var tasks = taskMap.get(event.level.dimension());
            if (tasks != null && !tasks.isEmpty())
            {
                for (Iterator<ITickingTask> iterator = tasks.iterator(); iterator.hasNext();)
                {
                    final ITickingTask task = iterator.next();
                    if (task.tick())
                    {
                        iterator.remove();
                    }
                }
            }
        }
    }

    public static void addTickingTask(final ResourceKey<Level> levelID, final ITickingTask task)
    {
        List<ITickingTask> taskList = taskMap.get(levelID);
        if (taskList == null)
        {
            taskList = new ArrayList<>();
        }

        taskList.add(task);
        taskMap.put(levelID, taskList);
    }

    public static void removeTickingTask(final ResourceKey<Level> levelID, Predicate<ITickingTask> matcher)
    {
        List<ITickingTask> taskList = taskMap.get(levelID);
        if (taskList == null)
        {
            return;
        }

        taskList.removeIf(matcher);
    }

    /**
     * Averages the arrays values.
     *
     * @param values
     * @return
     */
    private static long average(long[] values)
    {
        if (values == null || values.length == 0)
        {
            return 0L;
        }

        long sum = 0L;
        for (long v : values)
        {
            sum += v;
        }
        return sum / values.length;
    }
}
