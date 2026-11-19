package app.firetvremote.launcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LaunchRouteTest {
    public static void main(String[] args) {
        Set<String> own = new HashSet<>(Arrays.asList("in.startv.hotstar", "com.amazon.cloud9"));
        check(!LaunchRoute.build("app.default", "Main", "launcher", own).ownTask);
        check(LaunchRoute.build("app.default", "Main", "launcher", own).intent().flags == 0);
        for (String pkg : own) {
            LaunchRoute route = LaunchRoute.build(pkg, "Chosen", "leanback", own);
            check(route.ownTask && route.activity.equals("Chosen"));
            check(route.pkg.equals(pkg) && route.category.equals("leanback"));
            check(route.intent().flags == 0x10200000);
            check(route.intent().component.name.equals("Chosen"));
        }
        Set<String> prime = new HashSet<>(Arrays.asList("com.amazon.avod"));
        LaunchRoute.preferPrimeApp(prime);
        check(prime.contains("com.amazon.avod"));
        prime.add("com.amazon.firebat");
        LaunchRoute.preferPrimeApp(prime);
        check(prime.size() == 1 && prime.contains("com.amazon.firebat"));
        check(LaunchRoute.build("removed.app", null, "launcher", own) == null);
        System.out.println("PASS: default, own-task, Silk, Prime alias and absent component");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
