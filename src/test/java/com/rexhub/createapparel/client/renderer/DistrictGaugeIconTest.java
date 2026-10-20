package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.client.citizen.DistrictGaugeIcon;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DistrictGaugeIconTest {
    @Test
    void uuidColorIsStableAndHasNinetyPercentBrightness() {
        var colors = new HashSet<Integer>();
        for (int seed = 0; seed < 256; seed++) {
            var id = UUID.nameUUIDFromBytes(new byte[]{(byte) seed});
            int color = DistrictGaugeIcon.color(id);
            assertEquals(color, DistrictGaugeIcon.color(UUID.fromString(id.toString())));
            assertEquals(0, color >>> 24);
            assertEquals(230, Math.max(color >>> 16 & 255, Math.max(color >>> 8 & 255, color & 255)));
            colors.add(color);
        }
        assertTrue(colors.size() > 250);
        assertEquals(0xE6E6E6, DistrictGaugeIcon.color(new UUID(0, 0)));
        assertEquals(0xE6E6E6, DistrictGaugeIcon.color(new UUID(0, 0xFFFFFF)));
    }

    @Test
    void rgbComesFromTheLowerHashBitsAndRetainsHueAndSaturation() {
        assertEquals(0xE60000, DistrictGaugeIcon.color(new UUID(0, 0xFF0000)));
        assertEquals(0x00E600, DistrictGaugeIcon.color(new UUID(0, 0x00FF00)));
        assertEquals(0x0000E6, DistrictGaugeIcon.color(new UUID(0, 0x0000FF)));
        assertEquals(0xE67300, DistrictGaugeIcon.color(new UUID(0, 0xFF8000)));
        assertEquals(0x4D99E6, DistrictGaugeIcon.color(new UUID(0, 0x102030)));
        assertEquals(0x4D99E6, DistrictGaugeIcon.color(new UUID(0, 0x010203)));
        assertEquals(DistrictGaugeIcon.color(new UUID(0, 0x123456)), DistrictGaugeIcon.color(new UUID(0, 0x80123456L)));
    }
}
