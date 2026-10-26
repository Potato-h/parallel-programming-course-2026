package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;
        
        public Replay(String trace) { 
            this.trace = trace; 
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) {
                throw new IllegalArgumentException(String.format("trace ended at choice %s", position));
            }
            
            int id = trace.charAt(position++) - 'A';
            if (!enabled.contains(id)) { 
                throw new IllegalArgumentException(String.format("thread %s is not enabled", (char)('A' + id)));
            }

            return id;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        final Random rnd;
        
        public RandomPick(long seed) {
            this.rnd = new Random(seed);
         }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final ArrayList<Decision> path = new ArrayList<>();
        private int position;
        
        public Dfs(int bound) {}
        
        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() { 
            position = 0; 
        }
        
        public int pick(List<Integer> enabled, int last) {
            int chosen;
            if (position < path.size()) {
                chosen = enabled.get(path.get(position).chosenPosition);
            } else {
                path.add(new Decision(enabled.size(), 0));
                chosen = enabled.get(0);
            }

            position++;
            return chosen;
        }
        
        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (path.size() > 0) {
                var nextIdx = path.getLast().chosenPosition + 1;
                var options = path.getLast().options;
                if (nextIdx < options) {
                    path.set(path.size() - 1, new Decision(options, nextIdx));
                    return true;
                } else {
                    path.removeLast();
                }
            }

            return false;
        }
        
        private record Decision(int options, int chosenPosition) { }
    }
}
