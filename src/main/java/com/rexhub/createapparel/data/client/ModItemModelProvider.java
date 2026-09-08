package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.material.FabricAttribute;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
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
            } else if (item == ModItems.SEWING_PATTERN) {
                withExistingParent(item.getId().getPath(), mcLoc("item/generated"))
                        .texture("layer0", modLoc("item/sewing_pattern"));
            } else if (item == ModItems.BLOUSE_ROUND
                    || item == ModItems.BLOUSE_FRILL
                    || item == ModItems.BLOUSE_BOW_TIE
                    || item == ModItems.BLOUSE_ROUND_THREE_QUARTER_SLEEVE
                    || item == ModItems.BLOUSE_FRILL_THREE_QUARTER_SLEEVE
                    || item == ModItems.BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE) {
                blouseModel(item);
            } else if (item == ModItems.SPINNING_MACHINE
                    || item == ModItems.LOOM
                    || item == ModItems.SEWING_MACHINE
                    || item == ModItems.WARDROBE
                    || item == ModItems.T_SHIRT
                    || item == ModItems.T_SHIRT_U_NECK
                    || item == ModItems.T_SHIRT_V_NECK
                    || item == ModItems.T_SHIRT_LONGSLEEVES
                    || item == ModItems.T_SHIRT_LONGSLEEVES_U_NECK
                    || item == ModItems.T_SHIRT_LONGSLEEVES_V_NECK
                    || item == ModItems.SHIRT_SHORTSLEEVES
                    || item == ModItems.SHIRT_LONGSLEEVES) {
                continue;
            } else {
                basicItem(item.get());
            }
        }
    }

    private void dyedFabric(RegistryObject<Item> item, FabricAttribute attribute) {
        generatedItem(item, attribute.material().getSerializedName() + "_fabric_tint");
    }

    private void blouseModel(RegistryObject<Item> item) {
        getBuilder(item.getId().getPath()).parent(new ModelFile.UncheckedModelFile(mcLoc("builtin/entity")))
                .transforms()
                .transform(ItemDisplayContext.GUI).rotation(0, 180, 0).translation(0, -1, 0).scale(0.82F).end()
                .transform(ItemDisplayContext.GROUND).rotation(0, 180, 0).translation(0, 2, 0).scale(0.5F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.85F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.65F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.65F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 45, 0).translation(1.13F, 3.2F, 1.13F).scale(0.62F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 225, 0).translation(1.13F, 3.2F, 1.13F).scale(0.62F).end();
    }

    private void generatedItem(RegistryObject<Item> item, String textureName) {
        withExistingParent(item.getId().getPath(), mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + textureName));
    }
}
