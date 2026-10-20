package com.rexhub.createapparel.citizen.population;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

public final class CitizenSystemMetrics {
    private static final ThreadLocal<CitizenSystemMetrics> CURRENT = new ThreadLocal<>();
    private static volatile int activeProfiles;
    private final DiagnosticTiming timing = new DiagnosticTiming();
    private final java.util.Set<java.util.UUID> seenCitizens = new java.util.HashSet<>();
    private final long[] ticks = new long[4096];
    private boolean enabled;
    private long samples, totalNanos, populationNanos, packets, bytes, tickStarted;
    private long gcCount, gcMillis;
    public boolean enabled() { return enabled; }
    public static boolean profiling() { return activeProfiles > 0; }
    public long packetCount() { return packets; }
    public void start() {
        timing.start();
        if (!enabled) activeProfiles++;
        seenCitizens.clear();
        enabled = true; samples = totalNanos = populationNanos = packets = bytes = tickStarted = 0;
        Arrays.fill(ticks, 0); gcCount = gcCount(); gcMillis = gcMillis();
    }
    public void stop() { timing.stop(); if (enabled) activeProfiles--; enabled = false; }
    public DiagnosticTiming timing() { return timing; }
    public static DiagnosticTiming.Scope measure(net.minecraft.world.level.Level level, String section) {
        if (activeProfiles == 0 || !(level instanceof net.minecraft.server.level.ServerLevel server) || !server.getServer().isSameThread()) return DiagnosticTiming.NONE;
        var metrics = CitizenPopulationManager.get(server).metrics();
        if (!metrics.enabled) return DiagnosticTiming.NONE;
        var previous = CURRENT.get(); CURRENT.set(metrics); var scope = metrics.timing.measure(section);
        return () -> { try { scope.close(); } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); } };
    }
    public static DiagnosticTiming.Scope measure(String section) {
        if (activeProfiles == 0) return DiagnosticTiming.NONE;
        var current = CURRENT.get(); return current == null ? DiagnosticTiming.NONE : current.timing.measure(section);
    }
    public static void citizen(java.util.UUID id) {
        if (activeProfiles == 0) return;
        var current = CURRENT.get(); if (current == null) return;
        current.timing.count("citizenTicks", 1); if (current.seenCitizens.add(id)) current.timing.count("distinctCitizens", 1);
    }
    public void beginTick() { if (enabled) tickStarted = System.nanoTime(); }
    public void endTick() {
        if (!enabled || tickStarted == 0) return;
        long elapsed = System.nanoTime() - tickStarted;
        ticks[(int) (samples % ticks.length)] = elapsed; samples++; totalNanos += elapsed; tickStarted = 0;
        timing.interval();
    }
    public void population(long nanos) { if (enabled) populationNanos += nanos; }
    public void packet(int size) { if (enabled) { packets++; bytes += size; } }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
    public String summary() {
        var sorted = Arrays.copyOf(ticks, (int) Math.min(samples, ticks.length)); Arrays.sort(sorted);
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return String.format(Locale.ROOT, "profile=%s ticks=%d MSPT_mean=%.3f p95=%.3f p99=%.3f population_ms/tick=%.3f gauge_packets=%d gauge_bytes=%d heap_used=%d GC_count=%d GC_ms=%d (percentiles: last %d ticks)",
                enabled, samples, samples == 0 ? 0 : totalNanos / 1e6 / samples, percentile(sorted, .95), percentile(sorted, .99),
                samples == 0 ? 0 : populationNanos / 1e6 / samples, packets, bytes, used, gcCount() - gcCount, gcMillis() - gcMillis, sorted.length)
                + "\n" + timing.report("serverTick");
    }
    private static double percentile(long[] values, double quantile) { return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * quantile) - 1] / 1e6; }
}
