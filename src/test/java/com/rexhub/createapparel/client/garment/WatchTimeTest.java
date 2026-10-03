package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchTimeTest {
    @Test
    void naturalTimeMatchesCreateAtDawnNoonDuskAndMidnight() {
        assertEquals(new WatchTime(6, 0), WatchTime.fromDayTime(0, true));
        assertEquals(new WatchTime(12, 0), WatchTime.fromDayTime(6000, true));
        assertEquals(new WatchTime(18, 0), WatchTime.fromDayTime(12000, true));
        assertEquals(new WatchTime(0, 0), WatchTime.fromDayTime(18000, true));
        assertEquals(new WatchTime(6, 30), WatchTime.fromDayTime(500, true));
        assertEquals(new WatchTime(6, 59), WatchTime.fromDayTime(999, true));
        assertEquals(0, WatchTime.fromDayTime(6000, true).hourAngle());
        assertEquals(180, WatchTime.fromDayTime(500, true).minuteAngle());
    }

    @Test
    void unnaturalTimeRunsTwentyFourTimesFasterAndWrapsAtDayBoundaries() {
        assertEquals(new WatchTime(12, 0), WatchTime.fromDayTime(250, false));
        assertEquals(new WatchTime(6, 30), WatchTime.fromDayTime(21, false));
        assertEquals(new WatchTime(6, 0), WatchTime.fromDayTime(1000, false));
        assertEquals(WatchTime.fromDayTime(777, false), WatchTime.fromDayTime(24000L * 500000000 + 777, false));
        assertEquals(new WatchTime(5, 59), WatchTime.fromDayTime(-1, true));
    }
}
