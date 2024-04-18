package com.betterchunkloading.mixin;

import com.betterchunkloading.event.EventHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class PlayerTickEvent
{
    @Inject(method = "tick", at = @At("RETURN"))
    private void onPlayerTick(final CallbackInfo ci)
    {
        EventHandler.onPlayerTick((ServerPlayer) (Object) this);
    }
}
