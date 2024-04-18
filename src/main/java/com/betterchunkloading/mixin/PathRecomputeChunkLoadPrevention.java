package com.betterchunkloading.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(PathNavigation.class)
public class PathRecomputeChunkLoadPrevention
{
    @Shadow @Final protected Level level;

    @Shadow @Nullable private BlockPos targetPos;

    @Inject(method = "recomputePath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"), cancellable = true)
    private void checkLoaded(final CallbackInfo ci)
    {
        if (!level.hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4))
        {
            ci.cancel();
        }
    }
}
