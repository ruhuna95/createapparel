package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.client.garment.CitizenTextureCache;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.GarmentRenderContext;
import com.rexhub.createapparel.client.garment.LoadoutRenderSnapshot;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.WeakHashMap;

public final class CitizenRenderer extends EntityRenderer<Citizen> {
    private static int previewDepth;
    private final CitizenModel model;
    private final WeakHashMap<Citizen, CachedLoadout> snapshots = new WeakHashMap<>();
    private java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> snapshotLevel = new java.lang.ref.WeakReference<>(null);

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new CitizenModel(context.bakeLayer(CreateApparelClient.CITIZEN_LAYER));
        shadowRadius = 0.35F;
    }

    @Override
    public void render(Citizen entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenBodyRendering) { shadowRadius = 0; return; }
        try (var diagnosticScope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.citizen(entity.getId())) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (snapshotLevel.get() != minecraft.level) { snapshots.clear(); snapshotLevel = new java.lang.ref.WeakReference<>(minecraft.level); }
            shadowRadius = 0.35F * entity.getScale();
            if (!entity.isInvisible()) {
                var state = entity.citizenState();
                var appearance = state.appearance();
                var cached = snapshots.get(entity);
                long generation = ClientGarmentAssets.INSTANCE.generation();
                if (cached == null || cached.revision() != state.revision() || cached.generation() != generation) {
                    com.rexhub.createapparel.client.garment.GarmentDiagnostics.count("citizenSnapshotMiss", 1);
                    var source = new LoadoutRenderSnapshot(state.loadout(), appearance.armProfile(), appearance.chestProfile());
                    int previousLod = cached == null ? -1 : cached.lod;
                    cached = new CachedLoadout(state.revision(), generation, source);
                    cached.lod = previousLod;
                    snapshots.put(entity, cached);
                } else com.rexhub.createapparel.client.garment.GarmentDiagnostics.count("citizenSnapshotHit", 1);
                var snapshot = cached.snapshot();
                boolean world = previewDepth == 0 && !com.rexhub.createapparel.client.mirror.MirrorRenderManager.INSTANCE.isRenderingReflection();
                int lod = 0;
                if (world && !com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenLod) {
                    var config = com.rexhub.createapparel.config.ModConfigs.CLIENT;
                    lod = com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugForceCitizenLod;
                    if (lod < 0) lod = com.rexhub.createapparel.client.garment.CitizenRenderLod.select(cached.lod,
                            minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(entity.position()),
                            config.citizenLod0Distance.get(), config.citizenLod1Distance.get(), config.citizenLod2Distance.get(), config.citizenLodHysteresis.get());
                    com.rexhub.createapparel.client.garment.GarmentDiagnostics.lod(lod, cached.lod >= 0 && cached.lod != lod);
                    cached.lod = lod;
                } else com.rexhub.createapparel.client.garment.GarmentDiagnostics.lod(0, false);
                if (com.rexhub.createapparel.client.garment.GarmentDiagnostics.enabled()) {
                    boolean clothed = false;
                    for (var slot : com.rexhub.createapparel.client.garment.ResolvedApparelState.RENDER_ORDER) {
                        if (snapshot.garment(slot).isPresent()) { clothed = true; break; }
                    }
                    com.rexhub.createapparel.client.garment.GarmentDiagnostics.clothing(entity.getId(), clothed);
                }
                int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
                try (var scope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.measure("citizenModelPose")) {
                    model.apply(entity, partialTick);
                }
                var frame = com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenGarmentRendering ? null
                        : new com.rexhub.createapparel.client.garment.ApparelRenderFrame(snapshot::garment, model, lod);
                boolean useOcclusion = world && com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugEnableCitizenOcclusion
                        && !com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenOcclusion;
                if (useOcclusion && frame != null && cached.occlusion == null)
                    cached.occlusion = new CommonGarmentRenderer.SupportOcclusion(snapshot, model);
                var occlusion = useOcclusion && cached.occlusion != null && cached.occlusion.applicable() ? cached.occlusion : null;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
                float scale = CitizenModel.BODY_SCALE * entity.getScale();
                poseStack.scale(-scale, -scale, scale);
                poseStack.translate(0, -1.5, 0);
                try (var scope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.measure("citizenBodySupport")) {
                    ClientGarmentAssets.INSTANCE.resolveBody(appearance.armProfile(), appearance.chestProfile()).ifPresent(parts ->
                        CommonGarmentRenderer.renderHumanoidSupport(parts, model, appearance.armProfile(), appearance.skinTone().tintRgb(),
                                poseStack, buffer, light, overlay, occlusion, frame, false));
                }
                try (var scope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.measure("citizenUnderwearSupport")) {
                    ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(appearance.chestProfile(),
                            snapshot.garment(GarmentSlot.INNER_TOP).isEmpty() && snapshot.garment(GarmentSlot.TOP).isEmpty(),
                            snapshot.garment(GarmentSlot.INNER_BOTTOM).isEmpty()).ifPresent(parts ->
                            CommonGarmentRenderer.renderHumanoidSupport(parts, model, appearance.armProfile(), 0xFFFFFF,
                                    poseStack, buffer, light, overlay, occlusion, frame, true));
                }
                var style = ClientCitizenAssets.INSTANCE.resolve(appearance.hairStyleId());
                com.mojang.blaze3d.vertex.VertexConsumer vertices;
                try (var scope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.measure("citizenHead")) {
                    vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(CitizenTextureCache.get(appearance, style)));
                    model.head.render(poseStack, vertices, light, overlay);
                }
                try (var scope = com.rexhub.createapparel.client.garment.GarmentDiagnostics.measure("citizenCosmetic")) {
                    if (!com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenCosmeticRendering) {
                        boolean covered = snapshot.garment(GarmentSlot.HEAD).isPresent()
                                || java.util.Arrays.stream(GarmentSlot.values()).map(snapshot::garment).flatMap(java.util.Optional::stream)
                                .flatMap(garment -> garment.parts().stream()).anyMatch(part -> part.semanticName().equals("hood_up"));
                        if (style.hatPolicy().showHat(covered)) model.hat.render(poseStack, vertices, light, overlay);
                        if (style.hatPolicy().showExtra(covered)) style.geometry().ifPresent(extra ->
                                extra.renderHeadAttached(poseStack, vertices, light, overlay, model.head, style.hatPolicy().showHat(covered)));
                    }
                }
                if (!com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenGarmentRendering)
                    CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, snapshot, GarmentRenderContext.TargetType.CITIZEN, lod, frame),
                        poseStack, buffer, light, overlay);
                poseStack.popPose();
            }
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }
    }

    @Override
    public boolean shouldRender(Citizen entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        boolean visible = !com.rexhub.createapparel.client.garment.GarmentDiagnostics.debugDisableCitizenBodyRendering && super.shouldRender(entity, frustum, x, y, z);
        com.rexhub.createapparel.client.garment.GarmentDiagnostics.count(visible ? "vanillaCitizenCullingAccepted" : "vanillaCitizenCullingRejected", 1);
        return visible;
    }

    @Override
    public ResourceLocation getTextureLocation(Citizen entity) {
        var appearance = entity.citizenState().appearance();
        return CitizenTextureCache.get(appearance, ClientCitizenAssets.INSTANCE.resolve(appearance.hairStyleId()));
    }

    private static final class CachedLoadout {
        private final long revision, generation;
        private final LoadoutRenderSnapshot snapshot;
        private int lod = -1;
        private CommonGarmentRenderer.SupportOcclusion occlusion;
        private CachedLoadout(long revision, long generation, LoadoutRenderSnapshot snapshot) {
            this.revision = revision; this.generation = generation; this.snapshot = snapshot;
        }
        private long revision() { return revision; }
        private long generation() { return generation; }
        private LoadoutRenderSnapshot snapshot() { return snapshot; }
    }
    public void forget(Citizen entity) { snapshots.remove(entity); }

    public static void withPreview(Runnable render) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        previewDepth++;
        try { render.run(); } finally { previewDepth--; }
    }
}
