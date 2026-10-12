package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.entity.MannequinColor;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.MannequinVariant;
import com.rexhub.createapparel.item.MannequinItem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.item.ApparelDyeItem;
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
        for (var color : MannequinColor.values()) {
            existingFileHelper.trackGenerated(modLoc("item/mannequin/" + color.id()),
                    PackType.CLIENT_RESOURCES, ".png", "textures");
        }
        var dyeModel = withExistingParent("apparel_dye", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/apparel_dye_base"));
        for (RegistryObject<Item> item : ModRegistries.ITEMS.getEntries()) {
            if (com.rexhub.createapparel.init.ModMirrors.ITEMS.containsValue(item)
                    || item == com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_BUCKET) continue;
            FabricAttribute attribute = ModItems.fabricItems().get(item);
            if (attribute != null && attribute.isDyed()) {
                dyedFabric(item, attribute);
            } else if (item.get() instanceof ApparelDyeItem) {
                getBuilder(item.getId().getPath()).parent(dyeModel);
            } else if (item == ModItems.COTTON_BOLL) {
                generatedItem(item, "cotton_ball");
            } else if (item == ModItems.SILKWORM_SPAWN_EGG || item == ModItems.CITIZEN_SPAWN_EGG) {
                withExistingParent(item.getId().getPath(), mcLoc("item/template_spawn_egg"));
            } else if (item == ModItems.SEWING_PATTERN) {
                withExistingParent(item.getId().getPath(), mcLoc("item/generated"))
                        .texture("layer0", modLoc("item/sewing_pattern"));
            } else if (item == ModItems.SMART_PHONE) {
                smartPhoneModel();
            } else if (item == ModItems.WARDROBE) {
                wardrobeModel();
            } else if (item.get() instanceof MannequinItem mannequin) {
                mannequinModel(item, mannequin.variant());
            } else if (item.get() instanceof GarmentItem) {
                garmentModel(item);
            } else if (item == ModItems.SPINNING_MACHINE
                    || item == ModItems.SILKWORM_REARING_TRAY
                    || item == ModItems.WALL_HANGER
                    || item == ModItems.CLOTHING_RACK
                    || item == ModItems.CLIP_WALL_HANGER
                    || item == ModItems.CLIP_CLOTHING_RACK
                    || item == ModItems.LOOM
                    || item == ModItems.SEWING_MACHINE) {
                continue;
            } else {
                basicItem(item.get());
            }
        }
    }

    private void dyedFabric(RegistryObject<Item> item, FabricAttribute attribute) {
        generatedItem(item, attribute.material().getSerializedName() + "_fabric_tint");
    }

    private void smartPhoneModel() {
        ItemModelBuilder model = getBuilder("smart_phone").texture("phone", modLoc("item/smart_phone"))
                .texture("particle", "#phone");
        var body = model.element().from(4.5F, 1.5F, 7.5F).to(11.5F, 14.5F, 8.5F);
        body.face(Direction.SOUTH).texture("#phone").uvs(0, 1, 7, 14).end();
        body.face(Direction.NORTH).texture("#phone").uvs(8, 1, 15, 14).end();
        for (Direction side : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
            body.face(side).texture("#phone").uvs(15, 1, 16, 2).end();
        }
        body.end();
        model.transforms()
                .transform(ItemDisplayContext.GUI).rotation(10, -15, 0).scale(1).end()
                .transform(ItemDisplayContext.GROUND).rotation(90, 0, 0).translation(0, 1, 0).scale(0.5F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.75F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 0, -10).translation(1, 1, 0).scale(0.7F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 0, 10).translation(1, 1, 0).scale(0.7F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(0, 0, 0).translation(0, 2, 1).scale(0.6F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(0, 0, 0).translation(0, 2, 1).scale(0.6F).end();
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

    private void mannequinModel(RegistryObject<Item> item, MannequinVariant variant) {
        ItemModelBuilder model = getBuilder(item.getId().getPath()).texture("body", modLoc("item/mannequin/" + variant.color().id()))
                .texture("particle", "#body");
        mannequinBox(model, -6, -1, -6, 6, 0, 6);
        if (variant.form().hasHead()) {
            float headBottom = variant.form() == MannequinForm.HEAD ? 7 : 24;
            mannequinBodyBox(model, variant.form(), -4, headBottom, -4, 4, headBottom + 8, 4);
        }
        if (variant.form().hasTorso()) {
            mannequinBodyBox(model, variant.form(), -4, 12, -2, 4, 24, 2);
            boolean slim = variant.armProfile() == ArmProfile.SLIM;
            mannequinBodyBox(model, variant.form(), slim ? -7 : -8, slim ? 11.5F : 12, -2, -4, slim ? 23.5F : 24, 2);
            mannequinBodyBox(model, variant.form(), 4, slim ? 11.5F : 12, -2, slim ? 7 : 8, slim ? 23.5F : 24, 2);
            if (slim) {
                float scale = MannequinForm.BODY_SCALE;
                var bust = model.element().from(8 - 3.5F * scale, 19.52F * scale, 8 - 1.39F * scale)
                        .to(8 + 3.5F * scale, 23.52F * scale, 8 + 1.61F * scale);
                bust.rotation().origin(8, 25 * scale, 8 - 0.9F * scale).axis(Direction.Axis.X).angle(22.5F).end();
                bust.allFaces((direction, face) -> face.texture("#body").uvs(0, 0, 16, 16)).end();
            }
        }
        if (variant.form().hasLegs()) {
            mannequinBodyBox(model, variant.form(), -4, 0, -2, 0, 12, 2);
            mannequinBodyBox(model, variant.form(), 0, 0, -2, 4, 12, 2);
        }
        if (variant.form() == MannequinForm.LOWER) {
            mannequinBodyBox(model, variant.form(), -4, 12, -2, 4, 17, 2);
        }
        if (variant.form() == MannequinForm.UPPER) {
            mannequinBox(model, -0.75F, 0, -0.75F, 0.75F, variant.form().supportHeightPixels(), 0.75F);
        } else if (variant.form() == MannequinForm.HEAD) {
            mannequinBodyBox(model, variant.form(), -4, 5, -2, 4, 7, 2);
            mannequinBox(model, -0.75F, 0, -0.75F, 0.75F, variant.form().supportHeightPixels(), 0.75F);
        }
        model.transforms()
                .transform(ItemDisplayContext.GUI).rotation(15, 210, 0).translation(0, -4, 0).scale(0.45F).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 2, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).translation(0, -4, 0).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.3F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.3F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.35F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.35F).end();
    }

    private static void mannequinBodyBox(ItemModelBuilder model, MannequinForm form,
                                         float x0, float y0, float z0, float x1, float y1, float z1) {
        float scale = MannequinForm.BODY_SCALE;
        float yOffset = form == MannequinForm.HEAD ? scale : 0;
        mannequinBox(model, x0 * scale, y0 * scale + yOffset, z0 * scale,
                x1 * scale, y1 * scale + yOffset, z1 * scale);
    }

    private static void mannequinBox(ItemModelBuilder model, float x0, float y0, float z0, float x1, float y1, float z1) {
        model.element().from(x0 + 8, y0, z0 + 8).to(x1 + 8, y1, z1 + 8)
                .allFaces((direction, face) -> face.texture("#body").uvs(0, 0, 16, 16)).end();
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

    private void garmentModel(RegistryObject<Item> item) {
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
