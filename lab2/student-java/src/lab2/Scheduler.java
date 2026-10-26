package lab2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    final ArrayList<SchedLock> locks = new ArrayList<>();

    static final ThreadLocal<SchedulerRef> LOCAL = new ThreadLocal<>();

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        var threadSem = new Semaphore(0);
        var threadId = threads.size();
        var state = new ThreadState(
            new Thread(() -> {
                LOCAL.set(new SchedulerRef(this, threadId));
                
                try {
                    threadSem.acquire();
                    body.run();
                    turn(new Finished());
                } catch (Throwable ignored) {}
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
                    case WaitingLock(var lock) -> lock.owner == -1;
                    case Awaiting(var cond) -> cond.getAsBoolean();
                })
                .mapToObj(Integer::valueOf)
                .toList();   

            if (enabled.isEmpty()) {
                var lockStatus = threads.stream()
                    .flatMap(thread -> switch (thread.status) {
                        case WaitingLock(var lock) when lock.owner != -1 -> Stream.of(lock);
                        default -> Stream.empty();
                    })
                    .sorted(Comparator.comparing(lock -> lock.name))
                    .map(lock -> String.format("%s:%s", lock.name, threads.get(lock.owner).name))
                    .collect(Collectors.joining(","));
                
                if (!lockStatus.isBlank()) {
                    deadlock = String.format("held={%s}", lockStatus);
                    
                    for (var thread : threads) {
                        if (!(thread.status instanceof Finished)) {
                            thread.thread.interrupt();
                        }
                    }
                }

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
        
        if (!state.firstPause) {
            state.status = status;
            scheduler.schedule.release();

            if (status instanceof Finished) {
                return;
            }

            try {
                state.semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        state.firstPause = false;
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        addLog("window");
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
        boolean firstPause;
    
        ThreadState(Thread thread, char name, Semaphore semaphore, Status status) {
            this.thread = thread;
            this.name = name;
            this.semaphore = semaphore;
            this.status = status;
            this.firstPause = true;
        }
    }
    
    sealed interface Status permits Finished, Paused, WaitingLock, Awaiting 
    {}
    
    record Finished() implements Status {}
    record Paused() implements Status {}
    record WaitingLock(SchedLock lock) implements Status {}
    record Awaiting(BooleanSupplier cond) implements Status {} 
}
