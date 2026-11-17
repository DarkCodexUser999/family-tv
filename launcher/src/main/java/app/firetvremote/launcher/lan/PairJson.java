package app.firetvremote.launcher.lan;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, bounded three-string object reader; no permissive duplicate-key JSON parser. */
final class PairJson {
    private final String text; private int pos;
    private PairJson(String text) { this.text=text; }
    private void space() { while(pos<text.length() && " \r\n\t".indexOf(text.charAt(pos))>=0) pos++; }
    private void take(char c) { space(); if(pos>=text.length()||text.charAt(pos++)!=c) throw new IllegalArgumentException(); }
    private String string() {
        take('"'); StringBuilder out=new StringBuilder();
        while(pos<text.length()) {
            char c=text.charAt(pos++);
            if(c=='"') return out.toString();
            if(c<32) throw new IllegalArgumentException();
            if(c=='\\') {
                if(pos>=text.length()) throw new IllegalArgumentException();
                c=text.charAt(pos++);
                switch(c) {
                    case '"': case '\\': case '/': break;
                    case 'b': c='\b'; break; case 'f': c='\f'; break;
                    case 'n': c='\n'; break; case 'r': c='\r'; break; case 't': c='\t'; break;
                    case 'u':
                        if(pos+4>text.length()) throw new IllegalArgumentException();
                        c=(char)Integer.parseInt(text.substring(pos,pos+4),16); pos+=4; break;
                    default: throw new IllegalArgumentException();
                }
            }
            out.append(c);
        }
        throw new IllegalArgumentException();
    }
    static Map<String,String> parse(String json) {
        PairJson r=new PairJson(json); Map<String,String> out=new LinkedHashMap<>(); r.take('{');
        for(int i=0;i<3;i++) {
            if(i>0) r.take(',');
            String key=r.string(); r.take(':'); String value=r.string();
            if(out.put(key,value)!=null) throw new IllegalArgumentException();
        }
        r.take('}'); r.space();
        if(r.pos!=json.length() || !out.containsKey("pin") || !out.containsKey("sessionId") || !out.containsKey("deviceName")
                || !out.get("pin").matches("[0-9]{6}") || !out.get("sessionId").matches("[A-Za-z0-9_-]{1,64}")
                || out.get("deviceName").length()<1 || out.get("deviceName").length()>48) throw new IllegalArgumentException();
        return out;
    }
}
