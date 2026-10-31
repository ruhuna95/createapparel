package com.rexhub.createapparel;

import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.data.CreateApparelDataGenerators;
import com.rexhub.createapparel.init.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(CreateApparel.MOD_ID)
public final class CreateApparel {
    public static final String MOD_ID = "createapparel";

    public CreateApparel(IEventBus modEventBus, ModContainer container) {
        ModRegistries.register(modEventBus);
        modEventBus.addListener(com.rexhub.createapparel.init.ModBlockEntities::registerCapabilities);
        ModConfigs.register(container);
        com.rexhub.createapparel.recipe.OptionalRecipeCondition.register(modEventBus);
        modEventBus.addListener(com.rexhub.createapparel.network.ModNetwork::register);
        modEventBus.addListener(com.rexhub.createapparel.advertisement.AdvertisementNetwork::register);
        ApparelCapabilities.register(modEventBus);
        if (net.neoforged.fml.ModList.get().isLoaded("destroy")) {
            com.rexhub.createapparel.compat.destroy.MirrorMixtureIngredient.register(modEventBus);
        }
        modEventBus.addListener(CreateApparelCommon::onCommonSetup);
        modEventBus.addListener(CreateApparelDataGenerators::gatherData);
    }
}
