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

/** Public-media sample task flow via host mirror. Emulator debug only; never ships. */
public final class PublicMediaTailRunner extends Instrumentation {
    private Bundle args; private Activity activity; private File output; private final JSONArray checks=new JSONArray(),images=new JSONArray(),captureErrors=new JSONArray();
    interface Task {void run()throws Exception;} interface Read<T>{T get()throws Exception;}
    private void ui(Task work)throws Exception {AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new Exception(error.get());}
    private <T>T read(Read<T> work)throws Exception {AtomicReference<T> result=new AtomicReference<>();ui(()->result.set(work.get()));return result.get();}
    private Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    private void until(Read<Boolean> ready,String why)throws Exception {long end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){if(ready.get())return;SystemClock.sleep(100);}throw new AssertionError(why);}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks.put(why);Bundle status=new Bundle();status.putString("check",why);sendStatus(0,status);}
    private void walk(View v,List<View> out){out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),out);}
    private List<View> tree(){List<View> all=new ArrayList<>();walk(activity.getWindow().getDecorView(),all);return all;}
    private View text(String value){for(View v:tree())if(v instanceof TextView&&v.isShown()&&value.equals(((TextView)v).getText().toString()))return v;return null;}
    private void click(String value)throws Exception {until(()->read(()->{View v=text(value);if(v==null)return false;while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();return v.isClickable()&&v.performClick();}),"not clickable "+value);}
    private List<View> tiles(){List<View> out=new ArrayList<>();for(View v:tree())if(v instanceof LinearLayout&&v.isFocusable()&&String.valueOf(v.getContentDescription()).startsWith("关联影片"))out.add(v);return out;}
    private void key(int code){sendKeyDownUpSync(code);SystemClock.sleep(120);}
    private Activity openCinema(String view){Intent i=new Intent();i.setClassName(getTargetContext(),"org.zhixiaoce.aperture.CinemaActivity");i.putExtra("view",view);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);return startActivitySync(i);}

    private void picture(String name)throws Exception {
        boolean resume=read(()->Boolean.TRUE.equals(field(activity,"prepared"))&&field(activity,"player")!=null&&((android.media.MediaPlayer)field(activity,"player")).isPlaying());
        if(resume)ui(()->call(field(activity,"session"),"pause",new Class[]{}));
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(args.getString("fixture")+"/capture?name="+name+"&package="+getTargetContext().getPackageName()).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(120000);
        try{int code=c.getResponseCode();if(code!=200)throw new java.io.IOException("host capture HTTP "+code);try(java.io.InputStream input=c.getInputStream()){while(input.read()!=-1){}}images.put(name+".png");}
        catch(Exception error){captureErrors.put(name+": "+error.toString());}
        finally{c.disconnect();if(resume)ui(()->call(field(activity,"session"),"play",new Class[]{}));}
    }
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private boolean playerStarted()throws Exception{return read(()->Boolean.TRUE.equals(field(activity,"prepared"))&&field(activity,"player")!=null&&((android.media.MediaPlayer)field(activity,"player")).isPlaying());}
    private boolean artwork()throws Exception{return read(()->{for(View v:tree())if(v instanceof ImageView&&v.isShown()&&((ImageView)v).getDrawable() instanceof android.graphics.drawable.BitmapDrawable)return true;return false;});}
    private Object load(String url)throws Exception{Class<?> c=getTargetContext().getClassLoader().loadClass("org.zhixiaoce.aperture.CinemaHttp");Constructor<?> constructor=c.getDeclaredConstructor();constructor.setAccessible(true);return call(constructor.newInstance(),"importUrl",new Class[]{String.class,boolean.class},url,true);}
    private void save(Object config)throws Exception{ui(()->{Object vault=field(activity,"vault");call(vault,"importConfig",new Class[]{config.getClass()},config);activity.finish();});}
    private Object shelf()throws Exception{return call(field(activity,"vault"),"shelf",new Class[]{});}
    private List<?> records(String kind,String flag)throws Exception{return (List<?>)call(shelf(),"list",new Class[]{String.class,String.class},kind,flag);}
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){JSONObject report=new JSONObject();Bundle result=new Bundle();boolean passed=false;try{
        if(!(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))||(getTargetContext().getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new SecurityException("Emulator debug only");
        output=new File(getTargetContext().getFilesDir(),"s66-public-tests");output.mkdirs();getTargetContext().getSharedPreferences("cinema-vault",0).edit().clear().commit();String base=args.getString("fixture");
        activity=openCinema("movies");Object pack=load(base+"/pack.json");save(pack);activity=openCinema("movies");click("收藏影片");until(()->read(()->text("取消收藏")!=null),"favorite seed did not persist");check(read(()->records("vod","favorite").size()==1),"real source favorite is seeded through the detail UI");
        click("Big Buck Bunny · HLS片段");click("播放");until(()->playerStarted(),"public HLS sample did not play");check(true,"Mux HLS excerpt native decoder starts");picture("hls");key(KeyEvent.KEYCODE_BACK);
        Object playlist=load(base+"/channels.m3u");Object channel=((List<?>)field(((List<?>)field(playlist,"lives")).get(0),"channels")).get(0);
        check((base+"/poster.jpg").equals(field(channel,"logo")),"production M3U import resolves relative public artwork");save(playlist);activity=openCinema("live");
        Object reread=read(()->field(activity,"config"));Object cached=((List<?>)field(((List<?>)field(reread,"lives")).get(0),"channels")).get(0);
        check((base+"/poster.jpg").equals(field(cached,"logo")),"cached channel logo survives activity restart without source base");
        check(read(()->field(activity,"sectionLayout")!=null),"live enters split section directly without choosing source or group");
        until(()->playerStarted(),"live sample playback failed");check(true,"live section automatically starts the real native decoder");ui(()->{call(activity,"showPlayerControls",new Class[]{});View choose=text("频道");if(choose==null||!choose.performClick()||!Boolean.TRUE.equals(call(field(activity,"livePanel"),"open",new Class[]{})))throw new AssertionError("channel control did not open its panel");});until(()->read(()->((ImageView)field(field(activity,"livePanel"),"logo")).getDrawable() instanceof android.graphics.drawable.BitmapDrawable),"sidebar logo not decoded");ui(()->call(field(activity,"livePanel"),"show",new Class[]{int.class},(Integer)field(activity,"playingIndex")));check(read(()->((ImageView)field(field(activity,"livePanel"),"logo")).isShown()),"relative public logo renders in native playing-channel sidebar");picture("channels");
        check(read(()->records("vod","favorite").size()==1),"replacing sources preserves previous movie favorite");passed=true;
    }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));if(output!=null)picture("failure");}catch(Throwable ignored){}}
    try{report.put("passed",passed).put("checks",checks).put("images",images).put("targetPackage",getTargetContext().getPackageName()).put("versionCode",getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode).put("realXiaomi",false).put("phase","media-tail").put("publicMediaViaHostMirror",true).put("directNativeInternetTested",false).put("screenshotBackend","host ADB screencap; media paused only for capture").put("captureErrors",captureErrors).put("visualCaptureComplete",captureErrors.length()==0);if(output!=null)try(FileOutputStream f=new FileOutputStream(new File(output,"report.json"))){f.write(report.toString(2).getBytes("UTF-8"));}result.putString("report",report.toString());if(activity!=null)runOnMainSync(activity::finish);}catch(Throwable ignored){}finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
