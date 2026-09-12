package com.rexhub.createapparel.client.screen;

final class PreviewRotation {
    private static final long FRONT_PAUSE = 3_000;
    private static final long BACK_PAUSE = 1_000;
    private static final long HALF_TURN = 2_000;
    private static final long RETURN_TIME = 600;
    private long startedAt;
    private long returnStartedAt;
    private float manualAngle;
    private boolean dragging;
    private boolean returning;

    void reset(long now) {
        startedAt = now;
        dragging = false;
        returning = false;
        manualAngle = 0;
    }

    float angle(long now) {
        if (dragging) return manualAngle;
        if (returning) {
            long elapsed = Math.max(0, now - returnStartedAt);
            if (elapsed < RETURN_TIME) return manualAngle * (1 - ease(elapsed, RETURN_TIME));
            returning = false;
            startedAt = returnStartedAt + RETURN_TIME;
        }
        long phase = Math.max(0, now - startedAt) % (FRONT_PAUSE + BACK_PAUSE + 2 * HALF_TURN);
        if (phase < FRONT_PAUSE) return 0;
        phase -= FRONT_PAUSE;
        if (phase < HALF_TURN) return 180 * ease(phase, HALF_TURN);
        phase -= HALF_TURN;
        if (phase < BACK_PAUSE) return 180;
        return 180 + 180 * ease(phase - BACK_PAUSE, HALF_TURN);
    }

    void startDrag(long now) {
        manualAngle = wrap(angle(now));
        returning = false;
        dragging = true;
    }

    void drag(double deltaX) {
        if (dragging) manualAngle = wrap(manualAngle + (float) deltaX * 1.5F);
    }

    boolean release(long now) {
        if (!dragging) return false;
        dragging = false;
        returning = true;
        returnStartedAt = now;
        return true;
    }

    boolean isDragging() { return dragging; }

    private static float wrap(float angle) {
        return ((angle + 180) % 360 + 360) % 360 - 180;
    }

    private static float ease(long elapsed, long duration) {
        float t = (float) elapsed / duration;
        return t * t * (3 - 2 * t);
    }
}
