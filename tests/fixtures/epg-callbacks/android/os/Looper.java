package android.os;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test-only callback queue. This is not Android runtime or UI evidence. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    private final Thread owner = Thread.currentThread();
    final LinkedBlockingQueue<Posted> queue = new LinkedBlockingQueue<>();
    static final class Posted {
        final Handler handler;
        final Runnable task;
        Posted(Handler handler, Runnable task) { this.handler = handler; this.task = task; }
    }
    public static Looper getMainLooper() { return MAIN; }
    public static void dispatchOne(long timeoutMillis) throws InterruptedException {
        if (Thread.currentThread() != MAIN.owner) throw new IllegalStateException("Wrong test owner");
        Posted next = MAIN.queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        if (next != null) next.task.run();
    }
}
