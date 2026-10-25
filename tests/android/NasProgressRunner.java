package org.zhixiaoce.aperture.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Runs only on a dedicated development emulator with the loopback synthetic SMB fixture. */
public final class NasProgressRunner extends Instrumentation {
    private Bundle options;private Activity cinema,nas;private JSONObject ref;private Object vault;
    private final JSONArray checks=new JSONArray();private final JSONObject timings=new JSONObject();private String stage="setup";private File output;
    interface Work { void run()throws Exception; }
    interface Condition { boolean ready()throws Exception; }
    @Override public void onCreate(Bundle args){options=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();JSONObject report=new JSONObject();boolean passed=false;
        try{output=new File(getTargetContext().getFilesDir(),"s50-nas-tests");output.mkdirs();runChecks();passed=true;}
        catch(Throwable failure){try{report.put("error",android.util.Log.getStackTraceString(failure));if(nas!=null){report.put("playerAtFailure",player());report.put("recordAtFailure",record());}}catch(Exception ignored){}}
        try{report.put("passed",passed).put("stage",stage).put("checks",checks).put("timings",timings).put("targetPackage",getTargetContext().getPackageName()).put("syntheticSmb",true).put("realXiaomi",false);}catch(Exception failed){result.putString("coreReportError",failed.toString());}
        result.putString("report",report.toString());
        try{report.put("environment",environment());}catch(Exception failed){try{report.put("environmentError",failed.toString()).put("metadataIncomplete",true);}catch(Exception ignored){}}
        result.putString("report",report.toString());
        try{try(FileOutputStream stream=new FileOutputStream(new File(output,"report.json"))){stream.write(report.toString(2).getBytes("UTF-8"));}}catch(Exception failed){result.putString("writeError",failed.toString());}
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Class<?> type(String name)throws Exception{return getTargetContext().getClassLoader().loadClass("org.zhixiaoce.aperture."+name);}
    private JSONObject environment()throws Exception{
        android.content.pm.PackageInfo product=getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0),provider=android.webkit.WebView.getCurrentWebViewPackage();
        JSONObject result=new JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("abi",new JSONArray(java.util.Arrays.asList(android.os.Build.SUPPORTED_ABIS))).put("versionCode",product.versionCode).put("versionName",product.versionName);
        if(provider!=null)result.put("webView",new JSONObject().put("packageName",provider.packageName).put("versionName",provider.versionName).put("versionCode",provider.versionCode));return result;
    }
    private Object construct(String name)throws Exception{Constructor<?> c=type(name).getDeclaredConstructor(Context.class);c.setAccessible(true);return c.newInstance(getTargetContext());}
    private Object call(Object owner,String name,Class<?>[] args,Object... values)throws Exception{Method method=owner.getClass().getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(owner,values);}
    private Object stat(String type,String name,Class<?>[] args,Object... values)throws Exception{Method method=type(type).getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(null,values);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void main(Work work)throws Exception{AtomicReference<Exception> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Exception e){error.set(e);}});if(error.get()!=null)throw error.get();}
    private void check(boolean value,String text){if(!value)throw new AssertionError(stage+": "+text);checks.put(text);Bundle progress=new Bundle();progress.putString("check",text);sendStatus(0,progress);}
    private void until(Condition condition,String text)throws Exception{until(condition,text,45000);}
    private void until(Condition condition,String text,long timeout)throws Exception{Bundle progress=new Bundle();progress.putString("stage",stage);sendStatus(0,progress);long end=SystemClock.uptimeMillis()+timeout;Throwable last=null;while(SystemClock.uptimeMillis()<end){try{if(condition.ready())return;}catch(Throwable error){last=error;}SystemClock.sleep(180);}throw new AssertionError(stage+": "+text+(last==null?"":" "+last));}
    private View find(View root,String text){if(!root.isShown())return null;if(root instanceof TextView&&text.contentEquals(((TextView)root).getText())&&root.isClickable())return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=group.getChildCount()-1;i>=0;i--){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private View visible(String text)throws Exception{Class<?> global=Class.forName("android.view.WindowManagerGlobal");Object instance=global.getDeclaredMethod("getInstance").invoke(null);Field field=global.getDeclaredField("mViews");field.setAccessible(true);List<?> views=(List<?>)field.get(instance);for(int i=views.size()-1;i>=0;i--){View root=(View)views.get(i);if(root.hasWindowFocus()){View found=find(root,text);if(found!=null)return found;}}return null;}
    private void click(String text)throws Exception{until(()->{AtomicReference<Boolean> ready=new AtomicReference<>(false);main(()->ready.set(visible(text)!=null));return ready.get();},"missing native control "+text);main(()->{View view=visible(text);view.requestFocus();view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);});SystemClock.sleep(200);AtomicReference<Boolean> clicked=new AtomicReference<>(false);main(()->{View view=visible(text);android.graphics.Rect area=new android.graphics.Rect();if(view!=null&&view.getGlobalVisibleRect(area)&&area.width()>0&&area.height()>0)clicked.set(view.performClick());});check(clicked.get(),"native control: "+text);}
    private JSONObject player()throws Exception{AtomicReference<JSONObject> result=new AtomicReference<>();main(()->{JSONObject state=new JSONObject();MediaPlayer p=(MediaPlayer)field(nas,"player");boolean prepared=(Boolean)field(nas,"prepared");state.put("prepared",prepared);if(p!=null&&prepared)state.put("position",p.getCurrentPosition()).put("duration",p.getDuration()).put("playing",p.isPlaying()).put("width",p.getVideoWidth());Object session=field(nas,"session");state.put("state",String.valueOf(call(session,"state",new Class<?>[0])));result.set(state);});return result.get();}
    private JSONObject record()throws Exception{Object shelf=call(vault,"shelf",new Class<?>[0]);return (JSONObject)call(shelf,"get",new Class<?>[]{JSONObject.class},ref);}
    private void seed()throws Exception{
        Object nasVault=construct("NasVault");JSONArray previous=(JSONArray)call(nasVault,"read",new Class<?>[0]);for(int i=0;i<previous.length();i++){JSONObject item=previous.getJSONObject(i);if("S50 测试 NAS".equals(item.optString("name")))call(nasVault,"remove",new Class<?>[]{String.class},item.getString("id"));}JSONObject input=new JSONObject().put("name","S50 测试 NAS").put("brand","通用 SMB / WebDAV").put("root",options.getString("server","smb://10.0.2.2:4458/Family")).put("user",options.getString("user","ubuntu")).put("password","synthetic-nas-only").put("domain","");
        JSONObject connection=(JSONObject)call(nasVault,"add",new Class<?>[]{JSONObject.class},input);
        ref=(JSONObject)stat("NasWatchRoute","ref",new Class<?>[]{String.class,String.class,String.class},connection.getString("id"),"电影/周末/家庭测试.mp4","电影");vault=construct("CinemaVault");
        Class<?> edit=type("CinemaShelfStore$Edit");Object mutation=Proxy.newProxyInstance(edit.getClassLoader(),new Class<?>[]{edit},(proxy,method,args)->{if(method.getName().equals("apply")){call(args[0],"watch",new Class<?>[]{JSONObject.class,String.class,String.class,long.class,long.class,boolean.class,long.class},ref,"NAS",ref.getString("title"),40000L,120000L,false,System.currentTimeMillis());return null;}return null;});
        call(vault,"updateShelf",new Class<?>[]{edit},mutation);
    }
    private void openSaved()throws Exception{
        ActivityMonitor monitor=addMonitor("org.zhixiaoce.aperture.NasActivity",null,false);
        main(()->call(cinema,"openSaved",new Class<?>[]{JSONObject.class,Runnable.class},ref,(Runnable)()->{}));
        nas=monitor.waitForActivityWithTimeout(45000);removeMonitor(monitor);check(nas!=null,"unified cinema saved-item route opens native NAS Activity");
        until(()->player().optBoolean("playing")&&player().optInt("width")>0,"NAS decoder did not start");
    }
    private void runChecks()throws Exception{
        stage="seed stable synthetic reference";seed();String sealed=getTargetContext().getSharedPreferences("cinema-vault",0).getString("shelf","");check(!sealed.isEmpty()&&!sealed.contains("家庭测试")&&!sealed.contains(ref.getString("source")),"AndroidKeyStore shared shelf is encrypted");
        cinema=startActivitySync(new Intent().setComponent(new ComponentName(getTargetContext().getPackageName(),"org.zhixiaoce.aperture.CinemaActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        stage="unified NAS reopen and resume";openSaved();check(player().getInt("position")>=39000,"actual NAS MediaPlayer resumes saved position");main(()->{check("电影/周末".equals(field(nas,"folder")),"saved item restores its parent folder");check(((Integer)field(nas,"page"))==1,"saved item restores its file page instead of forcing first page");});
        int first=player().getInt("position");long clockStarted=SystemClock.uptimeMillis();until(()->player().getInt("position")>=first+250,"NAS media clock did not advance");timings.put("advance250msWaitMs",SystemClock.uptimeMillis()-clockStarted);check(true,"NAS media clock advances from SMB bytes");
        stage="NAS pause and favorite";click("播放 / 暂停");until(()->!player().optBoolean("playing"),"pause failed");long paused=player().getInt("position");check(Math.abs(record().getLong("position")-paused)<1500,"pause persists current NAS position");click("收藏");check(record().optBoolean("favorite"),"NAS favorite persisted in unified vault");click("播放 / 暂停");until(()->player().optBoolean("playing"),"explicit resume after favorite did not start");long before=record().getLong("watchedAt");until(()->record().optLong("watchedAt")>before+1000,"15-second progress checkpoint did not run");check(true,"periodic NAS checkpoint persists without leaving player");
        stage="completion and start over";check(player().optBoolean("playing"),"player remains active before completion seek");long completionStarted=SystemClock.uptimeMillis();main(()->{MediaPlayer p=(MediaPlayer)field(nas,"player");p.seekTo(p.getDuration()-800);});until(()->player().optString("state").equals("COMPLETED"),"NAS did not complete",180000);timings.put("completionAfterSeekWaitMs",SystemClock.uptimeMillis()-completionStarted);check(record().optBoolean("complete"),"native completion is recorded");click("从头播放");until(()->player().optBoolean("playing")&&player().optInt("position")<5000,"start over failed");check(!record().optBoolean("complete"),"start over clears completed record");
        stage="return and cross-Activity refresh";click("前进30秒");until(()->player().optInt("position")>20000,"seek failed");click("返回文件夹");check(record().optLong("position")>20000,"exit saves final position");main(nas::finish);until(()->cinema.hasWindowFocus(),"cinema did not resume");
        main(()->{Object shelf=field(cinema,"shelf");JSONObject r=(JSONObject)call(shelf,"get",new Class<?>[]{JSONObject.class},ref);check(r!=null&&r.optBoolean("favorite")&&r.optLong("position")>20000,"Cinema Activity refresh sees NAS favorite and latest progress");});click("我的");click("收藏 NAS");
        stage="reopen again";openSaved();check(player().optInt("position")>20000,"second unified reopen resumes latest progress");main(nas::finish);until(()->cinema.hasWindowFocus(),"cinema did not regain focus");
        stage="deleted source";Object nasVault=construct("NasVault");call(nasVault,"remove",new Class<?>[]{String.class},ref.getString("source"));boolean rejected=false;try{stat("NasActivity","openSaved",new Class<?>[]{Activity.class,JSONObject.class},cinema,ref);}catch(InvocationTargetException expected){rejected=true;}check(rejected,"removed NAS fails explicitly without using another connection");check(record().optBoolean("favorite"),"source removal retains favorite record for explicit user removal");stage="completed";
    }
}
