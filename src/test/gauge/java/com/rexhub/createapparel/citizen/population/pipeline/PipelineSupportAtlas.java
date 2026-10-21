package com.rexhub.createapparel.citizen.population.pipeline;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;

final class PipelineSupportAtlas {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("createapparel", "pipeline_trial/support_atlas");
    private static final String[] SOURCES = {"textures/body/body_tint_base.png", "textures/garment/default_underwear_top/top_torso/base/default.png",
            "textures/garment/default_underwear_top/top_bust/base/default.png", "textures/garment/default_underwear_bottom/bottom/base/default.png"};
    private static final Map<RenderType, Tile> TILES = new IdentityHashMap<>();
    private static boolean owned;
    private static long generation;
    private static long builds, releases;
    private static DynamicTexture ownedTexture;
    private static int ownedTextureId;
    private static DynamicTexture reloadingTexture;
    private static long reloadBuilds;
    private static long lastBuildNanos, lastUploadNanos;
    private static long attemptedGeneration = Long.MIN_VALUE;
    private static String fallbackReason = "none";
    private static long fallbackRequests;
    private PipelineSupportAtlas() { }

    static void prepare() {
        long nextGeneration = ClientGarmentAssets.INSTANCE.generation();
        if (attemptedGeneration == nextGeneration) return;
        close();
        attemptedGeneration = nextGeneration;
        long started = System.nanoTime();
        var mc = Minecraft.getInstance();
        fallbackReason = environmentRejection(mc.getResourceManager().listPacks().map(pack -> pack.packId()).toList(),
                net.minecraftforge.fml.ModList.get().isLoaded("oculus") || net.minecraftforge.fml.ModList.get().isLoaded("iris") || hasOptifine());
        if (!fallbackReason.equals("none")) return;
        NativeImage image = new NativeImage(512, 512, true);
        DynamicTexture texture = null;
        try {
            for (int i = 0; i < SOURCES.length; i++) {
                var source = ResourceLocation.fromNamespaceAndPath("createapparel", SOURCES[i]);
                try (var stream = mc.getResourceManager().getResource(source).orElseThrow().open(); var input = NativeImage.read(stream)) {
                    int width = input.getWidth(), height = input.getHeight(), x = (i % 2) * 256 + 2, y = (i / 2) * 256 + 2;
                    String rejection = imageRejection(width, height, mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("createapparel", SOURCES[i] + ".mcmeta")).isPresent());
                    if (!rejection.equals("none")) throw new java.io.IOException(rejection);
                    for (int row = -2; row < height + 2; row++) for (int column = -2; column < width + 2; column++)
                        image.setPixelRGBA(x + column, y + row, input.getPixelRGBA(Math.floorMod(column, width), Math.floorMod(row, height)));
                    TILES.put(RenderType.entityCutoutNoCull(source), new Tile(x, y, width, height));
                }
            }
            long uploadStarted = System.nanoTime();
            texture = new DynamicTexture(image); image = null;
            mc.getTextureManager().register(ID, texture); owned = true; builds++;
            ownedTexture = texture;
            ownedTextureId = texture.getId();
            generation = ClientGarmentAssets.INSTANCE.generation();
            lastUploadNanos = System.nanoTime() - uploadStarted;
            lastBuildNanos = System.nanoTime() - started;
        } catch (Exception failure) {
            if (texture != null) texture.close();
            if (image != null) image.close();
            TILES.clear();
            fallbackReason = "source:" + failure.getClass().getSimpleName();
            com.mojang.logging.LogUtils.getLogger().warn("Support atlas unavailable; retaining original textures", failure);
        } catch (Error failure) {
            if (texture != null) texture.close();
            if (image != null) image.close();
            TILES.clear();
            throw failure;
        }
    }
    static String environmentRejection(java.util.Collection<String> packs, boolean shaderExtension) {
        if (shaderExtension) return "shaderExtension";
        if (packs.stream().anyMatch(id -> !id.equals("vanilla") && !id.equals("mod_resources"))) return "unverifiedResourcePack";
        return "none";
    }
    static String imageRejection(int width, int height, boolean metadata) {
        if (width < 1 || height < 1 || width > 128 || height > 128) return "sourceDimensions";
        return metadata ? "sourceMetadata" : "none";
    }
    private static boolean hasOptifine() {
        try { Class.forName("net.optifine.Config", false, PipelineSupportAtlas.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
        catch (LinkageError unsupported) { return true; }
    }
    static VertexConsumer consumer(RenderType type, MultiBufferSource source) {
        var tile = TILES.get(type);
        if (tile == null) { if (!fallbackReason.equals("none")) fallbackRequests++; return source.getBuffer(type); }
        var target = source.getBuffer(RenderType.entityCutoutNoCull(ID));
        if (!consumerCompatible(target)) {
            fallbackRequests++; fallbackReason = "consumerFormat"; TILES.clear();
            return source.getBuffer(type);
        }
        return new Uv(target, tile);
    }
    static boolean consumerCompatible(VertexConsumer target) {
        return target.getClass() == BufferBuilder.class && target instanceof PipelineBuffers.OwnedBuffer buffer
                && buffer.apparel$format() == DefaultVertexFormat.NEW_ENTITY;
    }
    static void close() {
        if (owned) {
            int textureId = ownedTextureId;
            Minecraft.getInstance().getTextureManager().release(ID); owned = false; releases++;
            if (ownedTexture.getPixels() != null || org.lwjgl.opengl.GL11.glIsTexture(textureId)) throw new AssertionError("Atlas NativeImage or GL texture survived release");
            ownedTexture = null;
        }
        TILES.clear();
        attemptedGeneration = Long.MIN_VALUE;
        fallbackReason = "none";
        fallbackRequests = 0;
    }
    static String report() {
        return "supportAtlasEntries=" + (owned ? 1 : 0) + " supportAtlasTexelBytes=" + (owned ? 512 * 512 * 4 : 0) + " supportAtlasBuilds=" + builds + " supportAtlasReleases=" + releases
                + " supportAtlasFallback=" + fallbackReason + " supportAtlasFallbackRequests=" + fallbackRequests
                + " supportAtlasLastBuildCpuMs=" + lastBuildNanos / 1e6 + " supportAtlasLastUploadCpuMs=" + lastUploadNanos / 1e6 + " (upload is included in build; not GPU elapsed)";
    }
    static void beginReloadProbe() { prepare(); reloadingTexture = ownedTexture; reloadBuilds = builds; }
    static void verifyReloadProbe() {
        prepare();
        if (builds != reloadBuilds + 1 || reloadingTexture == ownedTexture || reloadingTexture == null || reloadingTexture.getPixels() != null)
            throw new AssertionError("Active atlas did not replace and release its old resource generation");
        reloadingTexture = null;
        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_PIPELINE_ACTIVE_RELOAD_PASSED generation={} replacedOnce=true oldNativeImageClosed=true", generation);
    }
    static void verifyFallbackReload() {
        prepare();
        if (owned || builds != reloadBuilds || !fallbackReason.equals("unverifiedResourcePack") || reloadingTexture == null || reloadingTexture.getPixels() != null)
            throw new AssertionError("Resource pack did not safely release and bypass the active atlas: " + report());
        reloadingTexture = null;
    }
    static void verifyClosed() { if (owned || !TILES.isEmpty() || builds != releases) throw new AssertionError("Support atlas lifetime mismatch"); }
    static void verifyApplied() { if (!owned || fallbackRequests != 0) throw new AssertionError("Atlas fixture unexpectedly fell back: " + report()); }
    private record Tile(int x, int y, int width, int height) {
        float u(float value) { return (x + value * width) / 512; }
        float v(float value) { return (y + value * height) / 512; }
    }
    private record Uv(VertexConsumer delegate, Tile tile) implements VertexConsumer {
        @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            delegate.vertex(x, y, z, r, g, b, a, tile.u(u), tile.v(v), overlay, light, nx, ny, nz);
        }
        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(tile.u(u), tile.v(v)); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
