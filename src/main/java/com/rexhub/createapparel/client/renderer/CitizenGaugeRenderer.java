package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.GaugeSurface;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.rexhub.createapparel.client.citizen.DistrictGaugeIcon;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.factoryBoard.*;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class CitizenGaugeRenderer implements BlockEntityRenderer<CitizenGaugeBlockEntity> {
    public static final float ITEM_ICON_SCALE = .5F * (.5F + 1 / 64F);
    public CitizenGaugeRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(CitizenGaugeBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = entity.getBlockState(); float yRot = FactoryPanelBlock.getYRot(state);
        for (var slot : FactoryPanelBlock.PanelSlot.values()) if (entity.hasPanel(slot.ordinal())) {
            CachedBuffers.partial(ModPartialModels.CITIZEN_GAUGE_PANEL, state)
                    .rotateCentered(yRot, Direction.UP).rotateCentered((float) Math.PI / 2, Direction.EAST)
                    .rotateCentered((float) Math.PI, Direction.UP).translate(slot.xOffset * .5, 0, slot.yOffset * .5)
                    .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
            var status = entity.districtStatus(slot.ordinal());
            if (status != null) {
                CachedBuffers.partial(status.satisfied() ? AllPartialModels.FACTORY_PANEL_LIGHT : AllPartialModels.FACTORY_PANEL_RED_LIGHT, state)
                        .rotateCentered(yRot, Direction.UP).rotateCentered((float) Math.PI / 2, Direction.EAST)
                        .rotateCentered((float) Math.PI, Direction.UP).translate(slot.xOffset * .5, 0, slot.yOffset * .5)
                        .light(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).overlay(overlay)
                        .renderInto(pose, buffers.getBuffer(RenderType.translucent()));
            }
            boolean district = entity.initialCenter(slot.ordinal()) != null;
            if (district && status == null) continue;
            pose.pushPose(); pose.translate(.5, .5, .5);
            pose.mulPose(Axis.YP.rotation(yRot)); pose.mulPose(Axis.XP.rotationDegrees(90)); pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-.5 + slot.xOffset * .5 + .25, -.5 + .14, -.5 + slot.yOffset * .5 + .25);
            pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.mulPose(Axis.ZP.rotationDegrees(180)); pose.scale(ITEM_ICON_SCALE, ITEM_ICON_SCALE, ITEM_ICON_SCALE);
            if (district) DistrictGaugeIcon.render(pose, buffers, status.districtId(), light, overlay);
            else DistrictGaugeIcon.render(pose, buffers, entity.outflow(slot.ordinal()) ? DistrictGaugeIcon.OUTFLOW : DistrictGaugeIcon.INFLUX, 0xFFFFFF, light, overlay);
            pose.popPose();
        }
        var level = entity.getLevel();
        if (level == null) return;
        for (var edge : entity.connections()) {
            if (!entity.hasPanel(edge.slot()) || !GaugeSurface.loaded(level, edge.target()) || !(level.getBlockEntity(edge.target()) instanceof CitizenGaugeBlockEntity target) || !target.hasPanel(edge.targetSlot())
                    || !GaugeSurface.compatible(state, entity.getBlockPos(), target.getBlockState(), edge.target())) continue;
            var ownSlot = FactoryPanelBlock.PanelSlot.values()[edge.slot()];
            var path = GaugeWireRouter.path(entity, edge);
            float x = 0, z = 0;
            for (int index = 0; index < path.size(); index++) {
                var direction = path.get(index); x += direction.getStepX() * .5F; z += direction.getStepZ() * .5F;
                var partial = (index == 0 ? AllPartialModels.FACTORY_PANEL_ARROWS : AllPartialModels.FACTORY_PANEL_LINES).get(direction.getOpposite());
                CachedBuffers.partial(partial, state).rotateCentered(yRot, Direction.UP)
                        .rotateCentered((float) Math.PI / 2, Direction.EAST).rotateCentered((float) Math.PI, Direction.UP)
                        .translate(ownSlot.xOffset * .5 + .25, 0, ownSlot.yOffset * .5 + .25)
                        .translate(x, (1 + (direction.get2DDataValue() % 2) * .125F) / 512F, z)
                        .color(0x8DAEAF).light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(CitizenGaugeBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
