package org.collector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
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

    private static <T> BenchmarkResult<T> runBenchmark(
        MetricsCollector collector,
        long[] values,
        int nThreads,
        Supplier<T> body
    ) {
        var startedCount = collector.snapshot().count();
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
        var payload = body.get();
        stop.set(true);
        var t1 = System.currentTimeMillis();

        for (var worker : workers) {
            ignoreInterrupt(worker::join);
        }

        var passed = (t1 - t0) / 1000.0;
        var totalOps = LongStream.of(ops).sum();
        var finalCount = collector.snapshot().count();
        return new BenchmarkResult<>(
            (long)(totalOps / passed),
            payload,
            finalCount - startedCount,
            totalOps
        );
    }

    private static Supplier<Integer> sleepBody(int secs) {
        return () -> {
            ignoreInterrupt(() -> Thread.sleep(Duration.ofSeconds(secs)));
            return secs;
        };
    }

    public static long measurePoint(MetricsCollector collector, long[] values, int nThreads) {
        runBenchmark(collector, values, nThreads, sleepBody(5));
        var results = new ArrayList<Long>();
        var runs = 5;
        for (int i = 0; i < runs; i++) {
            var result = runBenchmark(collector, values, nThreads, sleepBody(5));
            results.add(result.opsPerSec);
        }
        System.out.printf("Total count: %d\n", collector.snapshot().count());
        var measurements = results.stream().sorted().toList();
        return measurements.get(runs / 2);
    }

    public static BenchmarkResult<TestResult> testCollector(MetricsCollector collector, long[] values, int nThreads) {
        runBenchmark(collector, values, nThreads, sleepBody(3));

        return runBenchmark(collector, values, nThreads, () -> {
           var total = 1_000_000;
           var less = 0L;
           var greater = 0L;

           for (var i = 0; i < total; i++) {
               var snapshot = collector.snapshot();
               var actualCount = LongStream.of(snapshot.buckets()).sum();
               if (snapshot.count() < actualCount) {
                   less++;
               }
               if (snapshot.count() > actualCount) {
                   greater++;
               }
           }

           return new TestResult(total, less, greater);
        });
    }

    public static String testResume(BenchmarkResult<TestResult> result) {
        var test = result.payload;

        return String.format(
            "broken snapshots: %.02f%%, count less than buckets: %d, count more than buckets: %d, final count: %d, actual ops: %d",
            (test.greater + test.less) * 100.0f / test.total,
            test.less,
            test.greater,
            result.finalCount,
            result.allOps
        );
    }

    public record BenchmarkResult<T>(
        long opsPerSec,
        T payload,
        long finalCount,
        long allOps
    ) {}

    public record TestResult(
        long total,
        long less,
        long greater
    ) {}
}
