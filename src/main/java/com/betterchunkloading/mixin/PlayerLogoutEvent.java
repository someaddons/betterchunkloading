package com.betterchunkloading.mixin;

import com.betterchunkloading.event.EventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerLogoutEvent
{
    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLogout(final ServerPlayer serverPlayer, final CallbackInfo ci)
    {
        EventHandler.onPlayerLogout(serverPlayer);
    }
}
