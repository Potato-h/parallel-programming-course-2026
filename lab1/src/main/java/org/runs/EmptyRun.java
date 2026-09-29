package org.runs;

import org.collector.*;

public class EmptyRun {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var collector = new EmptyCollector();
            var measurement = Utils.measurePoint(collector,  values, nThreads);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }
    }
}
