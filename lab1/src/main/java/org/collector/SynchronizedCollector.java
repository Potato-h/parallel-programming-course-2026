package org.collector;

public class SynchronizedCollector implements MetricsCollector {
    private final SingleThreadedCollector collector;

    public SynchronizedCollector(SingleThreadedCollector collector) {
        this.collector = collector;
    }

    @Override
    public synchronized void record(long value) {
        collector.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return collector.snapshot();
    }
}
