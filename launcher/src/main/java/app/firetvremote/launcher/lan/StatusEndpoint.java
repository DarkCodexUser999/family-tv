package app.firetvremote.launcher.lan;

/** Authenticated snapshot of cached values: no package queries, disk or provider requests. */
public final class StatusEndpoint {
    private StatusEndpoint() {}
    public static String json(LauncherBridge bridge) {
        AppsEndpoint apps=bridge.apps;boolean foreground=bridge.foreground(),navigate=foreground&&bridge.canNavigate();
        return "{\"apiVersion\":1,\"enabled\":true,\"foreground\":"+foreground
            +",\"canNavigate\":"+navigate+",\"catalogRevision\":"+(apps==null?0:apps.revision)
            +",\"tmdbConfigured\":"+bridge.tmdbConfigured+",\"refreshState\":\""+bridge.refresh.state()+"\""
            +",\"capabilities\":{\"navigate\":true,\"launch\":"+(apps!=null)
            +",\"rows\":"+(bridge.rows!=null)+",\"hidden\":"+(bridge.hidden!=null)
            +",\"artwork\":"+(bridge.artwork!=null)+",\"refresh\":"+(bridge.provider!=null)+"}}";
    }
}
