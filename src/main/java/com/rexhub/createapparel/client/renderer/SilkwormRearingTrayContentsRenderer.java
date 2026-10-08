package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.model.SilkwormModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class SilkwormRearingTrayContentsRenderer {
    private static final ResourceLocation LARVA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID, "textures/entity/silkworm/silkworm.png");
    @Nullable
    private static SilkwormModel model;

    private SilkwormRearingTrayContentsRenderer() { }

    public static void resetModel() { model = null; }

    public static void renderLarva(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                   boolean feeding) {
        if (model == null) {
            model = new SilkwormModel(Minecraft.getInstance().getEntityModels().bakeLayer(CreateApparelClient.SILKWORM_LAYER));
        }
        pose.pushPose();
        applyLarvaPose(pose, feeding);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LARVA_TEXTURE)),
                light, overlay, 1, 1, 1, 1);
        pose.popPose();
    }

    public static void renderOutput(ItemStack output, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                    boolean feeding) {
        if (output.isEmpty()) return;
        pose.pushPose();
        applyOutputPose(pose, feeding);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(output.copyWithCount(1), ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, minecraft.level, 0);
        pose.popPose();
    }

    static void applyLarvaPose(PoseStack pose, boolean feeding) {
        pose.translate(0.5, (feeding ? 14 : 13) / 16.0, 0.5);
        pose.scale(-0.5F, -0.5F, 0.5F);
        pose.translate(0, -23.5 / 16.0, -3.5 / 16.0);
    }

    static void applyOutputPose(PoseStack pose, boolean feeding) {
        pose.translate(11 / 16.0, ((feeding ? 14 : 13) + 0.125) / 16.0, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(0.25F, 0.25F, 0.25F);
    }
}
