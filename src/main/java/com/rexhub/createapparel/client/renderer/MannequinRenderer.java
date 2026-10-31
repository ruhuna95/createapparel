package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.garment.GarmentRenderContext;
import com.rexhub.createapparel.client.garment.LoadoutRenderSnapshot;
import com.rexhub.createapparel.client.model.MannequinModel;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.entity.MannequinForm;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.WeakHashMap;

public final class MannequinRenderer extends EntityRenderer<Mannequin> {
    private final MannequinModel classic;
    private final MannequinModel slim;
    private final WeakHashMap<Mannequin, CachedLoadout> snapshots = new WeakHashMap<>();

    public MannequinRenderer(EntityRendererProvider.Context context) {
        super(context);
        classic = new MannequinModel(context.bakeLayer(CreateApparelClient.MANNEQUIN_CLASSIC_LAYER));
        slim = new MannequinModel(context.bakeLayer(CreateApparelClient.MANNEQUIN_SLIM_LAYER));
    }

    @Override
    public void render(Mannequin entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        var model = entity.variant().armProfile() == ArmProfile.SLIM ? slim : classic;
        model.apply(entity.variant().form(), entity.mannequinState().pose());
        var cached = snapshots.get(entity);
        if (cached == null || cached.revision() != entity.mannequinState().revision()) {
            cached = new CachedLoadout(entity.mannequinState().revision(), new LoadoutRenderSnapshot(
                    entity.mannequinState().loadout(), entity.variant().armProfile(), entity.variant().chestProfile()));
            snapshots.put(entity, cached);
        }
        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        poseStack.pushPose();
        poseStack.translate(0, entity.variant().form().modelOriginPixels() / 16.0F, 0);
        poseStack.scale(-1, -1, 1);
        var vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        model.renderSupport(poseStack, vertices, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        poseStack.translate(0, entity.variant().form().bodyOriginHeight(), 0);
        poseStack.scale(-MannequinForm.BODY_SCALE, -MannequinForm.BODY_SCALE, MannequinForm.BODY_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.mannequinState().bodyRotationDegrees()));
        if (entity.lastHitTime() != Long.MIN_VALUE) {
            float sinceHit = entity.level().getGameTime() - entity.lastHitTime() + partialTick;
            if (sinceHit >= 0 && sinceHit < 5) {
                poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.sin(sinceHit / 1.5F * Math.PI) * 3));
            }
        }
        model.renderToBuffer(poseStack, vertices,
                light, OverlayTexture.NO_OVERLAY, -1);
        CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, cached.snapshot()),
                poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Mannequin entity) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                "textures/entity/mannequin/" + entity.variant().color().id() + ".png");
    }

    private record CachedLoadout(long revision, LoadoutRenderSnapshot snapshot) { }
}
