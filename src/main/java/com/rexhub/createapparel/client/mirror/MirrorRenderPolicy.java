package com.rexhub.createapparel.client.mirror;

public final class MirrorRenderPolicy {
    private MirrorRenderPolicy() { }

    public static boolean canRenderReflection(int currentDepth, int recursionDepth) {
        return currentDepth <= recursionDepth;
    }

    public static int reflectionPassLimit(int surfaceLimit, int recursionDepth) {
        return surfaceLimit * (recursionDepth + 1);
    }

    public static final class CaptureBudget {
        private final int limit;
        private final long nanos;
        private int started;

        public CaptureBudget(int limit, long nanos) {
            this.limit = limit;
            this.nanos = nanos;
        }

        public boolean canStart(long elapsed, long estimatedCost) {
            return started < limit && fitsBudget(elapsed, nanos, estimatedCost, started);
        }

        public void start() {
            if (started >= limit) throw new IllegalStateException("Reflection capture limit exceeded");
            started++;
        }

        public int started() { return started; }
    }

    public static boolean canDrawCachedReflection(int viewDepth, long captureFrame, long currentFrame) {
        return viewDepth == 0 || captureFrame == currentFrame;
    }

    public static boolean withinRenderDistance(int viewDepth, double cameraDistance, double viewerDistance, double range) {
        return (viewDepth == 0 ? cameraDistance : viewerDistance) <= range;
    }

    public static int recursiveTextureWidth(int desiredWidth, int currentDepth, int parentWidth) {
        return currentDepth == 0 ? desiredWidth : Math.min(desiredWidth, Math.max(128, parentWidth / 2));
    }

    public static int textureWidth(int windowWidth, double scale, double area, double distance, boolean dynamic, int previous) {
        int maximum = Math.min(2048, Math.max(256, (int) Math.ceil(windowWidth * scale)));
        if (distance < 4) maximum = Math.max(maximum, scale <= 0.25 ? 512 : 1024);
        int desired = dynamic ? (int) Math.ceil(maximum * Math.sqrt(area)) : maximum;
        if (distance < 4 && area > 0.02) desired = Math.max(desired, scale <= 0.25 ? 256 : 512);
        int width = desired > 1024 ? 2048 : desired > 512 ? 1024 : desired > 256 ? 512 : 256;
        if (previous > width && desired > previous * 0.35) return previous;
        return width;
    }

    public static long updateInterval(double area, double distance, boolean moving, int maximumFps, boolean adaptive) {
        int fps = !adaptive || moving || area > 0.12 ? maximumFps
                : area > 0.02 ? Math.max(1, maximumFps / 2) : Math.max(1, maximumFps / 4);
        return 1_000_000_000L / fps;
    }

    public static boolean prominentNearby(double area, double distance) {
        return distance < 8 && area > 0.02;
    }

    public static long nextUpdate(long now, long deadline, long interval) {
        long next = deadline == 0 ? now + interval : deadline + interval;
        return next <= now ? now + interval : next;
    }

    public static boolean updateDue(long now, long deadline, long interval, long frameInterval) {
        return now + Math.min(interval / 4, frameInterval / 2) >= deadline;
    }

    public static boolean fitsBudget(long elapsed, long budget, long estimatedCost, int updated) {
        return updated == 0 || elapsed <= budget && estimatedCost <= budget - elapsed;
    }

    public static int[] scissor(double[] bounds, int width, int height) {
        int x1 = Math.max(0, (int) Math.floor((bounds[0] - 0.03 + 1) * width / 2));
        int y1 = Math.max(0, (int) Math.floor((bounds[1] - 0.03 + 1) * height / 2));
        int x2 = Math.min(width, (int) Math.ceil((bounds[2] + 0.03 + 1) * width / 2));
        int y2 = Math.min(height, (int) Math.ceil((bounds[3] + 0.03 + 1) * height / 2));
        return new int[]{x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)};
    }

    public static boolean reserveBackgroundTurn(long age, long sinceBackground) {
        return age >= 1_000_000_000L && sinceBackground >= 100_000_000L;
    }
}
