package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.route.RouteMarkerPermissions;
import com.rexhub.createapparel.citizen.route.RouteSettings;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteViewPacket;
import com.rexhub.createapparel.network.RouteViewRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientRouteView {
    private static List<RouteViewPacket.Node> nodes = List.of();
    private static List<RouteViewPacket.Edge> edges = List.of();
    private static Map<net.minecraft.core.BlockPos, RouteViewPacket.Node> positions = Map.of();
    private static Set<net.minecraft.core.BlockPos> connected = Set.of();
    private static List<com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate> edgeCandidates = List.of();
    private static final List<RouteViewPacket.Node> pendingNodes = new ArrayList<>();
    private static final List<RouteViewPacket.Edge> pendingEdges = new ArrayList<>();
    private static long snapshot = -1;
    private static int nextPart;
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
        nodes = List.of(); edges = List.of(); edgeCandidates = List.of(); pendingNodes.clear(); pendingEdges.clear(); selected = null;
        positions = Map.of(); connected = Set.of();
        snapshot = -1; nextPart = 0; requestedRadius = 0;
    }

    public static void accept(RouteViewPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !RouteMarkerPermissions.holdingPhone(minecraft.player)
                || !packet.dimension().equals(minecraft.level.dimension().location())) return;
        if (packet.part() == 0) { pendingNodes.clear(); pendingEdges.clear(); snapshot = packet.snapshot(); nextPart = 0; }
        if (packet.snapshot() != snapshot || packet.part() != nextPart) return;
        if (pendingNodes.size() + packet.nodes().size() > RouteSettings.MAX_VIEW_NODES
                || pendingEdges.size() + packet.edges().size() > RouteSettings.MAX_VIEW_EDGES) { clear(); return; }
        pendingNodes.addAll(packet.nodes()); pendingEdges.addAll(packet.edges()); nextPart++;
        if (packet.last()) {
            nodes = List.copyOf(pendingNodes); edges = List.copyOf(pendingEdges); selected = packet.selected();
            var indexed = new HashMap<net.minecraft.core.BlockPos, RouteViewPacket.Node>();
            for (var node : nodes) indexed.put(node.pos(), node);
            positions = Map.copyOf(indexed);
            var endpoints = new HashSet<net.minecraft.core.BlockPos>();
            for (var edge : edges) if (edge.health() != RouteViewPacket.Health.REMOVED) { endpoints.add(edge.a()); endpoints.add(edge.b()); }
            connected = Set.copyOf(endpoints);
            var candidates = new ArrayList<com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate>();
            for (var edge : edges) if (edge.health() != RouteViewPacket.Health.REMOVED)
                candidates.add(new com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate(edge.id(), edge.a(), edge.b()));
            for (var node : nodes) if (node.controlPos() != null) candidates.add(new com.rexhub.createapparel.citizen.route.RouteEdgePicking.Candidate(node.id(), node.pos(), node.controlPos(), true));
            edgeCandidates = List.copyOf(candidates);
        }
    }

    @SubscribeEvent public static void maintenanceHint(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !RouteMarkerPermissions.holdingPhone(minecraft.player)
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        var node = positions.get(hit.getBlockPos());
        if (node == null || connected.contains(node.pos())) return;
        event.getGuiGraphics().drawCenteredString(minecraft.font, net.minecraft.network.chat.Component.translatable("message.createapparel.route.disconnected"),
                minecraft.getWindow().getGuiScaledWidth() / 2, minecraft.getWindow().getGuiScaledHeight() / 2 + 16, 0xFFBB55);
    }

    @SubscribeEvent public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || selected == null || event.getEntity().isShiftKeyDown()
                || !RouteMarkerPermissions.canEditRoutes(event.getEntity(), event.getHand())
                || !(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof com.rexhub.createapparel.item.SmartPhoneItem)) return;
        event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (observedLevel != minecraft.level) { clear(); observedLevel = minecraft.level; }
        if (minecraft.player == null || !RouteMarkerPermissions.holdingPhone(minecraft.player)) { clear(); return; }
        if (!RouteMarkerPermissions.canEditRoutes(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND)) selected = null;
        int radius = ModConfigs.CLIENT.routeMarkerRenderDistance.get();
        if (requestedRadius != radius) { ModNetwork.requestRouteView(new RouteViewRequestPacket(radius)); requestedRadius = radius; }
    }
}
