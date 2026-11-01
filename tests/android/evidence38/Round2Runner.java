package org.zhixiaoce.aperture;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;
import java.lang.reflect.*;import java.util.concurrent.TimeUnit;import java.util.concurrent.atomic.AtomicInteger;import org.json.*;

/** Real Android windows and key dispatch, controlled WebView and fake upload; no network permission. */
public final class Round2Runner extends Instrumentation {
    final JSONArray checks=new JSONArray(),captures=new JSONArray();Activity open;
    private void ui(Runnable action){runOnMainSync(action);}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private Object field(Object target,String name){for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new AssertionError(name);}
    private void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks.put(name);android.util.Log.i("KaishiRound2Probe","PASS "+name);}
    private <T extends Activity>T launch(Class<T> type){T a=type.cast(startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));open=a;waitForIdleSync();return a;}
    private void end(){if(open!=null){Activity a=open;ui(a::finish);waitForIdleSync();open=null;}}
    private R2GalleryProbe gallery(){R2GalleryProbe a=launch(R2GalleryProbe.class);ui(()->a.view.finished());waitForIdleSync();return a;}
    private AlertDialog confirmation(R2GalleryProbe a){return (AlertDialog)field(a,"uploadConfirmation");}
    private void request(R2GalleryProbe a){ui(()->a.view.client.shouldOverrideUrlLoading(a.view,new WebResourceRequest(){
        public android.net.Uri getUrl(){return android.net.Uri.parse("aperture://gallery/upload");}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return true;}public String getMethod(){return "GET";}public java.util.Map<String,String>getRequestHeaders(){return java.util.Collections.emptyMap();}
    }));waitForIdleSync();}
    private void positive(R2GalleryProbe a){AlertDialog d=confirmation(a);key(KeyEvent.KEYCODE_DPAD_RIGHT);check(d.getButton(-1).hasFocus(),"remote Right focuses upload positive");}
    private boolean uploaded(R2GalleryProbe a){for(String s:a.view.evaluated)if(s.contains("Settings.upload("))return true;return false;}
    private void accepted(R2GalleryProbe a)throws Exception {request(a);positive(a);key(KeyEvent.KEYCODE_DPAD_CENTER);check(a.created==1&&a.last.entered.await(3,TimeUnit.SECONDS)&&a.last.starts==1,"fresh positive gesture creates exactly one fake session");}
    private void galleryChecks()throws Exception {
        R2GalleryProbe a=gallery();key(23);key(23);check(a.created==0&&confirmation(a)==null,"neutral entry repeated CENTER cannot create a session");request(a);AlertDialog first=confirmation(a);
        check(first!=null&&first.getButton(-2).hasFocus(),"native consent defaults to Cancel");request(a);check(confirmation(a)==first&&a.created==0,"duplicate bridge request reuses pending confirmation");key(23);check(a.created==0&&confirmation(a)==null,"CENTER on default Cancel never starts upload");end();
        a=gallery();request(a);key(4);check(a.created==0&&confirmation(a)==null,"Back cancels consent without session");end();
        a=gallery();request(a);positive(a);final R2GalleryProbe stale=a;final AlertDialog staleDialog=confirmation(a);long old=SystemClock.uptimeMillis()-1000;
        ui(()->{staleDialog.dispatchKeyEvent(new KeyEvent(old,old,0,23,0));staleDialog.dispatchKeyEvent(new KeyEvent(old,old+10,1,23,0));});check(a.created==0&&confirmation(a)!=null,"opener down and old release cannot accept native consent");
        long held=SystemClock.uptimeMillis()+1;ui(()->{staleDialog.dispatchKeyEvent(new KeyEvent(held,held,0,23,0));staleDialog.dispatchKeyEvent(new KeyEvent(held,held+50,0,23,1));staleDialog.dispatchKeyEvent(new KeyEvent(held,held+60,1,23,0));});check(a.created==0,"held CENTER repeat disarms confirmation");
        key(23);check(a.created==1&&a.last.entered.await(3,TimeUnit.SECONDS),"new released CENTER after blocked gesture can accept once");waitForIdleSync();key(23);check(a.created==1,"later CENTER cannot create duplicate active session");end();check(stale.last.closed,"exit closes accepted fake upload");
        a=gallery();request(a);final R2GalleryProbe paused=a;ui(()->callActivityOnPause(paused));check(a.created==0&&confirmation(a)==null,"pause dismisses pending consent before any session");ui(()->callActivityOnResume(paused));request(a);end();check(a.created==0,"destroy with pending confirmation creates no session");
        a=gallery();a.delay=true;accepted(a);final R2GalleryProbe delayed=a;ui(()->callActivityOnPause(delayed));check(a.last.closed,"pause immediately closes delayed fake session");a.last.release.countDown();check(a.last.returned.await(3,TimeUnit.SECONDS),"delayed fake startup returns after pause");waitForIdleSync();check(!uploaded(a),"late fake URL never reaches background UI");ui(()->callActivityOnResume(delayed));end();
        a=gallery();a.delay=true;accepted(a);final R2GalleryProbe destroyed=a;end();destroyed.last.release.countDown();check(destroyed.last.returned.await(3,TimeUnit.SECONDS),"fake startup can finish after activity exit");waitForIdleSync();check(destroyed.last.closed&&!uploaded(destroyed),"late fake startup stays closed after exit");
        a=gallery();accepted(a);a.last.returned.await(3,TimeUnit.SECONDS);waitForIdleSync();final R2GalleryProbe expired=a;ui(()->expired.last.close());request(a);check(confirmation(a)!=null&&a.created==1,"expired fake requires a new confirmation");positive(a);key(23);check(a.created==2&&a.last.entered.await(3,TimeUnit.SECONDS),"fresh consent can replace an expired fake");end();
    }
    private View tagged(Activity a,String tag){return a.getWindow().getDecorView().findViewWithTag(tag);}
    private RecoveryHomeProbe nativeHome(){getTargetContext().getSharedPreferences("aperture",0).edit().putBoolean("simple_home",false).apply();RecoveryHomeProbe.engine=88;return launch(RecoveryHomeProbe.class);}
    private void diagnosticsChecks()throws Exception {
        for(int start=0;start<4;start++){
            RecoveryHomeProbe a=nativeHome();ScenicNativeHome home=(ScenicNativeHome)field(a,"scenicHome");for(int n=0;n<start;n++)key(22);
            key(19);check(a.getCurrentFocus()==tagged(a,"native-settings"),"watch start "+start+" reaches settings with remote Up");key(23);AlertDialog menu=(AlertDialog)field(a,"settingsMenuDialog");
            key(20);check("menu-entry-1".equals(menu.getCurrentFocus().getTag()),"second settings entry is reachable diagnostics");View opener=menu.getCurrentFocus();key(23);AlertDialog info=(AlertDialog)field(a,"diagnosticsDialog");check(info!=null&&info.isShowing(),"remote OK opens diagnostics from native settings");
            boolean note=false;for(int n=0;n<24;n++){View focused=info.getCurrentFocus();if(focused!=null&&"diagnostics-说明".equals(focused.getTag())){note=true;break;}key(20);}check(note,"remote Down reaches Chinese diagnostic explanation");
            if(start==0)captures.put(EvidenceCapture.capture(this,a,info.getWindow(),"diagnostics-remote","diagnostics-说明",()->info.getWindow().getDecorView().findViewWithTag("diagnostics-说明"),()->((WebDiagnostics)field(a,"diagnostics")).mode().name(),8000));
            key(4);check(menu.isShowing()&&menu.getCurrentFocus()==opener,"Back restores diagnostic settings entry");key(4);check(a.getCurrentFocus()==tagged(a,"native-settings"),"second Back restores native settings opener");end();
        }
        RecoveryHomeProbe a=nativeHome();key(20);key(20);key(20); // watch -> shortcut -> dock -> apps page
        key(20);for(int n=0;n<5;n++)key(22);key(20); // first tool -> last tool -> explicit diagnostics
        boolean direct=a.getCurrentFocus()!=null&&"native-diagnostics".equals(a.getCurrentFocus().getTag());
        check(direct,"apps tools diagnostics reachable with directions only");View directOpener=a.getCurrentFocus();key(23);AlertDialog info=(AlertDialog)field(a,"diagnosticsDialog");check(info.isShowing(),"direct tools OK opens diagnostics");key(4);check(a.getCurrentFocus()==directOpener,"direct diagnostic Back restores tools focus");end();
        // Stress the production scroll container with an oversized Chinese row; no touch scrolling.
        R2FrameProbe frame=launch(R2FrameProbe.class);final AlertDialog[] longDialog={null};final TextView[] text={null};ui(()->{LinearLayout body=new LinearLayout(frame);body.setOrientation(1);StringBuilder words=new StringBuilder("说明\n");for(int n=0;n<100;n++)words.append("这是一段供遥控器逐行阅读的中文长说明。");text[0]=TvUi.text(frame,words.toString(),21,false);text[0].setFocusableInTouchMode(true);body.addView(text[0]);longDialog[0]=TvDialogs.panel(frame,"遥控滚动测试",body,frame.target);});waitForIdleSync();
        RemoteScrollView scroll=(RemoteScrollView)text[0].getParent().getParent();int previous=-1;for(int n=0;n<80;n++){int y=scroll.getScrollY();if(y==previous)break;previous=y;key(20);}check(scroll.getScrollY()>0,"remote Down scrolls oversized Chinese row");
        android.graphics.Rect rect=new android.graphics.Rect(0,0,text[0].getWidth(),text[0].getHeight());scroll.offsetDescendantRectToMyCoords(text[0],rect);check(rect.bottom<=scroll.getScrollY()+scroll.getHeight()+2,"remote reaches final Chinese line");
        for(int n=0;n<80&&scroll.getScrollY()>0;n++)key(19);check(scroll.getScrollY()==0,"remote Up returns to first Chinese line");key(4);check(frame.getCurrentFocus()==frame.target,"long panel Back restores exact opener");end();
    }
    private JSONObject capture(R2FrameProbe a,String name,java.util.function.Supplier<View> target,long timeout)throws Exception {JSONObject c=EvidenceCapture.capture(this,a,a.getWindow(),name,name,target,()->null,timeout);captures.put(c);return c;}
    private void frameChecks()throws Exception {
        R2FrameProbe a=launch(R2FrameProbe.class);final R2FrameProbe first=a;JSONObject normal=capture(a,"frame-normal",()->first.target,8000);check(normal.optBoolean("valid")&&normal.getInt("actualAppVersionCode")==38,"normal capture has actual v38 package and completed frame");
        final View delayed=a.target;java.util.concurrent.atomic.AtomicLong shownAt=new java.util.concurrent.atomic.AtomicLong();ui(()->{delayed.setVisibility(View.INVISIBLE);new Handler(Looper.getMainLooper()).postDelayed(()->{delayed.setVisibility(View.VISIBLE);shownAt.set(SystemClock.uptimeMillis());},250);});JSONObject wait=capture(a,"frame-delayed-visible",()->delayed,8000);check(wait.optBoolean("valid")&&shownAt.get()>0&&wait.getLong("capturedUptimeMillis")>=shownAt.get(),"capture waits for delayed visible frame");
        ui(()->delayed.setVisibility(View.INVISIBLE));check(!capture(a,"frame-invisible-timeout",()->delayed,400).optBoolean("valid"),"invisible timeout produces invalid evidence");ui(()->delayed.setVisibility(View.VISIBLE));AtomicInteger reads=new AtomicInteger();View replacement=new TextView(a);
        check(!capture(a,"frame-stale-target",()->reads.incrementAndGet()==1?delayed:replacement,2000).optBoolean("valid"),"target identity switch invalidates screenshot");end();
        R2FrameProbe destroyed=a;check(!capture(destroyed,"frame-destroyed",()->delayed,400).optBoolean("valid"),"destroyed Activity cannot provide valid screenshot");
        a=launch(R2FrameProbe.class);check(!capture(a,"frame-missing-target",()->null,400).optBoolean("valid"),"missing target bounded timeout is invalid");end();
        final R2FrameProbe switching=launch(R2FrameProbe.class);AtomicInteger switchReads=new AtomicInteger();check(!capture(switching,"frame-page-switch",()->{if(switchReads.incrementAndGet()==1)switching.getWindow().getDecorView().post(()->switching.replace("second"));return switching.target;},2000).optBoolean("valid"),"actual page replacement cannot validate old target frame");end();
        final R2FrameProbe leaving=launch(R2FrameProbe.class);AtomicInteger destroyReads=new AtomicInteger();check(!capture(leaving,"frame-destroy-during-wait",()->{if(destroyReads.incrementAndGet()==1)leaving.getWindow().getDecorView().post(leaving::finish);return leaving.target;},2000).optBoolean("valid"),"destroy during frame wait invalidates capture");end();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){JSONObject result=new JSONObject();try{galleryChecks();diagnosticsChecks();frameChecks();result.put("ok",true);}catch(Throwable e){try{result.put("ok",false).put("error",e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}android.util.Log.e("KaishiRound2Probe","failed",e);}finally{try{end();result.put("checks",checks).put("passed",checks.length()).put("captures",captures).put("uploadSessionType","in-memory fake only").put("realUploadListenerStarted",false).put("realXiaomi",false).put("controlledWebView",true);try(java.io.FileOutputStream f=getTargetContext().openFileOutput("s95-round2.json",0)){f.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception ignored){}Bundle b=new Bundle();b.putString("result",result.toString());finish(result.optBoolean("ok")?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);}}
}
