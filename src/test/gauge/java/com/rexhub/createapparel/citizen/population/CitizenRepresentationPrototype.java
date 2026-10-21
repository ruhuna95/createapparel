package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.client.renderer.CitizenRenderer;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

final class CitizenRepresentationPrototype extends EntityRenderer<Citizen> implements AutoCloseable {
    enum Mode { ORIGINAL, MESH, IMPOSTOR }
    private static final int CAPACITY = 64;
    private final CitizenRenderer original;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, .75F, true);
    private final WeakHashMap<Citizen, Identity> identities = new WeakHashMap<>();
    private final WeakHashMap<Citizen, Integer> levels = new WeakHashMap<>();
    private final ArrayDeque<ResourceLocation> reusableIds = new ArrayDeque<>();
    private TextureTarget target;
    private long generation = -1, lastSpriteFrame = -1;
    private Mode mode = Mode.ORIGINAL;
    private boolean closed;
    private final java.lang.reflect.Field snapshotsField;
    private java.lang.reflect.Field mainLodField;
    long reflectionChecks;
    final Set<Object> observedReflectionViews = Collections.newSetFromMap(new IdentityHashMap<>());
    private int serial;
    long hits, misses, builds, spriteBuilds, evictions, fallback, submitted, buildNanos, released, emptySprites;

    private CitizenRepresentationPrototype(EntityRendererProvider.Context context, CitizenRenderer original) {
        super(context);
        this.original = original;
        try { snapshotsField = CitizenRenderer.class.getDeclaredField("snapshots"); snapshotsField.setAccessible(true); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    static CitizenRepresentationPrototype install() {
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        var current = dispatcher.renderers.get(ModEntities.CITIZEN.get());
        if (current instanceof CitizenRepresentationPrototype existing) return existing;
        var context = new EntityRendererProvider.Context(dispatcher, mc.getItemRenderer(), mc.getBlockRenderer(),
                dispatcher.getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
        var prototype = new CitizenRepresentationPrototype(context, (CitizenRenderer) current);
        var renderers = new HashMap<>(dispatcher.renderers);
        renderers.put(ModEntities.CITIZEN.get(), prototype);
        dispatcher.renderers = renderers;
        return prototype;
    }

    void mode(Mode next) { clear(); closed = false; observedReflectionViews.clear(); mode = next; lastSpriteFrame = -1; hits = misses = builds = spriteBuilds = evictions = fallback = submitted = buildNanos = released = emptySprites = reflectionChecks = 0; }

    @Override public void render(Citizen entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        RenderSystem.assertOnRenderThread();
        if (closed) { original.render(entity, yaw, partialTick, pose, buffers, light); return; }
        var mc = Minecraft.getInstance();
        long nextGeneration = ClientGarmentAssets.INSTANCE.generation();
        if (generation != nextGeneration) { clear(); identities.clear(); levels.clear(); generation = nextGeneration; }
        var mirrors = com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE;
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(entity.position());
        int previous = levels.getOrDefault(entity, -1);
        int lod = com.rexhub.createapparel.client.garment.CitizenRenderLod.select(previous, distance, 12, 32, 64, 1);
        levels.put(entity, lod);
        if (mode == Mode.ORIGINAL || lod < 3 || mirrors.isRenderingReflection() || entity.isInvisible()) {
            int mainBefore = mirrors.isRenderingReflection() ? mainLod(entity) : -1;
            original.render(entity, yaw, partialTick, pose, buffers, light);
            if (mirrors.isRenderingReflection()) {
                if (mainBefore >= 0 && mainBefore != mainLod(entity)) throw new AssertionError("Reflection overwrote main-camera LOD");
                reflectionChecks++;
                if (observedReflectionViews.size() < 64) observedReflectionViews.add(mirrors.reflectionViewKey());
            }
            shadowRadius = GarmentDiagnostics.debugDisableCitizenShadows ? 0 : .35F * entity.getScale();
            return;
        }
        shadowRadius = GarmentDiagnostics.debugDisableCitizenShadows ? 0 : .35F * entity.getScale();
        var identity = identities.get(entity);
        if (identity == null || identity.revision != entity.citizenState().revision()) {
            var state = entity.citizenState();
            identity = new Identity(state.revision(), state.loadout().serializeNBT().toString(), state.appearance().toString(), state.age().name());
            identities.put(entity, identity);
        }
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float cameraYaw = mc.gameRenderer.getMainCamera().getYRot();
        int direction = Math.floorMod(Math.round((bodyYaw - cameraYaw) / 45), 8);
        int phase = Math.floorMod((int) Math.floor(entity.walkAnimation.position(partialTick) * .6662 / (Math.PI * 2) * 8), 8);
        var key = new Key(identity.loadout, identity.appearance, identity.age, entity.citizenState().behavior().mode().name() + entity.citizenState().behavior().pose().name(), phase,
                mode == Mode.IMPOSTOR ? direction : 0);
        var entry = entries.get(key);
        if (entry == null) {
            misses++;
            long frame = mc.level.getGameTime();
            if (mode == Mode.IMPOSTOR && lastSpriteFrame == frame) {
                fallback++; original.render(entity, yaw, partialTick, pose, buffers, light); return;
            }
            long start = System.nanoTime();
            var capture = new Capture();
            int forced = GarmentDiagnostics.debugForceCitizenLod;
            try {
                GarmentDiagnostics.debugForceCitizenLod = 3;
                original.render(entity, yaw, partialTick, new PoseStack(), capture, light);
            } finally { GarmentDiagnostics.debugForceCitizenLod = forced; }
            entry = new Entry(capture.finish(), bodyYaw);
            builds++;
            if (mode == Mode.IMPOSTOR) {
                lastSpriteFrame = frame;
                if (!makeSprite(entry, cameraYaw, light)) {
                    buildNanos += System.nanoTime() - start;
                    emptySprites++; fallback++; original.render(entity, yaw, partialTick, pose, buffers, light); return;
                }
                spriteBuilds++;
            }
            buildNanos += System.nanoTime() - start;
            if (entries.size() == CAPACITY) {
                var iterator = entries.entrySet().iterator();
                var oldest = iterator.next(); release(oldest.getValue()); iterator.remove(); evictions++;
            }
            entries.put(key, entry);
        } else hits++;
        try (var diagnostic = GarmentDiagnostics.citizen(entity.getId())) {
        GarmentDiagnostics.lod(lod, previous >= 0 && previous != lod);
        if (mode == Mode.MESH) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(entry.bodyYaw - bodyYaw));
            submitted += entry.mesh.draw(pose, buffers, light, net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(entity, 0));
            pose.popPose();
        } else {
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            var type = RenderType.entityCutoutNoCull(entry.texture);
            var consumer = buffers.getBuffer(type);
            var matrix = pose.last().pose();
            consumer.vertex(matrix, -1.5F, 0, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, 1.5F, 0, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, 1.5F, 3, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, -1.5F, 3, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            submitted += 4;
            pose.popPose();
        }
        }
    }

    private boolean makeSprite(Entry entry, float cameraYaw, int light) {
        var mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();
        int previousFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var viewport = new int[4]; org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var view = RenderSystem.getModelViewStack(); view.pushPose();
        try {
            if (target == null) target = new TextureTarget(128, 128, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            view.setIdentity(); RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(new Matrix4f().setOrtho(-1.5F, 1.5F, 0, 3, -10, 10));
            var spritePose = new PoseStack(); spritePose.mulPose(Axis.YP.rotationDegrees(cameraYaw - 180));
            entry.mesh.draw(spritePose, buffers, light, OverlayTexture.NO_OVERLAY); buffers.endBatch();
            var image = new com.mojang.blaze3d.platform.NativeImage(128, 128, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false); image.flipY();
            boolean visible = false;
            for (int y = 0; y < image.getHeight() && !visible; y++) for (int x = 0; x < image.getWidth(); x++)
                if ((image.getPixelRGBA(x, y) >>> 24) >= 26) { visible = true; break; }
            if (!visible) { image.close(); return false; }
            DynamicTexture texture;
            try { texture = new DynamicTexture(image); }
            catch (Throwable error) { image.close(); throw error; }
            var id = reusableIds.pollFirst();
            if (id == null) id = ResourceLocation.fromNamespaceAndPath("createapparel", "diagnostic/citizen_proxy_" + serial++);
            try { mc.getTextureManager().register(id, texture); entry.texture = id; }
            catch (Throwable error) { texture.close(); throw error; }
            return true;
        } finally {
            view.popPose(); RenderSystem.applyModelViewMatrix(); mc.gameRenderer.resetProjectionMatrix(projection);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previousFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    String report() {
        long bytes = entries.values().stream().mapToLong(entry -> entry.mesh.bytes()).sum();
        return "prototype=" + mode + " geometryHits=" + hits + " geometryMisses=" + misses + " meshBuilds=" + builds
                + " spriteBuilds=" + spriteBuilds + " evictions=" + evictions + " fallback3d=" + fallback + " vertices=" + submitted
                + " entries=" + entries.size() + " meshArrayBytes=" + bytes + " buildTotalMs=" + buildNanos / 1e6 + " emptySpriteFallback=" + emptySprites
                + " generation=" + generation + " (diagnostic pose-baked prototype; CPU submission, not GPU timing; mirrors use original 3D)";
    }
    CitizenRenderer original() { return original; }
    private int mainLod(Citizen citizen) {
        try {
            var cached = ((Map<?, ?>) snapshotsField.get(original)).get(citizen);
            if (cached == null) return -1;
            if (mainLodField == null) { mainLodField = cached.getClass().getDeclaredField("lod"); mainLodField.setAccessible(true); }
            return mainLodField.getInt(cached);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    int lod(Citizen citizen) { return levels.getOrDefault(citizen, -1); }
    boolean resourcesClosed() { return closed && entries.isEmpty() && target == null && released == spriteBuilds; }

    private void release(Entry entry) {
        if (entry.texture != null) { Minecraft.getInstance().getTextureManager().release(entry.texture); reusableIds.addLast(entry.texture); entry.texture = null; released++; }
    }
    private void clear() { for (var entry : entries.values()) release(entry); entries.clear(); }
    @Override public void close() { closed = true; clear(); identities.clear(); levels.clear(); observedReflectionViews.clear(); if (target != null) { target.destroyBuffers(); target = null; } }
    @Override public ResourceLocation getTextureLocation(Citizen entity) { return original.getTextureLocation(entity); }
    @Override public boolean shouldRender(Citizen entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return original.shouldRender(entity, frustum, x, y, z);
    }
    private record Identity(long revision, String loadout, String appearance, String age) { }
    private record Key(String loadout, String appearance, String age, String pose, int phase, int direction) { }
    private static final class Entry {
        final Mesh mesh; final float bodyYaw; ResourceLocation texture;
        Entry(Mesh mesh, float bodyYaw) { this.mesh = mesh; this.bodyYaw = bodyYaw; }
    }
    private record Segment(RenderType type, float[] vertices) { }
    private record Mesh(List<Segment> segments) {
        long bytes() { return segments.stream().mapToLong(segment -> segment.vertices.length * 4L).sum(); }
        int draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var position = new Vector4f(); var normal = new Vector3f(); int count = 0;
            for (var segment : segments) {
                var consumer = buffers.getBuffer(segment.type); var data = segment.vertices;
                for (int i = 0; i < data.length; i += 12) {
                    position.set(data[i], data[i + 1], data[i + 2], 1).mul(pose.last().pose());
                    normal.set(data[i + 9], data[i + 10], data[i + 11]).mul(pose.last().normal());
                    consumer.vertex(position.x, position.y, position.z, data[i + 3], data[i + 4], data[i + 5], data[i + 6],
                            data[i + 7], data[i + 8], overlay, light, normal.x, normal.y, normal.z);
                    count++;
                }
            }
            return count;
        }
    }
    private static final class Capture implements MultiBufferSource {
        final List<Segment> segments = new ArrayList<>();
        final List<Float> values = new ArrayList<>();
        RenderType current;
        private void flush() {
            if (current == null) return;
            var data = new float[values.size()]; for (int i = 0; i < data.length; i++) data[i] = values.get(i);
            segments.add(new Segment(current, data)); values.clear(); current = null;
        }
        Mesh finish() { flush(); return new Mesh(List.copyOf(segments)); }
        @Override public VertexConsumer getBuffer(RenderType type) {
            if (current != type) { flush(); current = type; }
            return new VertexConsumer() {
                final float[] vertex = new float[12];
                @Override public VertexConsumer vertex(double x, double y, double z) { vertex[0] = (float) x; vertex[1] = (float) y; vertex[2] = (float) z; return this; }
                @Override public VertexConsumer color(int r, int g, int b, int a) { vertex[3] = r / 255F; vertex[4] = g / 255F; vertex[5] = b / 255F; vertex[6] = a / 255F; return this; }
                @Override public VertexConsumer uv(float u, float v) { vertex[7] = u; vertex[8] = v; return this; }
                @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
                @Override public VertexConsumer uv2(int u, int v) { return this; }
                @Override public VertexConsumer normal(float x, float y, float z) { vertex[9] = x; vertex[10] = y; vertex[11] = z; return this; }
                @Override public void endVertex() { for (float value : vertex) values.add(value); }
                @Override public void defaultColor(int r, int g, int b, int a) { throw new AssertionError("Unexpected default color"); }
                @Override public void unsetDefaultColor() { throw new AssertionError("Unexpected default color reset"); }
            };
        }
    }
}
