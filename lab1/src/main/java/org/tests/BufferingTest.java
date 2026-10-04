package org.tests;

import org.collector.BufferingCollector;
import org.collector.Sampler;
import org.collector.Utils;

public class BufferingTest {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var result = Utils.testCollector(new BufferingCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
