package com.rexhub.createapparel.init;

import com.rexhub.createapparel.menu.WardrobeMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenuTypes {
    public static final DeferredHolder<MenuType<?>, MenuType<com.rexhub.createapparel.menu.CitizenGaugeMenu>> CITIZEN_GAUGE =
            ModRegistries.MENU_TYPES.register("citizen_gauge", () -> IMenuTypeExtension.create(com.rexhub.createapparel.menu.CitizenGaugeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.rexhub.createapparel.menu.RouteMarkerMenu>> ROUTE_MARKER =
            ModRegistries.MENU_TYPES.register("route_marker", () -> IMenuTypeExtension.create(com.rexhub.createapparel.menu.RouteMarkerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.rexhub.createapparel.menu.CitizenMenu>> CITIZEN =
            ModRegistries.MENU_TYPES.register("citizen",
                    () -> IMenuTypeExtension.create(com.rexhub.createapparel.menu.CitizenMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.rexhub.createapparel.menu.MannequinMenu>> MANNEQUIN =
            ModRegistries.MENU_TYPES.register("mannequin",
                    () -> IMenuTypeExtension.create(com.rexhub.createapparel.menu.MannequinMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WardrobeMenu>> WARDROBE = ModRegistries.MENU_TYPES.register(
            "wardrobe",
            () -> IMenuTypeExtension.create(WardrobeMenu::new)
    );

    private ModMenuTypes() {
    }

    public static void bootstrap() {
    }
}
