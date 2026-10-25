package com.github.catvod.spider;
import android.content.Context;
import com.github.catvod.crawler.Spider;
import java.util.*;
import org.json.*;
/** Original, provider-free DEX/JAR fixture. */
public final class Fixture extends Spider {
    private String prefix="";
    @Override public void init(Context context,String extension){prefix=extension;}
    private JSONObject item(String id,String title)throws Exception{return new JSONObject().put("vod_id",id).put("vod_name",title);}
    @Override public String homeContent(boolean filter)throws Exception{return new JSONObject().put("class",new JSONArray().put(new JSONObject().put("type_id","films").put("type_name","影片"))).put("list",new JSONArray().put(item("one",prefix+"测试短片"))).toString();}
    @Override public String categoryContent(String tid,String pg,boolean filter,HashMap<String,String> extend)throws Exception{return new JSONObject().put("page",Integer.parseInt(pg)).put("pagecount",3).put("list",new JSONArray().put(item(tid+"-one","测试短片"))).toString();}
    @Override public String detailContent(List<String> ids)throws Exception{return new JSONObject().put("list",new JSONArray().put(item(ids.get(0),"测试短片").put("vod_play_from","测试线路").put("vod_play_url","第一集$opaque-episode-1#第二集$opaque-episode-2"))).toString();}
    @Override public String searchContent(String key,boolean quick,String pg)throws Exception{return new JSONObject().put("page",Integer.parseInt(pg)).put("pagecount",3).put("list",new JSONArray().put(item("search-one",key))).toString();}
    @Override public String playerContent(String flag,String id,List<String> vipFlags)throws Exception{return new JSONObject().put("parse",0).put("url","https://media.example.test/"+id+".mp4").put("header",new JSONObject().put("Referer","https://media.example.test/")).toString();}
}
