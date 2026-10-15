package com.rexhub.createapparel.client.citizen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.citizen.route.RouteMarkerPermissions;
import com.rexhub.createapparel.citizen.route.RouteMarkerType;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.network.RouteViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class RouteMarkerRenderer {
    private static final int CROSSING_COLOR = 0x6BFFCC1A;
    private static final int SELECTED_COLOR = 0x6BFFFFFF;
    private static net.minecraft.client.multiplayer.ClientLevel surfaceLevel;
    private static RouteViewPacket.Node surfaceNode;
    private static long surfaceUpdate;
    private static List<com.rexhub.createapparel.citizen.route.RouteRegionSurfaces.Surface> surfaces = List.of();
    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) { }

    private RouteMarkerRenderer() { }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !RouteMarkerPermissions.holdingPhone(minecraft.player)) { clearSurfaces(); return; }
        var camera = event.getCamera().getPosition();
        var viewOffset = new Matrix4f(event.getPoseStack().last().pose()).invert().transformPosition(new Vector3f());
        var guideCamera = camera.add(viewOffset.x, viewOffset.y, viewOffset.z);
        double distance = ModConfigs.CLIENT.routeMarkerRenderDistance.get();
        var origin = minecraft.player.position();
        var quads = new ArrayList<Quad>();
        var targetedEdge = ClientRouteView.targetedEdge();
        RouteViewPacket.Node selected = null, aimed = null;
        for (var node : ClientRouteView.nodes()) {
            if (!CitizenNavigation.hasLoadedRegion(minecraft.level, node.pos(), 0)) continue;
            if (node.id().equals(ClientRouteView.selected())) selected = node;
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(node.pos())) aimed = node;
            var center = Vec3.atBottomCenterOf(node.pos());
            var box = RouteGuideGeometry.markerBox(center);
            if (center.distanceToSqr(origin) <= distance * distance && event.getFrustum().isVisible(box)) {
                int color = RouteGuideColors.nodeFill(node.type(), node.id().equals(ClientRouteView.selected()));
                box(quads, box, color, RouteGuideColors.nodeFrame(node.type(), node.on()));
            }
        }
        for (var edge : ClientRouteView.edges()) {
            if (!CitizenNavigation.hasLoadedRegion(minecraft.level, edge.a(), 0)
                    || !CitizenNavigation.hasLoadedRegion(minecraft.level, edge.b(), 0)) continue;
            var a = Vec3.atBottomCenterOf(edge.a()).add(0, 0.15, 0);
            var b = Vec3.atBottomCenterOf(edge.b()).add(0, 0.15, 0);
            int color = RouteGuideColors.routeEdge(edge.conditional(), edge.open());
            if (targetedEdge != null && !targetedEdge.control() && edge.id().equals(targetedEdge.id())) color = minecraft.level.getGameTime() % 12 < 6 ? 0xA6FF3333 : 0x40FF3333;
            edge(quads, event, a, b, origin, guideCamera, distance, true, color);
        }
        for (var node : ClientRouteView.nodes()) {
            if (node.controlPos() == null || !CitizenNavigation.hasLoadedRegion(minecraft.level, node.pos(), 0)
                    || !CitizenNavigation.hasLoadedRegion(minecraft.level, node.controlPos(), 0)) continue;
            int color = RouteGuideColors.controlEdge(node.powered());
            if (targetedEdge != null && targetedEdge.control() && node.id().equals(targetedEdge.id())) color = minecraft.level.getGameTime() % 12 < 6 ? 0xA6FF3333 : 0x40FF3333;
            edge(quads, event, Vec3.atBottomCenterOf(node.pos()).add(0, 0.15, 0), com.rexhub.createapparel.citizen.route.CrossingCondition.connectionPoint(minecraft.level, node.controlPos()), origin, guideCamera, distance, false, color);
        }
        if (selected != null && RouteMarkerPermissions.canEditRoutes(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND)
                && minecraft.screen == null && minecraft.hitResult != null
                && (aimed == null || !aimed.id().equals(selected.id()))) {
            var from = Vec3.atBottomCenterOf(selected.pos()).add(0, 0.15, 0);
            var to = aimed == null ? minecraft.hitResult.getLocation() : Vec3.atBottomCenterOf(aimed.pos()).add(0, 0.15, 0);
            boolean control = selected.type() == RouteMarkerType.CROSSING && aimed == null && minecraft.hitResult instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK && com.rexhub.createapparel.citizen.route.CrossingCondition.supports(minecraft.level.getBlockState(hit.getBlockPos()));
            if (control) to = com.rexhub.createapparel.citizen.route.CrossingCondition.connectionPoint(minecraft.level, ((BlockHitResult) minecraft.hitResult).getBlockPos());
            var selectedPos = selected.pos();
            var aimedPos = aimed == null ? null : aimed.pos();
            boolean existing = aimedPos != null && ClientRouteView.edges().stream().anyMatch(link ->
                    link.a().equals(selectedPos) && link.b().equals(aimedPos) || link.b().equals(selectedPos) && link.a().equals(aimedPos));
            if (control && ((BlockHitResult) minecraft.hitResult).getBlockPos().equals(selected.controlPos())) existing = true;
            if (!existing && !minecraft.player.isShiftKeyDown()) edge(quads, event, from, to, origin, guideCamera, distance, aimed != null,
                    selected.type() == RouteMarkerType.CROSSING && aimed != null && aimed.type() == RouteMarkerType.CROSSING ? CROSSING_COLOR : SELECTED_COLOR);
        }
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var source = minecraft.renderBuffers().bufferSource();
        if (minecraft.screen == null && minecraft.player.isShiftKeyDown() && aimed != null) {
            if (surfaceLevel != minecraft.level || !aimed.equals(surfaceNode) || minecraft.level.getGameTime() >= surfaceUpdate) {
                surfaceLevel = minecraft.level; surfaceNode = aimed; surfaceUpdate = minecraft.level.getGameTime() + 5;
                surfaces = com.rexhub.createapparel.citizen.route.RouteRegionSurfaces.collect(minecraft.level, aimed.pos(), aimed.radius(), aimed.tolerance());
            }
            var highlights = new ArrayList<Quad>();
            for (var face : surfaces) {
                double y = face.y() + 0.002;
                if (!event.getFrustum().isVisible(new AABB(face.minX(), y, face.minZ(), face.maxX(), y + 0.01, face.maxZ()))) continue;
                highlights.add(new Quad(new Vec3(face.minX(), y, face.minZ()), new Vec3(face.maxX(), y, face.minZ()),
                        new Vec3(face.maxX(), y, face.maxZ()), new Vec3(face.minX(), y, face.maxZ()), 0x55FFFFFF));
            }
            emit(pose, source.getBuffer(RouteGuideRenderType.HIGHLIGHT), highlights);
            source.endBatch(RouteGuideRenderType.HIGHLIGHT);
        } else clearSurfaces();
        // Resolve the nearest guide surface before blending overlapping nodes and edges once.
        emit(pose, source.getBuffer(RouteGuideRenderType.DEPTH), quads);
        source.endBatch(RouteGuideRenderType.DEPTH);
        emit(pose, source.getBuffer(RouteGuideRenderType.COLOR), quads);
        source.endBatch(RouteGuideRenderType.COLOR);
        pose.popPose();
    }

    private static void edge(List<Quad> quads, RenderLevelStageEvent event, Vec3 a, Vec3 b, Vec3 origin,
                             Vec3 camera, double distance, boolean markerAtEnd, int color) {
        if (!event.getFrustum().isVisible(new AABB(a, b).inflate(RouteGuideGeometry.EDGE_WIDTH))) return;
        var segment = RouteGuideGeometry.clipEdge(a, b, origin, distance);
        if (segment == null) return;
        var startBox = RouteGuideGeometry.markerBox(a.add(0, -0.15, 0));
        var boxes = markerAtEnd ? List.of(startBox, RouteGuideGeometry.markerBox(b.add(0, -0.15, 0))) : List.of(startBox);
        for (var polygon : RouteGuideGeometry.ribbonOutsideNodes(segment, camera, boxes)) {
            if (polygon.size() == 4) quads.add(new Quad(polygon.get(0), polygon.get(1), polygon.get(2), polygon.get(3), color));
            else for (int i = 1; i + 1 < polygon.size(); i++) quads.add(new Quad(polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1), color));
        }
    }

    private static void box(List<Quad> quads, AABB box, int color, int frameColor) {
        var a = new Vec3(box.minX, box.minY, box.minZ); var b = new Vec3(box.maxX, box.minY, box.minZ);
        var c = new Vec3(box.maxX, box.minY, box.maxZ); var d = new Vec3(box.minX, box.minY, box.maxZ);
        var e = new Vec3(box.minX, box.maxY, box.minZ); var f = new Vec3(box.maxX, box.maxY, box.minZ);
        var g = new Vec3(box.maxX, box.maxY, box.maxZ); var h = new Vec3(box.minX, box.maxY, box.maxZ);
        face(quads, a, b, c, d, color, frameColor); face(quads, e, f, g, h, color, frameColor);
        face(quads, a, b, f, e, color, frameColor); face(quads, b, c, g, f, color, frameColor);
        face(quads, c, d, h, g, color, frameColor); face(quads, d, a, e, h, color, frameColor);
    }

    private static void face(List<Quad> quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int frameColor) {
        faceRect(quads, a, b, c, d, 0.12, 0.88, 0.12, 0.88, color);
        faceRect(quads, a, b, c, d, 0, 1, 0, 0.12, frameColor);
        faceRect(quads, a, b, c, d, 0, 1, 0.88, 1, frameColor);
        faceRect(quads, a, b, c, d, 0, 0.12, 0.12, 0.88, frameColor);
        faceRect(quads, a, b, c, d, 0.88, 1, 0.12, 0.88, frameColor);
    }

    private static void faceRect(List<Quad> quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, double minU, double maxU, double minV, double maxV, int color) {
        var start = a.lerp(b, minU); var end = a.lerp(b, maxU);
        var oppositeStart = d.lerp(c, minU); var oppositeEnd = d.lerp(c, maxU);
        quads.add(new Quad(start.lerp(oppositeStart, minV), end.lerp(oppositeEnd, minV),
                end.lerp(oppositeEnd, maxV), start.lerp(oppositeStart, maxV), color));
    }

    private static void emit(PoseStack pose, VertexConsumer buffer, List<Quad> quads) {
        for (var quad : quads) {
            for (var vertex : new Vec3[]{quad.a(), quad.b(), quad.c(), quad.d()}) {
                buffer.vertex(pose.last().pose(), (float) vertex.x, (float) vertex.y, (float) vertex.z).color(quad.color()).endVertex();
            }
        }
    }

    static void clearSurfaces() { surfaceLevel = null; surfaceNode = null; surfaces = List.of(); }
}
