package app.firetvremote.launcher.lan;
import java.util.*;
import java.io.*;
public final class HiddenEndpointTest {
 static int count;static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
 public static void main(String[] args){
  final String sony="com.onemainstream.sonyliv.android";
  RowsEndpointTest.Disk disk=new RowsEndpointTest.Disk();
  HiddenEndpoint hidden=new HiddenEndpoint(disk,new HashSet<>(Arrays.asList(sony)));
  AppsEndpoint management=new AppsEndpoint(1,Arrays.asList(new AppsEndpoint.App(sony,"Sony LIV",null),new AppsEndpoint.App("tv.youtube","YouTube",null)));
  check(hidden.snapshot().packages.contains(sony),"Sony default preserved");
  check(!management.visible(hidden.snapshot().packages).contains(sony),"default hidden not launchable");
  check(management.page(null,25,hidden.snapshot().packages).contains("\"hidden\":true"),"management retains hidden app");
  String unhide=new HiddenEndpoint.State(0,Collections.emptySet()).json();
  check("unauthorized".equals(hidden.replace(unhide,"\"0\"",management,()->false)),"auth recheck");
  check(hidden.replace(unhide,"\"0\"",management,()->true)==null,"explicit unhide");
  check(management.visible(hidden.snapshot().packages).contains(sony),"unhidden available");
  check("conflict".equals(hidden.replace(unhide,"\"0\"",management,()->true)),"stale phone rejected");
  String hide=new HiddenEndpoint.State(1,Arrays.asList("tv.youtube",sony)).json();
  disk.fail=true;check("storage_unavailable".equals(hidden.replace(hide,"\"1\"",management,()->true)),"failed save");
  check(hidden.snapshot().packages.isEmpty(),"failure keeps state");disk.fail=false;
  check(hidden.replace(hide,"\"1\"",management,()->true)==null,"hide saved");
  check(new HiddenEndpoint(disk,Collections.emptySet()).snapshot().packages.contains(sony),"reload persisted");
  check("not_found".equals(hidden.replace(new HiddenEndpoint.State(2,Arrays.asList("tv.unknown")).json(),"\"2\"",management,()->true)),"unknown rejected");
  check("invalid_request".equals(hidden.replace(hide.replace("\"tv.youtube\"","\"a;command\""),"\"1\"",management,()->true)),"invalid package");
  disk.saved="{broken";check(new HiddenEndpoint(disk,new HashSet<>(Arrays.asList(sony))).snapshot().packages.contains(sony),"corruption restores exclusion defaults");
  try{HiddenEndpoint.parse("{\"apiVersion\":1,\"revision\":0,\"packages\":[\"tv.a\",\"tv.a\"]}");throw new AssertionError();}catch(IllegalArgumentException expected){count++;}
  System.out.println("PASS: "+count+" hide/unhide/default/revision/failure/reload guards (fake disk)");
 }
}
