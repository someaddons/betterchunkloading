package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class PlayerChunkDataMixin extends Player implements IPlayerDataPlayer
{
    @Shadow
    public abstract void setServerLevel(final ServerLevel p_284971_);

    public PlayerChunkDataMixin(final Level p_250508_, final BlockPos p_250289_, final float p_251702_, final GameProfile p_252153_)
    {
        super(p_250508_, p_250289_, p_251702_, p_252153_);
    }

    @Override
    public PlayerChunkData betterchunkloading$getPlayerChunkData()
    {
        return playerChunkData;
    }

    @Unique
    private PlayerChunkData playerChunkData = null;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(final MinecraftServer p_254143_, final ServerLevel p_254435_, final GameProfile p_253651_, final ClientInformation p_301997_, final CallbackInfo ci)
    {
        playerChunkData = new PlayerChunkData((ServerPlayer) (Object) this);
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void betterchunkloading$onrestore(final ServerPlayer old, final boolean p_9017_, final CallbackInfo ci)
    {
        playerChunkData = ((IPlayerDataPlayer) old).betterchunkloading$getPlayerChunkData();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V", ordinal = 0))
    private void onSameWorldTP(
        final ServerLevel p_9000_,
        final double x,
        final double y,
        final double z,
        final float p_9004_,
        final float p_9005_,
        final CallbackInfo ci)
    {
        playerChunkData.onChunkChanged((ServerPlayer) (Object) this, new ChunkPos(BlockPos.containing(x, y, z)));
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFFLjava/util/Set;)V"))
    private void onSameWorldTP2(
        final ServerLevel p_265564_,
        final double x,
        final double y,
        final double z,
        final Set<RelativeMovement> p_265192_,
        final float p_265059_,
        final float p_265266_,
        final CallbackInfoReturnable<Boolean> cir)
    {
        playerChunkData.onChunkChanged((ServerPlayer) (Object) this, new ChunkPos(BlockPos.containing(x, y, z)));
    }

    @Inject(method = "changeDimension", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void onDimensionTP(
        final DimensionTransition dimensionTransition, final CallbackInfoReturnable<Entity> cir)
    {
        playerChunkData.onChunkChanged((ServerPlayer) (Object) this, new ChunkPos(BlockPos.containing(dimensionTransition.pos())));
    }
}
