package app.firetvremote.launcher.lan;
import java.util.Arrays;
public final class StatusEndpointTest {
 public static void main(String[] args){
  LauncherBridge bridge=new LauncherBridge();String initial=StatusEndpoint.json(bridge);
  if(!initial.contains("\"foreground\":false")||!initial.contains("\"launch\":false")||!initial.contains("\"refreshState\":\"idle\""))throw new AssertionError("initial");
  bridge.apps=new AppsEndpoint(42,Arrays.asList(new AppsEndpoint.App("tv.test","Test",null)));
  LauncherBridge.Ui ui=new LauncherBridge.Ui(){public void post(Runnable r){r.run();}public boolean perform(String a){return true;}};
  bridge.resume(ui);
  if(!StatusEndpoint.json(bridge).contains("\"catalogRevision\":42")||!StatusEndpoint.json(bridge).contains("\"canNavigate\":true"))throw new AssertionError("cached ready");
  bridge.busy(true);if(!StatusEndpoint.json(bridge).contains("\"canNavigate\":false"))throw new AssertionError("busy");
  bridge.pause(ui);bridge.refresh.request("tv.test");String paused=StatusEndpoint.json(bridge);
  if(!paused.contains("phone_required")||!paused.contains("\"foreground\":false")||paused.contains("jobId")||paused.contains("tv.test"))throw new AssertionError("minimal cached snapshot");
  System.out.println("PASS: status cached revision/capabilities/busy/background/job privacy");
 }
}
