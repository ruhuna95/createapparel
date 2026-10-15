package com.rexhub.createapparel.citizen.route;

public final class RouteSettings {
    public static final double DEFAULT_RADIUS = 2;
    public static final double MIN_RADIUS = 0.5;
    public static final double MAX_RADIUS = 16;
    public static final double DEFAULT_VERTICAL_TOLERANCE = 1;
    public static final double MIN_VERTICAL_TOLERANCE = 0;
    public static final double MAX_VERTICAL_TOLERANCE = 8;
    public static final int DEFAULT_SIGNAL_THRESHOLD = 1;
    public static final int MIN_SIGNAL_THRESHOLD = 1;
    public static final int MAX_SIGNAL_THRESHOLD = 15;
    public static final int DISCOVERY_RADIUS = 32;
    public static final int RETRY_INTERVAL = 80;
    public static final int LINK_TIMEOUT = 1200;
    public static final int TEMPORARY_BLOCK_TICKS = 200;
    public static final int MAX_CACHED_ROUTES = 512;
    public static final int MAX_PLANNER_NODES = 4096;
    public static final int MENU_PAGE_SIZE = 8;
    public static final int VIEW_BATCH_SIZE = 128;
    public static final int MAX_VIEW_NODES = 8192;
    public static final int MAX_VIEW_EDGES = 16384;

    private RouteSettings() { }

    public static boolean validRegion(double radius, double tolerance) {
        return Double.isFinite(radius) && radius >= MIN_RADIUS && radius <= MAX_RADIUS
                && Double.isFinite(tolerance) && tolerance >= MIN_VERTICAL_TOLERANCE && tolerance <= MAX_VERTICAL_TOLERANCE;
    }

    public static boolean validSignalThreshold(int threshold) {
        return threshold >= MIN_SIGNAL_THRESHOLD && threshold <= MAX_SIGNAL_THRESHOLD;
    }
}
