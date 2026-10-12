package com.rexhub.createapparel.client.citizen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.CitizenTextureCache;
import com.rexhub.createapparel.entity.CitizenAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ClientCitizenAssets extends SimpleJsonResourceReloadListener {
    public static final ClientCitizenAssets INSTANCE = new ClientCitizenAssets();
    public static final ResourceLocation FACE = id("textures/entity/citizen/face_tint_base.png");
    private static final CitizenHairStyle FALLBACK = new CitizenHairStyle(CitizenAppearance.DEFAULT_HAIR,
            Optional.of(id("textures/entity/citizen/hair/short_tint_base.png")), Optional.empty(), CitizenHairStyle.HatPolicy.DEFAULT);
    private Map<ResourceLocation, CitizenHairStyle> styles = Map.of();
    private long generation;

    private ClientCitizenAssets() { super(new Gson(), "citizen/hair_styles"); }

    public long generation() { return generation; }

    public boolean hasStyle(ResourceLocation id) { return styles.containsKey(id); }

    public CitizenHairStyle resolve(ResourceLocation id) {
        return styles.getOrDefault(id, styles.getOrDefault(CitizenAppearance.DEFAULT_HAIR, FALLBACK));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> definitions, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, CitizenHairStyle> loaded = new HashMap<>();
        definitions.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                if (GsonHelper.getAsBoolean(json, "bald", false)) {
                    loaded.put(id, new CitizenHairStyle(id, Optional.empty(), Optional.empty(), CitizenHairStyle.HatPolicy.DEFAULT));
                    return;
                }
                ResourceLocation texture = ResourceLocation.parse(GsonHelper.getAsString(json, "texture"));
                texture = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
                if (manager.getResource(texture).isEmpty()) throw new IllegalArgumentException("Missing hair texture: " + texture);
                Optional<CitizenHairLayers> geometry = Optional.empty();
                if (json.has("geometry")) {
                    try {
                        var layers = loadGeometry(manager, ResourceLocation.parse(GsonHelper.getAsString(json, "geometry")));
                        try (var stream = manager.getResource(texture).orElseThrow().open(); var image = NativeImage.read(stream)) {
                            if (image.getWidth() != 64 || image.getHeight() != 32) throw new IllegalArgumentException("Invalid hair atlas dimensions");
                            layers = layers.withOpacity((u, v) -> image.getPixelRGBA(u, v) >>> 24 != 0);
                        }
                        geometry = Optional.of(layers);
                    } catch (RuntimeException | java.io.IOException exception) {
                        LogUtils.getLogger().warn("Citizen hair {} has no valid extension layers; using HEAD/HAT", id, exception);
                    }
                }
                var policy = CitizenHairStyle.HatPolicy.valueOf(GsonHelper.getAsString(json, "hat_policy", "DEFAULT").toUpperCase(Locale.ROOT));
                loaded.put(id, new CitizenHairStyle(id, Optional.of(texture), geometry, policy));
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Invalid Citizen hair style {}; using short", id, exception);
            }
        });
        CitizenTextureCache.clear();
        styles = Map.copyOf(loaded);
        generation++;
    }

    private static CitizenHairLayers loadGeometry(ResourceManager manager, ResourceLocation id) throws java.io.IOException {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + ".json");
        var resource = manager.getResource(file).orElseThrow(() -> new java.io.IOException("Missing geometry " + file));
        JsonObject json;
        try (var reader = resource.openAsReader()) { json = JsonParser.parseReader(reader).getAsJsonObject(); }
        return CitizenHairLayers.read(json);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path); }
}
