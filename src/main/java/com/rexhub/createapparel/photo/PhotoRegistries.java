package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class PhotoRegistries {
    public static final DeferredHolder<Item, Item> CAMERA = ModRegistries.ITEMS.register("camera", () -> new CameraItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> ADVERTISING_PHOTO = ModRegistries.ITEMS.register("advertising_photo", () -> new AdvertisingPhotoItem(new Item.Properties()));
    public static final DeferredHolder<MenuType<?>, MenuType<PhotographyMenu>> PHOTOGRAPHY = ModRegistries.MENU_TYPES.register("photography", () -> IMenuTypeExtension.create(PhotographyMenu::new));
    private PhotoRegistries() { }
    public static void bootstrap() { }
}
