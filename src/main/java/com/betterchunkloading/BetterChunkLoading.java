package com.betterchunkloading;

import com.betterchunkloading.config.CommonConfiguration;
import com.betterchunkloading.event.EventHandler;
import com.cupboard.config.CupboardConfig;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.Random;

import static com.betterchunkloading.BetterChunkLoading.MOD_ID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MOD_ID)
public class BetterChunkLoading
{
    public static final String                              MOD_ID = "betterchunkloading";
    public static final Logger                              LOGGER = LogManager.getLogger();
    public static       CupboardConfig<CommonConfiguration> config = new CupboardConfig<>(MOD_ID, new CommonConfiguration());
    public static       Random                              rand   = new Random();

    public static final TicketType<ChunkPos> TICKET_POST_PROCESS      = TicketType.create("betterchunkloadingpostprocess", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 5);
    public static final TicketType<ChunkPos> TICKET_PREDICTION        = TicketType.create("betterchunkloadingprediction", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 1);
    public static final TicketType<ChunkPos> TICKET_PLAYER_CHUNK_AREA =
      TicketType.create("betterchunkloadingplayerchunk", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 20);

    public static boolean IN_DEV = !FMLEnvironment.production;

    public BetterChunkLoading(IEventBus modEventBus, ModContainer modContainer)
    {
        NeoForge.EVENT_BUS.register(EventHandler.class);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.addListener(this::commandRegister);
    }

    @SubscribeEvent
    public void commandRegister(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(new Command().build());
    }

    @SubscribeEvent
    public void clientSetup(FMLClientSetupEvent event)
    {
        // Side safe client event handler
        BetterChunkLoadingClient.onInitializeClient(event);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        LOGGER.info(MOD_ID + " mod initialized");
    }
}
