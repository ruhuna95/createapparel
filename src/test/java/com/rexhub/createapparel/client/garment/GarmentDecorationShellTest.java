package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GarmentDecorationShellTest {
    private static final Path PARTS = Path.of("src/main/resources/assets/createapparel/garment_models/parts");
    private static final float EPSILON = 0.00001F;

    @Test
    void bustNorthAndDownShareAnOuterEdgeBeforeAndAfterRotation() throws IOException {
        for (String name : List.of("shirt/bust", "shirt/bust_opened", "blouse/bust", "hoodie/bust_pullover")) {
            var part = part(name);
            var body = part.elements().get(0);
            var front = part.elements().stream().skip(1).filter(e -> e.faces().containsKey(Face.FRONT)).findFirst().orElseThrow();
            var bottom = part.elements().stream().skip(1).filter(e -> e.faces().containsKey(Face.BOTTOM)).findFirst().orElseThrow();
            assertEquals(front.from(), bottom.from(), name);
            assertEquals(front.to(), bottom.to(), name);
            assertEquals(body.rotation(), front.rotation(), name);
            assertEquals(body.from().x() - 0.15F, front.from().x(), EPSILON, name);
            assertEquals(body.from().y() - 0.15F, front.from().y(), EPSILON, name);
            assertEquals(body.from().z() - 0.15F, front.from().z(), EPSILON, name);
            assertEquals(body.to().x() + 0.15F, front.to().x(), EPSILON, name);
            assertEquals(body.to().y(), front.to().y(), EPSILON, name);
            assertEquals(body.to().z(), front.to().z(), EPSILON, name);
            assertJoinedEdge(front, bottom);
        }
    }

    @Test
    void outerPlacketJoinsItsBottomAndCollarWithoutClosingTheFront() throws IOException {
        var part = part("shirt_outer/bust");
        var front = element(part, "bust_left_placket");
        var bottom = element(part, "bust_left_placket_bottom");
        var collar = element(part, "bust_left_collar");
        assertJoinedEdge(front, bottom);
        assertEquals(front.from(), bottom.from());
        assertEquals(front.to(), bottom.to());
        assertEquals(collar.from().y(), front.to().y());
        assertEquals(collar.from().x(), front.from().x());
        assertEquals(collar.from().z(), front.from().z());
        assertTrue(front.to().x() <= collar.to().x());
        assertEquals(18.75F, front.from().y());
        assertEquals(-2.14F, front.from().z());
        assertEquals(1.25F, front.from().x());
        assertEquals(4.25F, collar.to().x());
        assertEquals(-1.25F, element(part, "bust_right_collar").to().x());
    }

    @Test
    void ordinaryShellsKeepTheirMinimalFacesAndLayerSpacing() throws IOException {
        try (var paths = Files.walk(PARTS)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = PARTS.relativize(path).toString().replace('\\', '/').replace(".json", "");
                var part = part(name);
                boolean footwear = name.startsWith("shoes/") || name.startsWith("boots/")
                        || name.startsWith("tall_boots/") || name.startsWith("low_shoes/");
                if (footwear) continue;
                float spacing = name.startsWith("shirt_outer/") || name.startsWith("hoodie_outer/")
                        || name.startsWith("jacket/") || name.startsWith("denim_jacket/") || name.startsWith("coat/")
                        || name.startsWith("denim_pants/") || name.startsWith("shorts/") ? 0.25F : 0.15F;
                for (var shell : part.elements()) {
                    if (!shell.name().endsWith("_decoration") || shell.name().contains("bust")) {
                        continue;
                    }
                    var body = part.elements().stream()
                            .filter(e -> e.name().equals(shell.name().replace("_decoration", "")))
                            .findFirst().orElse(part.elements().get(0));
                    boolean tapered = name.equals("blouse/tucked_torso");
                    float lowerSpacing = tapered ? 0.25F : spacing;
                    assertEquals(body.from().z() - lowerSpacing, shell.from().z(), EPSILON, name);
                    assertEquals(body.to().z() + lowerSpacing, shell.to().z(), EPSILON, name);
                    assertEquals(body.corners().get(4).z() - spacing, shell.corners().get(4).z(), EPSILON, name);
                    assertEquals(body.to().y(), shell.to().y(), EPSILON, name);
                    if (shell.name().contains("sleeve")) {
                        assertEquals(body.from().y() - spacing, shell.from().y(), EPSILON, name);
                        if (body.from().x() > 0) {
                            assertEquals(4F - 0.0055F, shell.from().x(), EPSILON, name);
                            assertEquals(body.to().x() + spacing, shell.to().x(), EPSILON, name);
                            assertTrue(shell.faces().containsKey(Face.LEFT), name);
                        } else {
                            assertEquals(-4F + 0.0055F, shell.to().x(), EPSILON, name);
                            assertEquals(body.from().x() - spacing, shell.from().x(), EPSILON, name);
                            assertTrue(shell.faces().containsKey(Face.RIGHT), name);
                        }
                    } else if (shell.name().contains("leg")) {
                        boolean shorts = name.startsWith("shorts/");
                        float westSpacing = shorts && body.from().x() > -1 ? 0 : spacing;
                        float eastSpacing = shorts && body.to().x() < 1 ? 0 : spacing;
                        assertEquals(body.from().x() - westSpacing, shell.from().x(), EPSILON, name);
                        assertEquals(body.to().x() + eastSpacing, shell.to().x(), EPSILON, name);
                        assertEquals(body.from().y(), shell.from().y(), EPSILON, name);
                    } else {
                        boolean ribSides = shell.faces().containsKey(Face.LEFT) && shell.faces().containsKey(Face.RIGHT);
                        float ribOffset = ribSides ? 0.0005F : 0;
                        assertEquals(body.from().x() - ribOffset, shell.from().x(), EPSILON, name);
                        assertEquals(body.to().x() + ribOffset, shell.to().x(), EPSILON, name);
                        assertEquals(body.from().y(), shell.from().y());
                    }
                    assertFalse(shell.faces().containsKey(Face.TOP));
                    assertFalse(shell.faces().containsKey(Face.BOTTOM), name);
                }
            }
        }
    }

    private static void assertJoinedEdge(Element front, Element bottom) {
        var north = GarmentElementGeometry.faceVertices(front, Face.FRONT);
        var down = GarmentElementGeometry.faceVertices(bottom, Face.BOTTOM);
        assertEquals(front.rotation(), bottom.rotation());
        assertEquals(north.get(0), down.get(1));
        assertEquals(north.get(3), down.get(2));
        for (int i = 0; i < 2; i++) {
            Vec3 a = FlatGarmentGeometry.rotate(north.get(i == 0 ? 0 : 3), front.rotation());
            Vec3 b = FlatGarmentGeometry.rotate(down.get(i + 1), bottom.rotation());
            assertEquals(a, b);
        }
    }

    private static Element element(GarmentPartModel part, String name) {
        return part.elements().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(PARTS.resolve(name + ".json"))).getAsJsonObject());
    }
}
