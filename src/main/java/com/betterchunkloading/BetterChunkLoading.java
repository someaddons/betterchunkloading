package com.betterchunkloading;

import com.betterchunkloading.config.CommonConfiguration;
import com.betterchunkloading.event.EventHandler;
import com.cupboard.config.CupboardConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
public class BetterChunkLoading implements ModInitializer {

    public static final String MOD_ID = "betterchunkloading";
    public static final Logger LOGGER = LogManager.getLogger();
    public static Random rand = new Random();
    public static       int                                 player_modifier = 1;

    public static final TicketType<ChunkPos> TICKET_2min = TicketType.create("betterchunkloading5min", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 2);
    public static final TicketType<ChunkPos> TICKET_1min = TicketType.create("betterchunkloading1min", Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 1);
    public static final TicketType<ChunkPos> TICKET_15s  = TicketType.create("betterchunkloading15s", Comparator.comparingLong(ChunkPos::toLong), 20 * 15 * 1);

    public BetterChunkLoading() {
        ServerTickEvents.END_SERVER_TICK.register(EventHandler::onServerTick);
        CommandRegistrationCallback.EVENT.register((c, o, b) -> c.register(new Command().build()));
    }

    @Override
    public void onInitialize() {
        LOGGER.info(MOD_ID + " mod initialized");
    }

    public static Vec3 rotateLeft(final Vec3 vec)
    {
        if (Math.abs(vec.x) > Math.abs(vec.z))
        {
            return new Vec3(-vec.z, vec.y, vec.x);
        }
        else
        {
            return new Vec3(vec.z, vec.y, -vec.x);
        }
    }

    public static Vec3 rotateRight(final Vec3 vec)
    {
        if (Math.abs(vec.x) > Math.abs(vec.z))
        {
            return new Vec3(vec.z, vec.y, -vec.x);
        }
        else
        {
            return new Vec3(-vec.z, vec.y, vec.x);
        }
    }
}
