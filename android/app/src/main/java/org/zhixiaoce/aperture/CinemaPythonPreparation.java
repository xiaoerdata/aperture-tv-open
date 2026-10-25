package org.zhixiaoce.aperture;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One process-wide, bounded preparation of built-in Python modules, never user code. */
final class CinemaPythonPreparation {
    static final long TIMEOUT_MILLIS = 60_000;
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    interface Guard { void check() throws IOException; }
    private static final class Ready {
        final Object module;
        final long finished;
        Ready(Object module) { this.module = module; finished = System.nanoTime(); }
    }

    private final long timeoutNanos;
    private FutureTask<Ready> task;
    private long started;
    private IOException failure;

    CinemaPythonPreparation() { this(TIMEOUT_MILLIS); }
    // A shorter deadline lets JVM tests exercise the actual coordination without waiting a minute.
    CinemaPythonPreparation(long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis");
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    Object await(Callable<Object> builtins, Guard guard) throws IOException {
        guard.check();
        final FutureTask<Ready> shared;
        synchronized (this) {
            guard.check();
            if (failure != null) throw failure;
            if (task == null) {
                started = System.nanoTime();
                task = new FutureTask<>(() -> new Ready(builtins.call()));
                Thread thread = new Thread(task, "cinema-python-prepare");
                thread.setDaemon(true);
                thread.start();
            }
            shared = task;
        }
        while (true) {
            guard.check();
            synchronized (this) { if (failure != null) throw failure; }
            long remaining = timeoutNanos - (System.nanoTime() - started);
            if (remaining <= 0 && !shared.isDone()) throw failed(timeout());
            try {
                Ready ready = shared.get(Math.max(1, Math.min(remaining, POLL_NANOS)), TimeUnit.NANOSECONDS);
                if (ready.finished - started > timeoutNanos) throw failed(timeout());
                // A returning/unauthorized caller must never continue into plugin init or business code.
                guard.check();
                synchronized (this) { if (failure != null) throw failure; }
                return ready.module;
            } catch (TimeoutException waiting) {
                // Only stop this caller on cancellation; never cancel the shared preparation future.
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                throw new IOException("插件操作已取消", cancelled);
            } catch (ExecutionException broken) {
                throw failed(new IOException("Python 内置运行环境准备失败，请退出应用后重试", broken.getCause()));
            }
        }
    }

    private IOException timeout() {
        return new IOException("Python 内置运行环境准备超时，请退出应用后重试");
    }
    private synchronized IOException failed(IOException problem) {
        if (failure == null) failure = problem;
        // Initialization cannot safely restart in a second thread after a native hang/failure.
        // Keep the one future; this state has no effect on JAR or JavaScript runtimes.
        return failure;
    }
}
