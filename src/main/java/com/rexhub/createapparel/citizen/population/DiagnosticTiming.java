package com.rexhub.createapparel.citizen.population;

import java.util.*;
import java.util.function.LongSupplier;

public final class DiagnosticTiming {
    public static final Scope NONE = () -> { };
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final int WINDOW = 4096;
    private final LongSupplier clock;
    private final Map<String, Sample> samples = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private boolean enabled;
    private long intervals;
    private Thread owner;
    public DiagnosticTiming() { this(System::nanoTime); }
    public DiagnosticTiming(LongSupplier clock) { this.clock = clock; }
    public boolean enabled() { return enabled && owner == Thread.currentThread(); }
    public void start() {
        checkThread();
        if (!stack.isEmpty()) throw new IllegalStateException("Cannot restart inside a measured scope");
        owner = Thread.currentThread(); samples.clear(); counters.clear(); intervals = 0; enabled = true;
    }
    public void stop() { checkThread(); enabled = false; }
    private void checkThread() {
        if (owner != null && owner != Thread.currentThread()) throw new IllegalStateException("Diagnostic collector is thread confined");
    }
    public Scope measure(String section) {
        if (!enabled()) return NONE;
        var sample = samples.computeIfAbsent(section, ignored -> new Sample());
        var frame = new Frame(sample, clock.getAsLong()); stack.push(frame); return frame;
    }
    public void count(String key, long amount) { if (enabled()) counters.merge(key, amount, Long::sum); }
    public void interval() {
        if (!enabled()) return;
        int index = (int) (intervals++ % WINDOW);
        for (var sample : samples.values()) { sample.intervals[index] = sample.intervalTotal; sample.intervalTotal = 0; }
    }
    public long intervals() { return intervals; }
    public long counter(String key) { checkThread(); return counters.getOrDefault(key, 0L); }
    public record Result(long calls, long inclusive, long exclusive, long maximum, long p95, long p99) { }
    public Result result(String section) {
        checkThread(); var sample = samples.get(section); return sample == null ? new Result(0, 0, 0, 0, 0, 0) : sample.result();
    }
    public String report(String intervalName) {
        checkThread(); var output = new StringBuilder("intervals=" + intervals + " unit=" + intervalName + " quantiles=last4096_calls; inclusive_times_overlap; exclusive=subtract_instrumented_children_only\n");
        samples.forEach((name, sample) -> {
            var result = sample.result();
            var intervalSamples = Arrays.copyOf(sample.intervals, (int) Math.min(intervals, WINDOW)); Arrays.sort(intervalSamples);
            output.append(String.format(Locale.ROOT, "%s calls=%d totalMs=%.6f exclusiveMs=%.6f meanMs=%.6f maxMs=%.6f ms/%s=%.6f p95Ms=%.6f p99Ms=%.6f%n",
                    name, result.calls, result.inclusive / 1e6, result.exclusive / 1e6, result.inclusive / 1e6 / result.calls,
                    result.maximum / 1e6, intervalName, intervals == 0 ? 0 : result.inclusive / 1e6 / intervals, result.p95 / 1e6, result.p99 / 1e6));
            output.append(String.format(Locale.ROOT, "  perIntervalP95Ms=%.6f perIntervalP99Ms=%.6f exclusiveMs/%s=%.6f%n",
                    quantile(intervalSamples, .95) / 1e6, quantile(intervalSamples, .99) / 1e6, intervalName, intervals == 0 ? 0 : result.exclusive / 1e6 / intervals));
        });
        output.append("counters=").append(counters); return output.toString();
    }
    private final class Frame implements Scope {
        private final Sample sample;
        private final long started;
        private long children;
        private boolean closed;
        private Frame(Sample sample, long started) { this.sample = sample; this.started = started; }
        @Override public void close() {
            checkThread(); if (closed) return;
            if (stack.peek() != this) throw new IllegalStateException("Diagnostic scopes must close in nesting order");
            long elapsed = Math.max(0, clock.getAsLong() - started); stack.pop(); closed = true;
            sample.add(elapsed, Math.max(0, elapsed - children)); if (!stack.isEmpty()) stack.peek().children += elapsed;
        }
    }
    private static final class Sample {
        private final long[] recent = new long[WINDOW];
        private final long[] intervals = new long[WINDOW];
        private long calls, inclusive, exclusive, maximum, intervalTotal;
        private void add(long elapsed, long self) {
            recent[(int) (calls++ % WINDOW)] = elapsed; inclusive += elapsed; intervalTotal += elapsed; exclusive += self; maximum = Math.max(maximum, elapsed);
        }
        private Result result() {
            var sorted = Arrays.copyOf(recent, (int) Math.min(calls, WINDOW)); Arrays.sort(sorted);
            return new Result(calls, inclusive, exclusive, maximum, quantile(sorted, .95), quantile(sorted, .99));
        }
    }
    public static long quantile(long[] sorted, double fraction) { return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * fraction) - 1]; }
}
