package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.ApparelCropBlock;
import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.block.SewingMachineBlock;
import com.rexhub.createapparel.block.SpinningMachineBlock;
import com.rexhub.createapparel.block.WardrobeBlock;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final RegistryObject<Block> WALL_HANGER = ModRegistries.BLOCKS.register("wall_hanger",
            () -> new com.rexhub.createapparel.block.WallHangerBlock(
                    BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion()));
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
    public static final RegistryObject<Block> SPINNING_MACHINE = ModRegistries.BLOCKS.register(
            "spinning_machine",
            () -> new SpinningMachineBlock(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion())
    );
    public static final RegistryObject<Block> LOOM = ModRegistries.BLOCKS.register(
            "loom",
            () -> new LoomBlock(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion())
    );
    public static final RegistryObject<Block> SEWING_MACHINE = ModRegistries.BLOCKS.register(
            "sewing_machine",
            () -> new SewingMachineBlock(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion())
    );
    public static final RegistryObject<Block> WARDROBE = ModRegistries.BLOCKS.register(
            "wardrobe",
            () -> new WardrobeBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).noOcclusion())
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
