package com.betterchunkloading;

import com.betterchunkloading.config.CommonConfiguration;
import com.betterchunkloading.event.EventHandler;
import com.cupboard.config.CupboardConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
public class BetterChunkLoading implements ModInitializer
{
    public static final String MOD_ID = "betterchunkloading";
    public static final Logger LOGGER = LogManager.getLogger();
    public static       Random rand   = new Random();

    public static CupboardConfig<CommonConfiguration> config = new CupboardConfig<>("betterchunkloading", new CommonConfiguration());

    public static final TicketType<ChunkPos> TICKET_POST_PROCESS      = TicketType.create("betterchunkloadingpostprocess", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 5);
    public static final TicketType<ChunkPos> TICKET_PREDICTION        = TicketType.create("betterchunkloadingprediction", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 1);
    public static final TicketType<ChunkPos> TICKET_PLAYER_CHUNK_AREA =
      TicketType.create("betterchunkloadingplayerchunk", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 20);

    public static boolean IN_DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

    public BetterChunkLoading()
    {
        ServerTickEvents.END_SERVER_TICK.register(EventHandler::onServerTick);
        CommandRegistrationCallback.EVENT.register((c, o, b) -> c.register(new Command().build()));
        ServerChunkEvents.CHUNK_LOAD.register(EventHandler::onChunkLoad);
        ServerChunkEvents.CHUNK_UNLOAD.register(EventHandler::onChunkUnLoad);
    }

    @Override
    public void onInitialize()
    {
        LOGGER.info(MOD_ID + " mod initialized");
    }
}
