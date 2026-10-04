package org.benchmarks;

import org.collector.Sampler;
import org.collector.SingleThreadedCollector;
import org.collector.Utils;

public class SingleThreadedBenchmark {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var measurement = Utils.measurePoint(new SingleThreadedCollector(),  values, 1);
        System.out.printf("%d ops/sec\n", measurement);
    }
}