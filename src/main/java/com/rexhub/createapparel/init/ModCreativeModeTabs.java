package com.rexhub.createapparel.init;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeModeTabs {
    public static final RegistryObject<CreativeModeTab> CREATE_APPAREL =
            ModRegistries.CREATIVE_MODE_TABS.register("create_apparel", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createapparel"))
                    .icon(() -> ModItems.COTTON_FABRIC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ModItems.creativeTabItems()
                            .forEach(item -> output.accept(item.get())))
                    .build());
    public static final RegistryObject<CreativeModeTab> CREATE_APPAREL_GARMENT =
            ModRegistries.CREATIVE_MODE_TABS.register("create_apparel_garment", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createapparel.garment"))
                    .icon(() -> ModItems.T_SHIRT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ModItems.garmentCreativeTabItems()
                            .forEach(item -> output.accept(GarmentCreativePresets.create(item))))
                    .withTabsBefore(CREATE_APPAREL.getKey())
                    .build());

    private ModCreativeModeTabs() {
    }

    public static void bootstrap() {
    }
}
