package com.rexhub.createapparel.data.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.material.FabricAttribute;
import net.minecraft.data.PackOutput;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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
            } else if (item == ModItems.WARDROBE) {
                wardrobeModel();
            } else if (item == ModItems.PULLOVER_HOODIE || item == ModItems.ZIPUP_HOODIE
                    || item == ModItems.SWEATER || item == ModItems.V_NECK_SWEATER
                    || item == ModItems.DENIM_PANTS || item == ModItems.SLACKS || item == ModItems.SHORTS
                    || item == ModItems.A_LINE_SKIRT || item == ModItems.TIGHTS
                    || item == ModItems.ANKLE_SOCKS || item == ModItems.CREW_SOCKS
                    || item == ModItems.KNEE_HIGH_SOCKS || item == ModItems.OVER_KNEE_SOCKS
                    || item == ModItems.THIGH_HIGH_SOCKS
                    || item == ModItems.SUIT_JACKET || item == ModItems.BLAZER_JACKET
                    || item == ModItems.DENIM_JACKET || item == ModItems.CHESTERFIELD_COAT
                    || item == ModItems.TRENCH_COAT || item == ModItems.DUFFLE_COAT || item == ModItems.PEA_COAT
                    || item == ModItems.UNDERSHIRT || item == ModItems.BRA || item == ModItems.BRIEFS
                    || item == ModItems.BOXER_BRIEFS || item == ModItems.PANTIES) {
                blouseModel(item);
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

    private void wardrobeModel() {
        ItemModelBuilder model = getBuilder("wardrobe");
        wardrobeHalf(model, "lower_closed", 0);
        wardrobeHalf(model, "upper_closed", 16);
        model.texture("particle", modLoc("block/wardrobe/wardrobe_side_lower"));
        model.transforms()
                .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).translation(0, -2, 0).scale(0.45F).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 2, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(75, 45, 0).translation(0, 1, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(75, 225, 0).translation(0, 1, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.3F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.3F).end();
    }

    private void wardrobeHalf(ItemModelBuilder model, String name, int yOffset) {
        try (var reader = new InputStreamReader(existingFileHelper.getResource(
                modLoc("block/wardrobe/" + name), PackType.CLIENT_RESOURCES, ".json", "models").open(),
                StandardCharsets.UTF_8)) {
            JsonObject source = JsonParser.parseReader(reader).getAsJsonObject();
            source.getAsJsonObject("textures").entrySet().forEach(entry ->
                    model.texture(entry.getKey(), entry.getValue().getAsString()));
            for (var value : source.getAsJsonArray("elements")) {
                JsonObject element = value.getAsJsonObject();
                var from = element.getAsJsonArray("from");
                var to = element.getAsJsonArray("to");
                var builder = model.element()
                        .from(from.get(0).getAsFloat(), from.get(1).getAsFloat() + yOffset, from.get(2).getAsFloat())
                        .to(to.get(0).getAsFloat(), to.get(1).getAsFloat() + yOffset, to.get(2).getAsFloat());
                element.getAsJsonObject("faces").entrySet().forEach(entry -> {
                    var face = entry.getValue().getAsJsonObject();
                    var uv = face.getAsJsonArray("uv");
                    builder.face(Direction.byName(entry.getKey()))
                            .uvs(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat())
                            .texture(face.get("texture").getAsString()).end();
                });
                builder.end();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read wardrobe block model: " + name, exception);
        }
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
