package com.github.catvod.spider;
import android.content.Context;
import com.github.catvod.crawler.Spider;
import java.util.*;
import org.json.*;
/** Original plugin for the native approval-to-decoded-playback integration test. */
public final class Playback extends Spider {
    private String media;
    @Override public void init(Context context,String extension){media=extension;}
    private JSONObject item()throws Exception{return new JSONObject().put("vod_id","playback-one").put("vod_name","插件测试短片");}
    @Override public String homeContent(boolean filter)throws Exception{return new JSONObject().put("class",new JSONArray().put(new JSONObject().put("type_id","films").put("type_name","测试影片"))).put("list",new JSONArray().put(item())).toString();}
    @Override public String categoryContent(String tid,String pg,boolean filter,HashMap<String,String> extend)throws Exception{return homeContent(filter);}
    @Override public String detailContent(List<String> ids)throws Exception{return new JSONObject().put("list",new JSONArray().put(item().put("vod_play_from","插件线路").put("vod_play_url","插件第一集$opaque-native-playback"))).toString();}
    @Override public String playerContent(String flag,String id,List<String> vipFlags)throws Exception{if(!"opaque-native-playback".equals(id))throw new IllegalArgumentException("Unexpected episode ID");return new JSONObject().put("parse",0).put("url",media).put("header",new JSONObject().put("Referer","http://10.0.2.2:4401/")).toString();}
}
