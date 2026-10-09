package com.rexhub.createapparel.fluid;

import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;

import java.util.function.Consumer;

public final class MirrorCoatingFluidType extends FluidType {
    public MirrorCoatingFluidType() {
        super(Properties.create().descriptionId("fluid.createapparel.mirror_coating_fluid")
                .density(1100).viscosity(1200).canConvertToSource(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
    }

    @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new com.rexhub.createapparel.client.mirror.MirrorFluidExtensions());
    }
}
