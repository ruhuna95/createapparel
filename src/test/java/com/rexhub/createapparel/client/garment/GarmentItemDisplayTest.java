package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentItemDisplayTest {
    @Test
    void garmentItemModelsAreGeneratedWithoutHandwrittenOverrides() throws IOException {
        try (var definitions = Files.list(Path.of("src/main/resources/data/createapparel/garments"))) {
            var items = definitions.filter(path -> path.toString().endsWith(".json")).toList();
            assertFalse(items.isEmpty());
            for (var item : items) {
                String relative = "assets/createapparel/models/item/" + item.getFileName();
                assertFalse(Files.exists(Path.of("src/main/resources", relative)), "Handwritten model: " + item);
                var generated = Path.of("src/generated/resources", relative);
                assertTrue(Files.exists(generated), "Missing generated model: " + item);
                var model = JsonParser.parseString(Files.readString(generated)).getAsJsonObject();
                assertEquals("minecraft:builtin/entity", model.get("parent").getAsString(), item.toString());
                assertTrue(model.has("textures"), "Missing particle texture: " + item);
                assertEquals("createapparel:item/old_cloth",
                        model.getAsJsonObject("textures").get("particle").getAsString(), item.toString());
                assertTrue(Files.exists(Path.of("src/main/resources/assets/createapparel/textures/item/old_cloth.png")));
            }
        }
    }

    @Test
    void headFaceBackAndFeetStaySolidWhileHeldAccessoriesPitchForward() throws Exception {
        var method = CommonGarmentRenderer.class.getDeclaredMethod(
                "applyItemShape", PoseStack.class, GarmentSlot.class, ItemDisplayContext.class, float.class);
        method.setAccessible(true);
        for (var slot : GarmentSlot.values()) {
            for (var context : ItemDisplayContext.values()) {
                boolean held = List.of(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).contains(context);
                boolean pitched = (slot == GarmentSlot.HEAD || slot == GarmentSlot.FACE || slot == GarmentSlot.BACK) && held;
                boolean solid = slot == GarmentSlot.HEAD || slot == GarmentSlot.FACE
                        || slot == GarmentSlot.BACK || slot == GarmentSlot.FEET;
                boolean reversed = slot == GarmentSlot.BACK && context == ItemDisplayContext.GUI;
                var pose = new PoseStack();
                method.invoke(null, pose, slot, context, 0.25F);
                var transformed = pose.last().pose().transformPosition(new Vector3f(2, 3, 4));
                assertEquals(reversed ? -2 : 2, transformed.x(), 0.00001);
                assertEquals(pitched ? 4 : 3, transformed.y(), 0.00001);
                assertEquals(reversed ? -4 : pitched ? -3 : solid ? 4 : 1, transformed.z(), 0.00001);
                if (solid) {
                    assertEquals(new Vector3f(2, 3, 4).length(), transformed.length(), 0.00001);
                }
                var normal = pose.last().normal().transform(new Vector3f(0, 1, 0));
                if (solid) {
                    assertEquals(pitched ? 0 : 1, normal.y(), 0.00001);
                    assertEquals(pitched ? -1 : 0, normal.z(), 0.00001);
                }
            }
        }
    }
}
