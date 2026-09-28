import org.collector.Sampler;
import org.collector.ShardedCollector;
import org.collector.ThreadLocalCollector;
import org.collector.Utils;

public class ThreadLocalBench {
    public static void main(String[] args) {
        var values = Sampler.generate(1 << 20, 42);

        for (var nThreads : Utils.THREADS_NUM) {
            var measurement = Utils.measurePoint(new ThreadLocalCollector(),  values, 1);
            System.out.printf("%d threads: %d ops/sec\n", nThreads, measurement);
        }
    }
}
