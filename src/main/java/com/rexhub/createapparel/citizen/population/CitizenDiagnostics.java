package com.rexhub.createapparel.citizen.population;

public final class CitizenDiagnostics {
    private static final boolean TEST_WORLD = Boolean.getBoolean("apparel.diagnostics.testWorld");
    public static final boolean debugDisableCitizenAutonomy = flag("debugDisableCitizenAutonomy");
    public static final boolean debugDisableCitizenBehaviorGoal = flag("debugDisableCitizenBehaviorGoal");
    public static final boolean debugDisableCitizenPopulationTick = flag("debugDisableCitizenPopulationTick");
    public static final boolean debugDisableCitizenRoutePlanning = flag("debugDisableCitizenRoutePlanning");
    private CitizenDiagnostics() { }
    private static boolean flag(String name) {
        boolean requested = Boolean.getBoolean("apparel.diagnostics." + name);
        if (requested && !TEST_WORLD) throw new IllegalStateException("Server diagnostic switches require -Dapparel.diagnostics.testWorld=true in an isolated test world");
        if (requested) com.mojang.logging.LogUtils.getLogger().warn("DIAGNOSTIC ONLY: {} enabled; use an isolated world copy", name);
        return requested;
    }
}
