package com.rexhub.createapparel.citizen.population.pipeline;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PipelineAtlasResourceChecks {
    private static final String PACK = "file/atlas-validation.zip";
    private static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("createapparel", "textures/body/body_tint_base.png");
    private static int stage;
    private static CompletableFuture<Void> reload;
    private static List<String> selected;
    private static long generation;

    private PipelineAtlasResourceChecks() { }

    public static boolean tick(java.util.concurrent.Callable<Void> imageCheck) throws Exception {
        if (stage == 3) return true;
        var mc = Minecraft.getInstance();
        if (stage == 0) {
            selected = mc.getResourcePackRepository().getSelectedPacks().stream().map(pack -> pack.getId()).toList();
            var directory = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "atlas-validation-packs");
            Files.createDirectories(directory);
            try (var output = new ZipOutputStream(Files.newOutputStream(directory.resolve("atlas-validation.zip")));
                 var stream = mc.getResourceManager().getResource(BODY).orElseThrow().open(); var image = NativeImage.read(stream)) {
                image.fillRect(0, 0, image.getWidth(), image.getHeight(), 0xFF777777);
                entry(output, "pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Diagnostic atlas fallback\"}}".getBytes(StandardCharsets.UTF_8));
                entry(output, "assets/createapparel/" + BODY.getPath(), image.asByteArray());
                entry(output, "assets/createapparel/" + BODY.getPath() + ".mcmeta", "{\"texture\":{\"blur\":true,\"clamp\":true}}".getBytes(StandardCharsets.UTF_8));
            }
            PipelineBuffers.mode(4);
            PipelineSupportAtlas.verifyApplied();
            PipelineSupportAtlas.beginReloadProbe();
            generation = ClientGarmentAssets.INSTANCE.generation();
            var repository = mc.getResourcePackRepository();
            repository.addPackFinder(new FolderRepositorySource(directory, PackType.CLIENT_RESOURCES, PackSource.DEFAULT));
            repository.reload();
            var packs = new ArrayList<>(selected); packs.add(PACK); repository.setSelected(packs);
            if (!repository.getSelectedIds().contains(PACK)) throw new AssertionError("Diagnostic resource pack was not selected");
            reload = mc.reloadResourcePacks(); stage = 1;
            return false;
        }
        if (!reload.isDone()) return false;
        reload.join();
        if (ClientGarmentAssets.INSTANCE.generation() <= generation) throw new AssertionError("Resource generation did not advance");
        if (stage == 1) {
            PipelineSupportAtlas.verifyFallbackReload();
            var resource = mc.getResourceManager().getResource(BODY).orElseThrow();
            var metadata = resource.metadata().getSection(net.minecraft.client.resources.metadata.texture.TextureMetadataSection.SERIALIZER).orElseThrow();
            if (!metadata.isBlur() || !metadata.isClamp()) throw new AssertionError("Replacement sampler metadata was not loaded");
            try (var stream = resource.open(); var image = NativeImage.read(stream)) {
                if (!resource.sourcePackId().equals(PACK) || image.getPixelRGBA(0, 0) != 0xFF777777)
                    throw new AssertionError("Replacement body texture was not loaded");
            }
            var builder = new BufferBuilder(256);
            try {
                if (PipelineSupportAtlas.consumer(RenderType.entityCutoutNoCull(BODY), type -> builder) != builder)
                    throw new AssertionError("Unsupported pack retained UV remapping");
            } finally {
                org.lwjgl.system.MemoryUtil.getAllocator(false).free(org.lwjgl.system.MemoryUtil.memAddress0(((PipelineBuffers.OwnedBuffer) builder).apparel$nativeBuffer()));
            }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_ATLAS_RESOURCE_PACK_FALLBACK_PASSED source={} replacementPixels=true metadataRetained=true originalConsumer=true oldNativeImageClosed=true {}", resource.sourcePackId(), PipelineSupportAtlas.report());
            imageCheck.call();
            PipelineBuffers.close(); PipelineBuffers.verifyClosed();
            mc.getResourcePackRepository().setSelected(selected);
            generation = ClientGarmentAssets.INSTANCE.generation();
            reload = mc.reloadResourcePacks(); stage = 2;
            return false;
        }
        PipelineBuffers.mode(4); PipelineSupportAtlas.verifyApplied();
        PipelineBuffers.close(); PipelineBuffers.verifyClosed();
        if (!mc.getResourcePackRepository().getSelectedPacks().stream().map(pack -> pack.getId()).toList().equals(selected))
            throw new AssertionError("Selected pack order was not restored");
        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_ATLAS_RESOURCE_PACK_RESTORE_PASSED atlasRebuilt=true selectionOrderRestored=true resourcesClosed=true");
        stage = 3;
        return true;
    }

    private static void entry(ZipOutputStream output, String name, byte[] data) throws java.io.IOException {
        output.putNextEntry(new ZipEntry(name)); output.write(data); output.closeEntry();
    }
}
