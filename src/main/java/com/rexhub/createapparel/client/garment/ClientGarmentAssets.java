package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentWearContext;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ClientGarmentAssets implements ResourceManagerReloadListener {
    public static final ClientGarmentAssets INSTANCE = new ClientGarmentAssets();

    private static final Set<String> T_SHIRT_REQUIRED_PARTS = Set.of(
            "torso",
            "left_sleeve_classic",
            "right_sleeve_classic",
            "left_sleeve_slim",
            "right_sleeve_slim",
            "bust",
            "tucked_torso"
    );
    private static final ResourceLocation BODY_BASE_TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "body_base"
    );
    private static final ResourceLocation DEFAULT_UNDERWEAR_TOP_TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "default_underwear_top"
    );
    private static final ResourceLocation DEFAULT_UNDERWEAR_BOTTOM_TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "default_underwear_bottom"
    );

    private volatile AssetState state = AssetState.EMPTY;
    private volatile long generation;
    private static final int CHEST_PROFILE_COUNT = ChestProfile.values().length;
    private final Map<Integer, Optional<List<ResolvedSupportPart>>> bodyCache = new java.util.HashMap<>();
    private final Map<Integer, Optional<List<ResolvedSupportPart>>> underwearCache = new java.util.HashMap<>();

    public long generation() {
        return generation;
    }

    private ClientGarmentAssets() {
    }

    public static void acceptDefinitions(List<GarmentDefinition> synchronizedDefinitions) {
        INSTANCE.replaceDefinitions(synchronizedDefinitions);
    }

    public static Optional<GarmentDefinition> getDefinition(ResourceLocation id) {
        return Optional.ofNullable(INSTANCE.state.definitions().get(id))
                .or(() -> GarmentDefinitionManager.get(id));
    }

    public static Optional<ColorChannels> getRequiredColorChannels(ResourceLocation garmentId, String design) {
        AssetState snapshot = INSTANCE.state;
        GarmentDefinition definition = Optional.ofNullable(snapshot.definitions().get(garmentId))
                .or(() -> GarmentDefinitionManager.get(garmentId))
                .orElse(null);
        if (definition == null || !definition.designs().contains(design)) {
            return Optional.empty();
        }
        Map<String, ResourceLocation> variant = getModelVariant(definition, snapshot.garmentTemplates()).orElse(null);
        return variant == null
                ? Optional.empty()
                : Optional.of(colorChannels(variant, definition.textureVariant(), design, snapshot.garmentParts()));
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager manager) {
        ClientGarmentAssetLoader.LoadedAssets loaded = ClientGarmentAssetLoader.load(manager);
        AssetState current = state;
        validateDefinitions(current.definitions(), loaded.garmentTemplates(), loaded.garmentParts());
        validateSupportAssets(
                loaded.garmentTemplates(),
                loaded.garmentParts(),
                loaded.bodyTemplates(),
                loaded.bodyParts()
        );

        state = new AssetState(
                manager,
                current.definitions(),
                loaded.garmentTemplates(),
                loaded.garmentParts(),
                loaded.bodyTemplates(),
                loaded.bodyParts()
        );
        RuntimeGarmentTextureCache.clear();
        invalidateDerivedAssets();
    }

    public Optional<ResolvedGarment> resolve(ItemStack stack) {
        return resolve(stack, ArmProfile.CLASSIC, ChestProfile.FLAT);
    }

    public Optional<ResolvedGarment> resolveForItem(ItemStack stack) {
        return resolve(stack, null, ArmProfile.CLASSIC, ChestProfile.FLAT, true);
    }

    public Optional<ResolvedGarment> resolve(
            ItemStack stack,
            ArmProfile armProfile,
            ChestProfile chestProfile
    ) {
        return resolve(stack, null, armProfile, chestProfile);
    }

    public Optional<ResolvedGarment> resolve(
            ItemStack stack,
            GarmentWearContext wearContext,
            ArmProfile armProfile,
            ChestProfile chestProfile
    ) {
        return resolve(stack, wearContext, armProfile, chestProfile, false);
    }

    private Optional<ResolvedGarment> resolve(ItemStack stack, GarmentWearContext wearContext,
                                             ArmProfile armProfile, ChestProfile chestProfile, boolean itemDisplay) {
        AssetState snapshot = state;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return Optional.empty();
        }
        GarmentDefinition definition = Optional.ofNullable(snapshot.definitions().get(itemId))
                .or(() -> GarmentDefinitionManager.get(itemId))
                .orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        GarmentWearContext effectiveContext = wearContext == null ? GarmentWearContext.defaultsFor(definition) : wearContext;
        if (!definition.allowedSlots().contains(effectiveContext.slot())) {
            return Optional.empty();
        }
        Map<String, ResourceLocation> variant = getModelVariant(definition, effectiveContext.slot(), snapshot.garmentTemplates()).orElse(null);
        if (variant == null) {
            return Optional.empty();
        }
        GarmentAppearance data;
        try {
            data = GarmentAppearance.fromStack(stack, definition);
        } catch (IllegalArgumentException exception) {
            data = GarmentAppearance.defaultFor(stack, definition);
        }

        Map<String, String> styles = itemDisplay
                ? snapshot.garmentTemplates().get(definition.modelTemplate()).stylesForItem(definition)
                : effectiveContext.styleState().normalize(definition, effectiveContext.slot()).styles();
        List<String> selectedNames = GarmentPartSelection.select(styles, armProfile, chestProfile, variant.keySet());

        List<ResolvedPart> resolvedParts = new ArrayList<>();
        for (String name : selectedNames) {
            ResourceLocation partId = variant.get(name);
            GarmentPartModel part = snapshot.garmentParts().get(partId);
            if (part == null) {
                return Optional.empty();
            }
            resolvedParts.add(new ResolvedPart(
                    name,
                    part,
                    part.resolveTextures(definition, data)
            ));
        }
        return Optional.of(new ResolvedGarment(definition, data, List.copyOf(resolvedParts)));
    }

    public Optional<List<ResolvedSupportPart>> resolveBody(
            ArmProfile armProfile,
            ChestProfile chestProfile
    ) {
        int key = armProfile.ordinal() * CHEST_PROFILE_COUNT + chestProfile.ordinal();
        return bodyCache.computeIfAbsent(key, ignored -> resolveBodyUncached(armProfile, chestProfile));
    }

    private Optional<List<ResolvedSupportPart>> resolveBodyUncached(ArmProfile armProfile, ChestProfile chestProfile) {
        AssetState snapshot = state;
        String variantName = armProfile.getSerializedName() + "_" + chestProfile.getSerializedName();
        GarmentModelTemplate template = snapshot.bodyTemplates().get(BODY_BASE_TEMPLATE);
        if (template == null) {
            return Optional.empty();
        }
        Map<String, ResourceLocation> variant = template.variants().get(variantName);
        if (variant == null) {
            return Optional.empty();
        }
        return resolveSupportParts(variant, snapshot.bodyParts(), List.of(
                "torso",
                "right_arm",
                "left_arm",
                "right_leg",
                "left_leg",
                "bust"
        ));
    }

    public Optional<List<ResolvedSupportPart>> resolveDefaultUnderwear(
            ChestProfile chestProfile,
            boolean includeTop,
            boolean includeBottom
    ) {
        int key = chestProfile.ordinal() * 4 + (includeTop ? 2 : 0) + (includeBottom ? 1 : 0);
        return underwearCache.computeIfAbsent(key, ignored -> resolveUnderwearUncached(chestProfile, includeTop, includeBottom));
    }

    private Optional<List<ResolvedSupportPart>> resolveUnderwearUncached(ChestProfile chestProfile, boolean includeTop, boolean includeBottom) {
        AssetState snapshot = state;
        List<ResolvedSupportPart> resolved = new ArrayList<>();
        if (includeTop && !appendSupportTemplateParts(
                snapshot,
                DEFAULT_UNDERWEAR_TOP_TEMPLATE,
                List.of("top_torso", chestProfile == ChestProfile.BUST ? "top_bust" : ""),
                resolved
        )) {
            return Optional.empty();
        }
        if (includeBottom && !appendSupportTemplateParts(
                snapshot,
                DEFAULT_UNDERWEAR_BOTTOM_TEMPLATE,
                List.of("bottom_torso", "bottom_right_leg", "bottom_left_leg"),
                resolved
        )) {
            return Optional.empty();
        }
        return resolved.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(resolved));
    }

    ResourceManager resourceManager() {
        ResourceManager manager = state.resourceManager();
        if (manager == null) {
            throw new IllegalStateException("Garment resources have not been loaded");
        }
        return manager;
    }

    private static boolean appendSupportTemplateParts(
            AssetState snapshot,
            ResourceLocation templateId,
            List<String> selectedNames,
            List<ResolvedSupportPart> output
    ) {
        GarmentModelTemplate template = snapshot.garmentTemplates().get(templateId);
        if (template == null) {
            return false;
        }
        Map<String, ResourceLocation> variant = template.variants().get("default");
        if (variant == null) {
            return false;
        }
        Optional<List<ResolvedSupportPart>> resolved = resolveSupportParts(
                variant,
                snapshot.garmentParts(),
                selectedNames
        );
        resolved.ifPresent(output::addAll);
        return resolved.isPresent();
    }

    private static Optional<List<ResolvedSupportPart>> resolveSupportParts(
            Map<String, ResourceLocation> variant,
            Map<ResourceLocation, GarmentPartModel> parts,
            List<String> selectedNames
    ) {
        List<ResolvedSupportPart> resolved = new ArrayList<>();
        for (String name : selectedNames) {
            if (name.isEmpty()) {
                continue;
            }
            ResourceLocation partId = variant.get(name);
            if (partId == null) {
                if ("bust".equals(name)) {
                    continue;
                }
                return Optional.empty();
            }
            GarmentPartModel part = parts.get(partId);
            if (part == null) {
                return Optional.empty();
            }
            resolved.add(new ResolvedSupportPart(name, part, part.defaultTexture()));
        }
        return Optional.of(List.copyOf(resolved));
    }

    private synchronized void replaceDefinitions(List<GarmentDefinition> synchronizedDefinitions) {
        LinkedHashMap<ResourceLocation, GarmentDefinition> loaded = new LinkedHashMap<>();
        synchronizedDefinitions.stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .forEach(definition -> {
                    if (loaded.put(definition.id(), definition) != null) {
                        throw new IllegalStateException("Duplicate synchronized garment definition: " + definition.id());
                    }
                });
        Map<ResourceLocation, GarmentDefinition> immutable = Collections.unmodifiableMap(loaded);
        AssetState current = state;
        validateDefinitions(immutable, current.garmentTemplates(), current.garmentParts());
        state = new AssetState(
                current.resourceManager(),
                immutable,
                current.garmentTemplates(),
                current.garmentParts(),
                current.bodyTemplates(),
                current.bodyParts()
        );
        invalidateDerivedAssets();
    }

    private void invalidateDerivedAssets() {
        generation++;
        bodyCache.clear();
        underwearCache.clear();
        FlatGarmentGeometry.clearCache();
    }

    private static Optional<Map<String, ResourceLocation>> getModelVariant(
            GarmentDefinition definition,
            Map<ResourceLocation, GarmentModelTemplate> templates
    ) {
        return getModelVariant(definition, definition.defaultSlot(), templates);
    }

    private static Optional<Map<String, ResourceLocation>> getModelVariant(
            GarmentDefinition definition,
            com.rexhub.createapparel.garment.GarmentSlot slot,
            Map<ResourceLocation, GarmentModelTemplate> templates
    ) {
        GarmentModelTemplate template = templates.get(definition.modelTemplate());
        return template == null
                ? Optional.empty()
                : Optional.ofNullable(template.variants().get(definition.modelVariantFor(slot)));
    }

    private static void validateDefinitions(
            Map<ResourceLocation, GarmentDefinition> definitionsToValidate,
            Map<ResourceLocation, GarmentModelTemplate> templatesToValidate,
            Map<ResourceLocation, GarmentPartModel> partsToValidate
    ) {
        if (templatesToValidate.isEmpty() || partsToValidate.isEmpty()) {
            return;
        }
        for (GarmentDefinition definition : definitionsToValidate.values()) {
            if (!(ForgeRegistries.ITEMS.getValue(definition.id()) instanceof GarmentItem)) {
                throw new IllegalStateException("Unknown garment item: " + definition.id());
            }
            GarmentModelTemplate template = templatesToValidate.get(definition.modelTemplate());
            if (template == null) {
                throw new IllegalStateException("Unknown garment model template: " + definition.modelTemplate());
            }
            template.stylesForItem(definition);
            for (var slot : definition.allowedSlots()) {
                Map<String, ResourceLocation> variant = template.variants().get(definition.modelVariantFor(slot));
                if (variant == null) {
                    throw new IllegalStateException(
                            "Unknown garment model variant: " + definition.modelTemplate() + " / " + definition.modelVariant()
                    );
                }
                if (definition.modelTemplate().equals(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "t_shirt"))
                        && !variant.keySet().containsAll(T_SHIRT_REQUIRED_PARTS)) {
                    throw new IllegalStateException(
                            "T-Shirt model variant is missing required parts: " + definition.modelVariant()
                    );
                }
                for (Map.Entry<String, ResourceLocation> partEntry : variant.entrySet()) {
                    GarmentPartModel part = partsToValidate.get(partEntry.getValue());
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
                            .map(partsToValidate::get)
                            .filter(java.util.Objects::nonNull)
                            .noneMatch(part -> part.designsFor(definition.textureVariant()).containsKey(design))) {
                        throw new IllegalStateException(
                                "Garment design has no reachable mask: " + definition.id() + " / " + design
                        );
                    }
                }
                Set<String> missingStyleParts = new LinkedHashSet<>(GarmentPartSelection.requiredStyleParts(
                        definition.stylesFor(slot), variant.containsKey("bust")
                ));
                missingStyleParts.removeAll(variant.keySet());
                if (!missingStyleParts.isEmpty()) {
                    throw new IllegalStateException(
                            "Garment styles require missing parts: " + definition.id() + " / " + missingStyleParts
                    );
                }
            }
        }
    }

    private static void validateSupportAssets(
            Map<ResourceLocation, GarmentModelTemplate> garmentTemplates,
            Map<ResourceLocation, GarmentPartModel> garmentParts,
            Map<ResourceLocation, GarmentModelTemplate> bodyTemplates,
            Map<ResourceLocation, GarmentPartModel> bodyParts
    ) {
        GarmentModelTemplate bodyTemplate = requireTemplate(bodyTemplates, BODY_BASE_TEMPLATE);
        requireVariantParts(
                bodyTemplate,
                "classic_flat",
                Set.of("torso", "right_arm", "left_arm", "right_leg", "left_leg"),
                bodyParts
        );
        requireVariantParts(
                bodyTemplate,
                "classic_bust",
                Set.of("torso", "right_arm", "left_arm", "right_leg", "left_leg", "bust"),
                bodyParts
        );
        requireVariantParts(
                bodyTemplate,
                "slim_flat",
                Set.of("torso", "right_arm", "left_arm", "right_leg", "left_leg"),
                bodyParts
        );
        requireVariantParts(
                bodyTemplate,
                "slim_bust",
                Set.of("torso", "right_arm", "left_arm", "right_leg", "left_leg", "bust"),
                bodyParts
        );
        requireVariantParts(
                requireTemplate(garmentTemplates, DEFAULT_UNDERWEAR_TOP_TEMPLATE),
                "default",
                Set.of("top_torso", "top_bust"),
                garmentParts
        );
        requireVariantParts(
                requireTemplate(garmentTemplates, DEFAULT_UNDERWEAR_BOTTOM_TEMPLATE),
                "default",
                Set.of("bottom_torso", "bottom_right_leg", "bottom_left_leg"),
                garmentParts
        );
    }

    private static GarmentModelTemplate requireTemplate(
            Map<ResourceLocation, GarmentModelTemplate> templates,
            ResourceLocation id
    ) {
        GarmentModelTemplate template = templates.get(id);
        if (template == null) {
            throw new IllegalStateException("Missing support model template: " + id);
        }
        return template;
    }

    private static void requireVariantParts(
            GarmentModelTemplate template,
            String variantName,
            Set<String> requiredNames,
            Map<ResourceLocation, GarmentPartModel> parts
    ) {
        Map<String, ResourceLocation> variant = template.variants().get(variantName);
        if (variant == null || !variant.keySet().containsAll(requiredNames)) {
            throw new IllegalStateException(
                    "Support model variant is missing required parts: " + template.id() + " / " + variantName
            );
        }
        for (String name : requiredNames) {
            if (!parts.containsKey(variant.get(name))) {
                throw new IllegalStateException("Unknown support part model: " + variant.get(name));
            }
        }
    }

    static ColorChannels colorChannels(
            Map<String, ResourceLocation> variant,
            String textureVariant,
            String design,
            Map<ResourceLocation, GarmentPartModel> parts
    ) {
        boolean secondary = false;
        boolean accent = false;
        for (ResourceLocation partId : new LinkedHashSet<>(variant.values())) {
            GarmentPartModel part = parts.get(partId);
            if (part == null) {
                continue;
            }
            DesignMasks masks = part.designsFor(textureVariant).get(design);
            if (masks != null) {
                secondary |= masks.secondary().isPresent();
                accent |= masks.accent().isPresent();
            }
        }
        return new ColorChannels(secondary, accent);
    }

    private record AssetState(
            @Nullable ResourceManager resourceManager,
            Map<ResourceLocation, GarmentDefinition> definitions,
            Map<ResourceLocation, GarmentModelTemplate> garmentTemplates,
            Map<ResourceLocation, GarmentPartModel> garmentParts,
            Map<ResourceLocation, GarmentModelTemplate> bodyTemplates,
            Map<ResourceLocation, GarmentPartModel> bodyParts
    ) {
        private static final AssetState EMPTY = new AssetState(
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    public record GarmentModelTemplate(
            ResourceLocation id,
            Map<String, Map<String, ResourceLocation>> variants,
            Map<String, String> itemStyles
    ) {
        Map<String, String> stylesForItem(GarmentDefinition definition) {
            Map<String, String> styles = new LinkedHashMap<>(GarmentWearContext.defaultsFor(definition).styleState().styles());
            itemStyles.forEach((category, value) -> {
                if (!definition.stylesFor(definition.defaultSlot()).getOrDefault(category, List.of()).contains(value)) {
                    throw new IllegalStateException("Invalid item style: " + id + " / " + category + "=" + value);
                }
                styles.put(category, value);
            });
            return Map.copyOf(styles);
        }
    }

    public record GarmentPartModel(
            ResourceLocation id,
            boolean translucent,
            int textureWidth,
            int textureHeight,
            Vec3 pivot,
            List<Element> elements,
            Map<String, TextureVariant> variants,
            Map<String, DesignMasks> designs,
            Map<FabricMaterial, ResourceLocation> materialOverrides,
            Optional<LegEnvelope> legEnvelope,
            GarmentHemGeometry.Prepared hemGeometry
    ) {
        public ResolvedTextures resolveTextures(GarmentDefinition definition, GarmentAppearance data) {
            TextureVariant variant = variants.getOrDefault(definition.textureVariant(), variants.get("default"));
            ResourceLocation base = data instanceof GarmentData fabric
                    ? materialOverrides.getOrDefault(fabric.material(), variant.base()) : variant.base();
            DesignMasks masks = designsFor(definition.textureVariant()).getOrDefault(data.design(), DesignMasks.NONE);
            return new ResolvedTextures(
                    base, masks.secondary(), masks.accent(), variant.overlay(),
                    variant.brightness(), variant.brightnessMask()
            );
        }

        public ResourceLocation defaultTexture() {
            return variants.get("default").base();
        }

        public Map<String, DesignMasks> designsFor(String textureVariant) {
            return variants.getOrDefault(textureVariant, variants.get("default")).designs().orElse(designs);
        }
    }

    public record Vec3(float x, float y, float z) {
    }

    public record LegEnvelope(float clearance, DualEnvelopeDeformer.Prepared geometry) {
    }

    public record Rotation(Vec3 origin, Axis axis, float angle) {
    }

    public record Element(
            String name, Vec3 from, Vec3 to, Rotation rotation, Map<Face, Uv> faces, List<Vec3> corners,
            List<BakedFace> bakedFaces
    ) {
        public Element(String name, Vec3 from, Vec3 to, Rotation rotation, Map<Face, Uv> faces, List<Vec3> corners) {
            this(name, from, to, rotation, faces, corners, GarmentElementGeometry.bake(faces, corners));
        }
    }

    public record BakedVertex(float x, float y, float z, float u, float v) {
    }

    public record BakedFace(List<BakedVertex> vertices, Vec3 normal) {
    }

    public record Uv(float u0, float v0, float u1, float v1, List<TexturePoint> corners) {
        public Uv(float u0, float v0, float u1, float v1) {
            this(u0, v0, u1, v1, List.of(
                    new TexturePoint(u0, v1), new TexturePoint(u0, v0),
                    new TexturePoint(u1, v0), new TexturePoint(u1, v1)
            ));
        }
    }

    public record TexturePoint(float u, float v) {
    }

    public record TextureVariant(
            ResourceLocation base,
            Optional<ResourceLocation> overlay,
            float brightness,
            Optional<ResourceLocation> brightnessMask,
            Optional<Map<String, DesignMasks>> designs
    ) {
    }

    public record DesignMasks(Optional<ResourceLocation> secondary, Optional<ResourceLocation> accent) {
        private static final DesignMasks NONE = new DesignMasks(Optional.empty(), Optional.empty());
    }

    public record ColorChannels(boolean secondary, boolean accent) {
    }

    public record ResolvedTextures(
            ResourceLocation base,
            Optional<ResourceLocation> secondary,
            Optional<ResourceLocation> accent,
            Optional<ResourceLocation> overlay,
            float brightness,
            Optional<ResourceLocation> brightnessMask
    ) {
    }

    public record ResolvedPart(
            String semanticName,
            GarmentPartModel model,
            ResolvedTextures textures
    ) {
    }

    public record ResolvedSupportPart(
            String semanticName,
            GarmentPartModel model,
            ResourceLocation texture,
            ResourceLocation textureFile
    ) {
        public ResolvedSupportPart(String semanticName, GarmentPartModel model, ResourceLocation texture) {
            this(semanticName, model, texture, ResourceLocation.fromNamespaceAndPath(
                    texture.getNamespace(), "textures/" + texture.getPath() + ".png"));
        }
    }

    public record ResolvedGarment(
            GarmentDefinition definition,
            GarmentAppearance data,
            List<ResolvedPart> parts
    ) {
    }

    public enum Axis {
        X,
        Y,
        Z;

        static Axis fromName(String name) {
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

        static Face fromName(String name) {
            try {
                return valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown garment face: " + name, exception);
            }
        }
    }
}
