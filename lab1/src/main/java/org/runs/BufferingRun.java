package org.runs;

import org.collector.BufferingCollector;
import org.collector.Sampler;
import org.collector.Utils;

public class BufferingRun {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var measurement = Utils.measurePoint(new BufferingCollector(),  values, nThreads);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }

        var result = Utils.testCollector(new BufferingCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
