package com.betterchunkloading;

import com.betterchunkloading.event.EventHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

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
