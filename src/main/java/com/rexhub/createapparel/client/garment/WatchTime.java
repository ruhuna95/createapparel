package com.rexhub.createapparel.client.garment;

public record WatchTime(int hours, int minutes) {
    public static WatchTime fromDayTime(long dayTime, boolean natural) {
        int time = (int) ((Math.floorMod(dayTime, 24000L) * (natural ? 1 : 24)) % 24000);
        return new WatchTime((time / 1000 + 6) % 24, (time % 1000) * 60 / 1000);
    }

    public float hourAngle() {
        return (hours % 12) * 30;
    }

    public float minuteAngle() {
        return minutes * 6;
    }
}
