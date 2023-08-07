package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IDistanceManagerCleanup;
import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerRemoveListener extends LivingEntity
{
    protected PlayerRemoveListener(final EntityType<? extends LivingEntity> p_20966_, final Level p_20967_)
    {
        super(p_20966_, p_20967_);
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void onPlayerRemove(final Entity.RemovalReason p_150097_, final CallbackInfo ci)
    {
        if (this instanceof IPlayerDataPlayer)
        {
            final PlayerChunkData data = ((IPlayerDataPlayer) this).betterchunkloading$getPlayerChunkData();
            final ChunkPos lastPos = data.getLazyLoadingLastTicketPos();
            if (lastPos != null)
            {
                ((IDistanceManagerCleanup) ((ServerChunkCache) level.getChunkSource()).chunkMap.getDistanceManager()).betterchunkloading$cleanPlayer(data);
            }
        }
    }
}
