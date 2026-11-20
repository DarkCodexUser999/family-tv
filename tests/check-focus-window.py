"""Execute the production navigation and idle selection methods against small View fakes."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / "launcher/src/main/java/app/firetvremote/launcher"
activity = (src / "LauncherActivity.java").read_text()

def method(signature):
    start = activity.index(signature)
    opening = activity.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (activity[end] == "{") - (activity[end] == "}")
        end += 1
    return activity[start:end]

methods = ["public boolean dispatchKeyEvent(", "private void select(", "private boolean prepareOne(",
           "private static int windowStart(", "private void pruneRow(", "private void focusFirstFavorite("]
harness = """
package app.firetvremote.launcher;
import java.util.*;
class KeyEvent {
 static final int KEYCODE_DPAD_UP=19, KEYCODE_DPAD_DOWN=20, KEYCODE_DPAD_LEFT=21,
 KEYCODE_DPAD_RIGHT=22, KEYCODE_DPAD_CENTER=23, KEYCODE_ENTER=66, ACTION_DOWN=0;
 int key,action; KeyEvent(int k,int a){key=k;action=a;}
 int getAction(){return action;} int getKeyCode(){return key;} int getRepeatCount(){return 0;}
 long getEventTime(){return 1;} boolean isCanceled(){return false;}
}
class Base {boolean dispatchKeyEvent(KeyEvent e){return false;}}
public class WindowHarness extends Base {
 class Button {
  int row,col; boolean isFocused(){return focused==this;}
  boolean requestFocus(){focused=this;activeRow=row;focusedKey=rowData.get(row).get(col).key;
      rowFocus.remember(row,col);return true;}
  Object getTag(){return col;} void performClick(){clicked=focusedKey; clicks++;}
 }
 class TileButton extends Button {}
 class TileData {String key; Button button;}
 class FrameLayout extends ArrayList<Button>{
  int getChildCount(){return size();} Button getChildAt(int i){return get(i);}
  void removeViewAt(int i){remove(i);}
 }
 class Trace {void down(KeyEvent e){}}
 Trace trace; String clicked,confirmKey,focusedKey; boolean confirmReleased,fastNavigation;
 long lastNavigationDown; int wantedRow=-1,wantedColumn,activeRow,clicks,created;
 RowFocusMemory rowFocus; FrameLayout[] trays=new FrameLayout[2];
 Button gear=new Button(),pendingTile,firstFavorite,focused;
 List<List<TileData>> rowData=new ArrayList<>(); Map<String,Button> focusTargets=new HashMap<>();
 Button getCurrentFocus(){return focused;} void schedulePreparation(){} void retirePreview(int row){}
 void attachTile(int row,int col){created++;TileData d=rowData.get(row).get(col);
  TileButton b=new TileButton();b.row=row;b.col=col;d.button=b;trays[row].add(b);}
 void key(int key,int action){dispatchKeyEvent(new KeyEvent(key,action));}
 void check(boolean value){if(!value)throw new AssertionError();}
 void run(){
  String[][] keys=new String[2][100];
  for(int r=0;r<2;r++){trays[r]=new FrameLayout();rowData.add(new ArrayList<>());
    for(int c=0;c<100;c++){TileData d=new TileData();d.key=keys[r][c]=r+"/"+c;rowData.get(r).add(d);}}
  rowFocus=new RowFocusMemory(keys);attachTile(0,0);firstFavorite=trays[0].get(0);firstFavorite.requestFocus();
  key(20,0); for(int i=0;i<45;i++)key(22,0);check(created==1 && wantedColumn==45);
  key(23,0);key(23,1);check(clicks==0);prepareOne();
  check(clicks==1 && "1/45".equals(clicked));
  for(int i=0;i<50;i++){key(22,0);prepareOne();check(trays[1].size()<=9);}
  key(19,0);check("0/0".equals(focusedKey));key(20,0);check("1/95".equals(focusedKey));
  while(prepareOne()){}check(trays[0].size()<=8 && trays[1].size()<=8);
 }
 public static void main(String[] args){new WindowHarness().run();System.out.println("PASS: cold-row repeats, deferred OK, selection memory and bounded controls");}
"""
build = root / "launcher/build/focus-window-test"
build.mkdir(parents=True, exist_ok=True)
source = build / "WindowHarness.java"
source.write_text(harness + "\n".join(map(method, methods)) + "\n}")
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run([java/"javac", "--release", "8", "-d", build, source, src/"RowFocusMemory.java",
                src/"NavigationTiming.java"], check=True)
subprocess.run([java/"java", "-cp", build, "app.firetvremote.launcher.WindowHarness"], check=True)
