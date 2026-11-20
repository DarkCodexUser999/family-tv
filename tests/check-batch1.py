"""Executable production-method tests with host Android fakes, plus labeled source checks.
Not Android instrumentation: framework lifecycle/rendering remain device gates.
"""
from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[1];build=root/'launcher/build/batch1-check';build.mkdir(parents=True,exist_ok=True)
stubs={
'android/content/ComponentName.java': 'package android.content; public class ComponentName { public final String pkg,name; public ComponentName(String p,String n){pkg=p;name=n;} public String flattenToShortString(){return pkg+"/"+name;} }',
'android/content/Intent.java': '''package android.content; import java.util.*; public class Intent {
 public static final String ACTION_MAIN="android.intent.action.MAIN",CATEGORY_LEANBACK_LAUNCHER="android.intent.category.LEANBACK_LAUNCHER",CATEGORY_LAUNCHER="android.intent.category.LAUNCHER";
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=0x00200000;
 public String action; public Set<String> categories=new HashSet<>(); public ComponentName component; public int flags;
 public Intent(String a){action=a;} public Intent addCategory(String c){categories.add(c);return this;}
 public Intent setComponent(ComponentName c){component=c;return this;} public ComponentName getComponent(){return component;}
 public Intent addFlags(int f){if(component==null)throw new AssertionError("Flags before final component");flags|=f;return this;}
 public ComponentName resolveActivity(android.content.pm.PackageManager p){return p.component;}}
''',
'android/content/ActivityNotFoundException.java':'package android.content; public class ActivityNotFoundException extends RuntimeException {}',
'android/content/Context.java':'package android.content; public class Context {public android.content.res.AssetManager assets=new android.content.res.AssetManager(); public android.content.pm.PackageManager pm=new android.content.pm.PackageManager(); public android.content.res.AssetManager getAssets(){return assets;} public android.content.pm.PackageManager getPackageManager(){return pm;} public String getPackageName(){return "family.tv";}}',
'android/content/res/AssetManager.java':'''package android.content.res; import java.io.*; public class AssetManager {public String data=""; public boolean fail; public int failAt=-1; public InputStream open(String name)throws IOException {if(fail)throw new IOException(); final byte[] b=data.getBytes("UTF-8");return new InputStream(){int n;public int read()throws IOException {if(n==failAt)throw new IOException();return n<b.length?b[n++]:-1;}};}}''',
'android/content/pm/PackageManager.java':'''package android.content.pm; import java.util.*; public class PackageManager {public static final int PERMISSION_GRANTED=0; public boolean fail; public android.content.ComponentName component;public java.util.List<ResolveInfo> entries=new ArrayList<>();public List<ResolveInfo> queryIntentActivities(android.content.Intent i,int f){if(fail)throw new SecurityException();return entries;}public int checkPermission(String p,String n){return 0;}}''',
'android/content/pm/ResolveInfo.java':'package android.content.pm; public class ResolveInfo {public ActivityInfo activityInfo;}',
'android/content/pm/ActivityInfo.java':'package android.content.pm; public class ActivityInfo {public String packageName,name,permission;public boolean exported=true,enabled=true;public ApplicationInfo applicationInfo=new ApplicationInfo();}',
'android/content/pm/ApplicationInfo.java':'package android.content.pm; public class ApplicationInfo {public boolean enabled=true;}',
'android/util/Log.java':'package android.util; public class Log {public static int i(String a,String b){return 0;}public static int w(String a,String b){return 0;}public static int w(String a,String b,Throwable t){return 0;}}',
'android/provider/Settings.java':'package android.provider;public class Settings{public static final String ACTION_SETTINGS="android.settings.SETTINGS";}',
'android/widget/Toast.java':'package android.widget;public class Toast{public static int shown;public static final int LENGTH_SHORT=0;public static Toast makeText(Object c,String s,int n){shown++;return new Toast();}public void show(){}}',
'android/os/Environment.java':'package android.os;public class Environment{public static java.io.File root;public static boolean denied;public static java.io.File getExternalStorageDirectory(){if(denied)throw new SecurityException();return root;}}',
'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap{public enum Config{ARGB_8888,RGB_565}}',
'android/graphics/BitmapFactory.java':'''package android.graphics;public class BitmapFactory{public static int mode,sample;public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;public Bitmap.Config inPreferredConfig;}public static Bitmap decodeFile(String p,Options o){if(mode==1)throw new SecurityException();if(o.inJustDecodeBounds){o.outWidth=mode==2?-1:4000;o.outHeight=mode==2?-1:2000;return null;}sample=o.inSampleSize;return mode==3?null:new Bitmap();}}'''}
for name,text in stubs.items():
 f=build/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
base=root/'launcher/src/main/java/app/firetvremote/launcher';activity=(base/'LauncherActivity.java').read_text()
def method(signature, source=None):
 text=activity if source is None else source
 a=text.index(signature);brace=text.index('{',a);depth=1;i=brace+1
 while depth:
  if text[i]=='{':depth+=1
  if text[i]=='}':depth-=1
  i+=1
 return text[a:i]

