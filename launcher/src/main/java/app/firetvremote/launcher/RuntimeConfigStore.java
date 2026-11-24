package app.firetvremote.launcher;

import android.content.Context;
import android.util.AtomicFile;
import app.firetvremote.launcher.lan.RowsEndpoint;
import java.io.*;
import java.util.*;

/** Private atomic override; packaged rows.json is never modified. No Activity reference is retained. */
final class RuntimeConfigStore {
    private static RowsEndpoint rows;
    static synchronized RowsEndpoint open(Context context,List<List<String>> defaults,Set<String> installed) {
        if(rows!=null)return rows;
        List<List<String>> filtered=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(List<String> row:defaults) {
            List<String> allowed=new ArrayList<>();
            for(String pkg:row)if(installed.contains(pkg)&&allowed.size()<6&&seen.add(pkg))allowed.add(pkg);
            filtered.add(allowed);
        }
        if(filtered.isEmpty())filtered.add(new ArrayList<>());
        rows=new RowsEndpoint(disk(context,"remote-rows.json"),new RowsEndpoint.State(0,filtered));
        return rows;
    }
    private static app.firetvremote.launcher.lan.HiddenEndpoint hidden;
    static synchronized app.firetvremote.launcher.lan.HiddenEndpoint hidden(Context context,Set<String> defaults) {
        if(hidden==null)hidden=new app.firetvremote.launcher.lan.HiddenEndpoint(disk(context,"remote-hidden.json"),defaults);
        return hidden;
    }
    private static RowsEndpoint.Disk disk(Context context,String name) {
        final AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));
        return new RowsEndpoint.Disk(){
            public String read()throws IOException {
                try {
                if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists())return null;
                try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[1024];int count;
                    while((count=in.read(buffer))!=-1){if(out.size()+count>8192)throw new IOException("Rows too large");out.write(buffer,0,count);}
                    return out.toString("UTF-8");
                }
                }catch(SecurityException denied){throw new IOException("Rows unavailable",denied);}
            }
            public void write(String json)throws IOException {
                FileOutputStream output=null;
                try {
                    output=file.startWrite();output.write(json.getBytes("UTF-8"));output.getFD().sync();
                    file.finishWrite(output);output=null;
                    if(!json.equals(read()))throw new IOException("Row write verification failed");
                }catch(IOException|SecurityException failed){if(output!=null)file.failWrite(output);throw new IOException("Rows could not be saved",failed);}
            }
        };
    }

}
