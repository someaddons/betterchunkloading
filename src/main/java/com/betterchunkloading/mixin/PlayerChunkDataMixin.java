package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerChunkDataMixin extends Player implements IPlayerDataPlayer
{
    public PlayerChunkDataMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Override
    public PlayerChunkData betterchunkloading$getPlayerChunkData()
    {
        return playerChunkData;
    }

    @Unique
    private PlayerChunkData playerChunkData = new PlayerChunkData();

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void betterchunkloading$onrestore(final ServerPlayer old, final boolean p_9017_, final CallbackInfo ci)
    {
        playerChunkData = ((IPlayerDataPlayer) old).betterchunkloading$getPlayerChunkData();
    }
}