harness=r'''package app.firetvremote.launcher;import android.content.*;import android.widget.Toast;import android.util.Log;import java.util.*;
public class ActivityHarness extends Context {
 static final String LOG_TAG="test";Object pendingTile;boolean settingsOpening,isSettingsLaunchPaused,finishing,destroyed;int started,cleared,rejection;
 final Scene scene=new Scene();final Gear gear=new Gear();Dialog remoteDialog;final Runnable launchSettings=()->dispatchFireSettings();
 static class Scene{final Queue<Runnable> jobs=new ArrayDeque<>();void post(Runnable r){jobs.add(r);}void postOnAnimation(Runnable r){jobs.add(r);}void drain(){while(!jobs.isEmpty())jobs.poll().run();}}
 static class Gear{int focused;void requestFocus(){focused++;}}static class Dialog{boolean dismissed;void dismiss(){dismissed=true;}}
 boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}void clearPending(){pendingTile=null;cleared++;}
 void startActivity(Intent i){if(rejection==1)throw new ActivityNotFoundException();if(rejection==2)throw new SecurityException();started++;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 ActivityHarness h=new ActivityHarness();h.pm.component=new ComponentName("settings.pkg","Settings");h.remoteDialog=new Dialog();
 h.openFireSettings();h.openFireSettings();check(h.remoteDialog.dismissed&&Toast.shown==1);h.scene.drain();check(h.started==1&&h.gear.focused==1);
 h.rejection=1;h.pendingTile=new Object();h.openIntent("removed app",new Intent(Intent.ACTION_MAIN));check(h.pendingTile==null&&h.cleared==1&&h.started==1);
 h.rejection=2;h.openIntent("denied app",new Intent(Intent.ACTION_MAIN));check(h.cleared==2);
 h.openFireSettings();h.isSettingsLaunchPaused=true;h.scene.drain();check(h.started==1);
 h.isSettingsLaunchPaused=false;h.pm.component=null;h.openFireSettings();h.scene.drain();check(!h.settingsOpening&&h.cleared==3);
 h.destroyed=true;h.openFireSettings();h.scene.drain();check(h.started==1);
 System.out.println("PASS: executable production Settings/dispatch methods: duplicate OK, dismissal, focus, paused/destroyed, missing handler, removed/denied app recovery (host fakes)");
 }
'''
harness+='\n'.join(method(s) for s in ['private void openFireSettings()','private void dispatchFireSettings()','private void openIntent('])+'\n}'
(build/'ActivityHarness.java').write_text(harness)
(build/'RecoveryTest.java').write_text(r'''package app.firetvremote.launcher;import android.content.*;import android.os.Environment;import android.graphics.*;import java.nio.file.*;import java.util.*;
public class RecoveryTest{static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[] a)throws Exception{
 Context c=new Context();c.assets.data="com.custom.hidden\n";check(AppCatalog.hiddenPackages(c).equals(new HashSet<>(Arrays.asList("com.custom.hidden"))));
 c.assets.fail=true;Set<String> defaults=AppCatalog.hiddenPackages(c);check(defaults.contains("com.onemainstream.sonyliv.android")&&defaults.size()==11);
 c.assets.fail=false;c.assets.failAt=c.assets.data.length();Set<String> partial=AppCatalog.hiddenPackages(c);check(partial.containsAll(defaults)&&partial.contains("com.custom.hidden"));
 c.pm.fail=true;try{AppCatalog.list(c);throw new AssertionError();}catch(SecurityException expected){}
 Environment.root=Files.createTempDirectory(Paths.get(a[0]),"art-").toFile();check(ArtworkFiles.tile("app.test")==null);
 Path dir=Environment.root.toPath().resolve("FamilyTV/backdrops");Files.createDirectories(dir);Files.write(dir.resolve("app.test.png"),new byte[]{1});check(ArtworkFiles.backdrop("app.test").getName().endsWith(".png"));
 Files.write(dir.resolve("app.test.jpg"),new byte[]{1});check(ArtworkFiles.backdrop("app.test").getName().endsWith(".jpg"));
 Environment.denied=true;check(ArtworkFiles.tile("app.test")==null&&ArtworkFiles.backdrop("app.test")==null);Environment.denied=false;
 java.io.File file=dir.resolve("app.test.jpg").toFile();for(int mode=1;mode<=3;mode++){BitmapFactory.mode=mode;check(ArtworkFiles.decode(file,640,240,Bitmap.Config.RGB_565)==null);}
 BitmapFactory.mode=0;check(ArtworkFiles.decode(file,640,240,Bitmap.Config.RGB_565)!=null&&BitmapFactory.sample==8);
 for(String pkg:Arrays.asList("app.default","com.amazon.cloud9","in.startv.hotstar")){
 Set<String> own=new HashSet<>(Arrays.asList("com.amazon.cloud9","in.startv.hotstar"));LaunchRoute route=LaunchRoute.build(pkg,"Chosen","leanback",own);Intent intent=route.intent();
 check(intent.component.pkg.equals(pkg)&&intent.component.name.equals("Chosen")&&intent.categories.contains("leanback"));
 check(intent.flags==(own.contains(pkg)?0x10200000:0));}
 System.out.println("PASS: executable production hide recovery (complete/partial), artwork lookup/decode failures/priority and Android intent construction (host fakes)");}}
''')
shelf_harness = r'''package app.firetvremote.launcher;import android.graphics.Bitmap;import java.util.*;import java.util.concurrent.*;
public class ShelfHarness{
 boolean closed,suspended;String requested="bad",displayed="old";Future<?> pending;final List<String> order=new ArrayList<>();
 final ExecutorService worker=Executors.newSingleThreadExecutor();final Image image=new Image();
 static class Image{Bitmap value=new Bitmap();void setImageBitmap(Bitmap b){value=b;}float getAlpha(){return 1;}void setAlpha(float a){}}
 final Map<String,Bitmap> cache=new HashMap<>(); Object trace;void invalidate(){}
 final BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();
 final List<String> loads=Collections.synchronizedList(new ArrayList<>());
 final CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
 Bitmap load(String pkg){loads.add(pkg);if(pkg.equals("slow")){started.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}}return null;}
 void post(Runnable r){ui.add(r);}
 void complete()throws Exception{Runnable r=ui.poll(2,TimeUnit.SECONDS);if(r==null)throw new AssertionError("missing completion");r.run();}
 public static void main(String[] args)throws Exception{ShelfHarness h=new ShelfHarness();try{h.showNow("bad");h.pending.get(2,TimeUnit.SECONDS);h.complete();if(h.image.value!=null||!"bad".equals(h.displayed))throw new AssertionError();
 h.showNow(h.requested="slow");if(!h.started.await(2,TimeUnit.SECONDS))throw new AssertionError();
 h.showNow(h.requested="skipped");h.showNow(h.requested="newest");
 h.release.countDown();h.complete();h.complete();
 if(!h.loads.equals(Arrays.asList("bad","slow","newest"))||!"newest".equals(h.displayed))throw new AssertionError();
 Bitmap warm=new Bitmap();h.cache.put("warm",warm);h.showNow(h.requested="warm");
 if(h.image.value!=warm||h.pending!=null)throw new AssertionError();
 System.out.println("PASS: shelf failure clears stale art; latest-only blocked decode and immediate cache hit");}finally{h.release.countDown();h.worker.shutdownNow();}}
''' + method('private void showNow(', (base/'TopShelf.java').read_text()) + method('private void present(', (base/'TopShelf.java').read_text()) + '\n}'
(build/'ShelfHarness.java').write_text(shelf_harness)
java=Path(os.environ['JAVA_HOME'])/'bin'
sources=[p for p in build.rglob('*.java') if not p.name.startswith('._')]+[base/(n+'.java') for n in ['LaunchRoute','AppCatalog','ArtworkFiles']]+[root/'tests/LaunchRouteTest.java']
subprocess.run([str(java/'javac'),'--release','8','-d',str(build),*[str(p) for p in sources]],check=True)
for cls in ['LaunchRouteTest','RecoveryTest','ActivityHarness','ShelfHarness']:
 subprocess.run([str(java/'java'),'-cp',str(build),'app.firetvremote.launcher.'+cls,str(build)],check=True)
