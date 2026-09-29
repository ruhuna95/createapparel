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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentItemDisplayTest {
    @Test
    void headFaceAndFeetStaySolidWhileHeldHeadAndFacePitchForward() throws Exception {
        var method = CommonGarmentRenderer.class.getDeclaredMethod(
                "applyItemShape", PoseStack.class, GarmentSlot.class, ItemDisplayContext.class, float.class);
        method.setAccessible(true);
        for (var slot : GarmentSlot.values()) {
            for (var context : ItemDisplayContext.values()) {
                boolean held = List.of(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).contains(context);
                boolean pitched = (slot == GarmentSlot.HEAD || slot == GarmentSlot.FACE) && held;
                boolean solid = slot == GarmentSlot.HEAD || slot == GarmentSlot.FACE || slot == GarmentSlot.FEET;
                var pose = new PoseStack();
                method.invoke(null, pose, slot, context, 0.25F);
                var transformed = pose.last().pose().transformPosition(new Vector3f(2, 3, 4));
                assertEquals(2, transformed.x(), 0.00001);
                assertEquals(pitched ? 4 : 3, transformed.y(), 0.00001);
                assertEquals(pitched ? -3 : solid ? 4 : 1, transformed.z(), 0.00001);
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

    @Test
    void allGarmentGuiModelsFaceForward() throws IOException {
        var items = List.of("t_shirt", "t_shirt_u_neck", "t_shirt_v_neck",
                "t_shirt_longsleeves", "t_shirt_longsleeves_u_neck", "t_shirt_longsleeves_v_neck",
                "shirt_shortsleeves", "shirt_longsleeves", "blouse_round", "blouse_frill", "blouse_bow_tie",
                "blouse_round_three_quarter_sleeve", "blouse_frill_three_quarter_sleeve",
                "blouse_bow_tie_three_quarter_sleeve");
        for (String item : items) {
            String relative = "assets/createapparel/models/item/" + item + ".json";
            Path path = Path.of("src/main/resources", relative);
            if (!Files.exists(path)) {
                path = Path.of("src/generated/resources", relative);
            }
            assertTrue(Files.exists(path), item);
            var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var rotation = model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation");
            assertEquals(0, rotation.get(0).getAsFloat(), item);
            assertEquals(180, rotation.get(1).getAsFloat(), item);
            assertEquals(0, rotation.get(2).getAsFloat(), item);
        }
    }
}
