package app.firetvremote.launcher.lan;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict tiny JSON grammar for v1 action envelopes. No arrays, duplicate keys or extra fields. */
public final class ActionMessage {
    public final String id, action;
    public final long sentAt, epoch;
    private ActionMessage(String id,String action,long sentAt,long epoch) {
        this.id=id; this.action=action; this.sentAt=sentAt; this.epoch=epoch;
    }
    public static boolean allowed(String action) {
        return action!=null && (action.equals("UP")||action.equals("DOWN")||action.equals("LEFT")||action.equals("RIGHT")
                ||action.equals("OK")||action.equals("BACK")||action.equals("HOME")||action.equals("OPTIONS"));
    }
    @SuppressWarnings("unchecked")
    public static ActionMessage parse(String text) {
        Reader r=new Reader(text); Map<String,Object> root=r.object(0); r.space();
        if(r.pos!=text.length() || root.size()!=4 || !Long.valueOf(1).equals(root.get("v")) || !"action".equals(root.get("type"))
                || !(root.get("id") instanceof String) || !(root.get("payload") instanceof Map)) throw new IllegalArgumentException();
        Map<String,Object> payload=(Map<String,Object>)root.get("payload");
        String id=(String)root.get("id"),action=payload.get("action") instanceof String?(String)payload.get("action"):null;
        if(payload.size()!=4 || !id.matches("[A-Za-z0-9_-]{1,64}") || !id.equals(payload.get("id")) || !allowed(action)
                || !(payload.get("sentAtMs") instanceof Long) || !(payload.get("epoch") instanceof Long)) throw new IllegalArgumentException();
        return new ActionMessage(id,action,(Long)payload.get("sentAtMs"),(Long)payload.get("epoch"));
    }
    static final class Reader {
        final String text; final boolean rows; int pos;
        Reader(String text) { this(text,false); }
        Reader(String text,boolean rows) { if(text.length()>(rows?8192:4096)) throw new IllegalArgumentException(); this.text=text;this.rows=rows; }
        void space() { while(pos<text.length() && " \r\n\t".indexOf(text.charAt(pos))>=0)pos++; }
        char peek() { space(); if(pos==text.length())throw new IllegalArgumentException(); return text.charAt(pos); }
        void take(char c) { if(peek()!=c)throw new IllegalArgumentException();pos++; }
        String string() {
            take('"');StringBuilder s=new StringBuilder();
            while(pos<text.length()) {
                char c=text.charAt(pos++);
                if(c=='"')return s.toString();
                if(c<32 || s.length()>=160)throw new IllegalArgumentException();
                if(c=='\\') {
                    if(pos==text.length())throw new IllegalArgumentException();c=text.charAt(pos++);
                    switch(c) {
                        case '"':case '\\':case '/':break;
                        case 'u':if(pos+4>text.length())throw new IllegalArgumentException();c=(char)Integer.parseInt(text.substring(pos,pos+4),16);pos+=4;break;
                        case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
                        default:throw new IllegalArgumentException();
                    }
                }s.append(c);
            }throw new IllegalArgumentException();
        }
        Object value(int depth) {
            char c=peek();if(c=='"')return string();if(c=='{')return object(depth+1);
            if(rows && c=='[')return array(depth+1);
            int start=pos;while(pos<text.length() && text.charAt(pos)>='0' && text.charAt(pos)<='9')pos++;
            String n=text.substring(start,pos);
            if(!n.matches("0|[1-9][0-9]{0,14}"))throw new IllegalArgumentException();return Long.valueOf(n);
        }
        java.util.List<Object> array(int depth) {
            if(depth>3)throw new IllegalArgumentException();take('[');
            java.util.List<Object> list=new java.util.ArrayList<>();if(peek()==']'){pos++;return list;}
            while(true){if(list.size()>=24)throw new IllegalArgumentException();list.add(value(depth));
                char c=peek();pos++;if(c==']')return list;if(c!=',')throw new IllegalArgumentException();}
        }
        Map<String,Object> object(int depth) {
            if(depth>(rows?3:1))throw new IllegalArgumentException();take('{');Map<String,Object> out=new LinkedHashMap<>();
            if(peek()=='}'){pos++;return out;}
            while(true) {
                if(out.size()>=4)throw new IllegalArgumentException();String key=string();take(':');
                if(out.put(key,value(depth))!=null)throw new IllegalArgumentException();
                char c=peek();pos++;if(c=='}')return out;if(c!=',')throw new IllegalArgumentException();
            }
        }
    }
}
