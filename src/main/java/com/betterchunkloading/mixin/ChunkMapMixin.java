package com.betterchunkloading.mixin;

import com.betterchunkloading.IPlayerDataPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public class ChunkMapMixin
{
    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos getAdjustPosForPlayer(final EntityAccess player)
    {
        if (player instanceof IPlayerDataPlayer)
        {
            return ((IPlayerDataPlayer) player).betterchunkloading$getPlayerChunkData().getLastPosAvg((ServerPlayer) player);
        }

        return SectionPos.of(player);
    }
}
