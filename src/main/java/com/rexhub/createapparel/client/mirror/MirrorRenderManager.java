package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.block.MirrorBlock;
import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import com.rexhub.createapparel.mirror.MirrorSurfaceProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.*;

public final class MirrorRenderManager {
    public static final MirrorRenderManager INSTANCE = new MirrorRenderManager();
    private static final int MAX_CACHED_SURFACES = 32;
    private final MirrorFramebufferPool pool = new MirrorFramebufferPool();
    private final Map<MirrorBlockEntity, LocalCache> localCache = new WeakHashMap<>();
    private final RenderView mainView = new RenderView(0);
    private RenderView activeView = mainView;
    private final Set<RenderTarget> activeTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable private MirrorReflectionCamera reflectionCamera;
    private int recursionDepth;
    private int remainingReflectionPasses;
    @Nullable private RenderTarget reflectionTarget;
    @Nullable private ShaderInstance shader;
    @Nullable private Object world;
    private boolean failed;
    private long frame;
    private long previousFrameNanos;
    private long frameIntervalNanos = 16_666_667;

    private record SurfaceKey(MirrorBlockEntity blockEntity, int face) { }
    private record LocalCache(BlockState state, Object owner, List<MirrorGeometry> geometry) { }
    private record Submission(MirrorSurfaceClusters.Entry entry, MirrorTransformContext transform) { }
    private record Candidate(Set<SurfaceKey> key, List<MirrorGeometry> quads, AABB bounds,
                             double area, double distance, double priority, boolean moving, boolean uncovered) { }
    private static final class RenderView {
        private final int depth;
        private final Map<SurfaceKey, Submission> submissions = new LinkedHashMap<>();
        private final Map<Set<SurfaceKey>, ReflectionCache> textures = new HashMap<>();
        private Set<MirrorSurfaceClusters.Entry> previousTopology = Set.of();
        private List<List<MirrorSurfaceClusters.Entry>> clusters = List.of();
        private Matrix4f inverseView = new Matrix4f();
        private Vec3 mainEye = Vec3.ZERO;
        private boolean collecting;
        private boolean previousMerge;
        private long lastBackgroundUpdateNanos;
        private RenderView(int depth) { this.depth = depth; }
    }

    private static final class ReflectionCache {
        private RenderTarget target;
        @Nullable private RenderTarget spareTarget;
        private final RenderView children;
        private Matrix4f projectionView = new Matrix4f();
        private Vec3 eye = Vec3.ZERO;
        private Vec3 origin = Vec3.ZERO;
        private Vec3 normal = Vec3.ZERO;
        private double[] coverage = {0, 0, 1, 1};
        private long updated;
        private long updatedNanos;
        private long updateStartedNanos;
        private long updateDeadlineNanos;
        private long updateIntervalNanos;
        private long estimatedCostNanos = 1_000_000;
        private long seen;
        private boolean ready;
        private ReflectionCache(RenderTarget target, int depth) {
            this.target = target;
            children = new RenderView(depth);
        }
    }

    private record CaptureState(RenderTarget target, Matrix4f projectionView, Vec3 eye, Vec3 origin,
                                Vec3 normal, double[] coverage, long updated, long updatedNanos,
                                long updateStartedNanos, long updateDeadlineNanos, long updateIntervalNanos, boolean ready) {
        private CaptureState(ReflectionCache cache) {
            this(cache.target, cache.projectionView, cache.eye, cache.origin, cache.normal, cache.coverage,
                    cache.updated, cache.updatedNanos, cache.updateStartedNanos, cache.updateDeadlineNanos,
                    cache.updateIntervalNanos, cache.ready);
        }

        private void restore(ReflectionCache cache) {
            cache.target = target;
            cache.projectionView = projectionView;
            cache.eye = eye;
            cache.origin = origin;
            cache.normal = normal;
            cache.coverage = coverage;
            cache.updated = updated;
            cache.updatedNanos = updatedNanos;
            cache.updateStartedNanos = updateStartedNanos;
            cache.updateDeadlineNanos = updateDeadlineNanos;
            cache.updateIntervalNanos = updateIntervalNanos;
            cache.ready = ready;
        }
    }

