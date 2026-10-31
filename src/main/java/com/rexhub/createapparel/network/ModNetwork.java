package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.PatternData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "42";

    private static boolean registered;

    private ModNetwork() {
    }

    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(GarmentDefinitionsPacket.TYPE, GarmentDefinitionsPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToClient(OpenSewingPatternScreenPacket.TYPE, OpenSewingPatternScreenPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(UpdateSewingPatternPacket.TYPE, UpdateSewingPatternPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(ApparelInventorySyncPacket.TYPE, ApparelInventorySyncPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(WardrobeActionPacket.TYPE, WardrobeActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToServer(MannequinActionPacket.TYPE, MannequinActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToServer(CitizenActionPacket.TYPE, CitizenActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(CitizenBehaviorInfoPacket.TYPE, CitizenBehaviorInfoPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(RouteMarkerActionPacket.TYPE, RouteMarkerActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(RouteMarkerInfoPacket.TYPE, RouteMarkerInfoPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(RouteViewRequestPacket.TYPE, RouteViewRequestPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(RouteViewPacket.TYPE, RouteViewPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(RouteEdgeActionPacket.TYPE, RouteEdgeActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToServer(CitizenGaugeActionPacket.TYPE, CitizenGaugeActionPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(CitizenGaugeInfoPacket.TYPE, CitizenGaugeInfoPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToServer(GaugeReservationDiscardPacket.TYPE, GaugeReservationDiscardPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToServer(RetailInspectionRequestPacket.TYPE, RetailInspectionRequestPacket.STREAM_CODEC,
                (packet, context) -> packet.handle((ServerPlayer) context.player()));
        registrar.playToClient(RetailInspectionPacket.TYPE, RetailInspectionPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToClient(RetailLinkSessionPacket.TYPE, RetailLinkSessionPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registrar.playToClient(PhotographyStylesPacket.TYPE, PhotographyStylesPacket.STREAM_CODEC,
                (packet, context) -> packet.handleClient());
        registered = true;
    }

    public static void sendGarmentDefinitions(ServerPlayer player, List<GarmentDefinition> definitions) {
        if (registered) {
            PacketDistributor.sendToPlayer(player, new GarmentDefinitionsPacket(definitions));
        }
    }

    public static void sendApparelInventory(ServerPlayer player, ApparelInventory inventory) {
        if (registered) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, apparelInventoryPacket(player, inventory)
            );
        }
    }

    public static void sendApparelInventory(
            ServerPlayer target,
            ServerPlayer observer,
            ApparelInventory inventory
    ) {
        if (registered) {
            PacketDistributor.sendToPlayer(observer, apparelInventoryPacket(target, inventory)
            );
        }
    }

    private static ApparelInventorySyncPacket apparelInventoryPacket(
            ServerPlayer player,
            ApparelInventory inventory
    ) {
        return new ApparelInventorySyncPacket(
                player.getId(),
                inventory.copyContents(),
                inventory.copyStyles(),
                inventory.forceSkin(),
                inventory.forceHideArmor(),
                inventory.armProfile(),
                inventory.chestProfile(),
                inventory.skinTone()
        );
    }

    public static void openSewingPatternEditor(ServerPlayer player, InteractionHand hand) {
        PacketDistributor.sendToPlayer(player, new OpenSewingPatternScreenPacket(hand)
        );
    }

    public static void updateSewingPattern(InteractionHand hand, PatternData data) {
        PacketDistributor.sendToServer(new UpdateSewingPatternPacket(
                hand,
                data.recipe(),
                data.design(),
                data.secondaryColor(),
                data.accentColor()
        ));
    }

    public static void updateWardrobe(WardrobeActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void updateMannequin(MannequinActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void updateCitizen(CitizenActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }
    public static void updateGauge(CitizenGaugeActionPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void discardGaugeReservation(GaugeReservationDiscardPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendGaugeInfo(ServerPlayer player, CitizenGaugeInfoPacket packet) {
        if (registered && player.connection != null) {
            var metrics = com.rexhub.createapparel.citizen.population.CitizenPopulationManager.get(player.serverLevel()).metrics();
            if (metrics.enabled()) {
                var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try { packet.encode(buffer); metrics.packet(buffer.readableBytes()); } finally { buffer.release(); }
            }
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void sendCitizenBehaviorInfo(ServerPlayer player, CitizenBehaviorInfoPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }

    public static void updateRouteMarker(RouteMarkerActionPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void deleteRouteEdge(RouteEdgeActionPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void requestRouteView(RouteViewRequestPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendRouteMarkerInfo(ServerPlayer player, RouteMarkerInfoPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void sendRouteView(ServerPlayer player, RouteViewPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void requestRetailInspection(RetailInspectionRequestPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendRetailInspection(ServerPlayer player, RetailInspectionPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void sendRetailLinkSession(ServerPlayer player, RetailLinkSessionPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void sendPhotographyStyles(ServerPlayer player, PhotographyStylesPacket packet) {
        if (registered && player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
}
