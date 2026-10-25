package org.zhixiaoce.aperture.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Development instrumentation only. Reports emulator observations separately from Xiaomi hardware. */
public final class FeedbackRunner extends Instrumentation {
    private Activity home;private WebView web;private File output;private String stage="starting";private final JSONArray checks=new JSONArray(),images=new JSONArray();
    @Override public void onCreate(Bundle args){start();}
    private void check(boolean valid,String message){if(!valid)throw new AssertionError(stage+": "+message);checks.put(message);Bundle b=new Bundle();b.putString("check",message);sendStatus(0,b);}
    interface Condition{boolean get()throws Exception;}
    private void until(Condition condition,String message)throws Exception{long end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(condition.get())return;SystemClock.sleep(150);}throw new AssertionError(stage+": "+message);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    private String js(String code)throws Exception{AtomicReference<String> result=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);runOnMainSync(()->web.evaluateJavascript(code,value->{result.set(value);latch.countDown();}));if(!latch.await(25,TimeUnit.SECONDS))throw new AssertionError("WebView JS did not answer");return result.get();}
    private AccessibilityNodeInfo find(String text){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text);for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&text.contentEquals(n.getText()==null?"":n.getText()))return n;return null;}
    private Rect bounds(String text)throws Exception{until(()->find(text)!=null,"missing visible control "+text);Rect r=new Rect();find(text).getBoundsInScreen(r);return r;}
    private void click(String text)throws Exception{Rect r=bounds(text);long now=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(android.view.MotionEvent.obtain(now,now,0,r.centerX(),r.centerY(),0),true);getUiAutomation().injectInputEvent(android.view.MotionEvent.obtain(now,now+40,1,r.centerX(),r.centerY(),0),true);}
    private void key(int code){sendKeyDownUpSync(code);SystemClock.sleep(200);}
    private void picture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("no screenshot");File file=new File(output,name+".png");try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();images.put(file.getName());}
    private Activity start(String name,String view){Intent intent=new Intent().setClassName(getTargetContext().getPackageName(),"org.zhixiaoce.aperture."+name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(view!=null)intent.putExtra("view",view);return startActivitySync(intent);}
    @Override public void onStart(){Bundle result=new Bundle();JSONObject report=new JSONObject();boolean passed=false;try{
        output=new File(getTargetContext().getFilesDir(),"s53-feedback-tests");output.mkdirs();home=start("MainActivity",null);boolean parents=getTargetContext().getPackageName().endsWith(".parents");
        stage="offline WebView frame";
        // The official API28 provider is below the production glass-home threshold. Force ONLY the test view.
        // This exercises native focus/scroll behavior without changing the production provider gate.
        runOnMainSync(()->{try{call(home,"createWeb",new Class[]{boolean.class},true);web=(WebView)field(home,"web");web.loadUrl("https://appassets.androidplatform.net/assets/index.html?demo=1&edition="+(parents?"parents":"standard"));}catch(Exception e){throw new RuntimeException(e);}});
        until(()->"true".equals(js("!!window.apertureRemoteKey")),"launcher not ready");SystemClock.sleep(1200);
        runOnMainSync(()->web.scrollTo(260,160));SystemClock.sleep(200);check(web.getScrollX()==0&&web.getScrollY()==0,"native WebView outer viewport rejects focus scrolling");
        String measure="JSON.stringify((function(){var ids="+(parents?"['.wordmark','.header-time','.destinations','.shortcuts-row']":"['.wordmark','.header-time','#home-watch','#personal-panel','#home-dock']")+";return {w:innerWidth,h:innerHeight,x:scrollX,y:scrollY,rects:ids.map(function(s){var r=document.querySelector(s).getBoundingClientRect();return {s:s,x:r.left,y:r.top,right:r.right,bottom:r.bottom};})};})())";
        JSONObject frame=new JSONObject(new JSONArray("["+js(measure)+"]").getString(0));check(frame.getInt("x")==0&&frame.getInt("y")==0,"DOM viewport remains at origin");for(int i=0;i<frame.getJSONArray("rects").length();i++){JSONObject r=frame.getJSONArray("rects").getJSONObject(i);check(r.getDouble("x")>=frame.getDouble("w")*.025&&r.getDouble("right")<=frame.getDouble("w")*.975,r.getString("s")+" inside TV horizontal safe area");}picture("home");
        stage="native MENU dialog";key(KeyEvent.KEYCODE_MENU);Rect first=bounds("返回客厅入口"),second=bounds(parents?"连接 / 更换家庭服务":"打开直播");int width=getTargetContext().getResources().getDisplayMetrics().widthPixels;check(first.width()<=width*.48,"MENU action bounded below 48 percent of screen width");check(second.top-first.bottom>=12,"MENU actions have at least 12 physical pixels between them");picture("menu");key(KeyEvent.KEYCODE_BACK);
        stage="cinema margins and settings";Activity cinema=start("CinemaActivity","live");Rect mine=bounds("我的"),source=bounds("来源"),nas=bounds("NAS");check(source.left-mine.right>=16&&nas.left-source.right>=16,"native cinema toolbar has real 16px minimum spaces");check(nas.right<=width*.95+1,"cinema toolbar retains right safe margin");Rect previous=bounds("上一页"),next=bounds("下一页"),back=bounds("返回");check(next.left-previous.right>=16&&back.left-next.right>=16,"cinema paging actions are separated");picture("cinema");click("来源");Rect read=bounds("读取地址");check(read.width()<width*.48,"source configuration is a bounded modal action");picture("sources-modal");key(KeyEvent.KEYCODE_BACK);runOnMainSync(cinema::finish);
        stage="NAS configuration modal";Activity nasActivity=start("NasActivity",null);click("添加连接");click("Xiaomi 智能存储 · 新款 NAS");Rect name=bounds("在电视上叫什么");check(name.left>width*.24&&name.right<width*.76,"NAS form stays within a centered modal");picture("nas-modal");key(KeyEvent.KEYCODE_BACK);runOnMainSync(nasActivity::finish);
        stage="return to original home";home=start("MainActivity",null);AtomicReference<String> expected=new AtomicReference<>();runOnMainSync(()->{try{Object destinations=field(home,"destinations");ResolveInfo selected=(ResolveInfo)call(destinations,"selected",new Class[]{String.class},"system");if(selected==null)throw new AssertionError("original launcher not resolved");expected.set(selected.activityInfo.packageName);call(destinations,"launch",new Class[]{String.class},"system");}catch(Exception e){throw new RuntimeException(e);}});
        until(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&expected.get().contentEquals(root.getPackageName());},"original launcher never became foreground");check(home.isFinishing(),"Kaishi task finishes only after original launcher starts");check(!expected.get().startsWith("org.zhixiaoce.aperture")&&!expected.get().startsWith("org.zhixiaoce.kaishi"),"neither Kaishi edition can be chosen as original home");picture("original-home");report.put("originalHomePackage",expected.get());passed=true;
    }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));if(output!=null)picture("failure");}catch(Exception ignored){}}
    try{report.put("passed",passed).put("stage",stage).put("checks",checks).put("images",images).put("targetPackage",getTargetContext().getPackageName()).put("realXiaomi",false).put("forcedOldProviderForScrollHarness",true);try(FileOutputStream out=new FileOutputStream(new File(output,"report.json"))){out.write(report.toString(2).getBytes("UTF-8"));}result.putString("report",report.toString());}catch(Exception e){result.putString("writeError",e.toString());}finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
