package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.entity.RackArmInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModArmInteractionPoints {
    public static final DeferredHolder<ArmInteractionPointType, ArmInteractionPointType> SILKWORM_REARING_TRAY =
            ModRegistries.ARM_INTERACTION_POINT_TYPES.register("silkworm_rearing_tray",
                    com.rexhub.createapparel.block.entity.RearingTrayArmInteractionPointType::new);
    public static final DeferredHolder<ArmInteractionPointType, ArmInteractionPointType> CLOTHING_RACK =
            ModRegistries.ARM_INTERACTION_POINT_TYPES.register("clothing_rack", RackArmInteractionPointType::new);

    private ModArmInteractionPoints() { }

    public static void bootstrap() { }
}
