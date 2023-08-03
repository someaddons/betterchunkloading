package com.betterchunkloading.mixin;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(DistanceManager.class)
public class DistanceManagerMixin
{
    @Inject(method = "removePlayer", at = @At(value = "INVOKE", target ="Lit/unimi/dsi/fastutil/objects/ObjectSet;remove(Ljava/lang/Object;)Z", remap = false), locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    private void onRemove(final SectionPos p_140829_, final ServerPlayer p_140830_, final CallbackInfo ci, ChunkPos chunkpos ,long i, ObjectSet<ServerPlayer> players)
    {
        if (players == null)
        {
            ci.cancel();
        }
    }
}
