package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.client.citizen.CitizenHairGravity;
import com.rexhub.createapparel.client.garment.FlatGarmentGeometry;
import com.rexhub.createapparel.client.garment.CitizenTextureCache;
import com.rexhub.createapparel.client.garment.CitizenGarmentRenderSource;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class CitizenRenderer extends EntityRenderer<Citizen> {
    private final CitizenModel model;
    private final WeakHashMap<Citizen, CachedLoadout> snapshots = new WeakHashMap<>();

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new CitizenModel(context.bakeLayer(CreateApparelClient.CITIZEN_LAYER));
        shadowRadius = 0.35F;
    }

    @Override
    public void render(Citizen entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        shadowRadius = 0.35F * entity.getScale();
        if (!entity.isInvisible()) {
            var state = entity.citizenState();
            var appearance = state.appearance();
            var cached = snapshots.get(entity);
            long generation = ClientGarmentAssets.INSTANCE.generation();
            if (cached == null || cached.revision() != state.revision() || cached.generation() != generation) {
                var source = new CitizenGarmentRenderSource(new LoadoutRenderSnapshot(state.loadout(),
                        appearance.armProfile(), appearance.chestProfile()));
                var torsoModels = new ArrayList<ClientGarmentAssets.GarmentPartModel>();
                ClientGarmentAssets.INSTANCE.resolveBody(appearance.armProfile(), appearance.chestProfile()).ifPresent(parts ->
                        parts.stream().map(ClientGarmentAssets.ResolvedSupportPart::model)
                                .filter(part -> part.elements().stream().anyMatch(element -> element.name().equals("torso") || element.name().equals("bust")))
                                .forEach(torsoModels::add));
                for (var slot : new GarmentSlot[]{GarmentSlot.INNER_TOP, GarmentSlot.TOP, GarmentSlot.OUTER}) {
                    source.garment(slot).ifPresent(garment -> garment.parts().stream()
                            .filter(part -> part.semanticName().contains("torso") || part.semanticName().equals("bust"))
                            .map(ClientGarmentAssets.ResolvedPart::model).forEach(torsoModels::add));
                }
                var torso = CitizenHairGravity.Torso.NONE;
                if (!torsoModels.isEmpty()) {
                    var bounds = FlatGarmentGeometry.cached(torsoModels).originalBounds();
                    torso = new CitizenHairGravity.Torso(bounds.min().x(), 24 - bounds.max().y(), bounds.min().z(),
                            bounds.max().x(), 24 - bounds.min().y(), bounds.max().z());
                }
                cached = new CachedLoadout(state.revision(), generation, source, torso);
                snapshots.put(entity, cached);
            }
            var snapshot = cached.snapshot();
            var torso = cached.torso();
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
            model.apply(entity, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
            float scale = CitizenModel.BODY_SCALE * entity.getScale();
            poseStack.scale(-scale, -scale, scale);
            poseStack.translate(0, -1.5, 0);
            ClientGarmentAssets.INSTANCE.resolveBody(appearance.armProfile(), appearance.chestProfile()).ifPresent(parts ->
                    CommonGarmentRenderer.renderHumanoidSupport(parts, model, appearance.armProfile(), appearance.skinTone().tintRgb(),
                            poseStack, buffer, light, overlay));
            ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(appearance.chestProfile(),
                    snapshot.garment(GarmentSlot.INNER_TOP).isEmpty() && snapshot.garment(GarmentSlot.TOP).isEmpty(),
                    snapshot.garment(GarmentSlot.INNER_BOTTOM).isEmpty()).ifPresent(parts ->
                    CommonGarmentRenderer.renderHumanoidSupport(parts, model, appearance.armProfile(), 0xFFFFFF,
                            poseStack, buffer, light, overlay));
            var style = ClientCitizenAssets.INSTANCE.resolve(appearance.hairStyleId());
            var vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(CitizenTextureCache.get(appearance, style)));
            model.head.render(poseStack, vertices, light, overlay);
            boolean covered = snapshot.garment(GarmentSlot.HEAD).isPresent()
                    || java.util.Arrays.stream(GarmentSlot.values()).map(snapshot::garment).flatMap(java.util.Optional::stream)
                    .flatMap(garment -> garment.parts().stream()).anyMatch(part -> part.semanticName().equals("hood_up"));
            if (style.hatPolicy().showHat(covered)) model.hat.render(poseStack, vertices, light, overlay);
            if (style.hatPolicy().showExtra(covered)) style.geometry().ifPresent(extra -> {
                var origin = new Vector3f(model.head.x, model.head.y, model.head.z);
                poseStack.pushPose();
                poseStack.translate(origin.x / 16, origin.y / 16, origin.z / 16);
                poseStack.scale(model.head.xScale, model.head.yScale, model.head.zScale);
                extra.render(poseStack, vertices, light, overlay,
                        new Quaternionf().rotationZYX(model.head.zRot, model.head.yRot, model.head.xRot),
                        new Vector3f(), torso.relativeTo(origin, model.head.xScale), style.hatPolicy().showHat(covered));
                poseStack.popPose();
            });
            CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, snapshot, GarmentRenderContext.TargetType.CITIZEN),
                    poseStack, buffer, light, overlay);
            poseStack.popPose();
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Citizen entity) {
        var appearance = entity.citizenState().appearance();
        return CitizenTextureCache.get(appearance, ClientCitizenAssets.INSTANCE.resolve(appearance.hairStyleId()));
    }

    private record CachedLoadout(long revision, long generation, CitizenGarmentRenderSource snapshot, CitizenHairGravity.Torso torso) { }
}
