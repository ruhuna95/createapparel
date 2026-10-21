package com.rexhub.createapparel.citizen.population.pipeline;

import com.mojang.blaze3d.vertex.*;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

public final class PipelineNativeChecks {
    private PipelineNativeChecks() { }
    public static void run() {
        if (!PipelineBuffers.OwnedBuffer.class.isAssignableFrom(BufferBuilder.class)) throw new AssertionError("Optional ownership hook unavailable");
        var builder = new BufferBuilder(256);
        var source = MultiBufferSource.immediate(builder);
        var type = RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath("createapparel", "textures/body/body_tint_base.png"));
        try {
            byte[] expected = draw(builder, source, type, false);
            byte[] actual = draw(builder, source, type, true);
            PipelineBuffers.verifyApplied();
            if (expected.length != actual.length) throw new AssertionError("Bulk vertex length differs");
            for (int i = 0; i < expected.length; i++) if (i % 36 != 35 && expected[i] != actual[i])
                throw new AssertionError("Bulk attribute byte differs at " + i);
            PipelineBuffers.mode(1);
            var cached = PipelineBuffers.wrap(source);
            cached.getBuffer(type);
            source.endLastBatch();
            cached.getBuffer(type);
            if (!builder.building()) throw new AssertionError("Consumer cache reused an ended batch");
            source.endBatch(); PipelineBuffers.finishCitizen();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            if (PipelineBuffers.bulkEligible(builder, type)) throw new AssertionError("Bulk accepted a non-NEW_ENTITY builder layout");
            if (PipelineSupportAtlas.consumerCompatible(builder)) throw new AssertionError("Atlas accepted an extended builder layout");
            builder.end().release();
            if (!PipelineSupportAtlas.environmentRejection(java.util.List.of("vanilla", "mod_resources"), true).equals("shaderExtension")
                    || !PipelineSupportAtlas.environmentRejection(java.util.List.of("vanilla", "file/custom.zip"), false).equals("unverifiedResourcePack")
                    || !PipelineSupportAtlas.imageRejection(256, 128, false).equals("sourceDimensions")
                    || !PipelineSupportAtlas.imageRejection(64, 64, true).equals("sourceMetadata"))
                throw new AssertionError("Atlas compatibility policy failed");
            PipelineBuffers.mode(4);
            MultiBufferSource foreign = requested -> builder;
            if (PipelineBuffers.wrap(foreign) != foreign) throw new AssertionError("Foreign source was wrapped by atlas");
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            var requested = new java.util.ArrayList<RenderType>();
            MultiBufferSource extended = requestedType -> { requested.add(requestedType); return builder; };
            if (PipelineSupportAtlas.consumer(type, extended) != builder || requested.size() != 2 || requested.get(1) != type)
                throw new AssertionError("Unsupported atlas format did not restore the original texture request");
            requested.clear();
            if (PipelineSupportAtlas.consumer(type, extended) != builder || requested.size() != 1 || requested.get(0) != type)
                throw new AssertionError("Unsupported atlas format was retried in the same resource generation");
            builder.end().release();
            PipelineBuffers.close();
            GarmentDiagnostics.startFramesOnly();
            PipelineProbe.reset();
            PipelineProbe.world(true); PipelineProbe.request(source, type); PipelineProbe.draw(); PipelineProbe.bind(); PipelineProbe.world(false);
            if (GarmentDiagnostics.timing().counter("pipeline.glDrawElements") != 0 || GarmentDiagnostics.timing().counter("pipeline.requests") != 0)
                throw new AssertionError("Disabled detail probe recorded counters");
            GarmentDiagnostics.stop();
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_PIPELINE_NATIVE_PASSED vertices=1028 allAttributeBytesExact=true paddingExcluded=true disabledProbeNoCounters=true endedBatchInvalidated=true actualFormatGuard=true");
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_ATLAS_COMPATIBILITY_POLICY_PASSED extendedFormat=true foreignSource=true shaderPolicy=true packPolicy=true oversizedSource=true metadata=true; not an actual shader-mod runtime test");
        } finally {
            PipelineBuffers.close(); PipelineBuffers.verifyClosed();
            MemoryUtil.getAllocator(false).free(MemoryUtil.memAddress0(((PipelineBuffers.OwnedBuffer) builder).apparel$nativeBuffer()));
        }
    }
    private static byte[] draw(BufferBuilder builder, MultiBufferSource.BufferSource source, RenderType type, boolean bulk) {
        PipelineBuffers.mode(bulk ? 3 : 0);
        var consumer = bulk ? PipelineBuffers.wrap(source).getBuffer(type) : source.getBuffer(type);
        for (int i = 0; i < 1028; i++) consumer.vertex(i * .03125F, -i * .125F, i * .0078125F,
                i % 255 / 255F, .375F, .875F, .5F, (i % 17) / 16F, (i % 9) / 8F,
                0x000A1234, 0x00F05678, i % 3 - 1, .25F, -.75F);
        PipelineBuffers.finishCitizen();
        var rendered = builder.end();
        try {
            var data = rendered.vertexBuffer(); byte[] bytes = new byte[data.remaining()]; data.get(bytes); return bytes;
        } finally { rendered.release(); source.endBatch(type); }
    }
}
