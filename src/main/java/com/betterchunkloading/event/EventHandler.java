package com.betterchunkloading.event;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.chunk.IPlayerDataPlayer;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.material.FluidState;

import java.util.*;

import static com.betterchunkloading.BetterChunkLoading.TICKET_POST_PROCESS;

public class EventHandler
{
    /**
     * Data storage for later post processing of chunk load data
     */
    public static ArrayDeque<ChunkInfo>    delayedLoading    = new ArrayDeque<>();
    public static Map<ChunkPos, ChunkInfo> delayedLoadingMap = new HashMap<>();

    public static void onServerTick(MinecraftServer server)
    {
        long serverTime = server.getTickCount();

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
                if (amount > 20)
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
                amount++;

                if (amount > 20)
                {
                    return;
                }
            }
            else
            {
                break;
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
                    }
                }
            }
        }

        ((ServerChunkCache) chunkInfo.level.getChunkSource()).distanceManager.removeTicket(TICKET_POST_PROCESS,
          chunkInfo.pos,
          33 - 1,
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

    public static void onPlayerTick(final ServerPlayer player)
    {
        if (player.tickCount % 3 == 0)
        {
            if (player instanceof IPlayerDataPlayer dataPlayer && player.getClass() == ServerPlayer.class)
            {
                dataPlayer.betterchunkloading$getPlayerChunkData().onChunkChanged((ServerPlayer) player);
            }
        }
    }

    public static void onPlayerLogout(final ServerPlayer player)
    {
        if (player instanceof IPlayerDataPlayer dataPlayer)
        {
            dataPlayer.betterchunkloading$getPlayerChunkData().onLogout(player);
        }
    }

    /**
     * Debugging Utils
     */

    static Map<ChunkPos, Integer> recentlyLoadedTimes   = new HashMap<>();
    static Map<ChunkPos, Integer> recentlyUnLoadedTimes = new HashMap<>();

    public static void onChunkLoad(final ServerLevel level, final LevelChunk chunk)
    {
        if (!BetterChunkLoading.IN_DEV)
        {
            return;
        }

        boolean sr = false;

        sr |= level.hasChunk(chunk.getPos().x + 1, chunk.getPos().z);
        sr |= level.hasChunk(chunk.getPos().x, chunk.getPos().z + 1);
        sr |= level.hasChunk(chunk.getPos().x - 1, chunk.getPos().z);
        sr |= level.hasChunk(chunk.getPos().x, chunk.getPos().z - 1);

        if (!sr)
        {
            BetterChunkLoading.LOGGER.warn("no surrounding chunk!");
        }

        recentlyLoadedTimes.put(chunk.getPos(), level.getServer().getTickCount());

        if (recentlyUnLoadedTimes.containsKey(chunk.getPos())
              && level.getServer().getTickCount() - recentlyUnLoadedTimes.get(chunk.getPos()) < 100)
        {
            BetterChunkLoading.LOGGER.warn("Loaded shortly after unload:" + chunk.getPos());
        }
    }

    public static void onChunkUnLoad(final ServerLevel level, final LevelChunk chunk)
    {
        if (!BetterChunkLoading.IN_DEV)
        {
            return;
        }

        recentlyUnLoadedTimes.put(chunk.getPos(), level.getServer().getTickCount());

        if (recentlyLoadedTimes.containsKey(chunk.getPos())
              && level.getServer().getTickCount() - recentlyLoadedTimes.get(chunk.getPos()) < 100)
        {
            BetterChunkLoading.LOGGER.warn("UnLoaded shortly after load:" + chunk.getPos());
        }
    }
}
