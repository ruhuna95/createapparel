package com.rexhub.createapparel;

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
            BlockStressValues.IMPACTS.register(
                    ModBlocks.SPINNING_MACHINE.get(),
                    () -> SpinningMachineBlock.STRESS_IMPACT
            );
        });
    }
}
