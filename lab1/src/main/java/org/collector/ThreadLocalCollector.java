package org.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.LongStream;

public class ThreadLocalCollector implements MetricsCollector {
    private static final int BUCKETS_NUM = 256;
    private final ThreadLocal<ThreadState> state;
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    public ThreadLocalCollector() {
        this.state = ThreadLocal.withInitial(() -> {
            var s = new ThreadState();
            synchronized (listLock) {
                allStates.add(s);
            }
            return s;
        });
    }

    @Override
    public void record(long value) {
        var state = this.state.get();
        var bucket = (int)Math.min(value, BUCKETS_NUM - 1);
        state.buckets.setRelease(bucket, state.buckets.getPlain(bucket) + 1);
        state.sum.setRelease(state.sum.getPlain() + value);

        if (state.min.getPlain() > value) {
            state.min.setRelease(value);
        }

        if (state.max.getPlain() < value) {
            state.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        var copyOfStates = new ArrayList<ThreadState>();
        synchronized (listLock) {
            copyOfStates.addAll(allStates);
        }

        var buckets = new long[BUCKETS_NUM];
        var sum = 0L;
        var min = Long.MAX_VALUE;
        var max = Long.MIN_VALUE;
        for (var state : copyOfStates) {
            for (var i = 0; i < BUCKETS_NUM; i++) {
                buckets[i] += state.buckets.get(i);
            }

            sum += state.sum.get();
            min = Math.min(min, state.min.get());
            max = Math.max(max, state.max.get());
        }


        return new Snapshot(
            buckets,
            LongStream.of(buckets).sum(),
            sum,
            min,
            max,
            Utils.percentile(buckets, 0.5),
            Utils.percentile(buckets, 0.99)
        );
    }

    private static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(BUCKETS_NUM);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }
}
