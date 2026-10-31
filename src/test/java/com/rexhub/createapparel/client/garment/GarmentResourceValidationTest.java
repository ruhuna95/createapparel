package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.validation.ProjectResources;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GarmentResourceValidationTest {
    private static final Map<String, Set<String>> STYLES = Map.of(
            "tuck", Set.of("untucked", "tucked"), "open", Set.of("closed", "opened", "open"),
            "sleeves", Set.of("normal", "rolled"), "hood", Set.of("down", "up"),
            "wear", Set.of("front", "back", "down", "up", "left", "right"), "carry", Set.of("back", "front"));
    private static final Map<ResourceLocation, GarmentDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ClientGarmentAssets.GarmentModelTemplate> TEMPLATES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ClientGarmentAssets.GarmentPartModel> PARTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BufferedImage> IMAGES = new HashMap<>();

    @BeforeAll
    static void load() throws Exception {
        String directory = "data/createapparel/garments";
        for (Path file : ProjectResources.files(directory, ".json")) {
            var id = ProjectResources.id(file, directory, ".json");
            DEFINITIONS.put(id, GarmentDefinition.fromJson(id, ProjectResources.json(file)));
        }
        loadAssets("garment_models", TEMPLATES, PARTS);
        assertFalse(DEFINITIONS.isEmpty());
        assertFalse(TEMPLATES.isEmpty());
        assertFalse(PARTS.isEmpty());
    }

    @Test
    void everyDefinitionResolvesAllSlotsStylesProfilesAndDesigns() {
        for (var definition : DEFINITIONS.values()) {
            var template = TEMPLATES.get(definition.modelTemplate());
            assertNotNull(template, "Missing template: " + definition.id());
            template.stylesForItem(definition);
            for (var slot : definition.allowedSlots()) {
                var variantNames = new java.util.LinkedHashSet<String>();
                variantNames.add(definition.modelVariantFor(slot));
                definition.contextModelVariants().forEach(rule -> variantNames.add(rule.modelVariant()));
                for (var variantName : variantNames) {
                    var variant = template.variants().get(variantName);
                    assertNotNull(variant, "Missing model variant: " + definition.id() + "/" + slot);
                    definition.stylesFor(slot).forEach((category, values) -> {
                        assertTrue(STYLES.containsKey(category), "Unknown style category: " + definition.id() + "/" + category);
                        assertTrue(STYLES.get(category).containsAll(values), "Unknown style value: " + definition.id() + "/" + values);
                    });
                    assertTrue(variant.keySet().containsAll(GarmentPartSelection.requiredStyleParts(
                            definition.stylesFor(slot), variant.containsKey("bust"))), "Missing style parts: " + definition.id());
                    for (var partId : variant.values()) {
                        var part = PARTS.get(partId);
                        assertNotNull(part, "Missing part: " + definition.id() + " -> " + partId);
                        assertTrue(part.variants().containsKey(definition.textureVariant()) || part.variants().containsKey("default"),
                                "Missing texture variant: " + definition.id() + " -> " + partId);
                    }
                    for (var styles : combinations(definition.stylesFor(slot))) {
                        for (var arms : ArmProfile.values()) for (var chest : ChestProfile.values()) {
                            var selected = GarmentPartSelection.select(styles, arms, chest, variant.keySet());
                            assertFalse(selected.isEmpty(), definition.id().toString());
                            assertTrue(variant.keySet().containsAll(selected),
                                    () -> "Unresolved selected parts: " + definition.id() + "/" + slot + "/" + styles + " -> " + selected);
                        }
                    }
                    for (String design : definition.designs()) {
                        assertTrue(design.equals("plain") || variant.values().stream()
                                .anyMatch(id -> PARTS.get(id).designsFor(definition.textureVariant()).containsKey(design)),
                                "Design has no reachable texture: " + definition.id() + "/" + design);
                    }
                }
            }
            ProjectResources.find("assets/{namespace}/models/item", definition.id(), ".json");
        }
    }

    @Test
    void everyTemplatePartAndTexturePassesValidationIncludingUnusedVariantsAndBodyAssets() throws Exception {
        validateAssets(TEMPLATES, PARTS, false);
        var bodyTemplates = new LinkedHashMap<ResourceLocation, ClientGarmentAssets.GarmentModelTemplate>();
        var bodyParts = new LinkedHashMap<ResourceLocation, ClientGarmentAssets.GarmentPartModel>();
        loadAssets("body_models", bodyTemplates, bodyParts);
        assertFalse(bodyTemplates.isEmpty());
        assertFalse(bodyParts.isEmpty());
        validateAssets(bodyTemplates, bodyParts, true);
        var body = bodyTemplates.get(ResourceLocation.parse("createapparel:body_base"));
        assertNotNull(body);
        for (var arms : ArmProfile.values()) for (var chest : ChestProfile.values()) {
            var variant = body.variants().get(arms.getSerializedName() + "_" + chest.getSerializedName());
            assertNotNull(variant);
            assertTrue(variant.keySet().containsAll(Set.of("torso", "left_arm", "right_arm", "left_leg", "right_leg")));
            if (chest == ChestProfile.BUST) assertTrue(variant.containsKey("bust"));
        }
    }

    @Test
    void everySewingDesignExposesTheUnionOfResolvedTextureColorChannels() throws Exception {
        int recipes = 0;
        for (Path file : ProjectResources.files("data/createapparel/recipe", ".json")) {
            var json = ProjectResources.json(file);
            if (!json.get("type").getAsString().equals("createapparel:sewing")) continue;
            recipes++;
            var definition = DEFINITIONS.get(ResourceLocation.parse(json.getAsJsonObject("result").get("id").getAsString()));
            assertNotNull(definition, "Missing sewing definition: " + file);
            for (var design : definition.designs()) {
                var appearance = new GarmentData(FabricMaterial.COTTON, design, FabricColor.WHITE,
                        Optional.of(FabricColor.NAVY), Optional.of(FabricColor.PLUM));
                boolean secondary = false;
                boolean accent = false;
                for (var slot : definition.allowedSlots()) {
                    var variant = TEMPLATES.get(definition.modelTemplate()).variants().get(definition.modelVariantFor(slot));
                    boolean expectedSecondary = false;
                    boolean expectedAccent = false;
                    for (var id : variant.values()) {
                        var textures = PARTS.get(id).resolveTextures(definition, appearance);
                        expectedSecondary |= textures.secondary().isPresent();
                        expectedAccent |= textures.accent().isPresent();
                    }
                    var channels = ClientGarmentAssets.colorChannels(variant, definition.textureVariant(), design, PARTS);
                    assertEquals(expectedSecondary, channels.secondary(), definition.id() + "/" + design);
                    assertEquals(expectedAccent, channels.accent(), definition.id() + "/" + design);
                    secondary |= channels.secondary();
                    accent |= channels.accent();
                }
                var defaultVariant = TEMPLATES.get(definition.modelTemplate()).variants().get(definition.modelVariant());
                assertEquals(new ClientGarmentAssets.ColorChannels(secondary, accent), ClientGarmentAssets.colorChannels(
                        defaultVariant, definition.textureVariant(), design, PARTS), "Pattern channel union: " + definition.id() + "/" + design);
            }
        }
        assertTrue(recipes > 0);
    }

    @Test
    void garmentItemsDoNotAddDedicatedFlatTextures() throws Exception {
        Set<String> itemPaths = DEFINITIONS.keySet().stream().map(ResourceLocation::getPath)
                .collect(java.util.stream.Collectors.toSet());
        for (Path file : ProjectResources.files("assets/createapparel/textures/item", ".png")) {
            var id = ProjectResources.id(file, "assets/createapparel/textures/item", ".png");
            assertFalse(itemPaths.contains(id.getPath()), "Dedicated flat garment texture: " + file);
        }
    }

    private static List<Map<String, String>> combinations(Map<String, List<String>> choices) {
        List<Map<String, String>> result = List.of(Map.of());
        for (var entry : choices.entrySet()) {
            var expanded = new java.util.ArrayList<Map<String, String>>();
            for (var previous : result) for (var value : entry.getValue()) {
                var next = new LinkedHashMap<>(previous);
                next.put(entry.getKey(), value);
                expanded.add(Map.copyOf(next));
            }
            result = expanded;
        }
        return result;
    }

    private static void loadAssets(String group, Map<ResourceLocation, ClientGarmentAssets.GarmentModelTemplate> templates,
                                   Map<ResourceLocation, ClientGarmentAssets.GarmentPartModel> parts) throws Exception {
        String directory = "assets/createapparel/" + group + "/templates";
        for (Path file : ProjectResources.files(directory, ".json")) {
            var id = ProjectResources.id(file, directory, ".json");
            templates.put(id, ClientGarmentAssetLoader.parseTemplate(id, ProjectResources.json(file)));
        }
        directory = "assets/createapparel/" + group + "/parts";
        for (Path file : ProjectResources.files(directory, ".json")) {
            var id = ProjectResources.id(file, directory, ".json");
            parts.put(id, ClientGarmentAssetLoader.parsePart(id, ProjectResources.json(file)));
        }
    }

    private static void validateAssets(Map<ResourceLocation, ClientGarmentAssets.GarmentModelTemplate> templates,
                                        Map<ResourceLocation, ClientGarmentAssets.GarmentPartModel> parts,
                                        boolean body) throws Exception {
        for (var template : templates.values()) for (var variant : template.variants().values()) {
            for (var id : variant.values()) assertTrue(parts.containsKey(id), "Missing template part: " + template.id() + " -> " + id);
        }
        for (var part : parts.values()) {
            for (var element : part.elements()) for (var uv : element.faces().values()) {
                for (var corner : uv.corners()) {
                    assertTrue(Float.isFinite(corner.u()) && Float.isFinite(corner.v())
                                    && corner.u() >= 0 && corner.v() >= 0
                                    && corner.u() <= part.textureWidth() && corner.v() <= part.textureHeight(),
                            "UV outside texture: " + part.id() + "/" + element.name());
                }
            }
            boolean fixed = part.id().getPath().startsWith("default_underwear_");
            for (var variant : part.variants().values()) {
                texture(part, variant.base(), false, !body && !fixed, !fixed);
                if (variant.overlay().isPresent()) texture(part, variant.overlay().get(), false, false, false);
                if (variant.brightnessMask().isPresent()) texture(part, variant.brightnessMask().get(), true, false, true);
                assertTrue(GarmentTexturePixelOps.isValidBrightness(variant.brightness()), part.id().toString());
                if (variant.designs().isPresent()) validateDesigns(part, variant.designs().get());
            }
            validateDesigns(part, part.designs());
            for (var id : part.materialOverrides().values()) texture(part, id, false, !body && !fixed, !fixed);
        }
    }

    private static void validateDesigns(ClientGarmentAssets.GarmentPartModel part,
                                        Map<String, ClientGarmentAssets.DesignMasks> designs) throws Exception {
        for (var masks : designs.values()) {
            for (var id : List.of(masks.secondary(), masks.accent(), masks.brightnessMask())) {
                if (id.isPresent()) texture(part, id.get(), true, false, true);
            }
            for (var id : List.of(masks.overlay(), masks.commonOverlay())) {
                if (id.isPresent()) texture(part, id.get(), false, false, false);
            }
        }
    }

    private static void texture(ClientGarmentAssets.GarmentPartModel part, ResourceLocation id,
                                 boolean mask, boolean binary, boolean grayscale) throws Exception {
        var image = IMAGES.get(id);
        if (image == null) {
            image = ProjectResources.png(ProjectResources.find("assets/{namespace}/textures", id, ".png"));
            IMAGES.put(id, image);
        }
        assertEquals(part.textureWidth(), image.getWidth(), part.id() + " -> " + id);
        assertEquals(part.textureHeight(), image.getHeight(), part.id() + " -> " + id);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y);
            int alpha = pixel >>> 24;
            int red = pixel >> 16 & 255;
            if (grayscale) assertTrue(red == (pixel >> 8 & 255) && red == (pixel & 255), "Not grayscale: " + id);
            if (binary) assertTrue(alpha == 0 || alpha == 255, "Nonbinary base alpha: " + id);
            if (mask) assertEquals(255, alpha, "Nonopaque mask: " + id);
        }
    }
}
