package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.material.FabricAttribute;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public final class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CreateApparel.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (RegistryObject<Item> item : ModRegistries.ITEMS.getEntries()) {
            FabricAttribute attribute = ModItems.fabricItems().get(item);
            if (attribute != null && attribute.isDyed()) {
                dyedFabric(item, attribute);
            } else if (item == ModItems.COTTON_BOLL) {
                generatedItem(item, "cotton_ball");
            } else if (item == ModItems.SILKWORM_SPAWN_EGG) {
                withExistingParent(item.getId().getPath(), mcLoc("item/template_spawn_egg"));
            } else if (item == ModItems.SPINNING_MACHINE) {
                continue;
            } else {
                basicItem(item.get());
            }
        }
    }

    private void dyedFabric(RegistryObject<Item> item, FabricAttribute attribute) {
        generatedItem(item, attribute.material().getSerializedName() + "_fabric_tint");
    }

    private void generatedItem(RegistryObject<Item> item, String textureName) {
        withExistingParent(item.getId().getPath(), mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + textureName));
    }
}
