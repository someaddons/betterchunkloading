package com.betterchunkloading.mixin;

import com.betterchunkloading.config.CommonConfiguration;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = LavaFluid.class, priority = 5000)
public class LavaFluidMixin
{
    @Overwrite
    protected boolean canConvertToSource(Level p_256295_)
    {
        return CommonConfiguration.convertLavaSource;
    }
}
