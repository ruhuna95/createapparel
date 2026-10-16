package com.rexhub.createapparel.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilkwormRearingTrayAssetTest {
    private static JsonObject model(String path) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/models/" + path + ".json"))).getAsJsonObject();
    }

    @Test
    void itemUsesCustomRendererWithInventoryAndHandTransforms() throws IOException {
        var item = model("item/silkworm_rearing_tray");
        assertEquals("minecraft:builtin/entity", item.get("parent").getAsString());
        var display = item.getAsJsonObject("display");
        for (String context : new String[]{"gui", "ground", "fixed", "thirdperson_righthand",
                "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"}) {
            assertTrue(display.has(context), context);
        }
        assertEquals(11, model("block/silkworm_rearing_tray/item").getAsJsonArray("elements").size());
    }

    @Test
    void shaftOpeningTextureIsRestrictedToBothShaftFaces() throws IOException {
        for (String variant : new String[]{"block", "item"}) {
            var body = model("block/silkworm_rearing_tray/" + variant);
            assertEquals("create:block/gearbox", body.getAsJsonObject("textures").get("opening").getAsString());
            var faces = body.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
            for (String face : new String[]{"east", "west"}) {
                assertEquals("#opening", faces.getAsJsonObject(face).get("texture").getAsString());
            }
            for (String face : new String[]{"north", "south", "up", "down"}) {
                assertEquals("#casing", faces.getAsJsonObject(face).get("texture").getAsString());
            }
        }
    }

    @Test
    void ponderReplacesShiftDetailsWhileTheSummaryRemainsInBothLanguages() throws IOException {
        String prefix = "block.createapparel.silkworm_rearing_tray.tooltip.";
        for (String language : new String[]{"ja_jp", "en_us"}) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
            assertTrue(translations.has(prefix + "summary"), language);
            assertFalse(translations.has("tooltip.createapparel.rearing.speed"), "Unused speed translation: " + language);
            for (int i = 1; i <= 6; i++) {
                assertFalse(translations.has(prefix + "condition" + i), language + ": condition" + i);
                assertFalse(translations.has(prefix + "behaviour" + i), language + ": behaviour" + i);
            }
        }
    }

    @Test
    void larvaRemainsCenteredAndOutputFitsAtTheEdgeWithoutIntersectingIt() {
        for (boolean feeding : new boolean[]{false, true}) {
            float floor = (feeding ? 14 : 13) / 16.0F;
            var larva = new PoseStack();
            SilkwormRearingTrayContentsRenderer.applyLarvaPose(larva, feeding);
            var feet = larva.last().pose().transformPosition(new Vector3f(0, 23.5F / 16, 3.5F / 16));
            assertEquals(0.5F, feet.x(), 0.00001);
            assertEquals(floor, feet.y(), 0.00001);
            assertEquals(0.5F, feet.z(), 0.00001);
            float larvaRight = larva.last().pose().transformPosition(new Vector3f(-2F / 16, 22F / 16, 3.5F / 16)).x();
            var output = new PoseStack();
            SilkwormRearingTrayContentsRenderer.applyOutputPose(output, feeding);
            for (float x : new float[]{-0.5F, 0.5F}) {
                for (float y : new float[]{-0.5F, 0.5F}) {
                    var point = output.last().pose().transformPosition(new Vector3f(x, y, -0.5F / 16));
                    assertTrue(point.x() >= larvaRight - 0.00001 && point.x() <= 13F / 16 + 0.00001);
                    assertTrue(point.z() >= 3F / 16 && point.z() <= 13F / 16);
                    assertEquals(floor, point.y(), 0.00001);
                }
            }
            assertEquals(0.25F, output.last().pose().transformDirection(new Vector3f(1, 0, 0)).length(), 0.00001);
        }
    }

}
