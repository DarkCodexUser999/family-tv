import app.firetvremote.launcher.lan.ProviderSettingsEndpoint;
public final class ProviderSettingsTest {
 public static void main(String[] args)throws Exception{
  final boolean[] value={false}, fail={false};
  ProviderSettingsEndpoint endpoint=new ProviderSettingsEndpoint(new ProviderSettingsEndpoint.Store(){
   public boolean enabled(){return value[0];}
   public boolean save(boolean next){if(fail[0])return false;value[0]=next;return true;}
  });
  if(endpoint.enabled())throw new AssertionError();
  endpoint.set(true);if(!endpoint.enabled())throw new AssertionError();
  fail[0]=true;try{endpoint.set(false);throw new AssertionError();}catch(java.io.IOException expected){}
  if(!endpoint.enabled())throw new AssertionError();
  fail[0]=false;endpoint.set(false);if(endpoint.enabled())throw new AssertionError();
  System.out.println("PASS: provider default, enable, failed persistence, disable");
 }
}
