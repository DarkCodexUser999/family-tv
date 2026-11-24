package app.firetvremote.launcher.lan;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public final class RowsEndpointTest {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static class Disk implements RowsEndpoint.Disk {
        String saved;boolean fail;CountDownLatch writing,finish;
        public String read(){return saved;}
        public void write(String value)throws IOException {
            if(writing!=null){writing.countDown();try{finish.await();}catch(InterruptedException e){throw new IOException(e);}}
            if(fail)throw new IOException("interrupted write");saved=value;
        }
    }
    static RowsEndpoint.State state(long revision,String... packages){return new RowsEndpoint.State(revision,Arrays.asList(Arrays.asList(packages)));}
    public static void main(String[] args)throws Exception {
        Disk disk=new Disk();RowsEndpoint rows=new RowsEndpoint(disk,state(0,"tv.a"));
        AppsEndpoint apps=new AppsEndpoint(1,Arrays.asList(new AppsEndpoint.App("tv.a","A",null),new AppsEndpoint.App("tv.b","B",null)));
        check(rows.snapshot().revision==0,"bundled fallback");
        String draft=state(0,"tv.b","tv.a").json();
        check("unauthorized".equals(rows.replace(draft,"\"0\"",apps,()->false)),"authorization recheck");
        check("invalid_request".equals(rows.replace(draft,"0",apps,()->true)),"quoted If-Match");
        check("not_found".equals(rows.replace(state(0,"tv.hidden").json(),"\"0\"",apps,()->true)),"unknown/hidden cannot assign");
        disk.fail=true;
        check("storage_unavailable".equals(rows.replace(draft,"\"0\"",apps,()->true)),"write failure surfaced");
        check(disk.saved==null && rows.snapshot().revision==0,"interrupted write preserves published state");
        disk.fail=false;check(rows.replace(draft,"\"0\"",apps,()->true)==null,"reorder saved");
        check(new RowsEndpoint(disk,state(0)).snapshot().json().equals(state(1,"tv.b","tv.a").json()),"reload persisted revision/order");
        check("conflict".equals(rows.replace(draft,"\"0\"",apps,()->true)),"stale edit cannot overwrite");
        for(String bad:new String[]{draft.replace("\"tv.a\"","\"tv.b\""),draft.replace("\"tv.a\"","\"tv.a;id\""),
                draft.replace("\"apiVersion\":1","\"apiVersion\":1,\"apiVersion\":1"),
                "{\"apiVersion\":1,\"revision\":0,\"rows\":[]}",
                "{\"apiVersion\":1,\"revision\":0,\"rows\":[[[[[]]]]]}",draft+"trailing"}) {
            try{RowsEndpoint.parse(bad);throw new AssertionError("bad document accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        try{new RowsEndpoint.State(0,Arrays.asList(Arrays.asList("tv.a"),Arrays.asList("tv.a")));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try{new RowsEndpoint.State(0,Arrays.asList(Arrays.asList("tv.a","tv.b","tv.c","tv.d","tv.e","tv.f","tv.g")));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try{new RowsEndpoint.State(0,Arrays.asList(Arrays.asList(),Arrays.asList(),Arrays.asList(),Arrays.asList(),Arrays.asList()));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        disk.saved="{broken";check(new RowsEndpoint(disk,state(3,"tv.a")).snapshot().revision==3,"invalid persisted override falls back");
        disk.saved=state(0,"tv.a").json();RowsEndpoint concurrent=new RowsEndpoint(disk,state(0));
        disk.writing=new CountDownLatch(1);disk.finish=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<String> first=workers.submit(()->concurrent.replace(draft,"\"0\"",apps,()->true));
            check(disk.writing.await(1,TimeUnit.SECONDS),"write reached disk");
            Future<String> second=workers.submit(()->concurrent.replace(draft,"\"0\"",apps,()->true));
            check(concurrent.snapshot().revision==0,"readers see old complete state during write");
            disk.finish.countDown();check(first.get()==null && "conflict".equals(second.get()),"concurrent phones: one writer wins");
        }finally{workers.shutdownNow();}
        System.out.println("PASS: "+checks+" rows validation, persistence, interrupted-write and concurrent-phone checks (fake disk)");
    }
}
