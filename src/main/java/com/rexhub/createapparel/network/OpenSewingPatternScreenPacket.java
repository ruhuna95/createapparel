package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.CreateApparelClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;

public record OpenSewingPatternScreenPacket(InteractionHand hand) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<OpenSewingPatternScreenPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "open_sewing_pattern_screen_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, OpenSewingPatternScreenPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), OpenSewingPatternScreenPacket::decode);

    @Override
    public Type<OpenSewingPatternScreenPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
    }

    public static OpenSewingPatternScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenSewingPatternScreenPacket(buffer.readEnum(InteractionHand.class));
    }

    public void handleClient() {
        CreateApparelClient.openSewingPatternScreen(hand);
    }
}
