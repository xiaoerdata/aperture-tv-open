package org.zhixiaoce.aperture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Playback budgets and cache isolation, independent of browsing/image caches. */
final class CinemaStreamPolicy {
    static final long MAX_DISK=128L*1024*1024;
    final int minBufferMs,maxBufferMs,startMs,rebufferMs,targetBytes;
    CinemaStreamPolicy(boolean live,int heapMiB){minBufferMs=live?8000:25000;maxBufferMs=live?20000:60000;startMs=live?1500:2000;rebufferMs=live?3000:5000;int mb=live?Math.max(8,Math.min(16,heapMiB/16)):Math.max(16,Math.min(48,heapMiB/8));targetBytes=mb*1024*1024;}
    static long diskBudget(long free){return free<64L*1024*1024?0:Math.min(MAX_DISK,free/8);}
    static boolean nativeFallback(int error){return error>=3000&&error<5000;}
    static String cacheKey(String uri,Map<String,String> supplied)throws Exception {StringBuilder key=new StringBuilder(uri).append('\n');Map<String,String> headers=new TreeMap<>();for(Map.Entry<String,String> h:supplied.entrySet())if(!h.getKey().equalsIgnoreCase("Range"))headers.put(h.getKey().toLowerCase(Locale.ROOT),h.getValue());for(Map.Entry<String,String> h:headers.entrySet())key.append(h.getKey().length()).append(':').append(h.getKey()).append(h.getValue().length()).append(':').append(h.getValue());StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
