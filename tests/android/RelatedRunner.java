package org.zhixiaoce.aperture.test;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Synthetic detail association task flow; rejects real devices and non-debug packages before writing. */
public final class RelatedRunner extends Instrumentation {
    private Activity activity; private File output; private final JSONArray checks=new JSONArray(),images=new JSONArray();
    interface Task {void run()throws Exception;} interface Read<T>{T get()throws Exception;}
    private void ui(Task work)throws Exception {AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new Exception(error.get());}
    private <T>T read(Read<T> work)throws Exception {AtomicReference<T> result=new AtomicReference<>();ui(()->result.set(work.get()));return result.get();}
    private Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    private void until(Read<Boolean> ready,String why)throws Exception {long end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError(why);}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks.put(why);Bundle status=new Bundle();status.putString("check",why);sendStatus(0,status);}
    private void walk(View v,List<View> out){out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),out);}
    private List<View> tree(){List<View> all=new ArrayList<>();walk(activity.getWindow().getDecorView(),all);return all;}
    private View text(String value){for(View v:tree())if(v instanceof TextView&&v.isShown()&&value.equals(((TextView)v).getText().toString()))return v;return null;}
    private void click(String value)throws Exception {until(()->read(()->text(value)!=null),"missing "+value);ui(()->{View v=text(value);while(v!=null&&!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();if(v==null||!v.performClick())throw new AssertionError("not clickable "+value);});}
    private List<View> tiles(){List<View> out=new ArrayList<>();for(View v:tree())if(v instanceof LinearLayout&&v.isFocusable()&&String.valueOf(v.getContentDescription()).startsWith("关联影片"))out.add(v);return out;}
    private void key(int code){sendKeyDownUpSync(code);SystemClock.sleep(120);}
    private Activity openCinema(){Intent i=new Intent();i.setClassName(getTargetContext(),"org.zhixiaoce.aperture.CinemaActivity");i.putExtra("view","movies");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);return startActivitySync(i);}
    private JSONObject film(String id,String title,String genre,String director,boolean rich)throws Exception {JSONObject item=new JSONObject().put("id",id).put("title",title).put("episodes",new JSONArray().put(new JSONObject().put("title","正片").put("url","https://media.example.invalid/never-played.mp4")));if(rich)item.put("metadata",new JSONObject().put("genre",genre).put("director",director).put("year","2026").put("area","中国"));return item;}
    private String pack(boolean rich)throws Exception {JSONArray items=new JSONArray().put(film("seed","关联种子","动作","导演甲",rich)).put(film("unrelated","非关联条目","纪录片","导演乙",rich));for(int i=1;i<=6;i++)items.put(film("related"+i,"关联影片"+i,"动作",i==3?"导演甲":"导演乙",rich));return new JSONObject().put("format","kaishi.resource-pack").put("version",1).put("id","org.example.s64.related").put("revision",rich?1:2).put("title","合成关联测试").put("resources",new JSONArray().put(new JSONObject().put("id","collection").put("type","collection").put("title","S64 关联片单").put("section","movies").put("items",items))).toString();}
    private void importPack(String json)throws Exception {ui(()->{Class<?> catalog=getTargetContext().getClassLoader().loadClass("org.zhixiaoce.aperture.CinemaCatalog");Method parse=catalog.getDeclaredMethod("config",String.class,String.class,boolean.class);parse.setAccessible(true);Object config=parse.invoke(null,json,null,false);Field field=activity.getClass().getDeclaredField("vault");field.setAccessible(true);Object vault=field.get(activity);call(vault,"importConfig",new Class[]{config.getClass()},config);activity.finish();});activity=openCinema();click("S64 关联片单");click("全部");click("关联种子");until(()->read(()->text("播放")!=null),"missing detail");}
    private void picture(String name)throws Exception {Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("no native screenshot");try(FileOutputStream file=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();images.put(name+".png");}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){super.onStart();JSONObject report=new JSONObject();boolean passed=false;Bundle result=new Bundle();try{
        boolean emulator=Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish");boolean debug=(getTargetContext().getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        if(!emulator||!debug)throw new SecurityException("Only an emulator debug package may run synthetic destructive tests");
        output=new File(getTargetContext().getFilesDir(),"s64-related-tests");output.mkdirs();getTargetContext().getSharedPreferences("cinema-vault",android.content.Context.MODE_PRIVATE).edit().clear().commit();activity=openCinema();importPack(pack(true));
        check(read(()->text("相似影片")!=null),"metadata associations use the similar-film heading");
        check(read(()->tiles().size()==6),"six genuinely associated available films are rendered");
        check(read(()->"关联影片3".equals(String.valueOf(tiles().get(0).getContentDescription()))),"stronger director and genre evidence sorts first in the actual UI");
        check(read(()->text("非关联条目")==null&&text("同一片单")==null),"unrelated source entries are not labelled as similar films");
        check(read(()->activity.getCurrentFocus()==text("播放")),"detail retains primary play focus");
        ui(()->tiles().get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_RIGHT);
        check(read(()->activity.getCurrentFocus()==tiles().get(1)),"D-pad moves between actual associated-film cards");picture("related");
        ui(()->tiles().get(0).performClick());until(()->read(()->text("关联影片3")!=null&&text("播放")!=null),"associated detail did not open");
        check(read(()->activity.getCurrentFocus()==text("播放")),"opening an associated film restores primary play focus");key(KeyEvent.KEYCODE_BACK);
        until(()->read(()->"关联种子".equals(String.valueOf(activity.getCurrentFocus()==null?null:activity.getCurrentFocus().getContentDescription()))),"original source-list focus was lost");check(true,"Back from an associated detail restores the original source-list position");
        importPack(pack(false));check(read(()->text("同一片单")!=null&&text("相似影片")==null),"missing metadata keeps an honest same-collection heading");
        check(read(()->tiles().size()==6&&text("非关联条目")!=null),"fallback keeps actual other source entries without inventing titles");picture("fallback");passed=true;
    }catch(Throwable error){try{report.put("error",android.util.Log.getStackTraceString(error));if(output!=null)picture("failure");}catch(Throwable ignored){}}
        try{report.put("passed",passed).put("checks",checks).put("images",images).put("targetPackage",getTargetContext().getPackageName()).put("versionCode",getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode).put("nativeActivityExecuted",activity!=null).put("realXiaomi",false).put("syntheticResourcePack",true).put("mediaPlayed",false);if(output!=null)try(FileOutputStream file=new FileOutputStream(new File(output,"report.json"))){file.write(report.toString(2).getBytes("UTF-8"));}result.putString("report",report.toString());if(activity!=null)runOnMainSync(activity::finish);}catch(Throwable error){result.putString("writeError",error.toString());}finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
