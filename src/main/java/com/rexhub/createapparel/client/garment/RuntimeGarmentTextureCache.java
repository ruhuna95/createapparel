package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.platform.NativeImage;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeGarmentTextureCache {
    private static final Map<CacheKey, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();

    private RuntimeGarmentTextureCache() {
    }

    public static ResourceLocation get(
            GarmentPartModel part,
            ResolvedTextures textures,
            GarmentData data
    ) {
        CacheKey key = new CacheKey(
                part.id(),
                textures.base(),
                textures.secondary(),
                textures.accent(),
                data.primaryColor(),
                data.secondaryColor(),
                data.accentColor()
        );
        return CACHE.computeIfAbsent(key, ignored -> compose(part, textures, data));
    }

    public static void clear() {
        if (CACHE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CACHE.values().forEach(minecraft.getTextureManager()::release);
        CACHE.clear();
    }

    private static ResourceLocation compose(
            GarmentPartModel part,
            ResolvedTextures textures,
            GarmentData data
    ) {
        ResourceManager manager = ClientGarmentAssets.INSTANCE.resourceManager();
        try (NativeImage base = read(manager, textures.base());
             NativeImage secondary = readOptional(manager, textures.secondary());
             NativeImage accent = readOptional(manager, textures.accent())) {
            NativeImage output = new NativeImage(part.textureWidth(), part.textureHeight(), false);
            for (int y = 0; y < part.textureHeight(); y++) {
                for (int x = 0; x < part.textureWidth(); x++) {
                    int basePixel = base.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(basePixel);
                    if (alpha == 0) {
                        output.setPixelRGBA(x, y, 0);
                        continue;
                    }
                    FabricColor color = selectColor(x, y, secondary, accent, data);
                    int grayscale = FastColor.ABGR32.red(basePixel);
                    int tint = color.tintRgb();
                    int red = grayscale * FastColor.ARGB32.red(tint) / 255;
                    int green = grayscale * FastColor.ARGB32.green(tint) / 255;
                    int blue = grayscale * FastColor.ARGB32.blue(tint) / 255;
                    output.setPixelRGBA(x, y, FastColor.ABGR32.color(alpha, blue, green, red));
                }
            }

            ResourceLocation dynamicId = ResourceLocation.fromNamespaceAndPath(
                    CreateApparel.MOD_ID,
                    "dynamic/garment/" + NEXT_TEXTURE_ID.getAndIncrement()
            );
            Minecraft.getInstance().getTextureManager().register(dynamicId, new DynamicTexture(output));
            return dynamicId;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compose garment texture: " + part.id(), exception);
        }
    }

    private static FabricColor selectColor(
            int x,
            int y,
            NativeImage secondary,
            NativeImage accent,
            GarmentData data
    ) {
        if (accent != null && FastColor.ABGR32.red(accent.getPixelRGBA(x, y)) == 255) {
            return data.accentColor().orElse(data.primaryColor());
        }
        if (secondary != null && FastColor.ABGR32.red(secondary.getPixelRGBA(x, y)) == 255) {
            return data.secondaryColor().orElse(data.primaryColor());
        }
        return data.primaryColor();
    }

    private static NativeImage read(ResourceManager manager, ResourceLocation texture) throws IOException {
        Resource resource = manager.getResource(textureResource(texture))
                .orElseThrow(() -> new IOException("Missing texture " + texture));
        return NativeImage.read(resource.open());
    }

    private static NativeImage readOptional(
            ResourceManager manager,
            Optional<ResourceLocation> texture
    ) throws IOException {
        return texture.isPresent() ? read(manager, texture.get()) : null;
    }

    private static ResourceLocation textureResource(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private record CacheKey(
            ResourceLocation part,
            ResourceLocation base,
            Optional<ResourceLocation> secondary,
            Optional<ResourceLocation> accent,
            FabricColor primaryColor,
            Optional<FabricColor> secondaryColor,
            Optional<FabricColor> accentColor
    ) {
    }
}
