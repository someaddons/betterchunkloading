package com.betterchunkloading.mixin;

import com.betterchunkloading.BetterChunkLoading;
import com.betterchunkloading.chunk.IDistanceManager;
import com.betterchunkloading.chunk.IPlayerDataPlayer;
import com.betterchunkloading.chunk.PlayerChunkData;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DistanceManager.class, remap = true)
public abstract class DistanceManagerMixin implements IDistanceManager
{
    @Shadow
    @Final
    private DistanceManager.PlayerTicketTracker playerTicketManager;

    @Shadow
    @Final
    private TickingTracker tickingTicketsTracker;

    @Shadow
    protected abstract int getPlayerTicketLevel();

    @Unique
    final Long2IntMap playerCountPerChunk = new Long2IntOpenHashMap();

    @Override
    public Long2IntMap getPlayerCountPerChunk()
    {
        return playerCountPerChunk;
    }

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

        playerCountPerChunk.put(newTicketPos.toLong(), playerCountPerChunk.getOrDefault(newTicketPos.toLong(), 0) + 1);
        this.playerTicketManager.update(newTicketPos.toLong(), 0, true);
        this.tickingTicketsTracker.addTicket(TicketType.PLAYER, newTicketPos, this.getPlayerTicketLevel(), newTicketPos);

        data.setLazyLoadingLastTicketPos(newTicketPos);
    }

    /**
     * Inject at head to make sure it runs on every chunk pos change
     * @param chunkSection
     * @param player
     * @param ci
     */
    @Inject(method = "removePlayer", at = @At(value = "HEAD"))
    private void onRemovePlayerFromChunkSection(final SectionPos chunkSection, final ServerPlayer player, final CallbackInfo ci)
    {
        if (!(player instanceof IPlayerDataPlayer))
        {
            return;
        }

        PlayerChunkData data = ((IPlayerDataPlayer) player).betterchunkloading$getPlayerChunkData();
        data.onChunkChanged(player);

        ChunkPos avgTicketPos = data.getSlowAvgPos();
        if (avgTicketPos != null && avgTicketPos.equals(data.getLazyLoadingLastTicketPos()) && !player.isRemoved())
        {
            return;
        }

        betterchunkloading$removePlayer(data);
    }

    /**
     * Prevent vanilla ticket changes as we handle them ourselves
     * @param p_140829_
     * @param p_140830_
     * @param ci
     */
    @Inject(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"), cancellable = true)
    private void betterchunkloading$skipRemovePlayer(final SectionPos p_140829_, final ServerPlayer p_140830_, final CallbackInfo ci)
    {
        ci.cancel();
    }

    /**
     * Removes loaded player tickets if there are no remaining players in the chunk
     *
     * @param data
     */
    @Unique
    private void betterchunkloading$removePlayer(final PlayerChunkData data)
    {
        final ChunkPos toUnload = data.getLazyLoadingLastTicketPos();
        if (toUnload != null)
        {
            final long posKey = toUnload.toLong();
            if (!playerCountPerChunk.containsKey(posKey))
            {
                BetterChunkLoading.LOGGER.warn("missing player in chunk");
            }

            if (playerCountPerChunk.get(posKey) <= 1)
            {
                playerCountPerChunk.remove(posKey);
                this.playerTicketManager.update(posKey, Integer.MAX_VALUE, false);
                this.tickingTicketsTracker.removeTicket(TicketType.PLAYER,
                  toUnload,
                  this.getPlayerTicketLevel(),
                  toUnload);
            }
            else
            {
                playerCountPerChunk.put(toUnload.toLong(), playerCountPerChunk.getOrDefault(toUnload.toLong(), 0) - 1);
            }
            data.setLazyLoadingLastTicketPos(null);
        }
    }
}
