package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.material.FabricColor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhotographyActionPacketTest {
    @Test void settingsColorsAndReverseScrollingSurviveTheVanillaSignedBytePacket() {
        assertTrue(PhotographyMenu.COLOR_ACTION + FabricColor.values().length <= PhotographyMenu.REVERSE_ACTION);
        for (int action = 0; action < 8; action++) {
            roundTrip(action);
            roundTrip(PhotographyMenu.REVERSE_ACTION + action);
        }
        roundTrip(8);
        for (int color = 0; color < FabricColor.values().length; color++) roundTrip(PhotographyMenu.COLOR_ACTION + color);
        assertTrue(PhotographyMenu.SELECT_SLOT_ACTION > PhotographyMenu.REVERSE_ACTION + 7);
        assertTrue(PhotographyMenu.SELECT_SLOT_ACTION + PhotographyMenu.GARMENT_SLOTS <= PhotographyMenu.STYLE_ACTION);
        for (int slot = 0; slot < PhotographyMenu.GARMENT_SLOTS; slot++) roundTrip(PhotographyMenu.SELECT_SLOT_ACTION + slot);
        for (int category = 0; category < 16; category++) {
            roundTrip(PhotographyMenu.STYLE_ACTION + category);
            roundTrip(PhotographyMenu.REVERSE_STYLE_ACTION + category);
        }
    }

    private static void roundTrip(int action) {
        assertTrue(action >= 0 && action <= Byte.MAX_VALUE);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundContainerButtonClickPacket.STREAM_CODEC.encode(buffer, new ServerboundContainerButtonClickPacket(12, action));
            var decoded = ServerboundContainerButtonClickPacket.STREAM_CODEC.decode(buffer);
            assertEquals(12, decoded.containerId());
            assertEquals(action, decoded.buttonId());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