    private MirrorRenderManager() { }

    public boolean isRenderingReflection() { return reflectionTarget != null; }
    @Nullable public RenderTarget reflectionTarget() { return reflectionTarget; }
    public Camera reflectionCamera() { return Objects.requireNonNull(reflectionCamera); }
    public int reflectionDepth() { return activeView.depth; }
    public void setShader(@Nullable ShaderInstance shader) { this.shader = shader; }

    public void beginFrame(PoseStack view, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (world != minecraft.level) {
            reset();
            world = minecraft.level;
        }
        activeView = mainView;
        recursionDepth = ModConfigs.CLIENT.recursionDepth.get();
        remainingReflectionPasses = MirrorRenderPolicy.reflectionPassLimit(updateLimit(), recursionDepth);
        frame++;
        long now = System.nanoTime();
        if (previousFrameNanos != 0) {
            long interval = Math.max(4_166_667, Math.min(50_000_000, now - previousFrameNanos));
            frameIntervalNanos = (frameIntervalNanos * 3 + interval) / 4;
        }
        previousFrameNanos = now;
        activeView.inverseView = new Matrix4f(view.last().pose()).invert();
        activeView.mainEye = camera.getPosition();
        activeView.submissions.clear();
        activeView.collecting = enabled();
    }

    public void collect(MirrorBlockEntity mirror, Matrix4f pose) {
        if (!activeView.collecting || mirror.getLevel() == null) return;
        BlockState state = mirror.getBlockState();
        if (!(state.getBlock() instanceof MirrorSurfaceProvider provider)) return;
        LocalCache cached = localCache.get(mirror);
        Object owner = com.rexhub.createapparel.client.compat.valkyrienskies.MirrorShipOwner.owner(mirror.getLevel(), mirror.getBlockPos());
        if (cached == null || cached.state != state || !cached.owner.equals(owner)) {
            var geometry = provider.mirrorGeometry(state).stream()
                    .map(surface -> surface.move(Vec3.atLowerCornerOf(mirror.getBlockPos()))).toList();
            cached = new LocalCache(state, owner, geometry);
            localCache.put(mirror, cached);
        }
        var context = MirrorTransformContext.capture(owner, pose, activeView.inverseView, activeView.mainEye);
        int index = 0;
        for (var geometry : cached.geometry) {
            int face = index++;
            if (state.getBlock() instanceof MirrorBlock block && !block.isFaceVisible(mirror.getLevel(), mirror.getBlockPos(), face)) continue;
            var key = new SurfaceKey(mirror, face);
            var entry = new MirrorSurfaceClusters.Entry(owner, key, geometry, provider.mergesSurfaces());
            activeView.submissions.put(key, new Submission(entry, context));
        }
    }

    public void finishFrame(LevelRenderer renderer, PoseStack view, float partialTick, long finishTime,
                            Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f projection) {
        finishView(renderer, view, partialTick, finishTime, camera, gameRenderer, light, projection, projection);
    }

