package org.runs;

import org.collector.*;

public class ThreadLocalRun {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var measurement = Utils.measurePoint(new ThreadLocalCollector(),  values, nThreads);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }

        var result = Utils.testCollector(new ThreadLocalCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
