package app.firetvremote.launcher.lan;

/** UI-owned cached values only. Focus changes allocate nothing and never perform I/O. */
public final class LauncherBridge {
    public static final LauncherBridge SHARED = new LauncherBridge();
    public interface Ui { void post(Runnable task); boolean perform(String action); default boolean launch(String pkg) { return false; } default void rowsChanged() {} }
    public interface Signal { void changed(); }
    public volatile boolean tmdbConfigured;
    public volatile ProviderSettingsEndpoint provider;
    public volatile MediaJobs artwork;
    public volatile AppsEndpoint apps;
    public volatile AppsEndpoint management;
    public volatile HiddenEndpoint hidden;
    public volatile RowsEndpoint rows;
    public void rowsChanged(){Ui target=ui;if(target!=null)target.post(target::rowsChanged);}
    private volatile Ui ui;
    private volatile boolean busy;
    private volatile String focused;
    private volatile long epoch;
    private volatile Signal signal;
    public synchronized void listen(Signal value) { signal=value; }
    public synchronized void detach(Signal value) { if(signal==value) signal=null; }
    private void changed() { Signal target=signal; if(target!=null) target.changed(); }
    public void resume(Ui value) { epoch++; busy=false;ui=value;epoch++;changed(); }
    public void pause(Ui value) { if(ui==value) { epoch++; ui=null;busy=false;epoch++;changed(); } }
    public void busy(boolean value) { if(busy!=value) { epoch++; busy=value;epoch++;changed(); } }
    public void busy(Ui source,boolean value){if(ui==source)busy(value);}
    public void focus(String value) { focused=value; changed(); }
    public boolean foreground() { return ui!=null; }
    public boolean canNavigate() { return ui!=null && !busy; }
    public long epoch() { return epoch; }
    public String focused() { return focused; }
    Ui ui() { return ui; }
    boolean perform(String action) { Ui target=ui; return target!=null && !busy && target.perform(action); }
}
