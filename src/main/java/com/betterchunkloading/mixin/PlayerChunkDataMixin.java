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
      final Level p_219727_,
      final BlockPos p_219728_,
      final float p_219729_,
      final GameProfile p_219730_,
      @Nullable final ProfilePublicKey p_219731_)
    {
        super(p_219727_, p_219728_, p_219729_, p_219730_, p_219731_);
    }

    @Override
    public PlayerChunkData betterchunkloading$getPlayerChunkData()
    {
        return playerChunkData;
    }

    @Unique
    private PlayerChunkData playerChunkData = new PlayerChunkData();
}
