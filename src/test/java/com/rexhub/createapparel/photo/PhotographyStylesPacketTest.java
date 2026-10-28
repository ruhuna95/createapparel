package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.network.PhotographyStylesPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhotographyStylesPacketTest {
    @Test void roundTripsSessionIdentityAndAllGarmentStyles() {
        var styles = new java.util.EnumMap<GarmentSlot, EquippedStyleState>(GarmentSlot.class);
        for (var slot : GarmentSlot.values()) styles.put(slot, new EquippedStyleState(Map.of("category", slot.getSerializedName())));
        var packet = new PhotographyStylesPacket(19, UUID.randomUUID(), styles);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertEquals(packet, PhotographyStylesPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void omittedSlotsAreExplicitlyEmpty() {
        var packet = new PhotographyStylesPacket(1, UUID.randomUUID(), Map.of());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            var decoded = PhotographyStylesPacket.decode(buffer);
            assertEquals(packet.photoId(), decoded.photoId());
            assertEquals(GarmentSlot.values().length, decoded.styles().size());
            assertTrue(decoded.styles().values().stream().allMatch(EquippedStyleState.EMPTY::equals));
        } finally { buffer.release(); }
    }
}
