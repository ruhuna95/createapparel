package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.platform.NativeImage;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.garment.GarmentAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeGarmentTextureCache {
    private static final ArrayDeque<ResourceLocation> FREE_TEXTURE_IDS = new ArrayDeque<>();
    private static final RenderResourceCache<CacheKey, ResourceLocation> CACHE = new RenderResourceCache<>(1024,
            texture -> {
                Minecraft.getInstance().getTextureManager().release(texture);
                FREE_TEXTURE_IDS.addLast(texture);
            });
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();

    private RuntimeGarmentTextureCache() {
    }

    public static ResourceLocation get(
            GarmentPartModel part,
            ResolvedTextures textures,
            GarmentAppearance data
    ) {
        return CACHE.get(key(part, textures, data), () -> compose(part, textures, data));
    }

    public static TextureReference reference(GarmentPartModel part, ResolvedTextures textures, GarmentAppearance data) {
        return new TextureReference(CACHE.reference(key(part, textures, data), () -> compose(part, textures, data)));
    }

    private static CacheKey key(GarmentPartModel part, ResolvedTextures textures, GarmentAppearance data) {
        return new CacheKey(
                part.id(),
                textures.base(),
                textures.secondary(),
                textures.accent(),
                textures.overlay(),
                textures.designOverlay(),
                textures.brightness(),
                textures.brightnessMask(),
                data.primaryTintRgb(),
                data.secondaryTintRgb(),
                data.accentTintRgb()
        );
    }

    public static void clear() {
        CACHE.clear();
    }

    public static void endFrame() {
        CACHE.endFrame();
    }

    public static final class TextureReference {
        private final RenderResourceCache<CacheKey, ResourceLocation>.Reference reference;

        private TextureReference(RenderResourceCache<CacheKey, ResourceLocation>.Reference reference) {
            this.reference = reference;
        }

        public ResourceLocation get() {
            return reference.get();
        }
    }

    private static ResourceLocation compose(
            GarmentPartModel part,
            ResolvedTextures textures,
            GarmentAppearance data
    ) {
        ResourceManager manager = ClientGarmentAssets.INSTANCE.resourceManager();
        try (NativeImage base = read(manager, textures.base());
             NativeImage secondary = readOptional(manager, textures.secondary());
             NativeImage accent = readOptional(manager, textures.accent());
             NativeImage overlay = readOptional(manager, textures.overlay());
             NativeImage designOverlay = readOptional(manager, textures.designOverlay());
             NativeImage brightnessMask = readOptional(manager, textures.brightnessMask())) {
            NativeImage output = new NativeImage(part.textureWidth(), part.textureHeight(), false);
            int primaryTint = data.primaryTintRgb();
            OptionalInt secondaryTint = data.secondaryTintRgb();
            OptionalInt accentTint = data.accentTintRgb();
            for (int y = 0; y < part.textureHeight(); y++) {
                for (int x = 0; x < part.textureWidth(); x++) {
                    int basePixel = base.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(basePixel);
                    int composedPixel = 0;
                    if (alpha != 0) {
                        int grayscale = FastColor.ABGR32.red(basePixel);
                        int tint = GarmentTexturePixelOps.composeTint(
                                primaryTint,
                                secondaryTint,
                                accentTint,
                                maskRed(secondary, x, y),
                                maskRed(accent, x, y)
                        );
                        int red = grayscale * FastColor.ARGB32.red(tint) / 255;
                        int green = grayscale * FastColor.ARGB32.green(tint) / 255;
                        int blue = grayscale * FastColor.ARGB32.blue(tint) / 255;
                        composedPixel = FastColor.ABGR32.color(alpha, blue, green, red);
                        composedPixel = GarmentTexturePixelOps.applyBrightness(
                                composedPixel,
                                textures.brightness(),
                                brightnessMask == null ? GarmentTexturePixelOps.NEUTRAL_BRIGHTNESS_LUMINANCE
                                        : maskRed(brightnessMask, x, y)
                        );
                    }
                    output.setPixelRGBA(x, y, GarmentTexturePixelOps.applyOverlays(
                            composedPixel, overlayPixel(designOverlay, x, y), overlayPixel(overlay, x, y)));
                }
            }

            ResourceLocation dynamicId = FREE_TEXTURE_IDS.pollFirst();
            if (dynamicId == null) {
                dynamicId = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                        "dynamic/garment/" + NEXT_TEXTURE_ID.getAndIncrement());
            }
            Minecraft.getInstance().getTextureManager().register(dynamicId, new DynamicTexture(output));
            return dynamicId;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compose garment texture: " + part.id(), exception);
        }
    }

    private static int maskRed(NativeImage mask, int x, int y) {
        return mask == null ? 0 : FastColor.ABGR32.red(mask.getPixelRGBA(x, y));
    }

    private static OptionalInt overlayPixel(NativeImage overlay, int x, int y) {
        return overlay == null ? OptionalInt.empty() : OptionalInt.of(overlay.getPixelRGBA(x, y));
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
            Optional<ResourceLocation> overlay,
            Optional<ResourceLocation> designOverlay,
            float brightness,
            Optional<ResourceLocation> brightnessMask,
            int primaryTint,
            OptionalInt secondaryTint,
            OptionalInt accentTint
    ) {
    }
}
