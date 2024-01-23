package com.betterchunkloading.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class OptionsDevMixin
{
    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void getRenderDistance(final CallbackInfoReturnable<Integer> cir)
    {
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
        {
            cir.setReturnValue(32);
        }
    }
}