    private boolean finishView(LevelRenderer renderer, PoseStack view, float partialTick, long finishTime,
                            Camera camera, GameRenderer gameRenderer, LightTexture light,
                            Matrix4f projection, Matrix4f captureProjection) {
        activeView.collecting = false;
        if (!enabled()) {
            clearTextures();
            return true;
        }
        var topology = new HashSet<MirrorSurfaceClusters.Entry>();
        activeView.submissions.values().forEach(submission -> topology.add(submission.entry));
        boolean merge = ModConfigs.CLIENT.mergeCoplanarMirrors.get();
        if (!activeView.previousTopology.equals(topology) || activeView.previousMerge != merge) {
            activeView.clusters = MirrorSurfaceClusters.build(List.copyOf(topology), merge);
            activeView.previousTopology = Set.copyOf(topology);
            activeView.previousMerge = merge;
        }
        var frustum = new Frustum(view.last().pose(), projection);
        frustum.prepare(activeView.mainEye.x, activeView.mainEye.y, activeView.mainEye.z);
        var candidates = candidates(frustum, projection, view.last().pose());
        var focused = candidates.stream().max(Comparator.comparingDouble(candidate -> candidate.area / (1 + candidate.distance))).orElse(null);
        long now = System.nanoTime();
        var oldestBackground = candidates.stream().filter(candidate -> candidate != focused)
                .max(Comparator.comparingLong(candidate -> textureAge(candidate, now))).orElse(null);
        var first = focused != null && oldestBackground != null && MirrorRenderPolicy.reserveBackgroundTurn(frameIntervalNanos,
                updateInterval(focused, true), textureAge(oldestBackground, now), now - activeView.lastBackgroundUpdateNanos) ? oldestBackground : focused;
        if (activeView.depth > 0) {
            candidates.sort(Comparator.<Candidate>comparingInt(candidate -> activeView.textures.containsKey(candidate.key) ? 1 : 0)
                    .thenComparingDouble(candidate -> candidate.area / (1 + candidate.distance)).reversed());
        } else {
            candidates.sort(Comparator.<Candidate>comparingInt(candidate -> candidate.uncovered ? 1 : 0)
                    .thenComparingInt(candidate -> candidate == first ? 1 : 0)
                    .thenComparingDouble(Candidate::priority).reversed());
        }
        int limit = activeView.depth == 0 && focused != null && MirrorRenderPolicy.prominentNearby(focused.area, focused.distance) ? 1 : updateLimit();
        int updated = 0;
        boolean complete = true;
        long started = System.nanoTime();
        long budget = (long) (ModConfigs.CLIENT.updateBudgetMillis.get() * 1_000_000);
        for (var candidate : candidates) {
            var cache = activeView.textures.get(candidate.key);
            if (cache != null) cache.seen = frame;
        }
        var groups = activeView.depth == 0 ? updateGroups(candidates) : candidates.stream().map(List::of).toList();
        for (var group : groups) {
            if (updated >= limit) break;
            boolean groupFocused = group.contains(focused);
            if (activeView.depth == 0) {
                long estimated = group.stream().mapToLong(candidate -> {
                    var cache = activeView.textures.get(candidate.key);
                    return cache == null ? 1_000_000 : cache.estimatedCostNanos;
                }).sum();
                if (!MirrorRenderPolicy.fitsBudget(System.nanoTime() - started, budget, estimated, updated)
                        || !needsSynchronization(group) && group.stream().noneMatch(candidate -> due(candidate, activeView.textures.get(candidate.key), groupFocused))) continue;
                if (updated == 0) remainingReflectionPasses = MirrorRenderPolicy.synchronizedPassLimit(updateLimit(), recursionDepth, group.size());
            }
            var staged = new LinkedHashMap<ReflectionCache, CaptureState>();
            boolean groupComplete = true;
            try {
                for (var candidate : group) {
                    if (remainingReflectionPasses == 0) {
                        groupComplete = false;
                        break;
                    }
                    var cache = activeView.textures.get(candidate.key);
                    if (cache == null && activeView.textures.size() >= MAX_CACHED_SURFACES && !evictOldest(group)) {
                        groupComplete = false;
                        break;
                    }
                    if (activeView.depth == 0 && cache != null) {
                        staged.put(cache, new CaptureState(cache));
                        if (cache.spareTarget != null && (cache.spareTarget.width != cache.target.width || cache.spareTarget.height != cache.target.height)) {
                            pool.release(cache.spareTarget);
                            cache.spareTarget = null;
                        }
                        cache.target = cache.spareTarget == null ? pool.acquire(cache.target.width, cache.target.height) : cache.spareTarget;
                        cache.spareTarget = null;
                        cache.ready = false;
                    }
                    if (!update(candidate, renderer, view, partialTick, finishTime, camera, gameRenderer, light, captureProjection, groupFocused)) {
                        groupComplete = false;
                        break;
                    }
                }
                for (var entry : staged.entrySet()) {
                    var cache = entry.getKey();
                    if (groupComplete) cache.spareTarget = entry.getValue().target;
                    else {
                        cache.spareTarget = cache.target;
                        entry.getValue().restore(cache);
                    }
                }
                if (!groupComplete) {
                    complete = false;
                    for (var candidate : group) {
                        var cache = activeView.textures.get(candidate.key);
                        if (cache != null && cache.updated == frame && !staged.containsKey(cache)) cache.ready = false;
                    }
                    break;
                }
                if (!groupFocused) activeView.lastBackgroundUpdateNanos = System.nanoTime();
                updated++;
            } catch (RuntimeException exception) {
                for (var entry : staged.entrySet()) {
                    var cache = entry.getKey();
                    var target = entry.getValue().target;
                    if (cache.target != target && cache.spareTarget != target) pool.release(target);
                }
                if (activeView.depth > 0) throw exception;
                failed = true;
                LogUtils.getLogger().warn("Mirror reflections disabled until resource reload after a renderer failure", exception);
                clearTextures();
                return false;
            }
        }
        try { draw(candidates, view); }
        catch (RuntimeException exception) {
            if (activeView.depth > 0) throw exception;
            failed = true;
            LogUtils.getLogger().warn("Mirror surface drawing unavailable; using fallback", exception);
            clearTextures();
        }
        activeView.textures.entrySet().removeIf(entry -> {
            if (frame - entry.getValue().seen < 120) return false;
            release(entry.getValue());
            return true;
        });
        return complete;
    }

