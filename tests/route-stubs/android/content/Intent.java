package android.content; import java.util.*; public class Intent {
 public static final String ACTION_MAIN="android.intent.action.MAIN",CATEGORY_LEANBACK_LAUNCHER="android.intent.category.LEANBACK_LAUNCHER",CATEGORY_LAUNCHER="android.intent.category.LAUNCHER";
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=0x00200000;
 public String action; public Set<String> categories=new HashSet<>(); public ComponentName component; public int flags;
 public Intent(String a){action=a;} public Intent addCategory(String c){categories.add(c);return this;}
 public Intent setComponent(ComponentName c){component=c;return this;} public ComponentName getComponent(){return component;}
 public Intent addFlags(int f){if(component==null)throw new AssertionError("Flags before final component");flags|=f;return this;}
}
