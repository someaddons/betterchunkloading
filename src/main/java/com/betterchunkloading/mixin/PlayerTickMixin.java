package com.betterchunkloading.mixin;

import com.betterchunkloading.IPlayerDataPlayer;
import com.betterchunkloading.PlayerChunkData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerTickMixin extends Player implements IPlayerDataPlayer
{
    public PlayerTickMixin(
      final Level p_219727_,
      final BlockPos p_219728_,
      final float p_219729_,
      final GameProfile p_219730_,
      @Nullable final ProfilePublicKey p_219731_)
    {
        super(p_219727_, p_219728_, p_219729_, p_219730_, p_219731_);
    }

    @Dynamic
    @Shadow
    public abstract void die(final DamageSource p_9035_);

    @Override
    public PlayerChunkData betterchunkloading$getPlayerChunkData()
    {
        return playerChunkData;
    }

    @Unique
    private PlayerChunkData playerChunkData = new PlayerChunkData();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(final CallbackInfo ci)
    {
        // Exclude fake players and the likes
        if (((Object) this).getClass() == ServerPlayer.class)
        {
            playerChunkData.tick((ServerPlayer) (Object) this);
        }
    }
}
