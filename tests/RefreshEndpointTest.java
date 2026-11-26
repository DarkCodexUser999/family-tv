import app.firetvremote.launcher.lan.RefreshEndpoint;
public final class RefreshEndpointTest {
 static int checks;
 static void check(boolean v){if(!v)throw new AssertionError("refresh guard "+checks);checks++;}
 public static void main(String[] args){
  long[] now={0};RefreshEndpoint jobs=new RefreshEndpoint(()->now[0]);
  String pending=jobs.request("tv.youtube"),id=pending.split("\"jobId\":\"")[1].split("\"")[0];
  check(pending.contains("phone_required"));check(jobs.request("tv.youtube").equals(pending));
  check(jobs.request("tv.other")==null);check(!jobs.claim("phone","stale"));
  check(jobs.claim("phone",id));check(!jobs.claim("second",id));
  check(!jobs.finish("second",id,"complete"));check(!jobs.finish("phone",id,"bad"));
  now[0]=60001;check(jobs.json().contains("phone_required"));check(!jobs.finish("phone",id,"complete"));
  check(jobs.claim("second",id));check(jobs.finish("second",id,"error"));
  check(jobs.json().contains("error"));check(!jobs.request("tv.youtube").equals(pending));
  jobs.disable();check(jobs.json().contains("idle"));check(!jobs.claim("phone",id));
  System.out.println("PASS: "+checks+" coalescing, single-owner, expiry and cancellation guards");
 }
}