    private List<List<Candidate>> updateGroups(List<Candidate> candidates) {
        var entries = new ArrayList<MirrorUpdateGroups.Entry<Candidate>>();
        for (var candidate : candidates) {
            var key = candidate.key.iterator().next();
            Object owner = activeView.submissions.get(key).entry.owner();
            var blocks = new HashSet<net.minecraft.core.BlockPos>();
            boolean synchronize = true;
            for (var surface : candidate.key) {
                blocks.add(surface.blockEntity.getBlockPos());
                synchronize &= surface.blockEntity.getBlockState().getBlock() instanceof MirrorBlock;
            }
            entries.add(new MirrorUpdateGroups.Entry<>(candidate, owner, Set.copyOf(blocks), synchronize));
        }
        return MirrorUpdateGroups.build(entries);
    }

    private boolean needsSynchronization(List<Candidate> group) {
        long captureFrame = -1;
        for (var candidate : group) {
            var cache = activeView.textures.get(candidate.key);
            if (cache == null || !cache.ready) return true;
            if (captureFrame >= 0 && captureFrame != cache.updated) return true;
            captureFrame = cache.updated;
        }
        return false;
    }

    private boolean enabled() {
        return !failed && shader != null && ModConfigs.CLIENT.enabled.get()
                && ModConfigs.CLIENT.quality.get() != ModConfigs.MirrorQuality.OFF
                && !Minecraft.useShaderTransparency() && !ModList.get().isLoaded("oculus");
    }

