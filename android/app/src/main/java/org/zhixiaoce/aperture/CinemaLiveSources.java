package org.zhixiaoce.aperture;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Collapse only byte-equivalent display sources, retaining every stored ID for old favorites. */
final class CinemaLiveSources {
    private static void add(StringBuilder out,String value){out.append(value.length()).append(':').append(value);}
    private static String fingerprint(CinemaCatalog.Live live){StringBuilder out=new StringBuilder();add(out,live.name);add(out,live.url);add(out,new TreeMap<>(live.headers).toString());for(CinemaCatalog.Channel c:live.channels){for(String value:new String[]{c.name,c.group,c.logo,c.epgId,String.valueOf(c.number)})add(out,value);for(int i=0;i<c.urls.size();i++){add(out,c.urls.get(i));add(out,new TreeMap<>(c.request(i)).toString());}}return out.toString();}
    static List<CinemaCatalog.Live> visible(List<CinemaCatalog.Live> sources){List<CinemaCatalog.Live> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(CinemaCatalog.Live source:sources)if(seen.add(fingerprint(source)))out.add(source);return out;}
}
