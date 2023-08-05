package com.betterchunkloading.mixin;

import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin
{
    @Shadow
    @Final
    private DistanceManager.PlayerTicketTracker playerTicketManager;

    @Shadow
    @Final
    private TickingTracker tickingTicketsTracker;

    @Shadow
    protected abstract int getPlayerTicketLevel();

    @Inject(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"), cancellable = true)
    private void onAddPlayerToChunkSection(final SectionPos chunkSection, final ServerPlayer player, final CallbackInfo ci)
    {
        if (!(player instanceof IPlayerDataPlayer))
        {
            return;
        }

        ci.cancel();

        PlayerChunkData data = ((IPlayerDataPlayer) player).betterchunkloading$getPlayerChunkData();
        data.onChunkChanged(player);

        ChunkPos newTicketPos = data.getSlowAvgPos();
        if (newTicketPos != null && newTicketPos.equals(data.getLazyLoadingLastTicketPos()))
        {
            return;
        }

        if (newTicketPos == null)
        {
            newTicketPos = player.chunkPosition();
        }

        this.playerTicketManager.update(newTicketPos.toLong(), 0, true);
        this.tickingTicketsTracker.addTicket(TicketType.PLAYER, newTicketPos, this.getPlayerTicketLevel(), newTicketPos);

        data.setLazyLoadingLastTicketPos(newTicketPos);
    }

    @Inject(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"), cancellable = true)
    private void onRemovePlayerFromChunkSection(final SectionPos chunkSection, final ServerPlayer player, final CallbackInfo ci)
    {
        if (!(player instanceof IPlayerDataPlayer))
        {
            return;
        }

        ci.cancel();

        PlayerChunkData data = ((IPlayerDataPlayer) player).betterchunkloading$getPlayerChunkData();
        data.onChunkChanged(player);

        ChunkPos avgTicketPos = data.getSlowAvgPos();
        if (avgTicketPos != null && avgTicketPos.equals(data.getLazyLoadingLastTicketPos()) && !player.isRemoved())
        {
            return;
        }

        if (data.getLazyLoadingLastTicketPos() != null)
        {
            this.playerTicketManager.update(data.getLazyLoadingLastTicketPos().toLong(), Integer.MAX_VALUE, false);
            this.tickingTicketsTracker.removeTicket(TicketType.PLAYER, data.getLazyLoadingLastTicketPos(), this.getPlayerTicketLevel(), data.getLazyLoadingLastTicketPos());

            data.setLazyLoadingLastTicketPos(null);
        }
    }
}
