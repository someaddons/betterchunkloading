package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public abstract class PlayerChunkDataMixin extends Player implements IPlayerDataPlayer
{
    public PlayerChunkDataMixin(
            Level level,
            BlockPos blockPos,
            float f,
            GameProfile gameProfile,
            @Nullable ProfilePublicKey profilePublicKey) {
        super(level, blockPos, f, gameProfile, profilePublicKey);
    }

    @Override
    public PlayerChunkData betterchunkloading$getPlayerChunkData()
    {
        return playerChunkData;
    }

    @Unique
    private PlayerChunkData playerChunkData = new PlayerChunkData();
}
