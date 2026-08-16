package com.rexhub.createapparel;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.data.CreateApparelDataGenerators;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CreateApparel.MOD_ID)
public class CreateApparel {
    public static final String MOD_ID = "createapparel";

    public CreateApparel(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModRegistries.register(modEventBus);
        ModConfigs.register(context);
        modEventBus.addListener(CreateApparelCommon::onCommonSetup);
        modEventBus.addListener(CreateApparelDataGenerators::gatherData);
    }
}
