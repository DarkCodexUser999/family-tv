package android.net.nsd;
public class NsdServiceInfo {
    public String name,type; public int port;
    public final java.util.Map<String,String> attributes=new java.util.HashMap<>();
    public void setServiceName(String name){this.name=name;}
    public String getServiceName(){return name;}
    public void setServiceType(String type){this.type=type;}
    public void setPort(int port){this.port=port;}
    public void setAttribute(String key,String value){attributes.put(key,value);}
}
