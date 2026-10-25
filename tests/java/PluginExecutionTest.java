package org.zhixiaoce.aperture;
import java.io.File;
import java.net.URLClassLoader;
import java.util.*;
import org.json.*;

/** Executes compiled fixture code from a real JAR using the production calling convention. */
public final class PluginExecutionTest {
    private static int assertions;
    private static void check(boolean okay){if(!okay)throw new AssertionError("Assertion "+assertions);assertions++;}
    public static void main(String[] args)throws Exception {
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{new File(args[0]).toURI().toURL()},PluginExecutionTest.class.getClassLoader())){
            Object spider=CinemaPluginAbi.open(loader,null,"csp_Fixture","自有 ");
            JSONObject home=new JSONObject(CinemaPluginAbi.invoke(spider,"home",new Object[]{false}));check(home.getJSONArray("class").length()==1);check(home.getJSONArray("list").getJSONObject(0).getString("vod_name").startsWith("自有 "));
            JSONObject category=new JSONObject(CinemaPluginAbi.invoke(spider,"category",new Object[]{"films","2",false,Collections.emptyMap()}));check(category.getInt("page")==2);
            JSONObject detail=new JSONObject(CinemaPluginAbi.invoke(spider,"detail",new Object[]{Arrays.asList("one")}));check(detail.getJSONArray("list").getJSONObject(0).getString("vod_play_url").contains("opaque-episode-2"));
            JSONObject search=new JSONObject(CinemaPluginAbi.invoke(spider,"search",new Object[]{"我的短片",false,"2"}));check(search.getJSONArray("list").getJSONObject(0).getString("vod_name").equals("我的短片"));
            JSONObject play=new JSONObject(CinemaPluginAbi.invoke(spider,"play",new Object[]{"测试线路","opaque-episode-1",Collections.emptyList()}));check(play.getInt("parse")==0);check(play.getString("url").endsWith("opaque-episode-1.mp4"));check(play.getJSONObject("header").has("Referer"));
            try{CinemaPluginAbi.open(loader,null,"../bad","");throw new AssertionError();}catch(java.io.IOException expected){check(true);}
        }
        System.out.println(assertions+" JAR execution assertions passed");
    }
}
