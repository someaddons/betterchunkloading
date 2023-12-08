package com.betterchunkloading.event;

import com.betterchunkloading.BetterChunkLoading;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.betterchunkloading.BetterChunkLoading.TICKET_2min;

public class EventHandler
{
    public static Map<ChunkInfo, ShortList[]> delayedLoading = new ConcurrentHashMap<>();

    public static void printPlayerTickets(CommandSourceStack commandSourceStack)
    {
        for (final ServerLevel level : commandSourceStack.getServer().getAllLevels())
        {
            level.getChunkSource().distanceManager.runAllUpdates(level.getChunkSource().chunkMap);

            int playerTickets = 0;

            for (final Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry : level.getChunkSource().distanceManager.tickets.long2ObjectEntrySet())
            {
                for (final Ticket<?> ticket : entry.getValue())
                {
                    if (ticket != null && ticket.getType() == TicketType.PLAYER)
                    {
                        playerTickets++;
                    }
                }
            }

            commandSourceStack.sendSystemMessage(Component.literal("Dimension:" + level.dimension().location().toString()));
            commandSourceStack.sendSystemMessage(Component.literal("Player tickets(viewdistance):" + playerTickets));
            BetterChunkLoading.LOGGER.warn("Dimension:" + level.dimension().location().toString());
            BetterChunkLoading.LOGGER.warn("Player tickets(viewdistance):" + playerTickets);

            playerTickets = 0;
            for (final Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry : level.getChunkSource().distanceManager.tickingTicketsTracker.tickets.long2ObjectEntrySet())
            {
                for (final Ticket<?> ticket : entry.getValue())
                {
                    if (ticket != null && ticket.getType() == TicketType.PLAYER)
                    {
                        playerTickets++;
                    }
                }
            }

            commandSourceStack.sendSystemMessage(Component.literal("Player ticking(sim distance) tickets:" + playerTickets));
            BetterChunkLoading.LOGGER.warn("Player ticking(sim distance) tickets:" + playerTickets);
        }
    }

    @SubscribeEvent()
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            BetterChunkLoading.player_modifier = (int) (1 + event.getServer().getPlayerCount() * 0.3);
            if (event.getServer().getPlayerCount() == 0)
            {
                BetterChunkLoading.player_modifier = 0;
            }

            long serverTime = event.getServer().getTickCount();
            for (Iterator<Map.Entry<ChunkInfo, ShortList[]>> iterator = delayedLoading.entrySet().iterator(); iterator.hasNext(); )
            {
                final Map.Entry<ChunkInfo, ShortList[]> dataEntry = iterator.next();
                if (serverTime - dataEntry.getKey().originalTime > 20 * 5
                      && dataEntry.getKey().level.hasChunk(dataEntry.getKey().pos.x, dataEntry.getKey().pos.z)
                      && dataEntry.getKey().level.hasChunk(dataEntry.getKey().pos.x + 1, dataEntry.getKey().pos.z)
                      && dataEntry.getKey().level.hasChunk(dataEntry.getKey().pos.x, dataEntry.getKey().pos.z + 1)
                      && dataEntry.getKey().level.hasChunk(dataEntry.getKey().pos.x - 1, dataEntry.getKey().pos.z)
                      && dataEntry.getKey().level.hasChunk(dataEntry.getKey().pos.x - 1, dataEntry.getKey().pos.z - 1))
                {
                    applyToChunk(dataEntry);
                    iterator.remove();
                }

                if (serverTime - dataEntry.getKey().originalTime > 20 * 60 * 2)
                {
                    iterator.remove();
                    return;
                }
            }
        }
    }

    /**
     * Re-apply of postprocessing logic of level chunks
     *
     * @param dataEntry
     */
    private static void applyToChunk(final Map.Entry<ChunkInfo, ShortList[]> dataEntry)
    {
        final LevelChunk chunk = dataEntry.getKey().level.getChunk(dataEntry.getKey().pos.x, dataEntry.getKey().pos.z);
        for (int i = 0; i < dataEntry.getValue().length; ++i)
        {
            if (dataEntry.getValue()[i] != null)
            {
                for (Short oshort : dataEntry.getValue()[i])
                {
                    BlockPos blockpos = ProtoChunk.unpackOffsetCoordinates(oshort, chunk.getSectionYFromSectionIndex(i), dataEntry.getKey().pos);
                    BlockState blockstate = chunk.getBlockState(blockpos);
                    FluidState fluidstate = blockstate.getFluidState();
                    if (!fluidstate.isEmpty())
                    {
                        fluidstate.tick(dataEntry.getKey().level, blockpos);
                    }

                    if (!(blockstate.getBlock() instanceof LiquidBlock))
                    {
                        BlockState blockstate1 = Block.updateFromNeighbourShapes(blockstate, dataEntry.getKey().level, blockpos);
                        dataEntry.getKey().level.setBlock(blockpos, blockstate1, 20);
                    }
                }

                dataEntry.getValue()[i].clear();
            }
        }

        ((ServerChunkCache) dataEntry.getKey().level.getChunkSource()).removeRegionTicket(TICKET_2min,
          dataEntry.getKey().pos,
          1,
          dataEntry.getKey().pos);
    }

    public static class ChunkInfo
    {
        private final long     originalTime;
        private final ChunkPos pos;
        private final Level    level;

        public ChunkInfo(final long originalTime, final ChunkPos pos, final Level level)
        {
            this.originalTime = originalTime;
            this.pos = pos;
            this.level = level;
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
}
