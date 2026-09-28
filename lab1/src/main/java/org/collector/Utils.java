package org.collector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

public final class Utils {
    public static int[] THREADS_NUM = new int[] {1, 2, 4, 8, 16};

    public static int percentile(long[] buckets, double part) {
        var threshold = (long)(LongStream.of(buckets).sum() * part);
        var collected = 0L;

        for (int i = 0; i < buckets.length; i++) {
            collected += buckets[i];
            if (collected >= threshold) {
                return i * 4;
            }
        }

        return buckets.length * 4;
    }

    @FunctionalInterface
    private interface InterruptedRunnable {
        void run() throws InterruptedException;
    }

    private static void ignoreInterrupt(InterruptedRunnable runnable) {
        try {
            runnable.run();
        } catch (InterruptedException ignored) {}
    }

    private static long runBenchmark(
        MetricsCollector collector,
        long[] values,
        int nThreads,
        int seconds
    ) {
        var start = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var ops = new long[nThreads];

        var workers = new ArrayList<Thread>();
        for (int i = 0; i < nThreads; i++) {
            var threadId = i;
            var thread = new Thread(() -> {
                var localCount = 0L;
                var j = threadId * 1000;

                ignoreInterrupt(start::await);
                while (!stop.get()) {
                    collector.record(values[j]);
                    localCount++;
                    j++;
                    if (j == values.length) {
                        j = 0;
                    }
                }

                ops[threadId] = localCount;
            });

            thread.start();
            workers.add(thread);
        }

        var t0 = System.currentTimeMillis();
        start.countDown();
        ignoreInterrupt(() -> Thread.sleep(Duration.ofSeconds(seconds)));
        stop.set(true);
        var t1 = System.currentTimeMillis();

        for (var worker : workers) {
            ignoreInterrupt(worker::join);
        }

        var passed = (t1 - t0) / 1000.0;
        return (long)(LongStream.of(ops).sum() / passed);
    }

    public static long measurePoint(MetricsCollector collector, long[] values, int nThreads) {
        runBenchmark(collector, values, nThreads, 5);
        var results = new ArrayList<Long>();
        var runs = 5;
        for (int i = 0; i < runs; i++) {
            results.add(runBenchmark(collector, values, nThreads, 5));
        }
        System.out.printf("Total count: %d\n", collector.snapshot().count());
        var measurements = results.stream().sorted().toList();
        return measurements.get(runs / 2);
    }
}
