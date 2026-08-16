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

    private ModCreativeModeTabs() {
    }

    public static void bootstrap() {
    }
}
