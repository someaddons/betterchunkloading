package com.betterchunkloading.mixin;

import net.minecraft.client.Options;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class OptionsDevMixin
{
    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
    private void getRenderDistance(final CallbackInfoReturnable<Integer> cir)
    {
        if (!FMLEnvironment.production)
        {
            cir.setReturnValue(32);
        }
    }
}
