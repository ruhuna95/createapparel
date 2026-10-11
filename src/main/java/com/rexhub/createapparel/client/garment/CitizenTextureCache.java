package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenHeadPixels;
import com.rexhub.createapparel.citizen.CitizenHairPixels;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.client.citizen.CitizenHairStyle;
import com.rexhub.createapparel.entity.CitizenAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.OptionalInt;

public final class CitizenTextureCache {
    private static final ArrayDeque<ResourceLocation> FREE_IDS = new ArrayDeque<>();
    private static int nextId;
    private static final RenderResourceCache<Key, ResourceLocation> CACHE = new RenderResourceCache<>(256, id -> {
        Minecraft.getInstance().getTextureManager().release(id);
        FREE_IDS.addLast(id);
    });
    private static final GarmentSourceImageCache<ResourceLocation, NativeImage> SOURCES =
            new GarmentSourceImageCache<>(CitizenTextureCache::read, NativeImage::close);

    private CitizenTextureCache() { }

    public static ResourceLocation get(CitizenAppearance appearance, CitizenHairStyle style) {
        RenderSystem.assertOnRenderThread();
        Key key = new Key(appearance.skinTone().tintRgb(), appearance.hairColorRgb(), appearance.eyeColorRgb(),
                style.id(), style.texture(), ClientCitizenAssets.INSTANCE.generation());
        return CACHE.get(key, () -> compose(key));
    }

    public static void endFrame() { CACHE.endFrame(); }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(CitizenTextureCache::clear);
            return;
        }
        CACHE.clear();
        SOURCES.clear();
    }

    private static ResourceLocation compose(Key key) {
        NativeImage face;
        NativeImage hair;
        try {
            face = SOURCES.get(ClientCitizenAssets.FACE);
            hair = key.texture().isPresent() ? SOURCES.get(key.texture().get()) : null;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Citizen head textures", exception);
        }
        NativeImage output = new NativeImage(64, 32, false);
        DynamicTexture texture = null;
        try {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                int hairPixel = hair == null ? 0 : CitizenHeadPixels.tintHair(argb(hair.getPixelRGBA(x, y)), key.hair());
                int pixel = hairPixel;
                if (x < 32 && y < 16) {
                    int facePixel = CitizenHeadPixels.tintFace(argb(face.getPixelRGBA(x, y)), x, y, key.skin(), key.hair(), key.eye());
                    pixel = GarmentTexturePixelOps.applyOverlay(facePixel, OptionalInt.of(hairPixel));
                }
                output.setPixelRGBA(x, y, argb(pixel));
            }
            ResourceLocation id = FREE_IDS.pollFirst();
            if (id == null) id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "dynamic/citizen/" + nextId++);
            texture = new DynamicTexture(output);
            texture.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        } catch (RuntimeException | Error exception) {
            if (texture == null) output.close();
            else texture.close();
            throw exception;
        }
    }

    private static NativeImage read(ResourceLocation id) {
        boolean face = id.equals(ClientCitizenAssets.FACE);
        NativeImage image = null;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id)
                    .orElseThrow(() -> new IOException("Missing Citizen texture: " + id));
            try (var stream = resource.open()) { image = NativeImage.read(stream); }
            if (image.getWidth() != (face ? 32 : 64) || image.getHeight() != (face ? 16 : 32)) {
                throw new IOException("Invalid Citizen texture dimensions: " + id);
            }
            return image;
        } catch (IOException | RuntimeException exception) {
            if (image != null) image.close();
            LogUtils.getLogger().warn("Invalid Citizen texture {}; using built-in fallback", id, exception);
            if (!face) {
                ResourceLocation shortTexture = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                        "textures/entity/citizen/hair/short_tint_base.png");
                if (!id.equals(shortTexture)) return read(shortTexture);
            }
            NativeImage fallback = new NativeImage(face ? 32 : 64, face ? 16 : 32, false);
            for (int y = 0; y < fallback.getHeight(); y++) for (int x = 0; x < fallback.getWidth(); x++) {
                int pixel = face ? CitizenHeadPixels.fallbackFaceTint(x, y) : fallbackHair(x, y);
                fallback.setPixelRGBA(x, y, argb(pixel));
            }
            return fallback;
        }
    }

    private static int fallbackHair(int x, int y) {
        return CitizenHairPixels.pixel("short", x, y);
    }

    private static int argb(int pixel) {
        return pixel & 0xFF00FF00 | (pixel & 0xFF) << 16 | pixel >>> 16 & 0xFF;
    }

    private record Key(int skin, int hair, int eye, ResourceLocation style, java.util.Optional<ResourceLocation> texture, long generation) { }
}
