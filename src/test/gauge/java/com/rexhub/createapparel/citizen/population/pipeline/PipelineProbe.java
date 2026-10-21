package com.rexhub.createapparel.citizen.population.pipeline;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.client.mirror.MirrorRenderManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PipelineProbe {
    private static final Map<RenderType, Integer> TYPES = new IdentityHashMap<>();
    private static final Map<MultiBufferSource, RenderType> PREVIOUS = new IdentityHashMap<>();
    private static final ArrayDeque<RenderType> FLUSHES = new ArrayDeque<>();
    private static final ArrayDeque<com.rexhub.createapparel.citizen.population.DiagnosticTiming.Scope> SCOPES = new ArrayDeque<>();
    private static int worldDepth;
    public static boolean enabled = true;

    private PipelineProbe() { }

    public static boolean active() { return enabled && worldDepth > 0 && GarmentDiagnostics.collectingRenderDetails(); }
    public static void world(boolean begin) {
        if (!begin) PipelineBuffers.fence();
        worldDepth += begin ? 1 : -1;
        if (worldDepth < 0) throw new AssertionError("Unbalanced world probe");
    }
    private static void count(String name, long value) {
        GarmentDiagnostics.count("pipeline." + name, value);
        GarmentDiagnostics.count(MirrorRenderManager.INSTANCE.isRenderingReflection() ? "pipeline.reflection." + name : "pipeline.main." + name, value);
    }
    private static int type(RenderType type) {
        if (TYPES.size() >= 64 && !TYPES.containsKey(type)) return 64;
        return TYPES.computeIfAbsent(type, ignored -> TYPES.size());
    }
    public static void request(MultiBufferSource source, RenderType type) {
        PipelineBuffers.externalRequest(source);
        if (!active()) return;
        count("requests", 1);
        if (PREVIOUS.put(source, type) != type) count("requestChanges", 1);
        GarmentDiagnostics.count("pipeline.type" + type(type) + ".requests", 1);
    }
    public static void flush(RenderType type, BufferBuilder builder) {
        if (!active()) return;
        FLUSHES.push(type);
        SCOPES.push(GarmentDiagnostics.measure("pipeline.type" + type(type) + ".flushCpu"));
        if (builder.building()) count(builder.isCurrentBatchEmpty() ? "emptyBufferEnds" : "nonemptyBufferEnds", 1);
    }
    public static void flushed() {
        if (!active()) return;
        SCOPES.pop().close(); FLUSHES.pop();
    }
    public static void ended(BufferBuilder.RenderedBuffer buffer) {
        if (!active()) return;
        count("bufferEnds", 1);
        count("uploadedVertices", buffer.drawState().vertexCount());
        if (!FLUSHES.isEmpty()) GarmentDiagnostics.count("pipeline.type" + type(FLUSHES.peek()) + ".vertices", buffer.drawState().vertexCount());
    }
    public static void draw() {
        if (!active()) return;
        count("glDrawElements", 1);
        if (!FLUSHES.isEmpty()) GarmentDiagnostics.count("pipeline.type" + type(FLUSHES.peek()) + ".draws", 1);
    }
    public static void bind() { if (active()) count("glTextureBinds", 1); }
    public static void reset() {
        if (!SCOPES.isEmpty() || !FLUSHES.isEmpty()) throw new AssertionError("Unbalanced flush probe");
        TYPES.clear(); PREVIOUS.clear();
    }
    public static String report() {
        var result = new StringBuilder("worldOnly=true attribution=world-not-Citizen GPUtime=unmeasured glDrawArrays/multiDraw/direct-mod-GL=unmeasured");
        TYPES.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> result.append("\ntype").append(entry.getValue()).append('=').append(entry.getKey()));
        return result.toString();
    }
    public static void verify() {
        if (GarmentDiagnostics.timing().counter("pipeline.glDrawElements") <= 0
                || GarmentDiagnostics.timing().counter("pipeline.requests") <= 0
                || GarmentDiagnostics.timing().counter("pipeline.bufferEnds") <= 0
                || GarmentDiagnostics.timing().counter("pipeline.nonemptyBufferEnds") <= 0
                || GarmentDiagnostics.timing().counter("pipeline.glTextureBinds") <= 0)
            throw new AssertionError("Optional pipeline hooks unavailable; metrics must not be reported as zero");
    }
}
