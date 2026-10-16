package com.rexhub.createapparel.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceIntegrityTest {
    @Test
    void everyJsonIsStrictUtf8WithoutDuplicateKeysOrTrailingContent() throws Exception {
        var files = new java.util.ArrayList<>(ProjectResources.files("", ".json"));
        files.addAll(ProjectResources.files("", ".mcmeta"));
        assertFalse(files.isEmpty());
        assertAll(files.stream().map(file -> () -> {
            try {
                assertFiniteNumbers(ProjectResources.json(file), file.toString());
            } catch (Exception exception) {
                fail("Invalid JSON: " + file, exception);
            }
        }));
    }

    @Test
    void everyPngHasAPngSignatureAndDecodes() throws Exception {
        var files = ProjectResources.files("", ".png");
        assertFalse(files.isEmpty());
        assertAll(files.stream().map(file -> () -> ProjectResources.png(file)));
    }

    @Test
    void handwrittenAndGeneratedResourcesNeverShadowEachOther() throws Exception {
        Set<Path> relative = new HashSet<>();
        for (String suffix : new String[]{".json", ".png", ".mcmeta", ".nbt"}) {
            for (Path file : ProjectResources.files("", suffix)) {
                Path root = ProjectResources.ROOTS.stream().filter(file::startsWith).findFirst().orElseThrow();
                assertTrue(relative.add(root.relativize(file)), "Duplicate resource: " + file);
            }
        }
    }

    @Test
    void vanillaModelsAndBlockstatesResolveLocalParentsTexturesAndOverridesWithoutCycles() throws Exception {
        String directory = "assets/createapparel/models";
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        for (Path file : ProjectResources.files(directory, ".json")) {
            models.put(ProjectResources.id(file, directory, ".json"), ProjectResources.json(file));
        }
        assertFalse(models.isEmpty());
        for (var entry : models.entrySet()) {
            var textures = inheritedTextures(entry.getKey(), models, new HashSet<>());
            for (var texture : textures.entrySet()) resolveTexture(texture.getValue(), textures, new HashSet<>());
            validateModelReferences(entry.getValue(), models, textures);
        }
        for (Path file : ProjectResources.files("assets/createapparel/blockstates", ".json")) {
            validateModelReferences(ProjectResources.json(file), models, Map.of());
        }
    }

    @Test
    void localRecipeAndItemTagReferencesExistAndTagsHaveNoCycles() throws Exception {
        Map<ResourceLocation, JsonObject> tags = new HashMap<>();
        String directory = "data/createapparel/tags/items";
        for (Path file : ProjectResources.files(directory, ".json")) {
            tags.put(ProjectResources.id(file, directory, ".json"), ProjectResources.json(file));
        }
        assertFalse(tags.isEmpty());
        for (var tag : tags.keySet()) validateTag(tag, tags, new HashSet<>());
        for (Path file : ProjectResources.files("data/createapparel/recipes", ".json")) {
            validateRecipeTags(ProjectResources.json(file), tags);
        }
    }

    @Test
    void invalidResourceFixturesAreRejected(@org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
        Path json = temporary.resolve("invalid.json");
        for (String invalid : new String[]{"{\"a\":1,\"a\":2}", "{} {}", "{\"a\":NaN}", "{\"a\":1,}", "{a:1}"}) {
            Files.writeString(json, invalid);
            assertThrows(Exception.class, () -> ProjectResources.json(json), invalid);
        }
        Files.write(json, new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'});
        assertThrows(AssertionError.class, () -> ProjectResources.json(json));
        Files.write(json, new byte[]{'{', '"', (byte) 0xC3, '"', ':', '1', '}'});
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> ProjectResources.json(json));
        Path png = temporary.resolve("fake.png");
        Files.writeString(png, "not a PNG");
        assertThrows(AssertionError.class, () -> ProjectResources.png(png));
        var id = ResourceLocation.parse("createapparel:broken");
        var model = new JsonObject();
        model.addProperty("parent", id.toString());
        assertThrows(AssertionError.class, () -> inheritedTextures(id, Map.of(id, model), new HashSet<>()));
        assertThrows(AssertionError.class, () -> inheritedTextures(id, Map.of(), new HashSet<>()));
        assertThrows(AssertionError.class, () -> resolveTexture("#missing", Map.of(), new HashSet<>()));
        assertThrows(AssertionError.class, () -> resolveTexture("#loop", Map.of("loop", "#loop"), new HashSet<>()));
        var tag = new JsonObject();
        var values = new com.google.gson.JsonArray();
        values.add("#" + id);
        tag.add("values", values);
        assertThrows(AssertionError.class, () -> validateTag(id, Map.of(id, tag), new HashSet<>()));
    }

    private static void assertFiniteNumbers(JsonElement value, String source) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> assertFiniteNumbers(child, source));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet()
                .forEach(entry -> assertFiniteNumbers(entry.getValue(), source + "/" + entry.getKey()));
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            assertTrue(Double.isFinite(value.getAsDouble()), "Nonfinite number: " + source);
        }
    }

    private static Map<String, String> inheritedTextures(ResourceLocation id, Map<ResourceLocation, JsonObject> models,
                                                          Set<ResourceLocation> chain) {
        assertTrue(chain.add(id), "Model parent cycle: " + chain + " -> " + id);
        JsonObject model = models.get(id);
        assertNotNull(model, "Missing local model: " + id);
        Map<String, String> textures = new HashMap<>();
        if (model.has("parent")) {
            var parent = ResourceLocation.parse(model.get("parent").getAsString());
            if (parent.getNamespace().equals("createapparel")) textures.putAll(inheritedTextures(parent, models, chain));
        }
        if (model.has("textures")) model.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        chain.remove(id);
        return textures;
    }

    private static void resolveTexture(String value, Map<String, String> textures, Set<String> chain) {
        if (value.startsWith("#")) {
            String key = value.substring(1);
            assertTrue(chain.add(key), "Texture alias cycle: " + chain);
            assertTrue(textures.containsKey(key), "Missing texture alias: " + value);
            resolveTexture(textures.get(key), textures, chain);
            chain.remove(key);
        } else {
            var id = ResourceLocation.parse(value);
            if (id.getNamespace().equals("createapparel")) ProjectResources.find("assets/{namespace}/textures", id, ".png");
        }
    }

    private static void validateModelReferences(JsonElement value, Map<ResourceLocation, JsonObject> models,
                                                Map<String, String> textures) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> validateModelReferences(child, models, textures));
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            Map<String, String> localTextures = new HashMap<>(textures);
            if (object.has("parent")) {
                var parent = ResourceLocation.parse(object.get("parent").getAsString());
                if (parent.getNamespace().equals("createapparel")) {
                    localTextures.putAll(inheritedTextures(parent, models, new HashSet<>()));
                }
            }
            if (object.has("textures")) {
                object.getAsJsonObject("textures").entrySet()
                        .forEach(entry -> localTextures.put(entry.getKey(), entry.getValue().getAsString()));
                object.getAsJsonObject("textures").entrySet().forEach(entry ->
                        resolveTexture(entry.getValue().getAsString(), localTextures, new HashSet<>()));
            }
            object.entrySet().forEach(entry -> {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                    var id = ResourceLocation.parse(entry.getValue().getAsString());
                    if (id.getNamespace().equals("createapparel")) {
                        if (id.getPath().endsWith(".obj")) validateObj(id);
                        else assertTrue(models.containsKey(id), "Missing model: " + id);
                    }
                } else if (entry.getKey().equals("texture") && entry.getValue().isJsonPrimitive()) {
                    resolveTexture(entry.getValue().getAsString(), localTextures, new HashSet<>());
                } else validateModelReferences(entry.getValue(), models, localTextures);
            });
        }
    }

    private static void validateObj(ResourceLocation id) {
        Path file = ProjectResources.find("assets/{namespace}", id, "");
        try {
            var lines = Files.readAllLines(file);
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("v ")), "OBJ has no vertices: " + id);
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("f ")), "OBJ has no faces: " + id);
            for (String line : lines) if (line.startsWith("mtllib ")) {
                Path material = file.getParent().resolve(line.substring(7).trim()).normalize();
                assertTrue(material.startsWith(file.getParent()) && Files.isRegularFile(material), "Missing OBJ material: " + id);
            }
        } catch (java.io.IOException exception) {
            fail("Unreadable OBJ: " + id, exception);
        }
    }

    private static void validateTag(ResourceLocation id, Map<ResourceLocation, JsonObject> tags, Set<ResourceLocation> chain) {
        assertTrue(chain.add(id), "Item tag cycle: " + chain + " -> " + id);
        assertTrue(tags.containsKey(id), "Missing item tag: " + id);
        for (var value : tags.get(id).getAsJsonArray("values")) {
            boolean required = !value.isJsonObject() || !value.getAsJsonObject().has("required")
                    || value.getAsJsonObject().get("required").getAsBoolean();
            String reference = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (required && reference.startsWith("#createapparel:")) {
                validateTag(ResourceLocation.parse(reference.substring(1)), tags, chain);
            }
        }
        chain.remove(id);
    }

    private static void validateRecipeTags(JsonElement value, Map<ResourceLocation, JsonObject> tags) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> validateRecipeTags(child, tags));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals("tag")) {
                if (entry.getValue().isJsonArray()) entry.getValue().getAsJsonArray().forEach(tag -> checkTag(tag, tags));
                else checkTag(entry.getValue(), tags);
            } else validateRecipeTags(entry.getValue(), tags);
        });
    }

    private static void checkTag(JsonElement value, Map<ResourceLocation, JsonObject> tags) {
        var id = ResourceLocation.parse(value.getAsString());
        if (id.getNamespace().equals("createapparel")) assertTrue(tags.containsKey(id), "Missing recipe item tag: " + id);
    }
}
