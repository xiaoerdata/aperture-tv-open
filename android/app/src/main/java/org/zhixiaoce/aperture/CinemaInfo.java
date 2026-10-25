package org.zhixiaoce.aperture;

import android.app.Activity;
import android.os.Handler;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Device-private metadata preferences; external providers never receive source/NAS credentials. */
final class CinemaInfo implements AutoCloseable {
    interface Done {void accept(JSONObject match,List<JSONObject> candidates,String state);}
    private final CinemaVault vault;private JSONObject settings=new JSONObject();
    private final CinemaHttp http=new CinemaHttp();private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));
    private final Map<String,List<JSONObject>> cache=new LinkedHashMap<>();private final Map<String,Long> ages=new HashMap<>();
    private volatile int epoch;private volatile boolean closed;private long lastRequest;
    CinemaInfo(Activity a){vault=new CinemaVault(a);try{settings=vault.metadata();}catch(Exception ignored){}}
    boolean enabled(){return settings.optBoolean("enabled",false);}
    String token(){return settings.optString("token");}
    void configure(boolean on,String token)throws Exception{String value=token.trim();if(value.length()>1024||value.contains("\n")||value.contains("\r"))throw new java.io.IOException("资料凭证格式无效");JSONObject next=new JSONObject(settings.toString()).put("enabled",on).put("token",value);vault.metadata(next);settings=next;cancel();synchronized(cache){cache.clear();ages.clear();}}
    private String key(String title,String year,String kind){CinemaMetadata.Name n=new CinemaMetadata.Name(title,year);return kind+":"+n.year+":"+CinemaMetadata.comparable(n.title);}
    void choose(String title,String year,String kind,JSONObject value)throws Exception{JSONObject next=new JSONObject(settings.toString()),choices=next.optJSONObject("choices");if(choices==null)choices=new JSONObject();String id=key(title,year,kind);if(choices.length()>=32&&!choices.has(id))choices.remove(choices.keys().next());choices.put(id,value==null?new JSONObject().put("suppressed",true):value);next.put("choices",choices);vault.metadata(next);settings=next;}
    void lookup(String title,String year,String kind,boolean manual,Done done){
        String id=key(title,year,kind);int turn=epoch;JSONObject choices=settings.optJSONObject("choices"),saved=choices==null?null:choices.optJSONObject(id);
        if(!manual&&saved!=null){done.accept(saved.optBoolean("suppressed")?null:saved,Collections.emptyList(),saved.optBoolean("suppressed")?"已保留来源资料":"手动匹配");return;}
        if(!enabled()){done.accept(null,Collections.emptyList(),"联网资料未开启");return;}
        final String credential=token();if(credential.isEmpty()&&"movie".equals(kind)){done.accept(null,Collections.emptyList(),"电影资料可在来源设置中连接 TMDB");return;}
        try{worker.execute(()->{if(closed||turn!=epoch)return;try{List<JSONObject> values;long age;synchronized(cache){values=cache.get(id);age=ages.getOrDefault(id,0L);}if(values==null||System.currentTimeMillis()-age>86400000){CinemaMetadata.Name name=new CinemaMetadata.Name(title,year);values=CinemaMetadata.search(name,kind,credential,(url,headers)->{long wait=900-(System.currentTimeMillis()-lastRequest);if(wait>0)Thread.sleep(wait);if(turn!=epoch||closed)throw new java.io.IOException();lastRequest=System.currentTimeMillis();return http.document(url,false,headers).text;});if(turn!=epoch||closed)return;synchronized(cache){if(cache.size()>=96){String first=cache.keySet().iterator().next();cache.remove(first);ages.remove(first);}cache.put(id,values);ages.put(id,System.currentTimeMillis());}}JSONObject match=CinemaMetadata.automatic(new CinemaMetadata.Name(title,year),values);List<JSONObject> result=values;main.post(()->{if(!closed&&turn==epoch)done.accept(match,result,match==null?result.isEmpty()?"未找到资料":"暂无匹配资料":"联网匹配");});}catch(Exception failure){main.post(()->{if(!closed&&turn==epoch)done.accept(null,Collections.emptyList(),"资料暂时不可用，仍可播放");});}});}catch(RejectedExecutionException full){done.accept(null,Collections.emptyList(),"资料稍后读取");}
    }
    void cancel(){epoch++;http.cancel();worker.getQueue().clear();main.removeCallbacksAndMessages(null);}
    public void close(){closed=true;cancel();worker.shutdownNow();}
}
