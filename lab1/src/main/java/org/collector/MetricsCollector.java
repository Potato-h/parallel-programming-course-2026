package org.collector;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}