    private List<Candidate> candidates(Frustum frustum, Matrix4f projection, Matrix4f view) {
        var result = new ArrayList<Candidate>();
        double range = renderDistance();
        var projectionView = new Matrix4f(projection).mul(view);
        for (var cluster : activeView.clusters) {
            var quads = new ArrayList<MirrorGeometry>();
            var keys = new HashSet<SurfaceKey>();
            AABB bounds = null;
            for (var entry : cluster) {
                var key = (SurfaceKey) entry.key();
                var submission = activeView.submissions.get(key);
                if (submission == null) continue;
                var geometry = entry.geometry().move(Vec3.atLowerCornerOf(key.blockEntity.getBlockPos()).scale(-1))
                        .transform(submission.transform.localToCamera()).move(activeView.mainEye);
                quads.add(geometry);
                keys.add(key);
                bounds = bounds == null ? geometry.bounds() : bounds.minmax(geometry.bounds());
            }
            if (bounds == null) continue;
            double distance = activeView.mainEye.distanceTo(closestPoint(bounds, activeView.mainEye));
            double viewerDistance = mainView.mainEye.distanceTo(closestPoint(bounds, mainView.mainEye));
            var first = quads.get(0);
            if (!MirrorRenderPolicy.withinRenderDistance(activeView.depth, distance, viewerDistance, range) || !frustum.isVisible(bounds.inflate(0.01))
                    || activeView.mainEye.subtract(first.origin()).dot(first.normal()) <= 0.005) continue;
            double[] screen = screenBounds(quads, projectionView, activeView.mainEye);
            double area = Math.max(0, screen[2] - screen[0]) * Math.max(0, screen[3] - screen[1]) / 4;
            if (area < 0.00001) continue;
            var frozenKeys = Set.copyOf(keys);
            var cached = textureFor(frozenKeys);
            boolean moving = cached != null && (cached.origin.distanceToSqr(first.origin()) > 0.000001
                    || cached.normal.dot(first.normal()) < 0.99999);
            double center = Math.hypot((screen[0] + screen[2]) / 2, (screen[1] + screen[3]) / 2);
            double age = cached == null ? 8 : Math.min(8, (frame - cached.updated) * 0.25);
            double priority = area / (1 + distance * 0.1) / (1 + center) * (1 + age) * (moving ? 2 : 1);
            boolean uncovered = cached == null || !cached.ready || !MirrorReflectionMath.coversVisibleMirror(quads, activeView.mainEye,
                    projectionView, textureMatrix(cached), cached.coverage);
            result.add(new Candidate(frozenKeys, List.copyOf(quads), bounds, area, distance, priority, moving, uncovered));
        }
        return result;
    }

    private static Vec3 closestPoint(AABB bounds, Vec3 eye) {
        return new Vec3(Math.max(bounds.minX, Math.min(bounds.maxX, eye.x)),
                Math.max(bounds.minY, Math.min(bounds.maxY, eye.y)),
                Math.max(bounds.minZ, Math.min(bounds.maxZ, eye.z)));
    }

    @Nullable private ReflectionCache textureFor(Set<SurfaceKey> key) {
        var cached = activeView.textures.get(key);
        if (cached != null) return cached;
        var previous = activeView.textures.entrySet().stream().filter(entry -> entry.getKey().containsAll(key))
                .min(Comparator.comparingInt(entry -> entry.getKey().size())).orElse(null);
        if (previous == null) return null;
        activeView.textures.remove(previous.getKey());
        activeView.textures.put(key, previous.getValue());
        return previous.getValue();
    }

    private Matrix4f textureMatrix(ReflectionCache cache) {
        var delta = activeView.mainEye.subtract(cache.eye);
        return new Matrix4f(cache.projectionView).translate((float) delta.x, (float) delta.y, (float) delta.z);
    }

