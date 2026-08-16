package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.ApparelCropBlock;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final RegistryObject<Block> COTTON_CROP = registerCrop(
            "cotton_crop",
            () -> ModItems.COTTON_SEEDS.get()
    );
    public static final RegistryObject<Block> FLAX_CROP = registerCrop(
            "flax_crop",
            () -> ModItems.FLAX_SEEDS.get()
    );
    public static final RegistryObject<Block> HEMP_CROP = registerCrop(
            "hemp_crop",
            () -> ModItems.HEMP_SEEDS.get()
    );

    private ModBlocks() {
    }

    public static void bootstrap() {
    }

    private static RegistryObject<Block> registerCrop(String name, Supplier<? extends ItemLike> seed) {
        return ModRegistries.BLOCKS.register(
                name,
                () -> new ApparelCropBlock(BlockBehaviour.Properties.copy(Blocks.WHEAT), seed)
        );
    }
}
