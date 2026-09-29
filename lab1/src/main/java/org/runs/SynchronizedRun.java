package org.runs;

import org.collector.Sampler;
import org.collector.SingleThreadedCollector;
import org.collector.SynchronizedCollector;
import org.collector.Utils;

public class SynchronizedRun {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var collector = new SynchronizedCollector(new SingleThreadedCollector());
            var measurement = Utils.measurePoint(collector,  values, nThreads);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }

        var collector = new SynchronizedCollector(new SingleThreadedCollector());
        var result = Utils.testCollector(collector, values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