# Explicitly source checks: these prove wiring, not Android lifecycle/rendering.
assert 'gear.setOnClickListener(v -> showOptions())' in activity
assert 'KEYCODE_MENU' in activity and 'showOptions();' in method('public boolean onKeyUp(')
assert 'SETTINGS_KEY.equals(key) ? gear' in method('private void restoreFocus(')
assert 'state.putString("last-focus", focusedKey)' in method('protected void onSaveInstanceState(')
assert 'scene.removeCallbacks(launchSettings)' in method('protected void onPause(')
assert 'scene.removeCallbacks(launchSettings)' in method('protected void onDestroy(')
assert 'catalogFailed = true;' in activity and 'if (catalogFailed) options.add("Retry loading apps")' in activity and 'if (choice == 6) { recreate(); return; }' in activity
assert 'if (pendingTile != null) return;' in activity and 'Loading…' in activity
for action in ['"pair"','"revoke"','paired-phones','Remote connection details']:assert action in activity
shelf=(base/'TopShelf.java').read_text()
assert 'image.setImageBitmap(art)' in shelf and '|| art == null' not in shelf
assert 'README' not in shelf and '.mkdirs()' not in shelf
print('PASS: SOURCE checks: menu/retry/focus/lifecycle/loading/LAN wiring and null-art clearing; actual Android UI remains UNVERIFIED')