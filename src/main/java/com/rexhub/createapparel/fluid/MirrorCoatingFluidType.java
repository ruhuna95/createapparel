package com.rexhub.createapparel.fluid;

import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

public final class MirrorCoatingFluidType extends FluidType {
    public MirrorCoatingFluidType() {
        super(Properties.create().descriptionId("fluid.createapparel.mirror_coating_fluid")
                .density(1100).viscosity(1200).canConvertToSource(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
    }


}
