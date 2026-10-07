package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.garment.HangerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public final class HangerDisplay {
    public static final net.minecraft.resources.ResourceLocation MODEL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "block/common_hanger");
    public static final net.minecraft.resources.ResourceLocation CLIP_MODEL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "block/common_clip_hanger");
    public static final float GARMENT_SCALE = 0.85F;
    private static final RandomSource RANDOM = RandomSource.create();

    private HangerDisplay() { }

    public static void renderHanger(BlockAndTintGetter level, BlockPos upperPos, BlockState state,
                                     HangerType type, RackMountType mount, int slot, Direction facing,
                                     PoseStack poseStack, MultiBufferSource buffer, int overlay) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var model = HangerBlockModel.rackModel(minecraft.getModelManager().getModel(type == HangerType.CLIP ? CLIP_MODEL : MODEL),
                type, mount, slot, facing);
        minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(level, model, state, upperPos,
                poseStack, buffer.getBuffer(RenderType.solid()), false, RANDOM, state.getSeed(upperPos),
                overlay, ModelData.EMPTY, RenderType.solid());
    }

    public static void orient(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        poseStack.translate(-0.5, 0, -0.5);
    }

    public static void renderGarment(ItemStack garment, PoseStack poseStack, MultiBufferSource buffer,
                                     int light, int overlay) {
        if (garment.isEmpty()) return;
        poseStack.pushPose();
        poseStack.scale(GARMENT_SCALE, GARMENT_SCALE, GARMENT_SCALE);
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(garment.getItem());
        double lift = id == null ? 0 : com.rexhub.createapparel.client.garment.ClientGarmentAssets.getDefinition(id)
                .map(com.rexhub.createapparel.garment.HangerGarments::displayLift).orElse(0.0);
        poseStack.translate(0, lift / GARMENT_SCALE, 0);
        CommonGarmentRenderer.renderHanger(garment, poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
