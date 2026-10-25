package org.zhixiaoce.aperture.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Signed test APK, installed only on the named development emulator; never shipped in product. */
public final class CinemaRunner extends Instrumentation {
    private Bundle args;private Activity cinema;private final JSONArray checks=new JSONArray(),images=new JSONArray();
    private String stage="initializing",server,notified="";private File output;private final JSONArray clockSamples=new JSONArray();
    interface Condition{boolean ready() throws Exception;}
    @Override public void onCreate(Bundle options){args=options;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();JSONObject report=new JSONObject();boolean passed=false;
        try{
            server=args.getString("server","http://10.0.2.2:4398");output=new File(getTargetContext().getFilesDir(),"plugin-playback".equals(args.getString("mode"))?"s50-plugin-playback-tests":"s50-cinema-tests");output.mkdirs();
            if("plugin-playback".equals(args.getString("mode")))runPluginChecks();else runChecks();passed=true;
        }catch(Throwable error){try{report.put("error",android.util.Log.getStackTraceString(error));screenshot("failure",false);}catch(Exception ignored){}}
        try{report.put("passed",passed).put("stage",stage).put("checks",checks).put("images",images).put("clockSamples",clockSamples).put("targetPackage",getTargetContext().getPackageName()).put("realXiaomi",false);try(FileOutputStream stream=new FileOutputStream(new File(output,"report.json"))){stream.write(report.toString(2).getBytes("UTF-8"));}result.putString("report",report.toString());}catch(Exception error){result.putString("writeError",error.getClass().getSimpleName());}
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(stage+": "+message);checks.put(message);Bundle progress=new Bundle();progress.putString("check",message);sendStatus(0,progress);}
    private void until(Condition condition,String message) throws Exception {
        if(!stage.equals(notified)){Bundle progress=new Bundle();progress.putString("stage",stage);progress.putInt("checks",checks.length());sendStatus(0,progress);notified=stage;}
        long end=SystemClock.uptimeMillis()+45000;Throwable last=null;
        while(SystemClock.uptimeMillis()<end){try{if(condition.ready())return;}catch(Throwable error){last=error;}SystemClock.sleep(180);}
        throw new AssertionError(stage+": "+message+(last==null?"":" ("+last.getClass().getSimpleName()+")"));
    }
    private AccessibilityNodeInfo find(String text){
        AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(node.getText()!=null&&node.getText().toString().equals(text)&&node.isVisibleToUser())return node;}
        return null;
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isEditable())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=editable(node.getChild(i));if(found!=null)return found;}return null;}
    private View viewIn(View root,String text,boolean edit){
        if(!root.isShown())return null;
        if(edit&&root instanceof EditText)return root;
        if(!edit&&root instanceof TextView&&text.contentEquals(((TextView)root).getText())&&root.isClickable())return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=group.getChildCount()-1;i>=0;i--){View found=viewIn(group.getChildAt(i),text,edit);if(found!=null)return found;}}return null;
    }
    // Test-only inspection of owned windows on API26; avoids accessibility action binder
    // deadlines under software emulation, while invoking the actual visible UI callbacks.
    private View ownedView(String text,boolean edit) throws Exception {
        Class<?> global=Class.forName("android.view.WindowManagerGlobal");Object instance=global.getDeclaredMethod("getInstance").invoke(null);Field f=global.getDeclaredField("mViews");f.setAccessible(true);List<?> roots=(List<?>)f.get(instance);
        for(int i=roots.size()-1;i>=0;i--){View root=(View)roots.get(i);if(root.hasWindowFocus()){View found=viewIn(root,text,edit);if(found!=null)return found;}}return null;
    }
    private boolean ownExists(String text,boolean edit){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{ready.set(ownedView(text,edit)!=null);}catch(Exception ignored){}});return ready.get();}
    private void click(String text) throws Exception {
        until(()->ownExists(text,false),"control missing: "+text);runOnMainSync(()->{try{View v=ownedView(text,false);if(v!=null){v.requestFocus();v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),true);}}catch(Exception ignored){}});SystemClock.sleep(220);AtomicReference<Boolean> clicked=new AtomicReference<>(false);
        runOnMainSync(()->{try{View v=ownedView(text,false);if(v!=null&&v.isEnabled()){android.graphics.Rect visible=new android.graphics.Rect();if(v.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0){v.requestFocus();boolean before=v instanceof android.widget.CompoundButton&&((android.widget.CompoundButton)v).isChecked();boolean result=v.performClick();boolean changed=v instanceof android.widget.CompoundButton&&before!=((android.widget.CompoundButton)v).isChecked();clicked.set(result||changed);}}}catch(Exception ignored){}});
        check(clicked.get(),"visible native UI callback: "+text);SystemClock.sleep(220);
    }
    private void input(String text) throws Exception {until(()->ownExists("",true),"input missing");runOnMainSync(()->{try{((EditText)ownedView("",true)).setText(text);}catch(Exception e){throw new RuntimeException(e);}});checks.put("set actual native source/search field");}
    private String focused(){AtomicReference<String> text=new AtomicReference<>("");runOnMainSync(()->{View v=cinema.getWindow().getDecorView().findFocus();if(v instanceof TextView)text.set(((TextView)v).getText().toString());});return text.get();}
    private Object field(String name) throws Exception {
        AtomicReference<Object> value=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();runOnMainSync(()->{try{Field f=cinema.getClass().getDeclaredField(name);f.setAccessible(true);value.set(f.get(cinema));}catch(Exception e){error.set(e);}});if(error.get()!=null)throw error.get();return value.get();
    }
    private JSONObject player() throws Exception {
        AtomicReference<JSONObject> value=new AtomicReference<>();runOnMainSync(()->{JSONObject state=new JSONObject();try{Field f=cinema.getClass().getDeclaredField("player");f.setAccessible(true);MediaPlayer p=(MediaPlayer)f.get(cinema);state.put("exists",p!=null);Field ready=cinema.getClass().getDeclaredField("prepared");ready.setAccessible(true);boolean prepared=(Boolean)ready.get(cinema);state.put("prepared",prepared);if(p!=null&&prepared)state.put("playing",p.isPlaying()).put("position",p.getCurrentPosition()).put("duration",p.getDuration());Field sf=cinema.getClass().getDeclaredField("session");sf.setAccessible(true);Object session=sf.get(cinema);java.lang.reflect.Method m=session.getClass().getDeclaredMethod("state");m.setAccessible(true);state.put("state",String.valueOf(m.invoke(session)));}catch(Exception e){try{state.put("state","UNREADY");}catch(Exception ignored){}}value.set(state);});return value.get();
    }
    private void playing(String label) throws Exception {until(()->player().optBoolean("playing")&&player().optInt("position")>150,"native playback did not start: "+label);int first=player().getInt("position");long wall=SystemClock.uptimeMillis();until(()->player().optBoolean("playing")&&player().optInt("position")>first+250,"native playback clock did not advance: "+label);clockSamples.put(new JSONObject().put("label",label).put("mediaAdvanceMs",player().getInt("position")-first).put("wallMs",SystemClock.uptimeMillis()-wall));check(true,"native playback clock advances: "+label);}
    private int coloredPixels(Bitmap image){
        int colored=0;for(int y=image.getHeight()*35/100;y<image.getHeight()*65/100;y+=9)for(int x=image.getWidth()*30/100;x<image.getWidth()*70/100;x+=9){int c=image.getPixel(x,y),r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>45&&Math.max(r,Math.max(g,b))>65)colored++;}return colored;
    }
    private void screenshot(String name,boolean video) throws Exception {
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("screenshot unavailable");
        // Audio and its clock can start before the first video frame is rendered.
        long end=SystemClock.uptimeMillis()+45000;
        while(video&&coloredPixels(image)<=30&&SystemClock.uptimeMillis()<end){image.recycle();SystemClock.sleep(500);image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("screenshot unavailable");}
        try{try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}images.put(name+".png");if(video)check(coloredPixels(image)>30,"native SurfaceView contains decoded synthetic video: "+name);}finally{image.recycle();}
    }
    private void back() throws Exception {until(()->{AtomicReference<Boolean> focus=new AtomicReference<>(false);runOnMainSync(()->focus.set(cinema.hasWindowFocus()));return focus.get();},"native activity window did not regain focus before Back");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);}
    private void control(String route) throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(server+route).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);try{check(c.getResponseCode()==200,"synthetic source control request");}finally{c.disconnect();}}
    private void openFromHome(Activity home,String text) throws Exception {
        ActivityMonitor monitor=addMonitor("org.zhixiaoce.aperture.CinemaActivity",null,false);click(text);Activity found=monitor.waitForActivityWithTimeout(45000);removeMonitor(monitor);if(found==null)throw new AssertionError("embedded activity did not open");cinema=found;check(cinema.getClass().getName().equals("org.zhixiaoce.aperture.CinemaActivity"),"home opens embedded activity in target APK");
    }
    private void runPluginChecks() throws Exception {
        stage="plugin home entry";Intent launch=new Intent().setComponent(new ComponentName(getTargetContext().getPackageName(),"org.zhixiaoce.aperture.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity home=startActivitySync(launch);until(()->find("看电影")!=null,"native home not available");openFromHome(home,"看电影");
        stage="import plugin reference";click("来源");input("http://10.0.2.2:4401/profile.json");if(!find("允许本次导入 HTTP 来源").isChecked())click("允许本次导入 HTTP 来源");click("读取地址");click("保存");
        // Reset only fixture approval through the actual product settings so repeat runs
        // continue to exercise the explicit confirmation, rather than cached trust.
        click("来源");click("撤销插件授权");click("撤销");click("返回");
        stage="native plugin approval";click("原生插件播放测试");until(()->find("运行此插件？")!=null,"plugin approval missing");check(!(Boolean)field("playingView"),"plugin cannot play before explicit approval");screenshot("approval",false);click("信任并运行");
        stage="plugin directory and opaque episode";click("插件测试短片");click("插件线路");click("插件第一集");playing("approved JAR opaque episode");screenshot("jar-decoded",true);
        Object resolved=field("resolvedEpisode");Field url=resolved.getClass().getDeclaredField("url");url.setAccessible(true);check((server+"/video.mp4").equals(url.get(resolved)),"native plugin resolves opaque episode before MediaPlayer");
        click("前进30秒");until(()->player().optInt("position")>20000,"plugin seek did not advance");if(ownExists("收藏",false))click("收藏");click("播放 / 暂停");until(()->player().optString("state").equals("PAUSED"),"plugin pause failed");
        Object shelf=field("shelf");java.lang.reflect.Method json=shelf.getClass().getDeclaredMethod("json");json.setAccessible(true);JSONArray saved=new JSONArray((String)json.invoke(shelf));boolean found=false;for(int i=0;i<saved.length();i++){JSONObject item=saved.getJSONObject(i);if("插件第一集".equals(item.optString("episode"))&&item.optLong("position")>20000&&item.optBoolean("favorite"))found=true;}check(found,"plugin playback writes shared progress and favorite");
        back();until(()->find("插件第一集")!=null,"plugin episode list not restored");check("插件第一集".equals(focused()),"plugin back restores selected episode");stage="completed";
    }
    private void runChecks() throws Exception {
        boolean parents=getTargetContext().getPackageName().endsWith(".parents");control("/control/offline?value=0");
        stage="launch installed home";Intent launch=new Intent().setComponent(new ComponentName(getTargetContext().getPackageName(),"org.zhixiaoce.aperture.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity home=startActivitySync(launch);until(()->find("看电视")!=null,"native home not available");openFromHome(home,"看电视");
        stage="source import";if("1".equals(args.getString("requireExistingConfig"))){check(!getTargetContext().getSharedPreferences("cinema-vault",0).getString("config","").isEmpty(),"encrypted source configuration survives APK upgrade");Object config=field("config");Field lives=config.getClass().getDeclaredField("lives");lives.setAccessible(true);check(!((List<?>)lives.get(config)).isEmpty(),"saved source decrypts through AndroidKeyStore after APK upgrade");}click("来源");screenshot("sources-empty",false);input(server+"/profile.json");if(!find("允许本次导入 HTTP 来源").isChecked())click("允许本次导入 HTTP 来源");click("读取地址");click("保存");
        until(()->find("直播清单")!=null,"saved live source missing");
        String sealed=getTargetContext().getSharedPreferences("cinema-vault",0).getString("config","");check(!sealed.isEmpty()&&!sealed.contains("http")&&!sealed.contains("家庭JSON站"),"AndroidKeyStore-backed source record is not plaintext");
        if("import-only".equals(args.getString("mode"))){stage="completed";return;}
        stage="native live MP4";click("直播清单");screenshot("channels",false);click("台一");playing("MP4 channel");screenshot("live-mp4",true);
        stage="native live HLS";click("下一台");playing("HLS channel");screenshot("live-hls",true);
        stage="pause and audio interruption";click("播放 / 暂停");until(()->!player().optBoolean("playing"),"pause failed");check(player().optString("state").equals("PAUSED"),"pause remains explicit");click("播放 / 暂停");playing("explicit resume");
        AudioManager manager=(AudioManager)getTargetContext().getSystemService("audio");AudioFocusRequest focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{}).build();
        AtomicReference<Integer> granted=new AtomicReference<>();runOnMainSync(()->granted.set(manager.requestAudioFocus(focus)));check(granted.get()==AudioManager.AUDIOFOCUS_REQUEST_GRANTED,"real Android AudioManager grants a transient competing focus request");until(()->player().optString("state").equals("INTERRUPTED")&&!player().optBoolean("playing"),"audio focus did not interrupt native player");runOnMainSync(()->manager.abandonAudioFocusRequest(focus));SystemClock.sleep(800);check(!player().optBoolean("playing"),"audio focus return does not auto-resume");click("播放 / 暂停");playing("resume after transient focus");
        stage="back from channel";back();until(()->find("台二")!=null,"channel list not restored");check(!(Boolean)field("playingView"),"back releases native playback view");
        check("台二".equals(focused()),"back restores selected live channel focus; actual="+focused());
        back();back();until(()->find("看电影")!=null,"home return missing");openFromHome(home,"看电影");
        stage="catalogue and search";click("家庭JSON站");click("搜索");input("周末");click("搜索");until(()->find("周末短片\n测试影片")!=null,"search result missing");screenshot("search",false);click("周末短片\n测试影片");click("家庭线路");click("下一页");click("第13集");playing("selected episode");screenshot("episode-13",true);
        stage="native seek";click("后退30秒");click("前进30秒");until(()->player().optInt("position")>20000,"native seeking did not advance");
        stage="viewing state";if(ownExists("收藏",false))click("收藏");click("播放 / 暂停");until(()->player().optString("state").equals("PAUSED"),"episode did not pause before state check");
        Object shelf=field("shelf");java.lang.reflect.Method json=shelf.getClass().getDeclaredMethod("json");json.setAccessible(true);JSONArray saved=new JSONArray((String)json.invoke(shelf));JSONObject watched=null;for(int i=0;i<saved.length();i++)if("第13集".equals(saved.getJSONObject(i).optString("episode")))watched=saved.getJSONObject(i);
        check(watched!=null&&watched.optLong("position")>20000&&watched.optBoolean("favorite"),"real native episode checkpoint preserves progress and favorite");String encryptedShelf=getTargetContext().getSharedPreferences("cinema-vault",0).getString("shelf","");check(!encryptedShelf.isEmpty()&&!encryptedShelf.contains("第13集")&&!encryptedShelf.contains("position"),"viewing shelf uses AndroidKeyStore encrypted storage");
        stage="episode back focus";back();until(()->find("第13集")!=null,"episode page not restored");check("第13集".equals(focused()),"back restores selected episode and page; actual="+focused());
        stage="failed media and recovery";back();back();back();click("全部");click("失效影片");click("错误线路");click("播放");until(()->player().optString("state").equals("ERROR"),"missing media did not produce a stable error");screenshot("media-error",false);back();check(!(Boolean)field("playingView"),"failed media can return to the catalogue");
        stage="source offline and health";control("/control/offline?value=1");click("来源");click("检查来源");until(()->find("家庭JSON站：不可读取")!=null,"offline API not classified");until(()->find("HTML来源：不可读取")!=null,"HTTP 200 HTML incorrectly treated as healthy media");screenshot("health",false);control("/control/offline?value=0");
        stage="source recovery and cancelled clear";click("返回来源设置");String beforeClear=getTargetContext().getSharedPreferences("cinema-vault",0).getString("config","");click("清除本机来源");click("返回");check(beforeClear.equals(getTargetContext().getSharedPreferences("cinema-vault",0).getString("config","")),"cancelling clear retains encrypted source record");until(()->cinema.hasWindowFocus()&&"清除本机来源".equals(focused()),"source settings did not regain focus after cancelled dialog");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);until(()->"返回".equals(focused()),"D-pad did not reach source settings Return");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);check(true,"real D-pad reaches and activates scrolled source settings Return");click("家庭JSON站");until(()->find("家庭电影")!=null,"source did not recover after offline fixture reset");check(true,"catalogue recovers after source returns online");screenshot("recovered",false);
        if(parents){stage="parents series home entry";back();back();until(()->find("看电视剧")!=null,"parents series entry missing");openFromHome(home,"看电视剧");check("series".equals(field("view")),"parents third core entry routes into embedded series view");click("家庭JSON站");click("家庭剧集");until(()->find("周末短片\n测试影片")!=null,"parents series category missing");screenshot("parents-series",false);}
        stage="completed";
    }
}
