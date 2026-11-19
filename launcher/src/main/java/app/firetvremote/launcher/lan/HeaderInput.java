package app.firetvremote.launcher.lan;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

/** Captures exactly one bounded header, never a body or a pipelined request. */
final class HeaderInput extends InputStream {
    interface Clock { long now(); }
    private final InputStream input;
    private final Clock clock;
    private final long deadline;
    private final byte[] header = new byte[ServerLimits.HEADER_BYTES];
    private int size, position;
    private boolean captured;
    private int remaining;
    private boolean websocket;
    void upgrade(){websocket=true;}

    HeaderInput(InputStream input) { this(input, System::nanoTime); }
    HeaderInput(InputStream input, Clock clock) {
        this.input = new BufferedInputStream(input, ServerLimits.HEADER_BYTES);
        this.clock = clock;
        deadline = clock.now() + ServerLimits.HEADER_TIMEOUT_NS;
    }

    private void capture() throws IOException {
        if (captured) return;
        while (size < header.length) {
            if (clock.now() - deadline >= 0) throw new SocketTimeoutException("Header deadline");
            int value = input.read();
            if (clock.now() - deadline >= 0) throw new SocketTimeoutException("Header deadline");
            if (value < 0) throw new IOException("Incomplete header");
            header[size++] = (byte)value;
            if (size >= 4 && header[size-4] == '\r' && header[size-3] == '\n'
                    && header[size-2] == '\r' && header[size-1] == '\n') {
                validateHeaderNames();
                configureBody();
                captured = true;
                return;
            }
        }
        throw new IOException("Header too large");
    }

    private void configureBody() throws IOException {
        String[] lines = new String(header,0,size,java.nio.charset.StandardCharsets.US_ASCII).split("\r\n");
        if (!lines[0].equals("POST /v1/pair HTTP/1.1")) return;
        for(int i=1;i<lines.length;i++) if(lines[i].toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
            String value=lines[i].substring(lines[i].indexOf(':')+1).trim();
            if(!value.matches("[0-9]{1,4}")) throw new IOException("Invalid body length");
            remaining=Integer.parseInt(value);
            if(remaining>1024) throw new IOException("Pairing body too large");
        }
    }
    private int body(byte[] bytes,int offset,int length) throws IOException {
        if(websocket)return input.read(bytes,offset,length);
        if(remaining==0) return -1;
        if(clock.now()-deadline>=0) throw new SocketTimeoutException("Body deadline");
        int n=input.read(bytes,offset,Math.min(length,remaining));
        if(clock.now()-deadline>=0) throw new SocketTimeoutException("Body deadline");
        if(n<0) throw new IOException("Incomplete body");
        remaining-=n; return n;
    }
    private void validateHeaderNames() throws IOException {
        String[] lines = new String(header, 0, size, java.nio.charset.StandardCharsets.US_ASCII).split("\r\n");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon < 1) throw new IOException("Malformed header");
            String name = lines[i].substring(0, colon);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || !names.add(name.toLowerCase(java.util.Locale.ROOT))) {
                throw new IOException("Invalid or duplicate header");
            }
        }
    }

    @Override public int read() throws IOException {
        capture();
        if(position<size) return header[position++] & 255;
        byte[] one=new byte[1]; return body(one,0,1)<0?-1:one[0]&255;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || length > bytes.length - offset) throw new IndexOutOfBoundsException();
        if (length == 0) return 0;
        capture();
        if (position == size) return body(bytes,offset,length);
        int count = Math.min(length, size - position);
        System.arraycopy(header, position, bytes, offset, count);
        position += count;
        return count;
    }
    @Override public void close() throws IOException { input.close(); }
}
