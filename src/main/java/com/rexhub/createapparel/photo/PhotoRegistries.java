package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

public final class PhotoRegistries {
    public static final RegistryObject<Item> CAMERA = ModRegistries.ITEMS.register("camera", () -> new CameraItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ADVERTISING_PHOTO = ModRegistries.ITEMS.register("advertising_photo", () -> new AdvertisingPhotoItem(new Item.Properties()));
    public static final RegistryObject<MenuType<PhotographyMenu>> PHOTOGRAPHY = ModRegistries.MENU_TYPES.register("photography", () -> IForgeMenuType.create(PhotographyMenu::new));
    private PhotoRegistries() { }
    public static void bootstrap() { }
}
