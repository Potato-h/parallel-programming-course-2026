package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    final StringBuilder trace = new StringBuilder();

    final Semaphore schedule = new Semaphore(1);

    final ArrayList<ThreadState> threads = new ArrayList<>();

    static final ThreadLocal<SchedulerRef> LOCAL = new ThreadLocal<>();

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        var threadSem = new Semaphore(1);
        threadSem.acquireUninterruptibly();
        var threadId = threads.size();
        var state = new ThreadState(
            new Thread(() -> {
                LOCAL.set(new SchedulerRef(this, threadId));
                threadSem.acquireUninterruptibly();
                body.run();
                turn(new Finished());
            }),
            (char)('A' + threadId),
            threadSem,
            new Paused()
        );
        threads.add(state);
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        schedule.acquireUninterruptibly();
        var last = -1;

        for (var state : threads) {
            state.thread.start();
        }

        while (true) {
            var enabled = IntStream.range(0, threads.size())
                .filter(threadId -> switch (threads.get(threadId).status) {
                    case Finished() -> false;
                    case Paused() -> true;
                    case Awaiting(var cond) -> cond.getAsBoolean();
                })
                .mapToObj(Integer::valueOf)
                .toList();   

            if (enabled.isEmpty()) {
                break;
            }

            var picked = strategy.pick(enabled, last);
            var threadState = threads.get(picked);
            trace.append(threadState.name);
            threadState.semaphore.release();
            last = picked;
            schedule.acquireUninterruptibly();
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    static void turn(Status status) {
        var schedulerRef = LOCAL.get();
        var scheduler = schedulerRef.scheduler;
        var state = scheduler.threads.get(schedulerRef.threadId);
        
        if (state.toPause) {
            state.toPause = false;
            state.status = status;
            scheduler.schedule.release();
            state.semaphore.acquireUninterruptibly();
        }

        state.toPause = true;
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        addLog("window");
        LOCAL.get().scheduler.threads.get(LOCAL.get().threadId).toPause = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        turn(new Awaiting(cond));
    }

    public static void addLog(String s) {
        var local = LOCAL.get();
        var scheduler = local.scheduler;
        
        if (scheduler.logging) {
            var name = scheduler.threads.get(local.threadId).name;
            scheduler.log.add(String.format("%s %s", name, s));
        }
    }

    record SchedulerRef(Scheduler scheduler, int threadId) {}

    private final class ThreadState {
        Thread thread; 
        char name;
        Semaphore semaphore;
        Status status;
        boolean toPause;
    
        ThreadState(Thread thread, char name, Semaphore semaphore, Status status) {
            this.thread = thread;
            this.name = name;
            this.semaphore = semaphore;
            this.status = status;
            this.toPause = false;
        }
    }
    
    sealed interface Status permits Finished, Paused, Awaiting 
    {}
    
    record Finished() implements Status {}
    record Paused() implements Status {}
    record Awaiting(BooleanSupplier cond) implements Status {} 
}
