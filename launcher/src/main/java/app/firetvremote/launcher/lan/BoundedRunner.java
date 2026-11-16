package app.firetvremote.launcher.lan;

import fi.iki.elonen.NanoHTTPD;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class BoundedRunner implements NanoHTTPD.AsyncRunner {
    private final Set<NanoHTTPD.ClientHandler> clients = new HashSet<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, ServerLimits.CLIENTS,
            15, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), task -> {
                Thread thread = new Thread(task, "family-tv-http");
                thread.setDaemon(true);
                return thread;
            });
    private boolean closed;

    @Override public synchronized void exec(NanoHTTPD.ClientHandler handler) {
        if (closed || clients.size() >= ServerLimits.CLIENTS) { handler.close(); return; }
        clients.add(handler);
        try { executor.execute(handler); }
        catch (RejectedExecutionException error) { clients.remove(handler); handler.close(); }
    }
    @Override public synchronized void closed(NanoHTTPD.ClientHandler handler) { clients.remove(handler); }
    synchronized int active() { return clients.size(); }
    @Override public void closeAll() {
        NanoHTTPD.ClientHandler[] closing;
        synchronized (this) {
            closed = true;
            closing = clients.toArray(new NanoHTTPD.ClientHandler[0]);
            clients.clear();
        }
        executor.shutdownNow();
        for (NanoHTTPD.ClientHandler handler : closing) handler.close();
    }
}
