package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkMap.class)
public abstract class ChunkMapViewDistanceFixedTwo
{
    /**
     * View distance handled by vanilla is 4, we handle further tickets ourselves
     *
     * @param distance
     * @return
     */
    @ModifyArg(method = "setServerViewDistance", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;updatePlayerTickets(I)V"), index = 0, require = 0)
    private int onSetViewDistance(int distance)
    {
        if (BetterChunkLoading.config.getCommonConfig().enableSmartChunkLoading)
        {
            return 3;
        }
        else
        {
            return distance;
        }
    }
}
