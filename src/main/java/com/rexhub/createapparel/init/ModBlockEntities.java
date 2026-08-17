package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.entity.SpinningMachineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final RegistryObject<BlockEntityType<SpinningMachineBlockEntity>> SPINNING_MACHINE =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "spinning_machine",
                    () -> BlockEntityType.Builder.of(
                            SpinningMachineBlockEntity::new,
                            ModBlocks.SPINNING_MACHINE.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void bootstrap() {
    }
}
