package org.tests;

import org.collector.BrokenBufferingCollector;
import org.collector.Sampler;
import org.collector.Utils;

public class BrokenBufferingTest {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var result = Utils.testCollector(new BrokenBufferingCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
