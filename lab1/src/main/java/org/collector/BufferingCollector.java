package org.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferingCollector implements MetricsCollector {
    private static final int BUCKETS_NUM = 256;
    private static final int NOWHERE = -1;

    private final ThreadLocal<ThreadBuffers> state;
    private final AtomicInteger active = new AtomicInteger(0);
    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private final Object collectorLock = new Object();

    private final long[] buckets;
    private long count;
    private long sum;
    private long min;
    private long max;

    public BufferingCollector() {
        this.buckets = new long[BUCKETS_NUM];
        this.count = 0;
        this.sum = 0;
        this.min = Long.MAX_VALUE;
        this.max = 0;

        this.state = ThreadLocal.withInitial(() -> {
            var s = new ThreadBuffers();
            synchronized (collectorLock) {
                allStates.add(s);
            }
            return s;
        });
    }

    @Override
    public void record(long value) {
        var state = this.state.get();
        int writeTo;

        while (true) {
            writeTo = active.get();
            state.inside.setRelease(writeTo);
            if (active.get() == writeTo) {
                break;
            }
            state.inside.setRelease(NOWHERE);
        }

        var bucket = (int)Math.min(value / 4, BUCKETS_NUM - 1);
        state.buckets[writeTo][bucket]++;
        state.count[writeTo]++;
        state.sum[writeTo] += value;
        state.min[writeTo] = Math.min(state.min[writeTo], value);
        state.max[writeTo] = Math.max(state.max[writeTo], value);
        state.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (collectorLock) {
            var readFrom = active.getPlain();
            active.setRelease(1 - readFrom);

            for (var state : allStates) {
                while (state.inside.get() == readFrom) {
                    Thread.onSpinWait();
                }
            }

            for (var state : allStates) {
                for (var i = 0; i < BUCKETS_NUM; i++) {
                    buckets[i] += state.buckets[readFrom][i];
                }

                count += state.count[readFrom];
                sum += state.sum[readFrom];
                min = Math.min(min, state.min[readFrom]);
                max = Math.max(max, state.max[readFrom]);

                Arrays.fill(state.buckets[readFrom], 0L);
                state.count[readFrom] = 0;
                state.sum[readFrom] = 0;
                state.min[readFrom] = Long.MAX_VALUE;
                state.max[readFrom] = 0;
            }

            var bucketsCopy = Arrays.copyOf(buckets, BUCKETS_NUM);
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

    private static final class ThreadBuffers {
        final long[][] buckets = new long[2][BUCKETS_NUM];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }
}
