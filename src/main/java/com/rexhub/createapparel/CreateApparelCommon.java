package com.rexhub.createapparel;

import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.block.SewingMachineBlock;
import com.rexhub.createapparel.block.SpinningMachineBlock;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.network.ModNetwork;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public final class CreateApparelCommon {
    private CreateApparelCommon() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.initialize();
            BlockStressValues.IMPACTS.register(ModBlocks.SILKWORM_REARING_TRAY.get(),
                    () -> com.rexhub.createapparel.block.SilkwormRearingTrayBlock.STRESS_IMPACT);
            BlockStressValues.IMPACTS.register(
                    ModBlocks.SPINNING_MACHINE.get(),
                    () -> SpinningMachineBlock.STRESS_IMPACT
            );
            BlockStressValues.IMPACTS.register(ModBlocks.LOOM.get(), () -> LoomBlock.STRESS_IMPACT);
            BlockStressValues.IMPACTS.register(
                    ModBlocks.SEWING_MACHINE.get(),
                    () -> SewingMachineBlock.STRESS_IMPACT
            );
        });
    }
}
