package org.collector;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

public class ShardedCollector implements MetricsCollector {
    private static final int BUCKETS_NUM = 256;
    private static final int SHARDS_NUM = 16;
    private static final int SHARDS_SIZE = BUCKETS_NUM / SHARDS_NUM;

    private final long[] buckets;
    private final Object[] shards;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public ShardedCollector() {
        this.buckets = new long[BUCKETS_NUM];
        this.shards = new Object[SHARDS_NUM];
        Arrays.setAll(shards, ignored -> new Object());
        this.sum = new AtomicLong(0);
        this.min = new AtomicLong(Long.MAX_VALUE);
        this.max = new AtomicLong(Long.MIN_VALUE);
    }

    @Override
    public void record(long value) {
        var bucket = (int) Math.min(value / 4, BUCKETS_NUM - 1);
        synchronized (shards[bucket % SHARDS_NUM]) {
            buckets[bucket]++;
        }

        sum.addAndGet(value);

        long minValue;
        while ((minValue = min.get()) > value) {
            min.compareAndSet(minValue, value);
        }

        long maxValue;
        while ((maxValue = max.get()) < value) {
            max.compareAndSet(maxValue, value);
        }

    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = new long[BUCKETS_NUM];
        for (var shard = 0; shard < SHARDS_NUM; shard++) {
            var shardStart = shard * SHARDS_SIZE;
            synchronized (shards[shard]) {
                System.arraycopy(buckets, shardStart, bucketsCopy, shardStart, SHARDS_SIZE);
            }
        }

        return new Snapshot(
            bucketsCopy,
            LongStream.of(bucketsCopy).sum(),
            sum.get(),
            min.get(),
            max.get(),
            Utils.percentile(bucketsCopy, 0.5),
            Utils.percentile(bucketsCopy, 0.99)
        );
    }
}
