package org.zhixiaoce.aperture;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Public calling convention implemented independently; shared by Android and the JVM fixture. */
final class CinemaPluginAbi {
    static Object open(ClassLoader loader,Context context,String api,String extension)throws Exception {
        if(!api.matches("csp_[A-Za-z0-9_$]+"))throw new IOException("插件类名无效");
        try{Class<?> init=loader.loadClass("com.github.catvod.spider.Init");init.getMethod("init",Context.class).invoke(null,context);}catch(ClassNotFoundException|NoSuchMethodException optional){}
        Object spider=loader.loadClass("com.github.catvod.spider."+api.substring(4)).getDeclaredConstructor().newInstance();spider.getClass().getMethod("init",Context.class,String.class).invoke(spider,context,extension);return spider;
    }
    static String invoke(Object spider,String operation,Object[] args)throws Exception {
        String name;Class<?>[] types;Object[] values=args;
        switch(operation){
            case "home":name="homeContent";types=new Class[]{boolean.class};break;
            case "homeVod":name="homeVideoContent";types=new Class[0];break;
            case "category":name="categoryContent";types=new Class[]{String.class,String.class,boolean.class,HashMap.class};values=new Object[]{args[0],String.valueOf(args[1]),args[2],new HashMap<>((Map<?,?>)args[3])};break;
            case "detail":name="detailContent";types=new Class[]{List.class};break;
            case "search":name="searchContent";types=new Class[]{String.class,boolean.class,String.class};try{spider.getClass().getMethod(name,types);}catch(NoSuchMethodException missing){types=new Class[]{String.class,boolean.class};values=new Object[]{args[0],args[1]};}break;
            case "play":name="playerContent";types=new Class[]{String.class,String.class,List.class};break;
            default:throw new IOException("插件操作无效");
        }
        try{return String.valueOf(spider.getClass().getMethod(name,types).invoke(spider,values));}catch(InvocationTargetException failure){throw new IOException("插件执行失败："+failure.getCause().getClass().getSimpleName());}
    }
}
