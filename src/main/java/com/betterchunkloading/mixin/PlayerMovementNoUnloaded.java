package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IPlayerDataPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerMovementNoUnloaded
{
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onMove(final ServerboundMovePlayerPacket p_9874_, final CallbackInfo ci)
    {
        if (player instanceof IPlayerDataPlayer playerDataPlayer)
        {
            if (playerDataPlayer.betterchunkloading$getPlayerChunkData().isFrozen)
            {
                ci.cancel();
            }
        }
    }
}
