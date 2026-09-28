package org.collector;

public class SingleThreadedBench {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var measurement = Utils.measurePoint(new SingleThreadedCollector(),  values, 1);
        System.out.printf("%d ops/sec\n", measurement);
    }
}