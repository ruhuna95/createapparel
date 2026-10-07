package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.entity.RackArmInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraftforge.registries.RegistryObject;

public final class ModArmInteractionPoints {
    public static final RegistryObject<ArmInteractionPointType> CLOTHING_RACK =
            ModRegistries.ARM_INTERACTION_POINT_TYPES.register("clothing_rack", RackArmInteractionPointType::new);

    private ModArmInteractionPoints() { }

    public static void bootstrap() { }
}
