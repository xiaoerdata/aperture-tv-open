package org.zhixiaoce.aperture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONArray;

/** Separate second-screen collection. Home quick slots remain independent. */
final class AppWall {
    static final int MAX=128;
    static List<String> read(String json,List<String> defaults){
        if(json==null)return clean(defaults);
        List<String> values=new ArrayList<>();
        try{if(json.length()>65536)return values;JSONArray a=new JSONArray(json);for(int i=0;i<a.length()&&values.size()<MAX;i++)if(a.opt(i) instanceof String)values.add(a.getString(i));}catch(Exception invalid){return values;}
        return clean(values);
    }
    static List<String> clean(List<String> values){LinkedHashSet<String> unique=new LinkedHashSet<>();for(String v:values)if(v!=null&&v.length()<=512&&v.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")){unique.add(v);if(unique.size()==MAX)break;}return new ArrayList<>(unique);}
    static String encode(List<String> values)throws IOException{if(values.size()>MAX)throw new IOException("最多添加128个应用");return new JSONArray(clean(values)).toString();}
    static String key(String component){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(component.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder("wall");for(int i=0;i<8;i++)result.append(String.format(Locale.ROOT,"%02x",h[i]&255));return result.toString();}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
