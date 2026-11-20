"""Exercise the real debug timestamp collector; these are synthetic clock values, not timings."""
from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parents[1]
build=root/'launcher/build/focus-trace-check'
build.mkdir(parents=True,exist_ok=True)
sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}',
'android/util/Log.java':'package android.util;public class Log {public static java.util.List<String> lines=new java.util.ArrayList<>();public static int d(String tag,String line){lines.add(line);return 0;}}',
'android/view/KeyEvent.java':'package android.view;public class KeyEvent {public long time;public int key,repeat;public long getEventTime(){return time;}public int getKeyCode(){return key;}public int getRepeatCount(){return repeat;}}',
'TraceTest.java':'''package app.firetvremote.launcher;
import android.os.SystemClock;import android.util.Log;import android.view.KeyEvent;
public class TraceTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 FocusTrace trace=new FocusTrace();trace.select("app.test");trace.draw("app.test",true);check(Log.lines.isEmpty());
 KeyEvent e=new KeyEvent();e.time=100;e.key=22;trace.down(e);trace.select("app.test");
 SystemClock.now=105;trace.draw("other.app",true);check(Log.lines.isEmpty());trace.draw("app.test",true);
 SystemClock.now=120;trace.draw("app.test",false);trace.draw("app.test",false);trace.draw("app.test",true);
 check(Log.lines.size()==2&&Log.lines.get(1).contains("id=1 key=22 repeat=0 outlineMs=5 artMs=20 ringToArtMs=15"));
 e.time=200;e.key=20;e.repeat=3;trace.down(e);trace.select("second.app");SystemClock.now=202;trace.draw("app.test",true);
 check(Log.lines.size()==2);trace.draw("second.app",false);check(Log.lines.get(2).contains("id=2 key=20 repeat=3 outlineMs=-1 artMs=2 ringToArtMs=-1"));
 SystemClock.now=204;trace.draw("second.app",true);check(Log.lines.get(3).contains("outlineMs=4 artMs=2 ringToArtMs=0"));
 System.out.println("PASS: debug trace IDs, matching draws, repeat metadata, missing/superseded samples and synthetic clock deltas (not device performance)");
 }}'''}
paths=[]
for name,source in sources.items():
 p=build/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);paths.append(str(p))
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac'),'--release','8','-d',str(build),*paths,str(root/'launcher/src/main/java/app/firetvremote/launcher/FocusTrace.java')],check=True)
subprocess.run([str(java/'java'),'-cp',str(build),'app.firetvremote.launcher.TraceTest'],check=True)
