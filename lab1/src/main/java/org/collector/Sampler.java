package org.collector;

import java.util.Random;

public final class Sampler {
    private static final int MIN = 1;
    private static final int MAX = 1023;
    private static final double EXPONENT = 1.15;
    private static final double[] CDF = new double[MAX + 1];

    static {
        var sum = 0.0;

        for (var k = MIN; k <= MAX; k++) {
            sum += 1.0 / Math.pow(k, EXPONENT);
            CDF[k] = sum;
        }

        for (var k = MIN; k <= MAX; k++) {
            CDF[k] /= sum;
        }
    }

    public static long[] generate(int n, long seed) {
        var rng = new Random(seed);
        var result = new long[n];
        for (var i = 0; i < n; i++) {
            result[i] = sample(rng.nextDouble());
        }
        return result;
    }

    private static long sample(double u) {
        var lo = MIN;
        var hi = MAX;

        while (lo < hi) {
            var mid = (lo + hi) / 2;
            if (CDF[mid] < u) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        return lo;
    }
}
