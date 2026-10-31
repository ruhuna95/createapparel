package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HandAccessoryAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path GENERATED = Path.of("src/generated/resources/data/createapparel/recipe");

    @Test
    void wristStylesSelectOneHandAndGlovesSelectBothForEveryBodyProfile() throws Exception {
        for (String id : List.of("watch", "bracelet", "wristband", "gloves")) {
            var template = json(ASSETS.resolve("garment_models/templates/" + id + ".json"))
                    .getAsJsonObject("default");
            for (var profile : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    String left = "left_hand_" + profile.getSerializedName();
                    String right = "right_hand_" + profile.getSerializedName();
                    if (id.equals("gloves")) {
                        assertEquals(List.of(left, right), GarmentPartSelection.select(
                                Map.of(), profile, chest, template.keySet()));
                    } else {
                        for (String side : List.of("left", "right")) {
                            assertEquals(List.of(side + "_hand_" + profile.getSerializedName()),
                                    GarmentPartSelection.select(Map.of("wear", side), profile, chest, template.keySet()));
                        }
                        assertEquals(template.keySet(), GarmentPartSelection.requiredStyleParts(
                                Map.of("wear", List.of("left", "right")), false));
                    }
                }
            }
        }
    }

    @Test
    void handsFollowTheMatchingFirstPersonArmWithSleeves() throws Exception {
        for (String profile : List.of("classic", "slim")) {
            for (String side : List.of("left", "right")) {
                String semantic = side + "_hand_" + profile;
                var model = part("watch", semantic);
                var resolved = new ResolvedPart(semantic, model, new ResolvedTextures(model.id(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, Optional.empty()));
                var state = new FirstPersonApparelState(slot -> slot == GarmentSlot.HANDS
                        ? Optional.of(new ResolvedGarment(null, null, List.of(resolved))) : Optional.empty(), Optional::empty);
                var arm = side.equals("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                assertEquals(List.of(resolved), state.arm(1, arm).garments());
                assertTrue(state.arm(1, arm.getOpposite()).isEmpty());
            }
        }
    }

    @Test
    void geometryUsesHandTipLengthsAbsoluteOffsetsAndClosedGloveTips() throws Exception {
        for (String id : List.of("watch", "bracelet", "wristband", "gloves")) {
            for (String profile : List.of("classic", "slim")) {
                int width = profile.equals("classic") ? 4 : 3;
                for (String side : List.of("left", "right")) {
                    var model = part(id, side + "_hand_" + profile);
                    var element = model.elements().get(0);
                    assertEquals(id.equals("gloves") ? 11.76F : 13, element.from().y());
                    assertEquals(id.equals("wristband") ? 15 : 14, element.to().y());
                    assertEquals(-2.24F, element.from().z());
                    assertEquals(2.24F, element.to().z());
                    assertEquals(side.equals("left") ? 3.996F : -4 - width - 0.24F, element.from().x());
                    assertEquals(side.equals("left") ? 4 + width + 0.24F : -3.996F, element.to().x());
                    assertFalse(element.faces().containsKey(Face.TOP));
                    assertEquals(id.equals("gloves"), element.faces().containsKey(Face.BOTTOM));
                    if (id.equals("watch")) {
                        var dial = model.elements().stream().filter(value -> value.name().equals("watch_dial"))
                                .findFirst().orElseThrow();
                        assertEquals(1, dial.to().z() - dial.from().z());
                        assertEquals(1, dial.to().y() - dial.from().y());
                        assertEquals(Set.of(side.equals("left") ? Face.RIGHT : Face.LEFT), dial.faces().keySet());
                        assertEquals(element.from().x(), dial.from().x());
                        assertEquals(element.to().x(), dial.to().x());
                        assertTrue(element.faces().containsKey(Face.FRONT));
                        assertFalse(element.faces().containsKey(side.equals("left") ? Face.RIGHT : Face.LEFT));
                    } else {
                        assertEquals(1, model.elements().size());
                    }
                }
            }
        }
    }

    @Test
    void texturesHaveGrayscaleBinaryBaseOpaqueGemMaskAndOneFixedDialPixel() throws Exception {
        for (String id : List.of("watch", "bracelet", "wristband", "gloves")) {
            for (String profile : List.of("classic", "slim")) {
                var model = part(id, "left_hand_" + profile);
                var variant = model.variants().get(id);
                var base = ImageIO.read(ASSETS.resolve("textures/" + variant.base().getPath() + ".png").toFile());
                assertEquals(16, base.getWidth());
                assertEquals(16, base.getHeight());
                Set<Integer> shades = new java.util.HashSet<>();
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int pixel = base.getRGB(x, y);
                        assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                        assertEquals(pixel & 255, (pixel >> 8) & 255);
                        assertEquals(pixel & 255, (pixel >> 16) & 255);
                        if (pixel >>> 24 != 0) shades.add(pixel & 255);
                    }
                }
                assertTrue(shades.size() > 1, id + "/" + profile);
                if (id.equals("watch") || id.equals("bracelet")) {
                    for (String side : List.of("left", "right")) {
                        model = part(id, side + "_hand_" + profile);
                        variant = model.variants().get(id);
                        var reference = id.equals("watch") ? variant.overlay().orElseThrow()
                                : model.designsFor(id).get("plain").secondary().orElseThrow();
                        var texture = ImageIO.read(ASSETS.resolve("textures/" + reference.getPath() + ".png").toFile());
                        int active = 0;
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                int pixel = texture.getRGB(x, y);
                                if (id.equals("bracelet")) {
                                    assertEquals(255, pixel >>> 24);
                                    assertEquals(pixel & 255, (pixel >> 8) & 255);
                                    assertEquals(pixel & 255, (pixel >> 16) & 255);
                                }
                                if (id.equals("watch") ? pixel >>> 24 != 0 : (pixel & 255) != 0) {
                                    active++;
                                    assertEquals(side.equals("left") ? 6 + (profile.equals("classic") ? 4 : 3) : 1, x);
                                    assertEquals(4, y);
                                }
                            }
                        }
                        assertEquals(1, active);
                    }
                }
            }
        }
        var dial = ImageIO.read(ASSETS.resolve("textures/gui/watch_dial.png").toFile());
        assertEquals(32, dial.getWidth());
        assertEquals(32, dial.getHeight());
    }

    @Test
    void recipesKeepFabricCountsAndTheWatchCraftingPattern() throws Exception {
        for (String id : List.of("wristband", "gloves")) {
            var recipe = json(GENERATED.resolve(id + ".json"));
            assertEquals(1, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            assertEquals(JsonParser.parseString(id.equals("gloves")
                            ? "[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/wool\",\"createapparel:fabrics/leather\",\"createapparel:fabrics/synthetic\"]"
                            : "[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/synthetic\"]"),
                    recipe.getAsJsonObject("fabric").get("tag"));
        }
        try (var recipes = Files.list(GENERATED.resolve("watch"))) {
            var paths = recipes.toList();
            assertEquals(com.rexhub.createapparel.material.FabricColor.values().length, paths.size());
            for (Path path : paths) {
                var recipe = json(path);
                assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
                assertEquals(JsonParser.parseString("[\" C \",\"IPI\",\" D \"]"), recipe.get("pattern"));
                var key = recipe.getAsJsonObject("key");
                assertEquals("minecraft:clock", key.getAsJsonObject("C").get("item").getAsString());
                assertEquals("minecraft:iron_nugget", key.getAsJsonObject("I").get("item").getAsString());
                assertEquals("create:precision_mechanism", key.getAsJsonObject("P").get("item").getAsString());
                var color = com.rexhub.createapparel.material.FabricColor.fromSerializedName(
                        path.getFileName().toString().replace(".json", "")).orElseThrow();
                assertEquals((color.isVanilla() ? "minecraft:" : "createapparel:")
                                + color.getSerializedName() + "_dye",
                        key.getAsJsonObject("D").get("item").getAsString());
            }
        }
        assertFalse(Files.exists(GENERATED.resolve("watch.json")));
        assertFalse(Files.exists(GENERATED.resolve("bracelet.json")));
    }

    @Test
    void braceletBasesRequireTwoMatchingNuggets() throws Exception {
        for (var metal : Map.of("gold", "gold", "silver", "iron").entrySet()) {
            String baseId = metal.getKey() + "_bracelet_base";
            var recipe = json(GENERATED.resolve("compacting/" + baseId + ".json"));
            assertEquals("create:compacting", recipe.get("type").getAsString());
            var ingredients = recipe.getAsJsonArray("ingredients");
            assertEquals(2, ingredients.size(), baseId);
            for (var ingredient : ingredients) {
                if (metal.getKey().equals("silver")) {
                    assertEquals("createapparel:silver_accessory_nuggets", ingredient.getAsJsonObject().get("tag").getAsString());
                } else {
                    assertEquals("minecraft:gold_nugget", ingredient.getAsJsonObject().get("item").getAsString());
                }
            }
            var results = recipe.getAsJsonArray("results");
            assertEquals(1, results.size());
            assertEquals("createapparel:" + baseId,
                    results.get(0).getAsJsonObject().get("id").getAsString());
        }
    }

    @Test
    void silverAccessoryNuggetsKeepIronAndOptionallyIncludeCommonSilver() throws Exception {
        var tag = json(GENERATED.getParent().resolve("tags/item/silver_accessory_nuggets.json")).getAsJsonArray("values");
        assertEquals(2, tag.size());
        assertEquals("minecraft:iron_nugget", tag.get(0).getAsString());
        assertEquals("#c:nuggets/silver", tag.get(1).getAsJsonObject().get("id").getAsString());
        assertFalse(tag.get(1).getAsJsonObject().get("required").getAsBoolean());
    }

    private static GarmentPartModel part(String id, String semantic) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + id + "/" + semantic),
                json(ASSETS.resolve("garment_models/parts/" + id + "/" + semantic + ".json")));
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
