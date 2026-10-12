package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.network.CitizenActionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitizenActionPacketTest {
    @Test
    void everyActionRetainsTargetAndBoundedFieldsOnWire() {
        for (var action : CitizenActionPacket.Action.values()) {
            var packet = new CitizenActionPacket(19, 1234, action, 2, "sleeves", "rolled");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                assertEquals(packet, CitizenActionPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void unknownActionDecodesToRejectedActionRatherThanIndexingEnum() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(19).writeVarInt(1234).writeVarInt(Integer.MAX_VALUE).writeVarInt(0).writeUtf("").writeUtf("");
            assertNull(CitizenActionPacket.decode(buffer).action());
        } finally { buffer.release(); }
    }

    @Test
    void wireRejectsOverlongStringsBeforeServerHandling() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(19).writeVarInt(1234).writeVarInt(0).writeVarInt(0).writeUtf("x".repeat(65)).writeUtf("");
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> CitizenActionPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
