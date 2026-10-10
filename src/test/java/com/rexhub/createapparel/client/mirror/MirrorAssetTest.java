package com.rexhub.createapparel.client.mirror;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class MirrorAssetTest {
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(Files.readString(GENERATED.resolve(path + ".json"))).getAsJsonObject();
    }

    @Test void mirrorItemsUseDedicatedOpaquePixelTextureInsteadOfIronBlock() throws IOException {
        var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/block/mirror_surface.png").toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) assertEquals(255, image.getRGB(x, y) >>> 24);
        assertEquals("createapparel:block/mirror_surface", read("assets/createapparel/models/block/mirror_panel")
                .getAsJsonObject("textures").get("mirror").getAsString());
        assertEquals("createapparel:block/mirror_surface", read("assets/createapparel/models/block/mirror_block")
                .getAsJsonObject("textures").get("all").getAsString());
        assertEquals("createapparel:block/mirrored_door_item", read("assets/createapparel/models/item/mirrored_door")
                .get("parent").getAsString());
        assertEquals("createapparel:block/mirror_surface", read("assets/createapparel/models/block/mirrored_door_item")
                .getAsJsonObject("textures").get("mirror").getAsString());
        for (String wood : WOODS) for (String suffix : List.of("_full_length_mirror_item", "_full_length_mirror_lower",
                "_full_length_mirror_upper", "_table_mirror")) {
            var textures = read("assets/createapparel/models/block/" + wood + suffix).getAsJsonObject("textures");
            assertEquals("createapparel:block/mirror_surface", textures.get("mirror").getAsString());
            assertEquals("minecraft:block/" + wood + "_planks", textures.get("wood").getAsString());
        }
    }

    @Test void reflectionUsesUnclippedProjectionForCullingButClippedProjectionForDrawing() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mirror/MirrorRenderManager.java"));
        assertTrue(manager.contains("MirrorReflectionMath.croppedCullingProjection(captureProjection,"));
        assertTrue(manager.contains("renderer.prepareCullFrustum(pose, reflectedEye, cullProjection)"));
        assertFalse(manager.contains("renderer.prepareCullFrustum(pose, reflectedEye, clipped)"));
        assertTrue(manager.contains("gameRenderer.resetProjectionMatrix(clipped)"));
        assertTrue(manager.contains("renderer.renderLevel(pose, partialTick, finishTime, false, captureCamera, gameRenderer, light, clipped)"));
        String mixin = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mixin/MirrorLevelRendererMixin.java"));
        assertFalse(mixin.contains("offsetToFullyIncludeCameraCube"));
    }

    @Test void recursionUsesPathLocalCachesAndRestoresTheParentTargetAndCamera() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mirror/MirrorRenderManager.java"));
        for (String expected : List.of("private final RenderView children", "activeView = cache.children",
                "activeView = originalView", "reflectionTarget = originalReflectionTarget", "reflectionCamera = originalCamera",
                "MirrorRenderPolicy.canRenderReflection(activeView.depth, recursionDepth)", "activeTargets.contains(cache.target)",
                "MirrorRenderPolicy.canDrawCachedReflection(activeView.depth, cache.updated, frame)",
                "remainingReflectionPasses--", "remainingReflectionPasses == 0")) {
            assertTrue(manager.contains(expected), expected);
        }
        assertFalse(manager.contains("!activeView.collecting || isRenderingReflection()"));
        String mixin = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mixin/MirrorLevelRendererMixin.java"));
        assertTrue(mixin.contains("Deque<Snapshot>"));
        assertTrue(mixin.contains("createapparel$reflectionStates.push("));
        assertTrue(mixin.contains("createapparel$reflectionStates.pop()"));
        assertTrue(mixin.contains("createapparel$reflectionChunkLists.pollFirst()"));
        assertTrue(mixin.contains("previous.chunks().clear()"));
        assertTrue(mixin.contains("createapparel$reflectionChunkLists.addFirst(previous.chunks())"));
    }

    @Test void reflectionRebindsViewportAfterOutlineTargetAndRestoresMainRenderState() throws IOException {
        String pool = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mirror/MirrorFramebufferPool.java"));
        assertTrue(pool.contains("new ReflectionTarget(width, height)"));
        assertTrue(pool.contains("void bindWrite(boolean setViewport) { super.bindWrite(true); }"));
        assertTrue(pool.contains("GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw)"));
        assertTrue(pool.contains("GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read)"));
        String manager = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/mirror/MirrorRenderManager.java"));
        for (String restore : List.of("Lighting.setupLevel(mainView.last().pose())", "RenderSystem.setShaderFogStart(fogStart)",
                "originalTarget.bindWrite(true)", "rendererState.createapparel$endReflection()", "RenderSystem.setInverseViewRotationMatrix(inverseRotation)"))
            assertTrue(manager.contains(restore));
    }

    @Test void allElevenWoodsHaveTwoFurnitureItemsAndRecipesUsingTheirOwnPlanks() throws IOException {
        for (String wood : WOODS) for (String furniture : List.of("full_length_mirror", "table_mirror")) {
            String id = wood + "_" + furniture;
            var recipe = read("data/createapparel/recipes/" + id);
            assertEquals("minecraft:" + wood + "_planks", recipe.getAsJsonObject("key")
                    .getAsJsonObject(furniture.equals("table_mirror") ? "B" : "W").get("item").getAsString());
            assertEquals("createapparel:mirror_panel", recipe.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
            String parent = read("assets/createapparel/models/item/" + id).get("parent").getAsString();
            assertTrue(Files.isRegularFile(GENERATED.resolve("assets/createapparel/models/" + parent.split(":")[1] + ".json")));
            var blockstate = read("assets/createapparel/blockstates/" + id).getAsJsonObject("variants");
            assertTrue(blockstate.has("facing=north,half=lower"));
            assertTrue(blockstate.has("facing=south,half=upper"));
            assertTrue(Files.isRegularFile(GENERATED.resolve("data/createapparel/loot_tables/blocks/" + id + ".json")));
        }
    }

    @Test void allWoodDoorsUseTheirOwnDoorRecipeFrameAndDoubleSidedMirror() throws IOException {
        for (String wood : WOODS) {
            String id = wood + "_mirrored_door";
            var recipe = read("data/createapparel/recipes/" + id);
            assertEquals("minecraft:" + wood + "_door", recipe.getAsJsonArray("ingredients").get(0)
                    .getAsJsonObject().get("item").getAsString());
            assertEquals(3, recipe.getAsJsonArray("ingredients").size());
            assertEquals("createapparel:block/" + id + "_item", read("assets/createapparel/models/item/" + id).get("parent").getAsString());
            var variants = read("assets/createapparel/blockstates/" + id).getAsJsonObject("variants");
            assertEquals(32, variants.size());
            for (String suffix : List.of("_lower", "_upper", "_item")) {
                var model = read("assets/createapparel/models/block/" + id + suffix);
                assertEquals("minecraft:block/" + wood + "_planks", model.getAsJsonObject("textures").get("frame").getAsString());
                var elements = model.getAsJsonArray("elements");
                assertTrue(elements.get(1).getAsJsonObject().getAsJsonArray("from").get(2).getAsDouble() < 13);
                assertTrue(elements.get(2).getAsJsonObject().getAsJsonArray("to").get(2).getAsDouble() > 16);
            }
        }
    }

    @Test void furnitureModelsRotateActualElementsAtExactAnglesAndRetainMirrorDimensions() throws IOException {
        for (String wood : java.util.stream.Stream.concat(WOODS.stream(), java.util.stream.Stream.of("iron")).toList()) {
            var full = read("assets/createapparel/models/block/" + wood + "_full_length_mirror_item");
            var table = read("assets/createapparel/models/block/" + wood + "_table_mirror");
            for (var model : List.of(full, table)) {
                assertEquals("createapparel:mirror_tilt", model.get("loader").getAsString());
                var elements = model.getAsJsonArray("elements");
                assertEquals(model == full ? 7 : 5, elements.size());
                assertTrue(elements.get(0).getAsJsonObject().getAsJsonObject("mirror_rotation").get("angle").getAsFloat() < 0);
                assertEquals(model == full ? 10 : 15, elements.get(3).getAsJsonObject().getAsJsonObject("mirror_rotation").get("angle").getAsInt());
                for (var element : elements) assertTrue(element.getAsJsonObject().has("mirror_rotation"));
            }
            var lower = full.getAsJsonArray("elements").get(4).getAsJsonObject();
            var upper = full.getAsJsonArray("elements").get(6).getAsJsonObject();
            assertEquals(12, lower.getAsJsonArray("to").get(0).getAsInt() - lower.getAsJsonArray("from").get(0).getAsInt());
            assertEquals(28, upper.getAsJsonArray("to").get(1).getAsInt() - lower.getAsJsonArray("from").get(1).getAsInt());
            var face = table.getAsJsonArray("elements").get(4).getAsJsonObject();
            assertEquals(8, face.getAsJsonArray("to").get(0).getAsInt() - face.getAsJsonArray("from").get(0).getAsInt());
            assertEquals(8, face.getAsJsonArray("to").get(1).getAsInt() - face.getAsJsonArray("from").get(1).getAsInt());
        }
    }

    @Test void panelInventoryModelIsCenteredWithoutMovingTheWallPanel() throws IOException {
        var item = read("assets/createapparel/models/item/mirror_panel");
        assertEquals("createapparel:block/mirror_panel_item", item.get("parent").getAsString());
        var element = read("assets/createapparel/models/block/mirror_panel_item").getAsJsonArray("elements").get(0).getAsJsonObject();
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(8, (element.getAsJsonArray("from").get(axis).getAsDouble()
                    + element.getAsJsonArray("to").get(axis).getAsDouble()) / 2);
        }
        var wall = read("assets/createapparel/models/block/mirror_panel").getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(15, wall.getAsJsonArray("from").get(2).getAsInt());
        assertEquals(16, wall.getAsJsonArray("to").get(2).getAsInt());
    }

    @Test void ironFurnitureUsesIronFramesRecipesLootAndPickaxes() throws IOException {
        var pickaxes = read("data/minecraft/tags/blocks/mineable/pickaxe").getAsJsonArray("values");
        for (String kind : List.of("full_length_mirror", "table_mirror")) {
            String id = "iron_" + kind;
            var recipe = read("data/createapparel/recipes/" + id);
            assertEquals(kind.equals("table_mirror") ? "minecraft:iron_block" : "minecraft:iron_ingot",
                    recipe.getAsJsonObject("key").getAsJsonObject(kind.equals("table_mirror") ? "B" : "W")
                            .get("item").getAsString());
            assertEquals("createapparel:mirror_panel", recipe.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
            String model = id + (kind.equals("full_length_mirror") ? "_item" : "");
            assertEquals("minecraft:block/iron_block", read("assets/createapparel/models/block/" + model)
                    .getAsJsonObject("textures").get("wood").getAsString());
            assertTrue(pickaxes.asList().stream().anyMatch(value -> value.getAsString().equals("createapparel:" + id)));
            assertTrue(Files.isRegularFile(GENERATED.resolve("data/createapparel/loot_tables/blocks/" + id + ".json")));
            assertTrue(read("assets/createapparel/lang/ja_jp").has("block.createapparel." + id));
        }
    }

    @Test void furnitureSupportHasTwoSideRailsAndAnOpenCenterJoinedAtTheFoot() throws IOException {
        for (String material : java.util.stream.Stream.concat(WOODS.stream(), java.util.stream.Stream.of("iron")).toList()) {
            for (String kind : List.of("full_length_mirror_item", "table_mirror")) {
                var elements = read("assets/createapparel/models/block/" + material + "_" + kind).getAsJsonArray("elements");
                var left = elements.get(0).getAsJsonObject();
                var right = elements.get(1).getAsJsonObject();
                var foot = elements.get(2).getAsJsonObject();
                assertEquals(left.getAsJsonArray("to").get(0), foot.getAsJsonArray("from").get(0));
                assertEquals(right.getAsJsonArray("from").get(0), foot.getAsJsonArray("to").get(0));
                assertEquals(0, foot.getAsJsonArray("from").get(1).getAsInt());
                assertEquals(1, foot.getAsJsonArray("to").get(1).getAsInt());
                assertEquals(left.getAsJsonObject("mirror_rotation"), right.getAsJsonObject("mirror_rotation"));
                assertEquals(left.getAsJsonObject("mirror_rotation"), foot.getAsJsonObject("mirror_rotation"));
            }
        }
    }

    @Test void loweredFurnitureUvsStayWithinTheirOwnAtlasSprite() throws IOException {
        for (String wood : WOODS) for (String kind : List.of("full_length_mirror_lower", "full_length_mirror_upper",
                "full_length_mirror_item", "table_mirror")) {
            var model = read("assets/createapparel/models/block/" + wood + "_" + kind);
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                    if (uv == null) continue;
                    for (var coordinate : uv) assertTrue(coordinate.getAsFloat() >= 0 && coordinate.getAsFloat() <= 16);
                }
            }
        }
    }

    @Test void spoutUses250MbAndFallbackMixingIsHeatedAndConfigConditional() throws IOException {
        var filling = read("data/createapparel/recipes/filling/mirror_panel");
        assertEquals("create:filling", filling.get("type").getAsString());
        assertEquals("minecraft:glass_pane", filling.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        var fluid = filling.getAsJsonArray("ingredients").get(1).getAsJsonObject();
        assertEquals(250, fluid.get("amount").getAsInt());
        assertEquals("createapparel:mirror_coating_fluid", fluid.get("fluid").getAsString());
        var mixing = read("data/createapparel/recipes/mixing/mirror_coating_fluid");
        assertEquals("heated", mixing.get("heatRequirement").getAsString());
        assertEquals("createapparel:mirror_coating_fallback", mixing.getAsJsonArray("conditions").get(0).getAsJsonObject().get("type").getAsString());
        var ingredients = mixing.getAsJsonArray("ingredients");
        assertEquals("create:zinc_nugget", ingredients.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:glowstone_dust", ingredients.get(1).getAsJsonObject().get("item").getAsString());
        assertEquals(1000, ingredients.get(2).getAsJsonObject().get("amount").getAsInt());
        var result = mixing.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals(1000, result.get("amount").getAsInt());
        assertEquals(fluid.get("fluid").getAsString(), result.get("fluid").getAsString());
    }

    @Test void fluidBucketAndShaderResourcesAreShippedWithMatchingUniforms() throws IOException {
        var bucket = read("assets/createapparel/models/item/mirror_coating_fluid_bucket");
        assertEquals("minecraft:item/generated", bucket.get("parent").getAsString());
        assertEquals("createapparel:item/mirror_coating_fluid_bucket", bucket.getAsJsonObject("textures").get("layer0").getAsString());
        var shaderDir = Path.of("src/main/resources/assets/createapparel/shaders/core");
        var shader = JsonParser.parseString(Files.readString(shaderDir.resolve("mirror.json"))).getAsJsonObject();
        assertEquals("createapparel:mirror", shader.get("vertex").getAsString());
        assertEquals("createapparel:mirror", shader.get("fragment").getAsString());
        assertTrue(Files.readString(shaderDir.resolve("mirror.vsh")).contains("uniform mat4 ReflectionMatrix"));
        assertTrue(Files.readString(shaderDir.resolve("mirror.fsh")).contains("reflectionPosition.xy / reflectionPosition.w"));
        assertTrue(Files.readString(shaderDir.resolve("mirror.fsh")).contains("clamp(uv, ReflectionBounds.xy, ReflectionBounds.zw)"));
        assertTrue(shader.getAsJsonArray("uniforms").asList().stream()
                .anyMatch(uniform -> uniform.getAsJsonObject().get("name").getAsString().equals("ReflectionBounds")));
    }

    @Test void fluidBucketOnlyRecolorsVanillaWaterPixelsAndPreservesItsMetalAndAlpha() throws IOException {
        var texture = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/mirror_coating_fluid_bucket.png").toFile());
        try (var stream = getClass().getResourceAsStream("/texture_reference/water_bucket.png")) {
            assertNotNull(stream);
            var vanilla = ImageIO.read(stream);
            assertEquals(16, texture.getWidth());
            assertEquals(16, texture.getHeight());
            var replacements = java.util.Map.of(0xFF234FCC, 0xFF8EA7AE, 0xFF2E58D3, 0xFFADC2C8,
                    0xFF345FDA, 0xFFC3D3D8, 0xFF446FE9, 0xFFDEE7EA, 0xFF5A82F3, 0xFFF2F6F7);
            int changed = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int original = vanilla.getRGB(x, y);
                int actual = texture.getRGB(x, y);
                assertEquals(replacements.getOrDefault(original, original).intValue(), actual, x + "/" + y);
                assertEquals(original >>> 24, actual >>> 24);
                if (original != actual) changed++;
            }
            assertEquals(24, changed);
        }
    }

    @Test void translationsMatchAcrossLanguagesAndPreserveUtf8WithoutBom() throws IOException {
        var english = read("assets/createapparel/lang/en_us");
        var japanese = read("assets/createapparel/lang/ja_jp");
        assertEquals(english.keySet(), japanese.keySet());
        for (String wood : WOODS) for (String kind : List.of("full_length_mirror", "table_mirror")) {
            assertTrue(japanese.has("block.createapparel." + wood + "_" + kind));
        }
        for (String language : List.of("ja_jp", "en_us")) {
            byte[] bytes = Files.readAllBytes(GENERATED.resolve("assets/createapparel/lang/" + language + ".json"));
            assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF);
        }
    }
}