    private double[] screenBounds(List<MirrorGeometry> quads, Matrix4f matrix, Vec3 eye) {
        double x1 = 1, y1 = 1, x2 = -1, y2 = -1;
        for (var quad : quads) for (var relative : MirrorReflectionMath.visiblePolygon(quad, eye, matrix)) {
            var clip = matrix.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1));
            if (clip.w <= 0.000001) continue;
            double x = Math.max(-1, Math.min(1, clip.x / clip.w)), y = Math.max(-1, Math.min(1, clip.y / clip.w));
            x1 = Math.min(x1, x); y1 = Math.min(y1, y); x2 = Math.max(x2, x); y2 = Math.max(y2, y);
        }
        return new double[]{x1, y1, x2, y2};
    }

    private long textureAge(Candidate candidate, long now) {
        var cache = activeView.textures.get(candidate.key);
        return cache == null || !cache.ready ? Long.MAX_VALUE : now - cache.updatedNanos;
    }

    private long updateInterval(Candidate candidate, boolean focused) {
        if (focused && MirrorRenderPolicy.prominentNearby(candidate.area, candidate.distance))
            return 1_000_000_000L / ModConfigs.CLIENT.nearbyReflectionFps.get();
        return MirrorRenderPolicy.updateInterval(candidate.area, candidate.distance,
                candidate.moving, ModConfigs.CLIENT.maxReflectionFps.get(), ModConfigs.CLIENT.adaptiveUpdateRate.get());
    }

    private boolean due(Candidate candidate, @Nullable ReflectionCache cache, boolean focused) {
        if (cache == null || !cache.ready || candidate.uncovered) return true;
        long interval = updateInterval(candidate, focused);
        long deadline = cache.updateIntervalNanos == interval ? cache.updateDeadlineNanos : cache.updateStartedNanos + interval;
        return MirrorRenderPolicy.updateDue(System.nanoTime(), deadline, interval, frameIntervalNanos);
    }

    private boolean update(Candidate candidate, LevelRenderer renderer, PoseStack mainView, float partialTick,
                        long finishTime, Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f projection, boolean focused) {
        remainingReflectionPasses--;
        long startedNanos = System.nanoTime();
        long intervalNanos = updateInterval(candidate, focused);
        var minecraft = Minecraft.getInstance();
        var geometry = candidate.quads.get(0);
        var captureCamera = new MirrorReflectionCamera();
        captureCamera.configure(Objects.requireNonNull(minecraft.level), Objects.requireNonNull(camera.getEntity()),
                partialTick, camera, geometry.origin(), geometry.normal());
        Vec3 reflectedEye = captureCamera.getPosition();
        var reflectedView = MirrorReflectionMath.reflectedView(mainView.last().pose(), geometry.normal());
        var captureProjection = MirrorReflectionMath.captureProjection(projection);
        var reflectedBounds = screenBounds(candidate.quads, new Matrix4f(captureProjection).mul(reflectedView), reflectedEye);
        var cullProjection = MirrorReflectionMath.croppedCullingProjection(captureProjection, reflectedBounds);
        var clipped = MirrorReflectionMath.clippedProjection(captureProjection, reflectedView,
                geometry.origin().subtract(reflectedEye), geometry.normal());
        var cache = activeView.textures.get(candidate.key);
        int width = MirrorRenderPolicy.textureWidth(minecraft.getWindow().getWidth(), resolutionScale(), candidate.area,
                candidate.distance, ModConfigs.CLIENT.dynamicResolution.get(), cache == null ? 0 : cache.target.width);
        width = MirrorRenderPolicy.recursiveTextureWidth(width, activeView.depth, reflectionTarget == null ? width : reflectionTarget.width);
        int height = Math.max(64, width * minecraft.getWindow().getHeight() / Math.max(1, minecraft.getWindow().getWidth()));
        if (cache == null) {
            cache = new ReflectionCache(pool.acquire(width, height), activeView.depth + 1);
            activeView.textures.put(candidate.key, cache);
        } else if (cache.target.width != width || cache.target.height != height) {
            pool.release(cache.target);
            cache.target = pool.acquire(width, height);
            cache.ready = false;
        }
        var originalView = activeView;
        var originalReflectionTarget = reflectionTarget;
        var originalCamera = reflectionCamera;
        var originalTarget = minecraft.getMainRenderTarget();
        var originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var inverseRotation = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        var modelView = RenderSystem.getModelViewStack();
        int originalFrontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] originalScissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, originalScissor);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var fogShape = RenderSystem.getShaderFogShape();
        var fogColor = RenderSystem.getShaderFogColor().clone();
        var shaderColor = RenderSystem.getShaderColor().clone();
        var originalShader = RenderSystem.getShader();
        int[] shaderTextures = new int[12];
        for (int i = 0; i < shaderTextures.length; i++) shaderTextures[i] = RenderSystem.getShaderTexture(i);
        var rendererState = (MirrorLevelRendererAccess) renderer;
        if (activeTargets.contains(cache.target)) throw new IllegalStateException("Reflection framebuffer is already active");
        rendererState.createapparel$beginReflection();
        activeTargets.add(cache.target);
        reflectionTarget = cache.target;
        reflectionCamera = captureCamera;
        activeView = cache.children;
        activeView.inverseView = new Matrix4f(reflectedView).invert();
        activeView.mainEye = reflectedEye;
        activeView.submissions.clear();
        activeView.collecting = MirrorRenderPolicy.canRenderReflection(activeView.depth, recursionDepth);
        minecraft.getProfiler().push("mirror_reflection");
        modelView.pushPose();
        try {
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            cache.target.bindWrite(true);
            int[] scissor = MirrorRenderPolicy.scissor(reflectedBounds, width, height);
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            gameRenderer.resetProjectionMatrix(clipped);
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(reflectedView).invert());
            GL11.glFrontFace(originalFrontFace == GL11.GL_CCW ? GL11.GL_CW : GL11.GL_CCW);
            var pose = new PoseStack();
            pose.mulPoseMatrix(reflectedView);
            pose.last().normal().set(new Matrix3f(reflectedView).invert().transpose());
            renderer.prepareCullFrustum(pose, reflectedEye, cullProjection);
            renderer.renderLevel(pose, partialTick, finishTime, false, captureCamera, gameRenderer, light, clipped);
            if (activeView.collecting) {
                var portalProjection = MirrorReflectionMath.croppedCullingProjection(clipped, reflectedBounds);
                if (!finishView(renderer, pose, partialTick, finishTime, captureCamera, gameRenderer, light, portalProjection, captureProjection)) return false;
            } else {
                clearTextures();
            }
            cache.projectionView = new Matrix4f(clipped).mul(reflectedView);
            cache.coverage = new double[]{(scissor[0] + 0.5) / width, (scissor[1] + 0.5) / height,
                    (scissor[0] + scissor[2] - 0.5) / width, (scissor[1] + scissor[3] - 0.5) / height};
            cache.eye = reflectedEye;
            cache.origin = geometry.origin();
            cache.normal = geometry.normal();
            cache.updated = frame;
            cache.updatedNanos = System.nanoTime();
            long deadline = cache.updateIntervalNanos == intervalNanos ? cache.updateDeadlineNanos : 0;
            cache.updateDeadlineNanos = MirrorRenderPolicy.nextUpdate(startedNanos, deadline, intervalNanos);
            cache.updateIntervalNanos = intervalNanos;
            cache.updateStartedNanos = startedNanos;
            cache.estimatedCostNanos = (cache.estimatedCostNanos * 3 + cache.updatedNanos - startedNanos) / 4;
            cache.seen = frame;
            cache.ready = true;
            return true;
        } finally {
            activeView.collecting = false;
            activeView = originalView;
            reflectionTarget = originalReflectionTarget;
            reflectionCamera = originalCamera;
            activeTargets.remove(cache.target);
            RenderSystem.enableScissor(originalScissor[0], originalScissor[1], originalScissor[2], originalScissor[3]);
            if (!scissorEnabled) RenderSystem.disableScissor();
            GL11.glFrontFace(originalFrontFace);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            gameRenderer.resetProjectionMatrix(originalProjection);
            RenderSystem.setInverseViewRotationMatrix(inverseRotation);
            originalTarget.bindWrite(true);
            rendererState.createapparel$endReflection();
            if (minecraft.level.effects().constantAmbientLight()) Lighting.setupNetherLevel(mainView.last().pose());
            else Lighting.setupLevel(mainView.last().pose());
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShader(() -> originalShader);
            for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
            minecraft.getEntityRenderDispatcher().prepare(minecraft.level, camera, minecraft.crosshairPickEntity);
            minecraft.getBlockEntityRenderDispatcher().prepare(minecraft.level, camera, minecraft.hitResult);
            minecraft.getProfiler().pop();
        }
    }

    private void draw(List<Candidate> candidates, PoseStack view) {
        if (shader == null) return;
        var originalShader = RenderSystem.getShader();
        var originalColor = RenderSystem.getShaderColor().clone();
        int originalTexture = RenderSystem.getShaderTexture(0);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean offsetEnabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float offsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), offsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1, -1);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(() -> shader);
        try {
            for (var candidate : candidates) {
                var cache = activeView.textures.get(candidate.key);
                if (cache == null || !cache.ready || activeTargets.contains(cache.target)) continue;
                if (!MirrorRenderPolicy.canDrawCachedReflection(activeView.depth, cache.updated, frame)) continue;
                Objects.requireNonNull(shader.getUniform("ReflectionMatrix")).set(textureMatrix(cache));
                Objects.requireNonNull(shader.getUniform("ReflectionBounds")).set((float) cache.coverage[0], (float) cache.coverage[1],
                        (float) cache.coverage[2], (float) cache.coverage[3]);
                RenderSystem.setShaderTexture(0, cache.target.getColorTextureId());
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                for (var quad : candidate.quads) for (var vertex : quad.vertices()) {
                    Vec3 relative = vertex.subtract(activeView.mainEye);
                    buffer.vertex((float) relative.x, (float) relative.y, (float) relative.z).endVertex();
                }
                var modelView = RenderSystem.getModelViewStack();
                modelView.pushPose();
                modelView.mulPoseMatrix(view.last().pose());
                RenderSystem.applyModelViewMatrix();
                try { BufferUploader.drawWithShader(buffer.end()); }
                finally { modelView.popPose(); RenderSystem.applyModelViewMatrix(); }
            }
        } finally {
            RenderSystem.polygonOffset(offsetFactor, offsetUnits);
            if (!offsetEnabled) RenderSystem.disablePolygonOffset();
            if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blendEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            RenderSystem.setShader(() -> originalShader);
            RenderSystem.setShaderColor(originalColor[0], originalColor[1], originalColor[2], originalColor[3]);
            RenderSystem.setShaderTexture(0, originalTexture);
        }
    }

    private int renderDistance() {
        return switch (ModConfigs.CLIENT.quality.get()) {
            case LOW -> 16; case HIGH -> 64; default -> ModConfigs.CLIENT.renderDistance.get();
        };
    }
    private int updateLimit() {
        return switch (ModConfigs.CLIENT.quality.get()) {
            case LOW -> 2; case HIGH -> 8; default -> ModConfigs.CLIENT.maxSurfacesPerFrame.get();
        };
    }
    private double resolutionScale() {
        return switch (ModConfigs.CLIENT.quality.get()) {
            case LOW -> 0.25; case HIGH -> 1; default -> ModConfigs.CLIENT.resolutionScale.get();
        };
    }

    private boolean evictOldest(List<Candidate> protectedGroup) {
        var oldest = activeView.textures.entrySet().stream()
                .filter(entry -> entry.getValue().updated != frame && protectedGroup.stream().noneMatch(candidate -> candidate.key.equals(entry.getKey())))
                .min(Comparator.comparingLong(entry -> entry.getValue().seen)).orElse(null);
        if (oldest == null) return false;
        release(oldest.getValue());
        activeView.textures.remove(oldest.getKey());
        return true;
    }
    private void release(ReflectionCache cache) {
        cache.children.textures.values().forEach(this::release);
        cache.children.textures.clear();
        pool.release(cache.target);
        if (cache.spareTarget != null) pool.release(cache.spareTarget);
    }
    private void clearTextures() {
        activeView.textures.values().forEach(this::release);
        activeView.textures.clear();
    }
    public void reset() {
        activeView = mainView;
        clearTextures();
        activeTargets.clear();
        reflectionTarget = null;
        reflectionCamera = null;
        pool.close();
        localCache.clear();
        activeView.submissions.clear();
        activeView.previousTopology = Set.of();
        activeView.clusters = List.of();
        failed = false;
        activeView.collecting = false;
        previousFrameNanos = 0;
        frameIntervalNanos = 16_666_667;
        activeView.lastBackgroundUpdateNanos = 0;
    }
}
