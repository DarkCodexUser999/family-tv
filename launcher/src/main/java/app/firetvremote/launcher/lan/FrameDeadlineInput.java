package app.firetvremote.launcher.lan;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

/** No idle timeout/polling. One deadline starts only after a frame's first byte arrives. */
final class FrameDeadlineInput extends FilterInputStream {
    private final EventHub hub;private final Runnable close;private ScheduledFuture<?> timer;
    FrameDeadlineInput(InputStream input,EventHub hub,Runnable close){super(input);this.hub=hub;this.close=close;}
    @Override public int read() throws IOException {
        int value=in.read();if(value>=0 && timer==null)timer=hub.deadline(close,2000);return value;
    }
    @Override public int read(byte[] b,int off,int length)throws IOException{return in.read(b,off,length);}
    void complete(){if(timer!=null){timer.cancel(false);timer=null;}}
    @Override public void close()throws IOException{complete();in.close();}
}
