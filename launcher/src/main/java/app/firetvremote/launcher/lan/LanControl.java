package app.firetvremote.launcher.lan;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A process-wide FIFO, including teardown of an old service before its replacement starts. */
final class LanControl {
    private final LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
            queue, task -> new Thread(task, "family-tv-lan-state"));

    LanControl() { worker.allowCoreThreadTimeOut(true); }

    synchronized void connectivity(Runnable reconcile) {
        if (queue.peekLast() != reconcile) worker.execute(reconcile);
    }

    synchronized boolean offerPair(Runnable command) {
        if (queue.size() >= 8) return false;
        worker.execute(command);
        return true;
    }

    synchronized void execute(Runnable command) { worker.execute(command); }
    void shutdown() { worker.shutdown(); }
}
