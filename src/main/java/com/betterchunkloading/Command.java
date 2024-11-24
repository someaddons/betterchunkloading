package com.betterchunkloading;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Command
{
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return Commands.literal(BetterChunkLoading.MOD_ID)
          .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
          .then(
            Commands.literal("printPlayerTicks")
              .executes(context ->
              {
                  printPlayerTickets(context.getSource());
                  return 1;
              }))
          .then(
            Commands.literal("setviewdist")
              .then(Commands.argument("distance", IntegerArgumentType.integer())
                .executes(context ->
                {
                    final int viewDist = IntegerArgumentType.getInteger(context, "distance");
                    context.getSource().getLevel().getChunkSource().setViewDistance(viewDist);
                    return 1;
                })))
          .then(
            Commands.literal("genChunkAt")
              .then(Commands.argument("position", BlockPosArgument.blockPos())
                .executes(context ->
                {
                    final BlockPos viewDist = BlockPosArgument.getBlockPos(context, "position");

                    ChunkPos currentChunk = new ChunkPos(viewDist.getX() >> 4, viewDist.getZ() >> 4);
                    final RegionFileStorage storage = ((IOWorker) context.getSource().getLevel().getChunkSource().chunkMap.chunkScanner()).storage;
                    CompletableFuture<List<ChunkPos>> future = ((IOWorker) context.getSource().getLevel().getChunkSource().chunkMap.chunkScanner()).submitTask(() -> {

                        List<ChunkPos> missing = new ArrayList<>();
                        for (int i = -10; i < 10; i++)
                        {
                            for (int j = -10; j < 10; j++)
                            {
                                if (Math.sqrt(i * i + j * j) > 20)
                                {
                                    continue;
                                }

                                ChunkPos current = new ChunkPos(currentChunk.x + i, currentChunk.z + j);
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
                        for (final ChunkPos pos : list)
                        {
                            ((ServerChunkCache) context.getSource().getLevel().getChunkSource()).chunkMap.getDistanceManager().addTicket(TicketType.UNKNOWN,
                              pos,
                              ChunkLevel.byStatus(ChunkStatus.FULL),
                              pos);
                        }

                        BetterChunkLoading.LOGGER.warn("Adding tickets to:" + list.size() + " chunks");
                        ((ServerChunkCache) context.getSource().getLevel().getChunkSource()).runDistanceManagerUpdates();
                        return null;
                    }, context.getSource().getServer());

                    return 1;
                })))
          .then(
            Commands.literal("loadchunk")
              .then(Commands.argument("position", BlockPosArgument.blockPos())
                .executes(context ->
                {
                    ChunkPos chunkPos = new ChunkPos(BlockPosArgument.getBlockPos(context, "position"));
                    context.getSource().getLevel().getChunk(chunkPos.x,chunkPos.z);
                    return 1;
                })))
          .then(
            Commands.literal("setsimdist")
              .then(Commands.argument("distance", IntegerArgumentType.integer())
                .executes(context ->
                {
                    final int viewDist = IntegerArgumentType.getInteger(context, "distance");
                    context.getSource().getLevel().getChunkSource().setSimulationDistance(viewDist);
                    return 1;
                })));
    }

    /**
     * Util to print player tickets
     */
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
}
