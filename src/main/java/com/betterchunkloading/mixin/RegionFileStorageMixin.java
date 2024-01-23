package com.betterchunkloading.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RegionFileStorage.class)
public class RegionFileStorageMixin
{
    @ModifyConstant(method = "getRegionFile", constant = @Constant(intValue = 256), require = 0)
    private int incLimit(final int constant)
    {
        return 1024;
    }
}
