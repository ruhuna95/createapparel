package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.route.RouteMarkerPermissions;
import com.rexhub.createapparel.citizen.route.RouteSettings;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteViewPacket;
import com.rexhub.createapparel.network.RouteViewRequestPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientRouteView {
    private static List<RouteViewPacket.Node> nodes = List.of();
    private static List<RouteViewPacket.Edge> edges = List.of();
    private static Map<net.minecraft.core.BlockPos, RouteViewPacket.Node> positions = Map.of();
    private static Set<net.minecraft.core.BlockPos> connected = Set.of();
    private static List<com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate> edgeCandidates = List.of();
    private static com.rexhub.createapparel.citizen.route.RouteViewTransfer.Receiver receiver = new com.rexhub.createapparel.citizen.route.RouteViewTransfer.Receiver();
    private static UUID selected;
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static int requestedRadius;

    private ClientRouteView() { }
    public static List<RouteViewPacket.Node> nodes() { return nodes; }
    public static List<RouteViewPacket.Edge> edges() { return edges; }
    @javax.annotation.Nullable public static UUID selected() { return selected; }
    @javax.annotation.Nullable public static com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate targetedEdge() {
        var minecraft = Minecraft.getInstance();
        return minecraft.player == null || minecraft.screen != null
                || !RouteMarkerPermissions.canEditRoutes(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND)
                ? null : com.rexhub.createapparel.citizen.route.RouteEdgePicking.pick(minecraft.player, edgeCandidates);
    }

    public static void clear() {
        RouteMarkerRenderer.clearSurfaces();
        nodes = List.of(); edges = List.of(); edgeCandidates = List.of(); selected = null;
        positions = Map.of(); connected = Set.of();
        receiver = new com.rexhub.createapparel.citizen.route.RouteViewTransfer.Receiver(); requestedRadius = 0;
    }

    public static void accept(RouteViewPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !RouteMarkerPermissions.holdingPhone(minecraft.player)
                || !packet.dimension().equals(minecraft.level.dimension().location())) return;
        if (receiver.accept(packet)) {
            nodes = receiver.nodes(); edges = receiver.edges(); selected = packet.selected();
            var indexed = new HashMap<net.minecraft.core.BlockPos, RouteViewPacket.Node>();
            for (var node : nodes) indexed.put(node.pos(), node);
            positions = Map.copyOf(indexed);
            var endpoints = new HashSet<net.minecraft.core.BlockPos>();
            for (var edge : edges) if (edge.health() != RouteViewPacket.Health.REMOVED) { endpoints.add(edge.a()); endpoints.add(edge.b()); }
            connected = Set.copyOf(endpoints);
            edgeCandidates = com.rexhub.createapparel.citizen.route.RouteEdgePicking.fromView(nodes, edges);
        }
        if (receiver.needsFull()) requestedRadius = 0;
    }

    @SubscribeEvent public static void maintenanceHint(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !RouteMarkerPermissions.holdingPhone(minecraft.player)
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        var node = positions.get(hit.getBlockPos());
        if (node == null || connected.contains(node.pos())) return;
        event.getGuiGraphics().drawCenteredString(minecraft.font, net.minecraft.network.chat.Component.translatable("message.createapparel.route.disconnected"),
                minecraft.getWindow().getGuiScaledWidth() / 2, minecraft.getWindow().getGuiScaledHeight() / 2 + 16, 0xFFBB55);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) return;
        com.rexhub.createapparel.citizen.route.SmartPhoneRouteEditor.interact(event, selected != null, targetedEdge());
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (observedLevel != minecraft.level) { clear(); observedLevel = minecraft.level; }
        if (minecraft.player == null || !RouteMarkerPermissions.holdingPhone(minecraft.player)) { clear(); return; }
        if (!RouteMarkerPermissions.canEditRoutes(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND)) selected = null;
        int radius = ModConfigs.CLIENT.routeMarkerRenderDistance.get();
        if (requestedRadius != radius) { ModNetwork.requestRouteView(new RouteViewRequestPacket(radius)); requestedRadius = radius; }
    }
}
