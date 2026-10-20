package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import com.rexhub.createapparel.network.CitizenGaugeInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientGaugeView {
    private static List<CitizenGauge> gauges = List.of();
    private static String dimension = "";
    private static long receivedAt;
    private static CitizenGauge selected;
    private static boolean relocating;
    private static String selectionDimension = "";
    private static long selectionUntil;
    private static List<CitizenGauge> territoryGauges = List.of();
    private static ClientDistrictTerritory territory = new ClientDistrictTerritory();
    private ClientGaugeView() { }
    public static void accept(CitizenGaugeInfoPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (packet.containerId() == -2) {
            selected = packet.info().hasUUID("Id") ? CitizenGauge.load(packet.info()) : null;
            relocating = packet.info().getBoolean("Relocating"); selectionDimension = packet.info().getString("Dimension");
            selectionUntil = minecraft.level.getGameTime() + 1200;
            minecraft.player.getPersistentData().putBoolean("CitizenGaugeSelection", selected != null); return;
        }
        if (packet.containerId() >= 0) {
            if (minecraft.player.containerMenu instanceof CitizenGaugeMenu menu && menu.containerId == packet.containerId() && menu.gaugeId().equals(packet.gaugeId())) menu.accept(packet.info());
            return;
        }
        var parsed = new ArrayList<CitizenGauge>();
        for (var raw : packet.info().getList("Gauges", Tag.TAG_COMPOUND)) {
            if (parsed.size() >= 256) break;
            try { parsed.add(CitizenGauge.load((net.minecraft.nbt.CompoundTag) raw)); } catch (IllegalArgumentException ignored) { }
        }
        String nextDimension = packet.info().getString("Dimension");
        if (!dimension.equals(nextDimension)) territory = new ClientDistrictTerritory();
        gauges = List.copyOf(parsed); dimension = nextDimension; receivedAt = minecraft.level.getGameTime();
        parsed.clear();
        if (packet.info().getBoolean("TerritoryComplete")) for (var raw : packet.info().getList("TerritoryGauges", Tag.TAG_COMPOUND)) {
            if (parsed.size() >= 1024) break;
            try { parsed.add(CitizenGauge.load((net.minecraft.nbt.CompoundTag) raw)); } catch (IllegalArgumentException ignored) { }
        }
        territoryGauges = List.copyOf(parsed);
        territory.accept(territoryGauges.stream().filter(gauge -> presentOrUnloaded(minecraft.level, gauge)).toList());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { gauges = List.of(); territoryGauges = List.of(); territory = new ClientDistrictTerritory(); dimension = ""; selected = null; }
    @SubscribeEvent public static void districtHint(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui
                || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof com.rexhub.createapparel.block.CitizenGaugeBlock)
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity entity)) return;
        int slot = com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(hit.getBlockPos(), entity.getBlockState(), hit.getLocation());
        var status = entity.districtStatus(slot);
        if (!entity.hasPanel(slot) || status == null) return;
        int x = mc.getWindow().getGuiScaledWidth() / 2, y = mc.getWindow().getGuiScaledHeight() / 2 + 16;
        var graphics = event.getGuiGraphics();
        graphics.drawCenteredString(mc.font, mc.font.plainSubstrByWidth(status.name(), Math.max(40, mc.getWindow().getGuiScaledWidth() - 24)), x, y, 0xFFFFFF);
        graphics.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("screen.createapparel.gauge.population_summary", status.population(), status.target()),
                x, y + 12, status.satisfied() ? 0x8DDB74 : 0xE87070);
    }
    @SubscribeEvent public static void emptyUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty event) {
        if (selected == null || event.getEntity() != Minecraft.getInstance().player) return;
        com.rexhub.createapparel.network.ModNetwork.updateGauge(new com.rexhub.createapparel.network.CitizenGaugeActionPacket(-1, selected.id(), 0,
                com.rexhub.createapparel.network.CitizenGaugeActionPacket.Action.CANCEL, 0, 0, null));
        selected = null; event.getEntity().getPersistentData().putBoolean("CitizenGaugeSelection", false);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (GaugeInteractions.observingDistricts(mc.player)) {
            var stack = mc.player.getMainHandItem().getItem() instanceof CitizenGaugeItem ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            territory.preview(stack.getItem() instanceof CitizenGaugeItem ? CitizenGaugeItem.center(stack, mc.level) : null, CitizenGauge.DEFAULT_RANGE);
            if (dimension.equals(mc.level.dimension().location().toString()) && mc.level.getGameTime() - receivedAt <= 100 && mc.level.getGameTime() % 5 == 0)
                territory.accept(territoryGauges.stream().filter(gauge -> presentOrUnloaded(mc.level, gauge)).toList());
            territory.tick(mc.level, mc.player.position());
        }
        if (selected == null) return;
        if (!selectionDimension.equals(mc.level.dimension().location().toString()) || mc.level.getGameTime() >= selectionUntil) {
            selected = null; mc.player.getPersistentData().putBoolean("CitizenGaugeSelection", false); return;
        }
        if (!GaugeSurface.loaded(mc.level, selected.pos()) || !(mc.level.getBlockState(selected.pos()).getBlock() instanceof com.rexhub.createapparel.block.CitizenGaugeBlock)) return;
        var state = mc.level.getBlockState(selected.pos());
        var point = Vec3.atLowerCornerOf(selected.pos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, selected.slot()));
        var facing = state.getValue(com.rexhub.createapparel.block.CitizenGaugeBlock.FACING);
        double x = facing.getAxis() == net.minecraft.core.Direction.Axis.X ? .03 : .22;
        double z = facing.getAxis() == net.minecraft.core.Direction.Axis.Z ? .03 : .22;
        net.createmod.catnip.outliner.Outliner.getInstance().showAABB("citizen_gauge_origin", new net.minecraft.world.phys.AABB(point, point).inflate(x, .22, z))
                .colored(mc.level.getGameTime() % 16 > 8 ? 0x38B764 : 0xA7F070).lineWidth(1 / 32F);
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.gauge." + (relocating ? "move_selected" : "selected")), true);
        if (!relocating || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return;
        var target = GaugeSurface.relocationTarget(mc.level, selected.pos(), state, hit);
        if (target != null) {
            var destination = Vec3.atLowerCornerOf(target.pos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, target.slot()));
            net.createmod.catnip.outliner.Outliner.getInstance().showAABB("citizen_gauge_destination", new net.minecraft.world.phys.AABB(destination, destination).inflate(x, .22, z))
                    .colored(0xEEEEEE).disableLineNormals().lineWidth(1 / 32F);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null || !GaugeInteractions.observingDistricts(minecraft.player)) return;
        var stack = minecraft.player.getMainHandItem().getItem() instanceof CitizenGaugeItem ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
        var center = stack.getItem() instanceof CitizenGaugeItem ? CitizenGaugeItem.center(stack, minecraft.level) : null;
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        pose.pushPose();
        var buffers = minecraft.renderBuffers().bufferSource(); var consumer = buffers.getBuffer(RenderType.lines());
        if (dimension.equals(minecraft.level.dimension().location().toString()) && minecraft.level.getGameTime() - receivedAt <= 100) {
            territory.render(consumer, pose.last(), minecraft.player.getY(), camera);
        }
        if (center != null) {
            boolean overlap = gauges.stream().anyMatch(gauge -> !gauge.source() && presentOrUnloaded(minecraft.level, gauge) && gauge.contains(Vec3.atCenterOf(center)));
            territory.renderPreview(consumer, pose.last(), minecraft.player.getY(), overlap ? 0xFF5926 : 0xFFD926, camera);
        }
        buffers.endBatch(RenderType.lines()); pose.popPose();
    }
    private static boolean presentOrUnloaded(net.minecraft.world.level.Level level, CitizenGauge gauge) {
        if (!GaugeSurface.loaded(level, gauge.pos())) return true;
        return level.getBlockEntity(gauge.pos()) instanceof com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity entity
                && entity.hasPanel(gauge.slot()) && gauge.id().equals(entity.gaugeId(gauge.slot()));
    }
}
