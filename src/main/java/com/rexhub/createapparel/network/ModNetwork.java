package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel channel;

    private ModNetwork() {
    }

    public static void initialize() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "main"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(GarmentDefinitionsPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GarmentDefinitionsPacket::encode)
                .decoder(GarmentDefinitionsPacket::decode)
                .consumerMainThread((packet, context) -> packet.handleClient())
                .add();
    }

    public static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Create: Apparel network channel has not been initialized");
        }
        return channel;
    }

    public static void sendGarmentDefinitions(ServerPlayer player, List<GarmentDefinition> definitions) {
        if (channel != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), new GarmentDefinitionsPacket(definitions));
        }
    }

}
