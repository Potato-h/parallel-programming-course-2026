package org.benchmarks;

import org.collector.*;

public class ThreadLocalBenchmark {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var measurement = Utils.measurePoint(new ThreadLocalCollector(),  values, nThreads);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }
    }
}
