package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ClientGarmentAssets implements ResourceManagerReloadListener {
    public static final ClientGarmentAssets INSTANCE = new ClientGarmentAssets();

    private static final String TEMPLATE_DIRECTORY = "garment_models/templates";
    private static final String PART_DIRECTORY = "garment_models/parts";
    private static final Set<String> T_SHIRT_REQUIRED_PARTS = Set.of(
            "torso",
            "left_sleeve_classic",
            "right_sleeve_classic",
            "left_sleeve_slim",
            "right_sleeve_slim",
            "bust",
            "tucked_torso"
    );

    private volatile ResourceManager resourceManager;
    private volatile Map<ResourceLocation, GarmentDefinition> definitions = Map.of();
    private volatile Map<ResourceLocation, GarmentModelTemplate> templates = Map.of();
    private volatile Map<ResourceLocation, GarmentPartModel> parts = Map.of();

    private ClientGarmentAssets() {
    }

    public static void acceptDefinitions(List<GarmentDefinition> synchronizedDefinitions) {
        INSTANCE.replaceDefinitions(synchronizedDefinitions);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation, GarmentModelTemplate> loadedTemplates = loadTemplates(manager);
        Map<ResourceLocation, GarmentPartModel> loadedParts = loadParts(manager);
        validateBodyBust(manager);
        validatePartTextures(manager, loadedParts);
        resourceManager = manager;
        templates = Map.copyOf(loadedTemplates);
        parts = Map.copyOf(loadedParts);
        RuntimeGarmentTextureCache.clear();
        validateDefinitions(definitions);
    }

    public Optional<ResolvedGarment> resolve(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return Optional.empty();
        }
        GarmentDefinition definition = Optional.ofNullable(definitions.get(itemId))
                .or(() -> GarmentDefinitionManager.get(itemId))
                .orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        GarmentModelTemplate template = templates.get(definition.modelTemplate());
        if (template == null) {
            return Optional.empty();
        }
        Map<String, ResourceLocation> variant = template.variants().get(definition.modelVariant());
        if (variant == null) {
            return Optional.empty();
        }
        GarmentPartModel referenceTorso = parts.get(variant.get("torso"));
        if (referenceTorso == null) {
            return Optional.empty();
        }
        float referenceTop = referenceTorso.upperY();

        GarmentData data;
        try {
            data = GarmentData.fromStack(stack, definition);
            validateColorChannels(definition, data, variant);
        } catch (IllegalArgumentException exception) {
            data = GarmentData.defaultFor(definition);
        }

        List<String> selectedNames = new ArrayList<>();
        selectedNames.add("tucked".equals(data.styles().get("tuck")) ? "tucked_torso" : "torso");
        selectedNames.add("left_sleeve_classic");
        selectedNames.add("right_sleeve_classic");

        List<ResolvedPart> resolvedParts = new ArrayList<>();
        for (String name : selectedNames) {
            ResourceLocation partId = variant.get(name);
            GarmentPartModel part = parts.get(partId);
            if (part == null) {
                return Optional.empty();
            }
            resolvedParts.add(new ResolvedPart(
                    name,
                    part,
                    part.resolveTextures(definition, data),
                    referenceTop - part.upperY()
            ));
        }
        return Optional.of(new ResolvedGarment(definition, data, List.copyOf(resolvedParts)));
    }

    public ResourceManager resourceManager() {
        ResourceManager manager = resourceManager;
        if (manager == null) {
            throw new IllegalStateException("Garment resources have not been loaded");
        }
        return manager;
    }

    private void replaceDefinitions(List<GarmentDefinition> synchronizedDefinitions) {
        LinkedHashMap<ResourceLocation, GarmentDefinition> loaded = new LinkedHashMap<>();
        synchronizedDefinitions.stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .forEach(definition -> {
                    if (loaded.put(definition.id(), definition) != null) {
                        throw new IllegalStateException("Duplicate synchronized garment definition: " + definition.id());
                    }
                });
        Map<ResourceLocation, GarmentDefinition> immutable = Map.copyOf(loaded);
        validateDefinitions(immutable);
        definitions = immutable;
    }

    private Map<ResourceLocation, GarmentModelTemplate> loadTemplates(ResourceManager manager) {
        FileToIdConverter converter = FileToIdConverter.json(TEMPLATE_DIRECTORY);
        LinkedHashMap<ResourceLocation, GarmentModelTemplate> loaded = new LinkedHashMap<>();
        converter.listMatchingResources(manager).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    JsonObject json = readJson(entry.getValue(), id);
                    loaded.put(id, parseTemplate(id, json));
                });
        return loaded;
    }

    private Map<ResourceLocation, GarmentPartModel> loadParts(ResourceManager manager) {
        FileToIdConverter converter = FileToIdConverter.json(PART_DIRECTORY);
        LinkedHashMap<ResourceLocation, GarmentPartModel> loaded = new LinkedHashMap<>();
        converter.listMatchingResources(manager).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    JsonObject json = readJson(entry.getValue(), id);
                    loaded.put(id, parsePart(id, json));
                });
        return loaded;
    }

    private GarmentModelTemplate parseTemplate(ResourceLocation id, JsonObject json) {
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
                throw new IllegalStateException("Garment model variant has no parts: " + id + " / " + variantEntry.getKey());
            }
            variants.put(variantEntry.getKey(), Collections.unmodifiableMap(variantParts));
        }
        if (variants.isEmpty()) {
            throw new IllegalStateException("Garment model template has no variants: " + id);
        }
        return new GarmentModelTemplate(id, Collections.unmodifiableMap(variants));
    }

    private GarmentPartModel parsePart(ResourceLocation id, JsonObject json) {
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
            elements.add(new Element(GsonHelper.getAsString(elementJson, "name"), from, to, rotation, Map.copyOf(faces)));
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
            FabricMaterial material = fabricMaterial(entry.getKey(), id);
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
                Map.copyOf(variants),
                Map.copyOf(designs),
                Map.copyOf(materialOverrides)
        );
    }

    private void validateDefinitions(Map<ResourceLocation, GarmentDefinition> definitionsToValidate) {
        if (templates.isEmpty() || parts.isEmpty()) {
            return;
        }
        for (GarmentDefinition definition : definitionsToValidate.values()) {
            if (!(ForgeRegistries.ITEMS.getValue(definition.id()) instanceof GarmentItem)) {
                throw new IllegalStateException("Unknown garment item: " + definition.id());
            }
            GarmentModelTemplate template = templates.get(definition.modelTemplate());
            if (template == null) {
                throw new IllegalStateException("Unknown garment model template: " + definition.modelTemplate());
            }
            Map<String, ResourceLocation> variant = template.variants().get(definition.modelVariant());
            if (variant == null) {
                throw new IllegalStateException(
                        "Unknown garment model variant: " + definition.modelTemplate() + " / " + definition.modelVariant()
                );
            }
            if (definition.modelTemplate().equals(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "t_shirt"))
                    && !variant.keySet().containsAll(T_SHIRT_REQUIRED_PARTS)) {
                throw new IllegalStateException("T-Shirt model variant is missing required parts: " + definition.modelVariant());
            }
            for (Map.Entry<String, ResourceLocation> partEntry : variant.entrySet()) {
                GarmentPartModel part = parts.get(partEntry.getValue());
                if (part == null) {
                    throw new IllegalStateException("Unknown garment part model: " + partEntry.getValue());
                }
                if (!part.variants().containsKey(definition.textureVariant())
                        && !part.variants().containsKey("default")) {
                    throw new IllegalStateException(
                            "Unresolved garment texture variant: " + part.id() + " / " + definition.textureVariant()
                    );
                }
            }
            for (String design : definition.designs()) {
                if (!"plain".equals(design) && variant.values().stream()
                        .map(parts::get)
                        .filter(java.util.Objects::nonNull)
                        .noneMatch(part -> part.designs().containsKey(design))) {
                    throw new IllegalStateException("Garment design has no reachable mask: " + definition.id() + " / " + design);
                }
            }
            if (definition.styles().getOrDefault("tuck", List.of()).contains("tucked")
                    && !variant.containsKey("tucked_torso")) {
                throw new IllegalStateException("Garment tuck style requires tucked_torso: " + definition.id());
            }
        }
    }

    private void validateColorChannels(
            GarmentDefinition definition,
            GarmentData data,
            Map<String, ResourceLocation> variant
    ) {
        boolean needsSecondary = false;
        boolean needsAccent = false;
        for (ResourceLocation partId : new LinkedHashSet<>(variant.values())) {
            GarmentPartModel part = parts.get(partId);
            if (part == null) {
                continue;
            }
            DesignMasks masks = part.designs().get(data.design());
            if (masks != null) {
                needsSecondary |= masks.secondary().isPresent();
                needsAccent |= masks.accent().isPresent();
            }
        }
        if (needsSecondary != data.secondaryColor().isPresent() || needsAccent != data.accentColor().isPresent()) {
            throw new IllegalArgumentException("Garment color channels do not match design masks: " + definition.id());
        }
    }

    private void validatePartTextures(ResourceManager manager, Map<ResourceLocation, GarmentPartModel> loadedParts) {
        for (GarmentPartModel part : loadedParts.values()) {
            for (TextureVariant variant : part.variants().values()) {
                validateTexture(manager, part, variant.base(), TextureKind.BASE);
            }
            for (DesignMasks masks : part.designs().values()) {
                masks.secondary().ifPresent(id -> validateTexture(manager, part, id, TextureKind.MASK));
                masks.accent().ifPresent(id -> validateTexture(manager, part, id, TextureKind.MASK));
            }
            for (ResourceLocation override : part.materialOverrides().values()) {
                validateTexture(manager, part, override, TextureKind.BASE);
            }
        }
    }

    private void validateTexture(
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
                    if (red != green || green != blue) {
                        throw new IllegalStateException("Garment texture is not grayscale: " + textureId);
                    }
                    if (kind == TextureKind.BASE) {
                        if (alpha != 0 && alpha != 255) {
                            throw new IllegalStateException("Garment base alpha is not binary: " + textureId);
                        }
                    } else if (alpha != 255 || red != 0 && red != 255) {
                        throw new IllegalStateException("Garment design mask is not binary: " + textureId);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read garment texture: " + textureId, exception);
        }
    }

    private void validateBodyBust(ResourceManager manager) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                CreateApparel.MOD_ID,
                "body_models/parts/bust.json"
        );
        Resource resource = manager.getResource(id)
                .orElseThrow(() -> new IllegalStateException("Missing BUST body model: " + id));
        JsonObject json = readJson(resource, id);
        if (!"bust".equals(GsonHelper.getAsString(json, "profile"))) {
            throw new IllegalStateException("Invalid BUST body profile: " + id);
        }
        JsonObject geometry = GsonHelper.getAsJsonObject(json, "geometry");
        assertVector(vector(GsonHelper.getAsJsonArray(geometry, "pivot"), id + " pivot"), 0.0F, 12.5F, -2.25F, id);
        JsonArray elements = GsonHelper.getAsJsonArray(geometry, "elements");
        if (elements.size() != 1) {
            throw new IllegalStateException("BUST body model must contain one element: " + id);
        }
        JsonObject element = elements.get(0).getAsJsonObject();
        assertVector(vector(GsonHelper.getAsJsonArray(element, "from"), id + " from"), -3.5F, 9.5F, -2.25F, id);
        assertVector(vector(GsonHelper.getAsJsonArray(element, "to"), id + " to"), 3.5F, 12.5F, -0.25F, id);
        JsonObject rotation = GsonHelper.getAsJsonObject(element, "rotation");
        assertVector(vector(GsonHelper.getAsJsonArray(rotation, "origin"), id + " origin"), 0.0F, 12.5F, -2.25F, id);
        if (!"x".equals(GsonHelper.getAsString(rotation, "axis"))
                || Float.compare(GsonHelper.getAsFloat(rotation, "angle"), 40.0F) != 0) {
            throw new IllegalStateException("Invalid BUST body rotation: " + id);
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

    private static FabricMaterial fabricMaterial(String name, ResourceLocation owner) {
        for (FabricMaterial material : FabricMaterial.values()) {
            if (material.getSerializedName().equals(name)) {
                return material;
            }
        }
        throw new IllegalStateException("Unknown garment material override: " + owner + " / " + name);
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

    private static void assertVector(Vec3 actual, float x, float y, float z, ResourceLocation id) {
        if (Float.compare(actual.x(), x) != 0
                || Float.compare(actual.y(), y) != 0
                || Float.compare(actual.z(), z) != 0) {
            throw new IllegalStateException("Unexpected BUST body geometry: " + id);
        }
    }

    public record GarmentModelTemplate(
            ResourceLocation id,
            Map<String, Map<String, ResourceLocation>> variants
    ) {
    }

    public record GarmentPartModel(
            ResourceLocation id,
            int textureWidth,
            int textureHeight,
            Vec3 pivot,
            List<Element> elements,
            Map<String, TextureVariant> variants,
            Map<String, DesignMasks> designs,
            Map<FabricMaterial, ResourceLocation> materialOverrides
    ) {
        public float upperY() {
            return elements.stream()
                    .map(Element::to)
                    .map(Vec3::y)
                    .max(Float::compare)
                    .orElseThrow();
        }

        public ResolvedTextures resolveTextures(GarmentDefinition definition, GarmentData data) {
            TextureVariant variant = variants.getOrDefault(definition.textureVariant(), variants.get("default"));
            ResourceLocation base = materialOverrides.getOrDefault(data.material(), variant.base());
            DesignMasks masks = designs.getOrDefault(data.design(), DesignMasks.NONE);
            return new ResolvedTextures(base, masks.secondary(), masks.accent());
        }
    }

    public record Vec3(float x, float y, float z) {
    }

    public record Rotation(Vec3 origin, Axis axis, float angle) {
    }

    public record Element(String name, Vec3 from, Vec3 to, Rotation rotation, Map<Face, Uv> faces) {
    }

    public record Uv(float u0, float v0, float u1, float v1) {
    }

    public record TextureVariant(ResourceLocation base) {
    }

    public record DesignMasks(Optional<ResourceLocation> secondary, Optional<ResourceLocation> accent) {
        private static final DesignMasks NONE = new DesignMasks(Optional.empty(), Optional.empty());
    }

    public record ResolvedTextures(
            ResourceLocation base,
            Optional<ResourceLocation> secondary,
            Optional<ResourceLocation> accent
    ) {
    }

    public record ResolvedPart(
            String semanticName,
            GarmentPartModel model,
            ResolvedTextures textures,
            float topAlignmentOffset
    ) {
    }

    public record ResolvedGarment(
            GarmentDefinition definition,
            GarmentData data,
            List<ResolvedPart> parts
    ) {
    }

    public enum Axis {
        X,
        Y,
        Z;

        private static Axis fromName(String name) {
            try {
                return valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown garment rotation axis: " + name, exception);
            }
        }
    }

    public enum Face {
        FRONT,
        BACK,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM;

        private static Face fromName(String name) {
            try {
                return valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown garment face: " + name, exception);
            }
        }
    }

    private enum TextureKind {
        BASE,
        MASK
    }
}
