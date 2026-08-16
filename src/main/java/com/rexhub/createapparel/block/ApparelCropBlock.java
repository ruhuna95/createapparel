package com.rexhub.createapparel.block;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import java.util.function.Supplier;

public final class ApparelCropBlock extends CropBlock {
    private final Supplier<? extends ItemLike> seed;

    public ApparelCropBlock(Properties properties, Supplier<? extends ItemLike> seed) {
        super(properties);
        this.seed = seed;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }
}
