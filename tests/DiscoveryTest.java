package app.firetvremote.launcher.lan;
import android.content.Context;
import android.net.nsd.*;
public final class DiscoveryTest {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static class Manager extends NsdManager {
        RegistrationListener listener; NsdServiceInfo info; int starts,stops; boolean fail;
        public void registerService(NsdServiceInfo i,int p,RegistrationListener l){
            starts++;if(fail)throw new IllegalArgumentException();info=i;listener=l;
        }
        public void unregisterService(RegistrationListener l){check(l==listener,"same listener");stops++;}
    }
    static Context context(Manager manager){return new Context(){public Object getSystemService(String name){return manager;}};}
    public static void main(String[] args){
        Manager m=new Manager();check(m.starts==0,"no registration before creation");
        Discovery d=new Discovery(context(m),48300,"abcd1234-installation");
        check(m.starts==1 && m.info.port==48300,"bound ephemeral port");
        check(m.info.type.equals("_familytv._tcp."),"service type");
        check(m.info.attributes.size()==1&&"1".equals(m.info.attributes.get("api")),"no secret advertisements");
        d.stop();check(m.stops==0,"pending registration deferred");
        m.listener.onServiceRegistered(m.info);check(m.stops==1,"late registration removed");
        d.stop();check(m.stops==1,"stop idempotent");
        m=new Manager();d=new Discovery(context(m),48301,"abcd1234-installation");
        m.info.setServiceName("Family TV (2)");m.listener.onServiceRegistered(m.info);
        check(d.status.contains("Family TV (2)"),"platform collision rename");d.stop();check(m.stops==1,"registered stop");
        m=new Manager();m.fail=true;d=new Discovery(context(m),48302,"abcd1234-installation");
        check(d.status.contains("manual"),"synchronous failure fallback");d.stop();
        m=new Manager();d=new Discovery(context(m),48303,"abcd1234-installation");
        m.listener.onRegistrationFailed(m.info,1);check(d.status.contains("manual"),"async failure fallback");d.stop();
        System.out.println("PASS: "+checks+" discovery lifecycle checks (host API fakes, not Fire OS NSD)");
    }
}
