package com.betterchunkloading.mixin;

import com.betterchunkloading.config.CommonConfiguration;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = WaterFluid.class, priority = 5000)
public class WaterFluidMixin
{
    @Overwrite
    public boolean canConvertToSource(Level p_256670_)
    {
        return CommonConfiguration.convertWaterSource;
    }
}
