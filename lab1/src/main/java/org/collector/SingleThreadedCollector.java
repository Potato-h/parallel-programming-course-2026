package org.collector;

import java.util.Arrays;

public final class SingleThreadedCollector implements MetricsCollector {
    private final long[] buckets;
    private long count;
    private long sum;
    private long min;
    private long max;

    public SingleThreadedCollector() {
        this.buckets = new long[256];
        this.count = 0;
        this.sum = 0;
        this.min = Long.MAX_VALUE;
        this.max = Long.MIN_VALUE;
    }

    @Override
    public void record(long value) {
        var bucket = (int)Math.min(value / 4, 255);
        buckets[bucket]++;
        count++;
        min = Math.min(min, value);
        max = Math.max(max, value);
        sum += value;
    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = Arrays.copyOf(buckets, buckets.length);
        return new Snapshot(
            bucketsCopy,
            count,
            sum,
            min,
            max,
            Utils.percentile(bucketsCopy, 0.5),
            Utils.percentile(bucketsCopy, 0.99)
        );
    }
}
