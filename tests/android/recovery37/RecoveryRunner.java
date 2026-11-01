package org.zhixiaoce.aperture;
import android.app.*;import android.content.*;import android.os.Bundle;import android.view.*;import android.widget.*;import java.lang.reflect.*;import org.json.*;

/** Controlled callbacks in real production Activities. No real renderer crash, resources or LAN. */
public final class RecoveryRunner extends Instrumentation {
    private JSONArray checks=new JSONArray(),captures=new JSONArray();private Activity open;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void ui(Runnable action){runOnMainSync(action);}
    private Object field(Object target,String name){for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException next){}catch(Exception e){throw new RuntimeException(e);}throw new AssertionError(name);}
    private void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks.put(name);android.util.Log.i("KaishiRecoveryProbe","PASS "+name);}
    private RecoveryHomeProbe home(){RecoveryHomeProbe.engine=90;return home(false);}
    private RecoveryHomeProbe home(boolean nativePref){
        getTargetContext().getSharedPreferences("aperture",0).edit().putBoolean("simple_home",nativePref).apply();
        Intent i=new Intent().setComponent(new ComponentName(getTargetContext(),RecoveryHomeProbe.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        RecoveryHomeProbe a=(RecoveryHomeProbe)startActivitySync(i);open=a;waitForIdleSync();return a;
    }
    private RecoveryGalleryProbe gallery(){Intent i=new Intent().setComponent(new ComponentName(getTargetContext(),RecoveryGalleryProbe.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);RecoveryGalleryProbe a=(RecoveryGalleryProbe)startActivitySync(i);open=a;waitForIdleSync();return a;}
    private void end(){if(open!=null){Activity a=open;ui(a::finish);ProbeWait.until(a::isDestroyed,10000,"Activity destroyed");open=null;}}
    private boolean nativeHome(RecoveryHomeProbe a){return field(a,"web")==null&&field(a,"scenicHome")!=null;}
    private void snap(String name){
        Activity activity=open;android.view.Window window=activity.getWindow();
        java.util.function.Supplier<View> target=()->{
            if(name.equals("home-recovery"))return (View)field(activity,"scenicHome");
            return window.getDecorView().findViewWithTag(name.equals("home-loading")?"kaishi-web-loading":"kaishi-gallery-error");
        };
        capture(name,activity,window,target);
    }
    private void capture(String name,Activity activity,android.view.Window window,java.util.function.Supplier<View> target){
        try{captures.put(EvidenceCapture.capture(this,activity,window,name,name,target,()->activity instanceof MainActivity?((WebDiagnostics)field(activity,"diagnostics")).mode().name():null,8000));}
        catch(Exception error){try{captures.put(new JSONObject().put("caseId",name).put("valid",false).put("invalidReason","capture-helper-exception"));}catch(Exception ignored){}}
    }
    @Override public void onStart(){JSONObject result=new JSONObject();try{
        RecoveryHomeProbe a=home();final RecoveryHomeProbe fast=a;
        check(field(a,"homeLoading")!=null,"home native loading visible");snap("home-loading");ui(()->fast.view.finished());
        check(Boolean.TRUE.equals(field(a,"ready")),"home fast ready");ui(()->fast.clock.advance(12000));check(!nativeHome(a),"home ready cancels timer");end();
        a=home();final RecoveryHomeProbe silent=a;ui(()->silent.clock.advance(11999));check(!nativeHome(a),"home waits up to budget");ui(()->silent.clock.advance(1));check(nativeHome(a),"home no pageFinished recovers at 12s");check(a.view.detachedAtDestroy,"home detached before destroy");snap("home-recovery");end();
        a=home();final RecoveryHomeProbe missing=a;ui(()->{missing.view.answer="false";missing.view.finished();missing.clock.advance(500);});check(nativeHome(a),"home missing JS enters native recovery");end();
        a=home();final RecoveryHomeProbe jsThrows=a;ui(()->{jsThrows.view.throwReadiness=true;jsThrows.view.finished();});check(nativeHome(a),"home JS evaluation exception safely recovers");end();
        a=home();final RecoveryHomeProbe paused=a;ui(()->{paused.clock.advance(3000);callActivityOnPause(paused);paused.clock.advance(60000);});check(!nativeHome(a),"home background does not consume deadline");ui(()->{callActivityOnResume(paused);paused.clock.advance(8999);});check(!nativeHome(a),"home remaining foreground budget retained");ui(()->paused.clock.advance(1));check(nativeHome(a),"home resume remaining budget bounded");end();
        a=home();final RecoveryHomeProbe focusHome=a;ui(()->{focusHome.clock.advance(2000);focusHome.onWindowFocusChanged(false);focusHome.clock.advance(60000);});check(!nativeHome(a),"home assistant or dialog focus loss pauses load deadline");ui(()->{focusHome.onWindowFocusChanged(true);focusHome.clock.advance(9999);});check(!nativeHome(a),"home focus return preserves remaining budget");ui(()->focusHome.clock.advance(1));check(nativeHome(a),"home timeout bounded after focus returns");end();
        a=home();final RecoveryHomeProbe late=a;ControlledWebView old=a.view;ui(()->{late.view.hold=true;late.view.finished();late.clock.advance(12000);late.view.lateReady();late.view.finished();});check(nativeHome(a)&&!Boolean.TRUE.equals(field(a,"ready")),"late home callbacks cannot revive native recovery");check(old.evalAfterDestroy==0,"late home callback never evaluates destroyed view");end();
        a=home();final RecoveryHomeProbe newer=a;ControlledWebView first=a.view;ui(()->{first.hold=true;first.finished();newer.onNewIntent(new Intent());first.lateReady();});check(newer.view!=first&&!Boolean.TRUE.equals(field(a,"ready")),"home new navigation rejects old JS result");ui(()->newer.view.finished());check(Boolean.TRUE.equals(field(a,"ready")),"new home navigation can become ready");end();
        a=home();final RecoveryHomeProbe gone=a;ui(()->gone.view.rendererGone());check(nativeHome(a)&&a.view.stops==0&&a.view.detachedAtDestroy,"home renderer-gone detach destroy without stopLoading");end();
        a=home();final RecoveryHomeProbe readyGone=a;ui(()->{readyGone.view.finished();readyGone.view.rendererGone();});check(nativeHome(a),"home renderer exit after ready recovers");end();
        a=home();final RecoveryHomeProbe doc=a;ui(()->doc.view.error(false,false));check(!nativeHome(a),"home subresource error does not replace page");ui(()->doc.view.error(true,true));check(nativeHome(a),"home main HTTP error recovers");end();
        a=home();final RecoveryHomeProbe leave=a;ControlledWebView leaving=a.view;ui(()->{leaving.hold=true;leaving.finished();});end();ui(()->{leaving.lateReady();leave.clock.advance(12000);});check(leaving.evalAfterDestroy==0,"destroyed home ignores pending JS and timer");
        a=home();final RecoveryHomeProbe backLoading=a;ui(backLoading::onBackPressed);check(nativeHome(a)&&field(a,"returnHomeDialog")!=null,"home Back while loading restores native confirmation");ui(()->((AlertDialog)field(backLoading,"returnHomeDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());end();
        RecoveryHomeProbe.engine=88;a=home(false);check(a.view==null&&nativeHome(a),"WebView below89 still native");end();RecoveryHomeProbe.engine=90;a=home(true);check(a.view==null&&((WebDiagnostics)field(a,"diagnostics")).mode()==WebDiagnostics.Home.NATIVE_SELECTED,"manual native mode distinguishable");end();
        RecoveryGalleryProbe g=gallery();final RecoveryGalleryProbe empty=g;
        check(field(g,"upload")==null&&new GalleryStore(g).snapshot().getJSONArray("photos").length()==0,"empty gallery creates no upload session");ui(()->empty.view.finished());check(Boolean.TRUE.equals(field(g,"ready")),"empty gallery ready");ui(empty::onBackPressed);check(g.isFinishing(),"gallery JS false returns natively");end();
        g=gallery();final RecoveryGalleryProbe noFinish=g;ui(()->noFinish.clock.advance(10000));check(field(g,"web")==null&&!g.isFinishing(),"gallery no pageFinished gives visible native failure");snap("gallery-error");check(g.view.detachedAtDestroy,"gallery detaches before destroy");ProbeKeys.key(this,open,KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(g.isFinishing(),"gallery failure native return button responds to OK");end();
        g=gallery();final RecoveryGalleryProbe noJs=g;ui(()->{noJs.view.answer="false";noJs.view.finished();noJs.clock.advance(500);});check(field(g,"web")==null&&field(g,"upload")==null,"gallery JS failure cancels and leaves upload closed");end();
        g=gallery();final RecoveryGalleryProbe galleryThrows=g;ui(()->{galleryThrows.view.throwReadiness=true;galleryThrows.view.finished();});check(field(g,"web")==null,"gallery JS evaluation exception shows native failure");end();
        g=gallery();final RecoveryGalleryProbe mainError=g;ui(()->mainError.view.error(false,false));check(field(g,"web")!=null,"gallery image error ignored by page recovery");ui(()->mainError.view.error(true,false));check(field(g,"web")==null,"gallery main document failure controlled");end();
        g=gallery();final RecoveryGalleryProbe renderer=g;ui(()->renderer.view.rendererGone());check(field(g,"web")==null&&g.view.stops==0&&g.view.detachedAtDestroy,"gallery renderer-gone never calls stop or JS");check(field(g,"upload")==null,"gallery renderer failure leaves no upload listener");end();
        g=gallery();final RecoveryGalleryProbe loadingBack=g;ui(loadingBack::onBackPressed);check(g.isFinishing()&&g.view.pending.isEmpty(),"gallery Back before JS does not wait for JS");end();
        g=gallery();final RecoveryGalleryProbe hungBack=g;ui(()->{hungBack.view.finished();hungBack.view.holdBack=true;hungBack.onBackPressed();hungBack.clock.advance(599);});check(!g.isFinishing(),"gallery Back grace before native escape");ui(()->hungBack.clock.advance(1));check(g.isFinishing(),"gallery missing Back callback exits at600ms");ui(()->hungBack.view.lateReady());check(g.view.evalAfterDestroy==0,"late gallery Back callback ignored after native exit");end();
        g=gallery();final RecoveryGalleryProbe repeated=g;ui(()->{repeated.view.finished();repeated.view.holdBack=true;repeated.onBackPressed();repeated.onBackPressed();});check(g.isFinishing()&&g.view.pending.size()==1,"repeat Back exits once without piling up JS");end();
        g=gallery();final RecoveryGalleryProbe albumPause=g;ui(()->{albumPause.clock.advance(2000);callActivityOnPause(albumPause);albumPause.clock.advance(60000);});check(field(g,"web")!=null,"gallery background cancels deadline");ui(()->{callActivityOnResume(albumPause);albumPause.clock.advance(7999);});check(field(g,"web")!=null,"gallery resumes remaining budget");ui(()->albumPause.clock.advance(1));check(field(g,"web")==null,"gallery foreground timeout after resume");end();
        g=gallery();final RecoveryGalleryProbe full=g;ui(()->{full.view.finished();full.view.fullscreen=true;full.onBackPressed();});check(!g.isFinishing(),"gallery Back closes fullscreen before exit");ui(full::onBackPressed);check(g.isFinishing(),"gallery next Back exits settings");end();
        Context context=getTargetContext();context.getSharedPreferences("aperture",0).edit().putString("origin","https://synthetic.invalid").apply();context.getSharedPreferences("local-gallery",0).edit().putString("template","diptych").putInt("seconds",20).apply();context.getSharedPreferences("kaishi_web_diagnostics",0).edit().putInt("versionCode",36).putString("reason","RENDERER_GONE").putLong("at",1234).apply();
        ui(()->new WebDiagnostics(context));check(!context.getSharedPreferences("kaishi_web_diagnostics",0).contains("reason"),"diagnostics resets previous-version failure");check(context.getSharedPreferences("aperture",0).getString("origin","").equals("https://synthetic.invalid")&&context.getSharedPreferences("local-gallery",0).getInt("seconds",0)==20,"diagnostics reset preserves separate user config");
        a=home();final RecoveryHomeProbe info=a;final AlertDialog[] infoDialog=new AlertDialog[1];ui(()->{info.view.finished();infoDialog[0]=((WebDiagnostics)field(info,"diagnostics")).show(info);ProbeKeys.track(info,infoDialog[0]);});waitForIdleSync();
        boolean reachedNote=false;for(int k=0;k<8;k++){final boolean[] atNote={false};ui(()->{View focus=infoDialog[0].getCurrentFocus();if(focus!=null&&"diagnostics-说明".equals(focus.getTag())){android.graphics.Rect rect=new android.graphics.Rect();atNote[0]=focus.getGlobalVisibleRect(rect)&&rect.height()>=focus.getHeight();}});if(atNote[0]){reachedNote=true;break;}ProbeKeys.key(this,open,KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();}
        check(reachedNote,"diagnostics Chinese explanation reached and fully visible with remote Down");capture("diagnostics",info,infoDialog[0].getWindow(),()->infoDialog[0].getWindow().getDecorView().findViewWithTag("diagnostics-说明"));ProbeKeys.key(this,open,KeyEvent.KEYCODE_BACK);end();
        result.put("ok",true);
    }catch(Throwable error){try{result.put("ok",false).put("error",error.getClass().getSimpleName()+": "+error.getMessage());}catch(JSONException impossible){throw new AssertionError(impossible);}android.util.Log.e("KaishiRecoveryProbe","failure",error);
    }finally{ProbeReport.finish(this,result,"s94-recovery.json",this::end,report->report.put("checks",checks).put("captures",captures).put("imageProofIndependentOfFunctionalChecks",true).put("passed",checks.length()).put("controlledWebView",true).put("actualRendererKilled",false).put("realXiaomi",false).put("noUploadStarted",true).put("keyReceipts",ProbeKeys.receipts()).put("inputLayer","app-internal-window-dispatch"));}}
}
