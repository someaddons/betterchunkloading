package com.betterchunkloading;

import com.betterchunkloading.config.CommonConfiguration;
import com.betterchunkloading.event.EventHandler;
import com.cupboard.config.CupboardConfig;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
    public static      CupboardConfig<CommonConfiguration> config = new CupboardConfig<>(MOD_ID, new CommonConfiguration());
    public static       Random                              rand   = new Random();

    public static final TicketType<ChunkPos> TICKET_2min = TicketType.create("betterchunkloading5min", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 2);
    public static final TicketType<ChunkPos> TICKET_1min = TicketType.create("betterchunkloading1min", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 1);


    public BetterChunkLoading()
    {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "", (a, b) -> true));
        Mod.EventBusSubscriber.Bus.FORGE.bus().get().register(EventHandler.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
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
