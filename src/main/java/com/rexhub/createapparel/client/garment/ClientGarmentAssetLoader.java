package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Axis;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.DesignMasks;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentModelTemplate;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TextureVariant;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ClientGarmentAssetLoader {
    private static final String TEMPLATE_DIRECTORY = "garment_models/templates";
    private static final String PART_DIRECTORY = "garment_models/parts";
    private static final String BODY_TEMPLATE_DIRECTORY = "body_models/templates";
    private static final String BODY_PART_DIRECTORY = "body_models/parts";

    private ClientGarmentAssetLoader() {
    }

    static LoadedAssets load(ResourceManager manager) {
        Map<ResourceLocation, GarmentModelTemplate> garmentTemplates = loadTemplates(manager, TEMPLATE_DIRECTORY);
        Map<ResourceLocation, GarmentPartModel> garmentParts = loadParts(manager, PART_DIRECTORY);
        Map<ResourceLocation, GarmentModelTemplate> bodyTemplates = loadTemplates(manager, BODY_TEMPLATE_DIRECTORY);
        Map<ResourceLocation, GarmentPartModel> bodyParts = loadParts(manager, BODY_PART_DIRECTORY);
        validatePartTextures(manager, garmentParts, false);
        validatePartTextures(manager, bodyParts, true);
        return new LoadedAssets(garmentTemplates, garmentParts, bodyTemplates, bodyParts);
    }

    private static Map<ResourceLocation, GarmentModelTemplate> loadTemplates(
            ResourceManager manager,
            String directory
    ) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        LinkedHashMap<ResourceLocation, GarmentModelTemplate> loaded = new LinkedHashMap<>();
        converter.listMatchingResources(manager).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    JsonObject json = readJson(entry.getValue(), id);
                    loaded.put(id, parseTemplate(id, json));
                });
        return Collections.unmodifiableMap(loaded);
    }

    private static Map<ResourceLocation, GarmentPartModel> loadParts(
            ResourceManager manager,
            String directory
    ) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        LinkedHashMap<ResourceLocation, GarmentPartModel> loaded = new LinkedHashMap<>();
        converter.listMatchingResources(manager).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    JsonObject json = readJson(entry.getValue(), id);
                    loaded.put(id, parsePart(id, json));
                });
        return Collections.unmodifiableMap(loaded);
    }

    private static GarmentModelTemplate parseTemplate(ResourceLocation id, JsonObject json) {
        LinkedHashMap<String, Map<String, ResourceLocation>> variants = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> variantEntry : json.entrySet()) {
            JsonObject partsJson = GsonHelper.convertToJsonObject(variantEntry.getValue(), variantEntry.getKey());
            LinkedHashMap<String, ResourceLocation> variantParts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> partEntry : partsJson.entrySet()) {
                JsonObject reference = GsonHelper.convertToJsonObject(partEntry.getValue(), partEntry.getKey());
                ResourceLocation model = resourceLocation(
                        GsonHelper.getAsString(reference, "model"),
                        id + " / " + variantEntry.getKey() + " / " + partEntry.getKey()
                );
                variantParts.put(partEntry.getKey(), model);
            }
            if (variantParts.isEmpty()) {
                throw new IllegalStateException(
                        "Garment model variant has no parts: " + id + " / " + variantEntry.getKey()
                );
            }
            variants.put(variantEntry.getKey(), Collections.unmodifiableMap(variantParts));
        }
        if (variants.isEmpty()) {
            throw new IllegalStateException("Garment model template has no variants: " + id);
        }
        return new GarmentModelTemplate(id, Collections.unmodifiableMap(variants));
    }

    private static GarmentPartModel parsePart(ResourceLocation id, JsonObject json) {
        String renderType = GsonHelper.getAsString(json, "render_type");
        if (!"cutout".equals(renderType)) {
            throw new IllegalStateException("Unsupported garment render type: " + id + " / " + renderType);
        }
        int[] textureSize = intPair(GsonHelper.getAsJsonArray(json, "texture_size"), id + " texture_size");
        if (textureSize[0] <= 0 || textureSize[1] <= 0) {
            throw new IllegalStateException("Invalid garment texture size: " + id);
        }

        JsonObject geometryJson = GsonHelper.getAsJsonObject(json, "geometry");
        Vec3 pivot = vector(GsonHelper.getAsJsonArray(geometryJson, "pivot"), id + " pivot");
        List<Element> elements = new ArrayList<>();
        for (JsonElement elementValue : GsonHelper.getAsJsonArray(geometryJson, "elements")) {
            JsonObject elementJson = GsonHelper.convertToJsonObject(elementValue, "element");
            Vec3 from = vector(GsonHelper.getAsJsonArray(elementJson, "from"), id + " from");
            Vec3 to = vector(GsonHelper.getAsJsonArray(elementJson, "to"), id + " to");
            if (from.x() >= to.x() || from.y() >= to.y() || from.z() >= to.z()) {
                throw new IllegalStateException("Garment element must have positive volume: " + id);
            }
            Rotation rotation = null;
            if (elementJson.has("rotation")) {
                JsonObject rotationJson = GsonHelper.getAsJsonObject(elementJson, "rotation");
                rotation = new Rotation(
                        vector(GsonHelper.getAsJsonArray(rotationJson, "origin"), id + " rotation origin"),
                        Axis.fromName(GsonHelper.getAsString(rotationJson, "axis")),
                        GsonHelper.getAsFloat(rotationJson, "angle")
                );
            }
            EnumMap<Face, Uv> faces = new EnumMap<>(Face.class);
            JsonObject facesJson = GsonHelper.getAsJsonObject(elementJson, "faces");
            for (Map.Entry<String, JsonElement> faceEntry : facesJson.entrySet()) {
                Face face = Face.fromName(faceEntry.getKey());
                JsonObject faceJson = GsonHelper.convertToJsonObject(faceEntry.getValue(), faceEntry.getKey());
                float[] uv = floatQuad(GsonHelper.getAsJsonArray(faceJson, "uv"), id + " " + faceEntry.getKey());
                faces.put(face, new Uv(uv[0], uv[1], uv[2], uv[3]));
            }
            if (faces.isEmpty()) {
                throw new IllegalStateException("Garment element has no faces: " + id);
            }
            elements.add(new Element(
                    GsonHelper.getAsString(elementJson, "name"),
                    from,
                    to,
                    rotation,
                    Collections.unmodifiableMap(faces)
            ));
        }
        if (elements.isEmpty()) {
            throw new IllegalStateException("Garment part has no geometry elements: " + id);
        }

        JsonObject texturesJson = GsonHelper.getAsJsonObject(json, "textures");
        LinkedHashMap<String, TextureVariant> variants = new LinkedHashMap<>();
        JsonObject variantsJson = GsonHelper.getAsJsonObject(texturesJson, "variants");
        for (Map.Entry<String, JsonElement> entry : variantsJson.entrySet()) {
            JsonObject variant = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
            variants.put(entry.getKey(), new TextureVariant(resourceLocation(
                    GsonHelper.getAsString(variant, "base"),
                    id + " / variant " + entry.getKey()
            )));
        }
        if (!variants.containsKey("default")) {
            throw new IllegalStateException("Garment part has no default texture variant: " + id);
        }

        LinkedHashMap<String, DesignMasks> designs = new LinkedHashMap<>();
        JsonObject designsJson = GsonHelper.getAsJsonObject(texturesJson, "designs", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : designsJson.entrySet()) {
            JsonObject masks = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
            Optional<ResourceLocation> secondary = optionalResourceLocation(masks, "secondary_mask", id);
            Optional<ResourceLocation> accent = optionalResourceLocation(masks, "accent_mask", id);
            if (secondary.isEmpty() && accent.isEmpty()) {
                throw new IllegalStateException("Garment design has no masks: " + id + " / " + entry.getKey());
            }
            designs.put(entry.getKey(), new DesignMasks(secondary, accent));
        }

        EnumMap<FabricMaterial, ResourceLocation> materialOverrides = new EnumMap<>(FabricMaterial.class);
        JsonObject overridesJson = GsonHelper.getAsJsonObject(json, "material_overrides", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : overridesJson.entrySet()) {
            FabricMaterial material = FabricMaterial.fromSerializedName(entry.getKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown garment material override: " + id + " / " + entry.getKey()
                    ));
            materialOverrides.put(material, resourceLocation(
                    GsonHelper.convertToString(entry.getValue(), entry.getKey()),
                    id + " / material " + entry.getKey()
            ));
        }
        return new GarmentPartModel(
                id,
                textureSize[0],
                textureSize[1],
                pivot,
                List.copyOf(elements),
                Collections.unmodifiableMap(variants),
                Collections.unmodifiableMap(designs),
                Collections.unmodifiableMap(materialOverrides)
        );
    }

    private static void validatePartTextures(
            ResourceManager manager,
            Map<ResourceLocation, GarmentPartModel> loadedParts,
            boolean bodyParts
    ) {
        for (GarmentPartModel part : loadedParts.values()) {
            TextureKind baseKind = bodyParts
                    ? TextureKind.BODY_TINT
                    : isDefaultUnderwear(part.id()) ? TextureKind.FIXED_COLOR : TextureKind.BASE;
            for (TextureVariant variant : part.variants().values()) {
                validateTexture(manager, part, variant.base(), baseKind);
            }
            for (DesignMasks masks : part.designs().values()) {
                masks.secondary().ifPresent(id -> validateTexture(manager, part, id, TextureKind.MASK));
                masks.accent().ifPresent(id -> validateTexture(manager, part, id, TextureKind.MASK));
            }
            for (ResourceLocation override : part.materialOverrides().values()) {
                validateTexture(manager, part, override, baseKind);
            }
        }
    }

    private static boolean isDefaultUnderwear(ResourceLocation partId) {
        return CreateApparel.MOD_ID.equals(partId.getNamespace())
                && (partId.getPath().startsWith("default_underwear_top/")
                || partId.getPath().startsWith("default_underwear_bottom/"));
    }

    private static void validateTexture(
            ResourceManager manager,
            GarmentPartModel part,
            ResourceLocation textureId,
            TextureKind kind
    ) {
        ResourceLocation resourceId = textureResource(textureId);
        Resource resource = manager.getResource(resourceId)
                .orElseThrow(() -> new IllegalStateException("Missing garment texture: " + textureId));
        try (NativeImage image = NativeImage.read(resource.open())) {
            if (image.getWidth() != part.textureWidth() || image.getHeight() != part.textureHeight()) {
                throw new IllegalStateException(
                        "Garment texture size does not match model: " + part.id() + " / " + textureId
                );
            }
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = FastColor.ABGR32.red(pixel);
                    int green = FastColor.ABGR32.green(pixel);
                    int blue = FastColor.ABGR32.blue(pixel);
                    int alpha = FastColor.ABGR32.alpha(pixel);
                    if (kind != TextureKind.FIXED_COLOR && (red != green || green != blue)) {
                        throw new IllegalStateException("Garment texture is not grayscale: " + textureId);
                    }
                    if (kind == TextureKind.BASE) {
                        if (alpha != 0 && alpha != 255) {
                            throw new IllegalStateException("Garment base alpha is not binary: " + textureId);
                        }
                    } else if (kind == TextureKind.MASK && (alpha != 255 || red != 0 && red != 255)) {
                        throw new IllegalStateException("Garment design mask is not binary: " + textureId);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read garment texture: " + textureId, exception);
        }
    }

    private static JsonObject readJson(Resource resource, ResourceLocation id) {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to parse garment resource: " + id, exception);
        }
    }

    private static ResourceLocation textureResource(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private static Optional<ResourceLocation> optionalResourceLocation(
            JsonObject json,
            String key,
            ResourceLocation owner
    ) {
        return json.has(key)
                ? Optional.of(resourceLocation(GsonHelper.getAsString(json, key), owner + " / " + key))
                : Optional.empty();
    }

    private static ResourceLocation resourceLocation(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalStateException("Invalid resource location: " + field + "=" + value);
        }
        return id;
    }

    private static Vec3 vector(JsonArray values, String field) {
        if (values.size() != 3) {
            throw new IllegalStateException("Expected three values for " + field);
        }
        return new Vec3(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static int[] intPair(JsonArray values, String field) {
        if (values.size() != 2) {
            throw new IllegalStateException("Expected two values for " + field);
        }
        return new int[]{values.get(0).getAsInt(), values.get(1).getAsInt()};
    }

    private static float[] floatQuad(JsonArray values, String field) {
        if (values.size() != 4) {
            throw new IllegalStateException("Expected four values for " + field);
        }
        return new float[]{
                values.get(0).getAsFloat(),
                values.get(1).getAsFloat(),
                values.get(2).getAsFloat(),
                values.get(3).getAsFloat()
        };
    }

    record LoadedAssets(
            Map<ResourceLocation, GarmentModelTemplate> garmentTemplates,
            Map<ResourceLocation, GarmentPartModel> garmentParts,
            Map<ResourceLocation, GarmentModelTemplate> bodyTemplates,
            Map<ResourceLocation, GarmentPartModel> bodyParts
    ) {
    }

    private enum TextureKind {
        BASE,
        BODY_TINT,
        FIXED_COLOR,
        MASK
    }
}
