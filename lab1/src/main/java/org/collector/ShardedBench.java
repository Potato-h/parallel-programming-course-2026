package org.collector;

public class ShardedBench {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var measurement = Utils.measurePoint(new ShardedCollector(),  values, 1);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }
    }
}
