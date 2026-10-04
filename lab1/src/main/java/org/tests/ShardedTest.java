package org.tests;

import org.collector.Sampler;
import org.collector.ShardedCollector;
import org.collector.Utils;

public class ShardedTest {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var result = Utils.testCollector(new ShardedCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
