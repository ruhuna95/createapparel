package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.PatternData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "7";

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
        channel.messageBuilder(OpenSewingPatternScreenPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenSewingPatternScreenPacket::encode)
                .decoder(OpenSewingPatternScreenPacket::decode)
                .consumerMainThread((packet, context) -> packet.handleClient())
                .add();
        channel.messageBuilder(UpdateSewingPatternPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateSewingPatternPacket::encode)
                .decoder(UpdateSewingPatternPacket::decode)
                .consumerMainThread((packet, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) {
                        packet.handle(player);
                    }
                })
                .add();
        channel.messageBuilder(ApparelInventorySyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ApparelInventorySyncPacket::encode)
                .decoder(ApparelInventorySyncPacket::decode)
                .consumerMainThread((packet, context) -> packet.handleClient())
                .add();
        channel.messageBuilder(WardrobeActionPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WardrobeActionPacket::encode)
                .decoder(WardrobeActionPacket::decode)
                .consumerMainThread((packet, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) {
                        packet.handle(player);
                    }
                })
                .add();
    }

    private static SimpleChannel channel() {
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

    public static void sendApparelInventory(ServerPlayer player, ApparelInventory inventory) {
        if (channel != null) {
            channel.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    apparelInventoryPacket(player, inventory)
            );
        }
    }

    public static void sendApparelInventory(
            ServerPlayer target,
            ServerPlayer observer,
            ApparelInventory inventory
    ) {
        if (channel != null) {
            channel.send(
                    PacketDistributor.PLAYER.with(() -> observer),
                    apparelInventoryPacket(target, inventory)
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
        channel().send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenSewingPatternScreenPacket(hand)
        );
    }

    public static void updateSewingPattern(InteractionHand hand, PatternData data) {
        channel().sendToServer(new UpdateSewingPatternPacket(
                hand,
                data.recipe(),
                data.design(),
                data.secondaryColor(),
                data.accentColor()
        ));
    }

    public static void updateWardrobe(WardrobeActionPacket packet) {
        channel().sendToServer(packet);
    }
}
