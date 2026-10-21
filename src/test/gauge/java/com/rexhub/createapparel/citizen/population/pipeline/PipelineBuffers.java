package com.rexhub.createapparel.citizen.population.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PipelineBuffers {
    public interface OwnedBuffer { ByteBuffer apparel$nativeBuffer(); VertexFormat apparel$format(); }
    private static int mode;
    private static long serial;
    private static Session session;
    private static final Map<RenderType, BufferBuilder> FIXED = new LinkedHashMap<>();
    private static final java.util.List<BufferBuilder> OWNED = new java.util.ArrayList<>();
    private static MultiBufferSource.BufferSource fixedSource;
    private static BufferBuilder fallback;
    private static ByteBuffer staging;
    private static Bulk bulk;
    private static boolean flushing;
    private static long wrapped, copied, allocations, releases, fallbacks;

    private PipelineBuffers() { }

    public static void mode(int value) {
        close(); mode = value;
        if (mode == 3) { staging = MemoryUtil.memAlloc(36 * 512); allocations++; }
        if (mode == 4) PipelineSupportAtlas.prepare();
    }
    public static MultiBufferSource wrap(MultiBufferSource source) {
        if (mode == 0) return source;
        if (!RenderSystem.isOnRenderThread()) return source;
        if (source.getClass() != MultiBufferSource.BufferSource.class) { fallbacks++; return source; }
        if (mode == 4) PipelineSupportAtlas.prepare();
        if (session != null) throw new AssertionError("Reentrant Citizen pipeline prototype");
        wrapped++;
        session = new Session(source);
        return session;
    }
    public static void finishCitizen() { fence(); session = null; }
    public static void externalRequest(MultiBufferSource source) {
        if (mode == 0 || !RenderSystem.isOnRenderThread()) return;
        serial++;
        if (mode == 2 && source != fixedSource) fence();
    }
    public static void builderEnded() { if (mode == 1 && RenderSystem.isOnRenderThread()) serial++; }
    public static void beginReloadProbe() { mode(4); PipelineSupportAtlas.beginReloadProbe(); }
    public static void verifyReloadProbe() { PipelineSupportAtlas.verifyReloadProbe(); close(); verifyClosed(); }
    static boolean bulkEligible(VertexConsumer consumer, RenderType type) {
        return consumer.getClass() == BufferBuilder.class && type.mode() == VertexFormat.Mode.QUADS && type.format() == DefaultVertexFormat.NEW_ENTITY
                && ((OwnedBuffer) consumer).apparel$format() == DefaultVertexFormat.NEW_ENTITY;
    }
    public static void fence() {
        if (flushing) return;
        flushing = true;
        try {
            if (bulk != null) bulk.flush();
            if (fixedSource != null) fixedSource.endBatch();
        } finally { flushing = false; }
    }
    public static void close() {
        RenderSystem.assertOnRenderThread();
        fence(); session = null; bulk = null;
        if (staging != null) { MemoryUtil.memFree(staging); staging = null; releases++; }
        for (var builder : OWNED) free(builder);
        FIXED.clear(); OWNED.clear();
        if (fallback != null) free(fallback);
        fallback = null; fixedSource = null;
        PipelineSupportAtlas.close();
        mode = 0;
    }
    private static void free(BufferBuilder builder) {
        if (builder.building()) throw new AssertionError("Owned builder still building");
        MemoryUtil.getAllocator(false).free(MemoryUtil.memAddress0(((OwnedBuffer) builder).apparel$nativeBuffer())); releases++;
    }
    private static boolean eligible(RenderType type) {
        return type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS
                && type.toString().startsWith("RenderType[entity_cutout_no_cull:");
    }
    private static VertexConsumer fixed(RenderType type, MultiBufferSource source) {
        if (!FIXED.containsKey(type)) {
            if (FIXED.size() == 8) { fence(); FIXED.clear(); }
            if (fixedSource == null) {
                fallback = new BoundedBuilder(); allocations++;
                if (!(fallback instanceof OwnedBuffer)) throw new AssertionError("Native ownership accessor unavailable");
                fixedSource = MultiBufferSource.immediateWithBuffers(FIXED, fallback);
            }
            int index = FIXED.size();
            if (index == OWNED.size()) { OWNED.add(new BoundedBuilder()); allocations++; }
            FIXED.put(type, OWNED.get(index));
        }
        return fixedSource.getBuffer(type);
    }
    public static String report() {
        long bytes = staging == null ? 0 : staging.capacity();
        for (var builder : OWNED) bytes += ((OwnedBuffer) builder).apparel$nativeBuffer().capacity();
        if (fallback != null) bytes += ((OwnedBuffer) fallback).apparel$nativeBuffer().capacity();
        return "pipelineMode=" + mode + " wrapped=" + wrapped + " bulkVertices=" + copied + " ownedNativeBytes=" + bytes
                + " nativeAllocations=" + allocations + " nativeReleases=" + releases + " unsupportedSourceFallbacks=" + fallbacks + " " + PipelineSupportAtlas.report() + " (test-only; fixed batches fenced per Citizen; max 8 types)";
    }
    public static void resetCounters() { wrapped = copied = allocations = releases = fallbacks = 0; }
    public static void verifyClosed() {
        PipelineSupportAtlas.verifyClosed();
        if (staging != null || fallback != null || !FIXED.isEmpty() || !OWNED.isEmpty() || allocations != releases)
            throw new AssertionError("Pipeline native ownership imbalance: " + report());
    }
    public static void verifyApplied() {
        if (mode == 4) PipelineSupportAtlas.verifyApplied();
        if (mode != 0 && wrapped == 0 || mode == 2 && OWNED.isEmpty() || mode == 3 && copied == 0)
            throw new AssertionError("Pipeline prototype hooks were not exercised: " + report());
    }

    private static final class BoundedBuilder extends BufferBuilder {
        private int vertices;
        private BoundedBuilder() { super(256); }
        @Override public void begin(VertexFormat.Mode mode, VertexFormat format) { vertices = 0; super.begin(mode, format); }
        @Override public void endVertex() {
            if (++vertices > 4096) throw new AssertionError("Diagnostic fixed buffer exceeded its per-batch vertex budget");
            super.endVertex();
        }
    }

    private static final class Session implements MultiBufferSource {
        private final MultiBufferSource source;
        private RenderType last;
        private VertexConsumer consumer;
        private long observedSerial;
        private boolean usingOriginal = true;
        private Session(MultiBufferSource source) { this.source = source; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            if (mode == 1 && type == last && serial == observedSerial && type.canConsolidateConsecutiveGeometry()) {
                GarmentDiagnostics.count("pipeline.reusedConsumerRequests", 1);
                return consumer;
            }
            if (bulk != null) bulk.flush();
            VertexConsumer result;
            if (mode == 2 && (FIXED.containsKey(type) || eligible(type))) {
                if (usingOriginal) { ((MultiBufferSource.BufferSource) source).endLastBatch(); usingOriginal = false; }
                result = fixed(type, source);
            } else {
                if (mode == 2) { fence(); usingOriginal = true; }
                result = mode == 4 ? PipelineSupportAtlas.consumer(type, source) : source.getBuffer(type);
            }
            if (mode == 3 && bulkEligible(result, type)) {
                bulk = new Bulk((BufferBuilder) result); result = bulk;
            }
            last = type; consumer = result; observedSerial = serial;
            return result;
        }
    }

    private static final class Bulk implements VertexConsumer {
        private final BufferBuilder target;
        private Bulk(BufferBuilder target) { this.target = target; }
        private void flush() {
            if (staging.position() == 0) return;
            int count = staging.position() / 36;
            staging.flip();
            try (var scope = GarmentDiagnostics.measure("pipelineBulkCopy")) { target.putBulkData(staging); }
            staging.clear(); copied += count;
            GarmentDiagnostics.count("pipeline.bulkVertices", count);
        }
        @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v,
                int overlay, int light, float nx, float ny, float nz) {
            if (staging.remaining() < 36) flush();
            staging.putFloat(x).putFloat(y).putFloat(z).put((byte) (r * 255)).put((byte) (g * 255)).put((byte) (b * 255)).put((byte) (a * 255))
                    .putFloat(u).putFloat(v).putShort((short) overlay).putShort((short) (overlay >>> 16))
                    .putShort((short) light).putShort((short) (light >>> 16))
                    .put(BufferVertexConsumer.normalIntValue(nx)).put(BufferVertexConsumer.normalIntValue(ny)).put(BufferVertexConsumer.normalIntValue(nz)).put((byte) 0);
        }
        @Override public VertexConsumer vertex(double x, double y, double z) { flush(); return target.vertex(x, y, z); }
        @Override public VertexConsumer color(int r, int g, int b, int a) { flush(); return target.color(r, g, b, a); }
        @Override public VertexConsumer uv(float u, float v) { flush(); return target.uv(u, v); }
        @Override public VertexConsumer overlayCoords(int u, int v) { flush(); return target.overlayCoords(u, v); }
        @Override public VertexConsumer uv2(int u, int v) { flush(); return target.uv2(u, v); }
        @Override public VertexConsumer normal(float x, float y, float z) { flush(); return target.normal(x, y, z); }
        @Override public void endVertex() { flush(); target.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { flush(); target.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { flush(); target.unsetDefaultColor(); }
    }
}
