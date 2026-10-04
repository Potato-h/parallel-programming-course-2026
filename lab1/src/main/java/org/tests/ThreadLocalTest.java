package org.tests;

import org.collector.Sampler;
import org.collector.ThreadLocalCollector;
import org.collector.Utils;

public class ThreadLocalTest {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);
        var result = Utils.testCollector(new ThreadLocalCollector(), values, 4);
        System.out.printf("%s\n", Utils.testResume(result));
    }
}
