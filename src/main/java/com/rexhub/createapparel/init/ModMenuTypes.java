package com.rexhub.createapparel.init;

import com.rexhub.createapparel.menu.WardrobeMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final RegistryObject<MenuType<com.rexhub.createapparel.menu.CitizenMenu>> CITIZEN =
            ModRegistries.MENU_TYPES.register("citizen",
                    () -> IForgeMenuType.create(com.rexhub.createapparel.menu.CitizenMenu::new));
    public static final RegistryObject<MenuType<com.rexhub.createapparel.menu.MannequinMenu>> MANNEQUIN =
            ModRegistries.MENU_TYPES.register("mannequin",
                    () -> IForgeMenuType.create(com.rexhub.createapparel.menu.MannequinMenu::new));
    public static final RegistryObject<MenuType<WardrobeMenu>> WARDROBE = ModRegistries.MENU_TYPES.register(
            "wardrobe",
            () -> IForgeMenuType.create(WardrobeMenu::new)
    );

    private ModMenuTypes() {
    }

    public static void bootstrap() {
    }
}
