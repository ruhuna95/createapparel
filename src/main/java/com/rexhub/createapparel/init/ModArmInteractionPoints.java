package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.entity.RackArmInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraftforge.registries.RegistryObject;

public final class ModArmInteractionPoints {
    public static final RegistryObject<ArmInteractionPointType> SILKWORM_REARING_TRAY =
            ModRegistries.ARM_INTERACTION_POINT_TYPES.register("silkworm_rearing_tray",
                    com.rexhub.createapparel.block.entity.RearingTrayArmInteractionPointType::new);
    public static final RegistryObject<ArmInteractionPointType> CLOTHING_RACK =
            ModRegistries.ARM_INTERACTION_POINT_TYPES.register("clothing_rack", RackArmInteractionPointType::new);

    private ModArmInteractionPoints() { }

    public static void bootstrap() { }
}
