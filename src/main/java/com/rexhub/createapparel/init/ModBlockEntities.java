package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.entity.LoomBlockEntity;
import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.block.entity.SpinningMachineBlockEntity;
import com.rexhub.createapparel.block.entity.WardrobeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final RegistryObject<BlockEntityType<com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity>> SILKWORM_REARING_TRAY =
            ModRegistries.BLOCK_ENTITY_TYPES.register("silkworm_rearing_tray", () -> BlockEntityType.Builder.of(
                    com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity::new,
                    ModBlocks.SILKWORM_REARING_TRAY.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.rexhub.createapparel.block.entity.ClothingRackBlockEntity>> CLOTHING_RACK =
            ModRegistries.BLOCK_ENTITY_TYPES.register("clothing_rack", () -> BlockEntityType.Builder.of(
                    com.rexhub.createapparel.block.entity.ClothingRackBlockEntity::new,
                    ModBlocks.CLOTHING_RACK.get(), ModBlocks.CLIP_CLOTHING_RACK.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.rexhub.createapparel.block.entity.WallHangerBlockEntity>> WALL_HANGER =
            ModRegistries.BLOCK_ENTITY_TYPES.register("wall_hanger", () -> BlockEntityType.Builder.of(
                    com.rexhub.createapparel.block.entity.WallHangerBlockEntity::new,
                    ModBlocks.WALL_HANGER.get(), ModBlocks.CLIP_WALL_HANGER.get()).build(null));
    public static final RegistryObject<BlockEntityType<SpinningMachineBlockEntity>> SPINNING_MACHINE =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "spinning_machine",
                    () -> BlockEntityType.Builder.of(
                            SpinningMachineBlockEntity::new,
                            ModBlocks.SPINNING_MACHINE.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<LoomBlockEntity>> LOOM =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "loom",
                    () -> BlockEntityType.Builder.of(
                            LoomBlockEntity::new,
                            ModBlocks.LOOM.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<SewingMachineBlockEntity>> SEWING_MACHINE =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "sewing_machine",
                    () -> BlockEntityType.Builder.of(
                            SewingMachineBlockEntity::new,
                            ModBlocks.SEWING_MACHINE.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<WardrobeBlockEntity>> WARDROBE =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "wardrobe",
                    () -> BlockEntityType.Builder.of(
                            WardrobeBlockEntity::new,
                            ModBlocks.WARDROBE.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void bootstrap() {
    }
}
