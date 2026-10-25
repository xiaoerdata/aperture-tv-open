package org.zhixiaoce.aperture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Bounded local viewing state. Reopens stable source/item IDs, never cached expiring media URLs. */
final class CinemaShelf {
    private final LinkedHashMap<String,JSONObject> records=new LinkedHashMap<>();
    CinemaShelf(String json)throws Exception {
        if(json.getBytes(StandardCharsets.UTF_8).length>CinemaCatalog.MAX_BYTES)throw new IOException("观看记录过大");JSONArray values=new JSONArray(json);if(values.length()>200)throw new IOException("观看记录超过限制");
        for(int i=0;i<values.length();i++){JSONObject item=values.getJSONObject(i);validate(item);String key=key(item);if(records.put(key,new JSONObject(item.toString()))!=null)throw new IOException("重复观看记录");}
    }
    static JSONObject ref(String kind,String source,String id,String title,String group)throws Exception{return new JSONObject().put("kind",kind).put("source",source).put("id",id).put("title",title).put("group",group);}
    private static void validate(JSONObject ref)throws Exception {
        if(!Arrays.asList("vod","live","nas").contains(ref.getString("kind")))throw new IOException("观看类型无效");
        for(String field:new String[]{"source","id","title","group","episode","line"})CinemaCatalog.field(ref.optString(field),field.equals("source")||field.equals("id")?8192:200);
        if(ref.optString("source").isEmpty()||ref.optString("id").isEmpty()||ref.optString("title").isEmpty())throw new IOException("观看记录缺少标识");
        for(String field:new String[]{"position","duration","watchedAt","checkedAt"})if(ref.optLong(field,0)<0)throw new IOException("观看记录数值无效");
    }
    static String key(JSONObject ref)throws Exception {validate(ref);String value=ref.getString("kind")+'\0'+ref.getString("source")+'\0'+ref.getString("id");byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    JSONObject get(JSONObject ref)throws Exception {JSONObject value=records.get(key(ref));return value==null?null:new JSONObject(value.toString());}
    private JSONObject edit(JSONObject ref)throws Exception {
        String key=key(ref);JSONObject record=records.get(key);if(record==null){if(records.size()>=200){String oldest=null;long time=Long.MAX_VALUE;for(Map.Entry<String,JSONObject> e:records.entrySet())if(!e.getValue().optBoolean("favorite")&&!e.getValue().optBoolean("follow")&&e.getValue().optLong("watchedAt")<time){oldest=e.getKey();time=e.getValue().optLong("watchedAt");}if(oldest==null)throw new IOException("收藏与追剧已满，请先移除一项");records.remove(oldest);}record=new JSONObject(ref.toString());records.put(key,record);}record.put("title",ref.getString("title"));return record;
    }
    void toggle(JSONObject ref,String flag)throws Exception{if(!"favorite".equals(flag)&&!"follow".equals(flag))throw new IOException("操作无效");if("follow".equals(flag)&&!"vod".equals(ref.getString("kind")))throw new IOException("仅剧集支持追剧");JSONObject record=edit(ref);record.put(flag,!record.optBoolean(flag));}
    void watch(JSONObject ref,String line,String episode,long position,long duration,boolean complete,long now)throws Exception {
        JSONObject record=edit(ref);record.put("line",CinemaCatalog.field(line,200)).put("episode",CinemaCatalog.field(episode,200)).put("position",Math.max(0,Math.min(604800000L,position))).put("duration",Math.max(0,Math.min(604800000L,duration))).put("complete",complete).put("watchedAt",Math.max(0,now));
    }
    void latest(JSONObject ref,int total,String latest,String remark,long now)throws Exception {JSONObject record=get(ref);if(record==null||!record.optBoolean("follow"))return;record=edit(ref);int before=record.optInt("seenTotal",total);record.put("seenTotal",before).put("newEpisodes",Math.max(0,total-before)).put("total",total).put("latest",CinemaCatalog.field(latest,200)).put("remark",CinemaCatalog.field(remark,200)).put("checkedAt",Math.max(0,now));}
    void seen(JSONObject ref)throws Exception {JSONObject record=records.get(key(ref));if(record!=null)record.put("newEpisodes",0).put("seenTotal",record.optInt("total",0));}
    long resume(JSONObject ref,String line,String episode)throws Exception {JSONObject value=get(ref);if(value==null||value.optBoolean("complete")||!line.equals(value.optString("line"))||!episode.equals(value.optString("episode")))return 0;long position=value.optLong("position");long duration=value.optLong("duration");return duration>0&&position<duration-3000?position:0;}
    List<JSONObject> list(String kind,String flag)throws Exception {List<JSONObject> values=new ArrayList<>();for(JSONObject r:records.values())if((kind==null||kind.equals(r.optString("kind")))&&(flag.equals("history")?r.optLong("watchedAt")>0:r.optBoolean(flag)))values.add(new JSONObject(r.toString()));values.sort((a,b)->Long.compare(b.optLong("watchedAt"),a.optLong("watchedAt")));return values;}
    void remove(JSONObject ref)throws Exception{records.remove(key(ref));}
    void clearHistory()throws Exception {Iterator<JSONObject> iterator=records.values().iterator();while(iterator.hasNext()){JSONObject r=iterator.next();if(!r.optBoolean("favorite")&&!r.optBoolean("follow"))iterator.remove();else for(String field:new String[]{"position","duration","watchedAt","complete","line","episode"})r.remove(field);}}
    String json()throws Exception {JSONArray values=new JSONArray();for(JSONObject r:records.values())values.put(r);String text=values.toString();if(text.getBytes(StandardCharsets.UTF_8).length>CinemaCatalog.MAX_BYTES)throw new IOException("观看记录超过容量");return text;}
}
