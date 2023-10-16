package com.betterchunkloading.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntityDebugMixin<T extends EntityAccess> {
    @Redirect(method = "method_31825", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntitySection;getEntities()Ljava/util/stream/Stream;", ordinal = 2, remap = true), remap = false)
    private Stream fixFabricCrash(final EntitySection<EntityAccess> instance) {
        return instance.getEntities().collect(Collectors.toList()).stream();
    }
}
