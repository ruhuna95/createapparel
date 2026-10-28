package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.network.RetailLinkSessionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetailLinkSessionPacketTest {
    private static PurchasedRegisterRef reference() {
        return new PurchasedRegisterRef(UUID.randomUUID(), ResourceLocation.parse("minecraft:overworld"), new BlockPos(-24, 63, 17));
    }

    @Test void selectionRoundTripsBothHandsAndEveryHotbarSlot() {
        for (var hand : InteractionHand.values()) for (int slot = 0; slot < 9; slot++) {
            var packet = new RetailLinkSessionPacket(reference(), hand, slot);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                assertEquals(packet, RetailLinkSessionPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test void clearingIsAnExplicitOneByteUpdate() {
        var packet = new RetailLinkSessionPacket(null, InteractionHand.MAIN_HAND, 0);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertEquals(1, buffer.readableBytes());
            assertEquals(packet, RetailLinkSessionPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void invalidHotbarSlotsAreRejected() {
        for (int slot : new int[]{-1, 9, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new RetailLinkSessionPacket(reference(), InteractionHand.MAIN_HAND, slot));
    }

    @Test void malformedHotbarSlotsAreRejectedOnDecode() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var reference = reference();
        try {
            buffer.writeBoolean(true); buffer.writeUUID(reference.registerId()); buffer.writeResourceLocation(reference.dimension());
            buffer.writeBlockPos(reference.lastKnownPos()); buffer.writeEnum(InteractionHand.MAIN_HAND); buffer.writeVarInt(9);
            assertThrows(IllegalArgumentException.class, () -> RetailLinkSessionPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
