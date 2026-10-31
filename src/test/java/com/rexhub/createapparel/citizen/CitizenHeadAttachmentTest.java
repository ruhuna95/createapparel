package com.rexhub.createapparel.citizen;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.client.citizen.CitizenHairLayers;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.entity.CitizenAge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenHeadAttachmentTest {
    @Test
    void everyBuiltInHairExtensionRootFollowsHeadAndTipsFallAlongBodyGravity() throws Exception {
        var geometry = CitizenHairLayers.read(JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/citizen/hair/extensions.json"))).getAsJsonObject());
        for (String name : CitizenHairStyles.BUILT_IN) {
            if (name.equals("bald")) continue;
            var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/entity/citizen/hair/"
                    + name + "_tint_base.png").toFile());
            var layers = geometry.withOpacity((u, v) -> image.getRGB(u, v) >>> 24 != 0);
            var visible = geometry.layers().stream().filter(layer -> {
                for (int u = (int) layer.u0(); u < layer.u1(); u++) for (int v = (int) layer.v0(); v < layer.v1(); v++) {
                    if (image.getRGB(u, v) >>> 24 != 0) return true;
                }
                return false;
            }).toList();
            if (name.equals("long_straight")) assertFalse(visible.isEmpty());
            for (CitizenAge age : CitizenAge.values()) for (boolean showHat : List.of(false, true)) {
                for (float yaw : new float[]{-0.6F, 0, 0.6F}) for (float pitch : new float[]{-0.4F, 0.3F}) {
                    var model = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
                    model.applyAge(age);
                    model.head.setPos(0.3F, -0.2F, 0.1F);
                    model.head.setRotation(pitch, yaw, 0.15F);
                    var expectedStack = new PoseStack();
                    model.head.translateAndRotate(expectedStack);
                    var expectedMatrix = new Matrix4f(expectedStack.last().pose());
                    var points = new ArrayList<Vector3f>();
                    var normals = new ArrayList<Vector3f>();
                    var consumer = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{VertexConsumer.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("addVertex") && arguments.length == 4) {
                                    points.add(new Vector3f((float) arguments[1], (float) arguments[2], (float) arguments[3])
                                            .mulPosition((Matrix4f) arguments[0]));
                                } else if (method.getName().equals("setNormal") && arguments.length == 4) {
                                    normals.add(new Vector3f((float) arguments[1], (float) arguments[2], (float) arguments[3]));
                                }
                                return method.getReturnType() == void.class ? null : proxy;
                            });
                    var actualStack = new PoseStack();
                    var before = new Matrix4f(actualStack.last().pose());
                    layers.renderHeadAttached(actualStack, consumer, 0, 0, model.head, showHat);
                    assertTrue(before.equals(actualStack.last().pose()), "Hair render restores its parent pose");
                    int index = 0;
                    for (var layer : visible) {
                        if (!showHat && layer.v0() >= 16) continue;
                        float firstX = layer.mirrorU() ? layer.x1() : layer.x0();
                        float firstZ = layer.mirrorU() ? layer.z1() : layer.z0();
                        float secondX = layer.mirrorU() ? layer.x0() : layer.x1();
                        float secondZ = layer.mirrorU() ? layer.z0() : layer.z1();
                        int vertex = 0;
                        for (var local : List.of(new Vector3f(firstX, layer.y0(), firstZ), new Vector3f(secondX, layer.y0(), secondZ),
                                new Vector3f(secondX, layer.y0(), secondZ), new Vector3f(firstX, layer.y0(), firstZ))) {
                            var expected = local.div(16).mulPosition(expectedMatrix);
                            if (vertex++ >= 2) expected.add(0, (layer.y1() - layer.y0()) * model.head.yScale / 16, 0);
                            assertTrue(expected.equals(points.get(index), 0.000001F), name + " / " + age + " / vertex " + index);
                            assertEquals(1, normals.get(index).length(), 0.000001);
                            index++;
                        }
                        var firstDrop = new Vector3f(points.get(index - 1)).sub(points.get(index - 4));
                        var secondDrop = new Vector3f(points.get(index - 2)).sub(points.get(index - 3));
                        var expectedDrop = new Vector3f(0, (layer.y1() - layer.y0()) * model.head.yScale / 16, 0);
                        assertTrue(expectedDrop.equals(firstDrop, 0.000001F), name + " / first tip retains vertical gravity");
                        assertTrue(expectedDrop.equals(secondDrop, 0.000001F), name + " / second tip retains vertical gravity");
                    }
                    assertEquals(index, points.size());
                }
            }
        }
    }

    @Test
    void citizenSpawnEggUsesVanillaModelAndBothLanguages() throws Exception {
        var model = JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/models/item/citizen_spawn_egg.json"))).getAsJsonObject();
        assertEquals("minecraft:item/template_spawn_egg", model.get("parent").getAsString());
        for (var pair : java.util.Map.of("en_us", "Citizen Spawn Egg", "ja_jp", "市民のスポーンエッグ").entrySet()) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + pair.getKey() + ".json"))).getAsJsonObject();
            assertEquals(pair.getValue(), language.get("item.createapparel.citizen_spawn_egg").getAsString());
        }
    }
}
