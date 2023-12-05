package com.betterchunkloading;

import com.betterchunkloading.event.EventHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.betterchunkloading.BetterChunkLoading.TICKET_15s;

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
                  EventHandler.printPlayerTickets(context.getSource());
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
              .then(Commands.argument("distance", BlockPosArgument.blockPos())
                .executes(context ->
                {
                    final BlockPos viewDist = context.getArgument("distance", Coordinates.class).getBlockPos(context.getSource());

                    ChunkPos currentChunk = new ChunkPos(viewDist.getX() >> 4, viewDist.getZ() >> 4);
                    final RegionFileStorage storage = ((IOWorker) context.getSource().getLevel().getChunkSource().chunkMap.chunkScanner()).storage;
                    CompletableFuture<List<ChunkPos>> future = ((IOWorker) context.getSource().getLevel().getChunkSource().chunkMap.chunkScanner()).submitTask(() -> {

                        List<ChunkPos> missing = new ArrayList<>();
                        for (int i = -10; i < 10; i++)
                        {
                            for (int j = -10; j < 10; j++)
                            {
                                if (Math.sqrt(i * i + j * j) > 10)
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
                            ((ServerChunkCache) context.getSource().getLevel().getChunkSource()).addRegionTicket(TICKET_15s,
                              pos,
                              0,
                              pos);
                        }

                        BetterChunkLoading.LOGGER.warn("Adding tickets to:" + list.size() + " chunks");
                        return null;
                    }, context.getSource().getServer());

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
}
