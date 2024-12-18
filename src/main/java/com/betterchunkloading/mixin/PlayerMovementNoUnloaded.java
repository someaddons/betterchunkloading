package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerMovementNoUnloaded
{
    @Shadow
    public ServerPlayer player;

    @Shadow
    private double firstGoodX;

    @Shadow
    private double firstGoodZ;

    @Shadow
    private static double clampHorizontal(final double p_143610_)
    {
        return 0;
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onMove(final ServerboundMovePlayerPacket packet, final CallbackInfo ci)
    {
        if (!BetterChunkLoading.config.getCommonConfig().preventWalkUnloaded)
        {
            return;
        }

        double clampedPacketX = clampHorizontal(packet.getX(this.player.getX()));
        double clampedPacketZ = clampHorizontal(packet.getZ(this.player.getZ()));

        double xDiff = Math.max(-16, Math.min(16, (clampedPacketX - this.firstGoodX) * 4));
        double zDiff = Math.max(-16, Math.min(16, (clampedPacketZ - this.firstGoodZ) * 4));

        final int projectedChunkX = Mth.floor(player.getX() + xDiff) >> 4;
        final int projectedChunkZ = Mth.floor(player.getZ() + zDiff) >> 4;

        if (projectedChunkX != player.chunkPosition().x || projectedChunkZ != player.chunkPosition().z)
        {
            if (!player.level().hasChunk(projectedChunkX, projectedChunkZ))
            {
                if (BetterChunkLoading.config.getCommonConfig().debugLogging)
                {
                    BetterChunkLoading.LOGGER.warn(
                        "Preventing player movement into unloaded chunk for:"+player+"! xDiff:" + xDiff + " zdiff:" + zDiff);
                }
                ci.cancel();
            }
        }
    }
}
