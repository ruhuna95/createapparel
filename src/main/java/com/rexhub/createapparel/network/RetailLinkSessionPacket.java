package com.rexhub.createapparel.network;

import com.rexhub.createapparel.fashion.PurchasedRegisterRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;

import javax.annotation.Nullable;

public record RetailLinkSessionPacket(@Nullable PurchasedRegisterRef register, InteractionHand hand, int slot) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RetailLinkSessionPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "retail_link_session_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RetailLinkSessionPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RetailLinkSessionPacket::decode);

    @Override
    public Type<RetailLinkSessionPacket> type() {
        return TYPE;
    }

    public RetailLinkSessionPacket {
        if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Invalid selected hotbar slot");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(register != null);
        if (register == null) return;
        buffer.writeUUID(register.registerId());
        buffer.writeResourceLocation(register.dimension());
        buffer.writeBlockPos(register.lastKnownPos());
        buffer.writeEnum(hand);
        buffer.writeVarInt(slot);
    }

    public static RetailLinkSessionPacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return new RetailLinkSessionPacket(null, InteractionHand.MAIN_HAND, 0);
        var reference = new PurchasedRegisterRef(buffer.readUUID(), buffer.readResourceLocation(), buffer.readBlockPos());
        return new RetailLinkSessionPacket(reference, buffer.readEnum(InteractionHand.class), buffer.readVarInt());
    }

    public void handleClient() {
        com.rexhub.createapparel.client.fashion.ClientRetailView.accept(this);
    }
}
