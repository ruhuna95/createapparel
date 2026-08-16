package com.rexhub.createapparel;

import com.rexhub.createapparel.network.ModNetwork;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public final class CreateApparelCommon {
    private CreateApparelCommon() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::initialize);
    }
}
