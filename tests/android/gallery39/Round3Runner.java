package org.zhixiaoce.aperture;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import org.json.*;import java.lang.reflect.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;

/** Real native/store and real WebView chain, plus separate controlled-callback observations. */
public final class Round3Runner extends Instrumentation {
    final JSONArray checks=new JSONArray(),captures=new JSONArray(),sessions=new JSONArray();Activity open;
    private void ui(Runnable r){runOnMainSync(r);}
    private Object field(Object target,String name){for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new AssertionError(name);}
    private void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks.put(name);android.util.Log.i("KaishiRound3Probe","PASS "+name);}
    private void key(int code){ProbeKeys.key(this,open,code);}
    private <T extends Activity>T launch(Class<T> type){T a=type.cast(startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));open=a;waitForIdleSync();return a;}
    private void end(){if(open!=null){Activity a=open;ui(a::finish);ProbeWait.until(a::isDestroyed,10000,"Activity destroyed");open=null;}}
    private boolean await(BooleanSupplier condition,long timeout)throws Exception{
        CountDownLatch done=new CountDownLatch(1);boolean[] value={false};Handler handler=new Handler(Looper.getMainLooper());long limit=SystemClock.uptimeMillis()+timeout;
        handler.post(new Runnable(){public void run(){if(condition.getAsBoolean()){value[0]=true;done.countDown();}else if(SystemClock.uptimeMillis()>=limit)done.countDown();else handler.postDelayed(this,20);}});return done.await(timeout+1000,TimeUnit.MILLISECONDS)&&value[0];
    }
    private JSONArray stages(){return parse(getTargetContext().getSharedPreferences("kaishi_gallery_stages",0).getString("events","[]"));}
    private JSONArray parse(String text){try{return new JSONArray(text);}catch(Exception e){throw new AssertionError(e);}}
    private boolean has(JSONArray rows,String stage){for(int i=0;i<rows.length();i++)if(stage.equals(rows.optJSONObject(i).optString("stage")))return true;return false;}
    private void capture(Activity a,String id,String tag)throws Exception{captures.put(EvidenceCapture.capture(this,a,a.getWindow(),id,tag,()->a.getWindow().getDecorView().findViewWithTag(tag),()->null,10000));}
    private void nativeChecks()throws Exception{
        R3NativeGalleryProbe a=launch(R3NativeGalleryProbe.class);check(await(()->field(a,"nativeGallery")!=null,1000),"actual old provider selects usable native album without constructing WebView");
        check(await(()->has(stages(),"NATIVE_SNAPSHOT_OK"),3000),"native album reads real app-private empty snapshot");check(a.getCurrentFocus()!=null&&"native-gallery-heading".equals(a.getCurrentFocus().getTag()),"native empty album neutral first focus");capture(a,"r3-native-empty","native-gallery-empty");
        key(23);key(23);check(a.uploadCreates==0&&field(a,"uploadConfirmation")==null,"native initial repeated OK opens no upload");
        key(20);check("native-gallery-upload".equals(a.getCurrentFocus().getTag()),"native remote Down reaches upload control");key(23);AlertDialog dialog=(AlertDialog)field(a,"uploadConfirmation");check(dialog!=null&&dialog.getButton(-2).hasFocus(),"real native UI opens unified confirmation with Cancel selected");key(23);check(field(a,"uploadConfirmation")==null&&a.uploadCreates==0,"real native Cancel closes without factory invocation");
        key(23);check(field(a,"uploadConfirmation")!=null,"native upload confirmation can reopen");key(4);check(field(a,"uploadConfirmation")==null&&a.uploadCreates==0,"real native confirmation Back starts no session");sessions.put(new JSONObject().put("caseId","native-empty-cold").put("stages",stages()).put("realWebView",false));key(4);check(a.isFinishing(),"native settings Back returns");end();
        R3NativeGalleryProbe warm=launch(R3NativeGalleryProbe.class);check(await(()->has(stages(),"NATIVE_SNAPSHOT_OK"),3000),"warm native reopen reads saved album");capture(warm,"r3-native-warm","native-gallery-heading");sessions.put(new JSONObject().put("caseId","native-empty-warm").put("stages",stages()));end();
    }
    private void realWebChain()throws Exception{
        for(int round=0;round<2;round++){
            R3WebGalleryProbe a=launch(R3WebGalleryProbe.class);check(await(()->Boolean.TRUE.equals(field(a,"ready")),20000),"real WebView chain reaches web-ready or operational native fallback");
            JSONArray trace=stages();boolean fallback=field(a,"nativeGallery")!=null;check(has(trace,"ROUTE_WEB")&&(has(trace,"READY_CALLBACK")||has(trace,"DEADLINE_EXPIRE")||has(trace,"FALLBACK")),"real local chain has an inspectable final stage");
            sessions.put(new JSONObject().put("caseId",round==0?"real-web-cold":"real-web-warm").put("forcedWebRouteForObservation",true).put("webUsable",!fallback).put("nativeFallback",fallback).put("stages",trace).put("provider",WebDiagnostics.provider()).put("timeoutRootCause",fallback?"unknown until stage review":"not reproduced"));
            capture(a,round==0?"r3-real-web-cold":"r3-real-web-warm",fallback?"kaishi-gallery-error":"gallery-web-root");check(a.uploadCreates==0,"real web observation never starts upload");key(4);check(await(a::isFinishing,2000),"real gallery return works without JS dependency");end();
        }
    }
    private void controlledChain()throws Exception{
        RecoveryGalleryProbe tracing=launch(RecoveryGalleryProbe.class);ui(()->tracing.view.getWebChromeClient().onConsoleMessage(new android.webkit.ConsoleMessage("Uncaught SyntaxError: SYNTHETIC_PRIVATE_TOKEN","https://synthetic.invalid/private?token=SYNTHETIC_PRIVATE_TOKEN",7,android.webkit.ConsoleMessage.MessageLevel.ERROR)));
        check(has(stages(),"CONSOLE")&&!stages().toString().contains("SYNTHETIC_PRIVATE_TOKEN"),"controlled console error stores only fixed category and bounded line");end();
        RecoveryGalleryProbe a=launch(RecoveryGalleryProbe.class);final RecoveryGalleryProbe ready=a;ui(()->ready.view.finished());check(Boolean.TRUE.equals(field(a,"ready"))&&has(stages(),"READY_REQUEST")&&has(stages(),"READY_CALLBACK"),"controlled ready request/callback stages recorded");end();
        a=launch(RecoveryGalleryProbe.class);final RecoveryGalleryProbe late=a;ui(()->{late.view.hold=true;late.view.finished();late.clock.advance(10000);late.view.lateReady();});check(field(a,"web")==null&&field(a,"nativeGallery")!=null&&has(stages(),"STALE_CALLBACK"),"late ready cannot revive web after native fallback");check(a.view.evalAfterDestroy==0,"late callback never touches destroyed WebView");end();
        ui(()->{AndroidGalleryTrace prior=new AndroidGalleryTrace(getTargetContext());AndroidGalleryTrace current=new AndroidGalleryTrace(getTargetContext());current.record(GalleryTrace.Stage.CREATE,GalleryTrace.Asset.NONE,GalleryTrace.Result.NONE,1,true,true,null,false,0);prior.record(GalleryTrace.Stage.DESTROY,GalleryTrace.Asset.NONE,GalleryTrace.Result.NONE,1,false,false,null,false,0);});check(has(stages(),"CREATE")&&!has(stages(),"DESTROY"),"prior activity logger cannot overwrite newer session journal");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){JSONObject result=new JSONObject();try{nativeChecks();realWebChain();controlledChain();result.put("ok",true);}catch(Throwable e){try{result.put("ok",false).put("error",e.getClass().getSimpleName()+": "+e.getMessage());}catch(JSONException impossible){throw new AssertionError(impossible);}android.util.Log.e("KaishiRound3Probe","failed",e);}finally{ProbeReport.finish(this,result,"s96-round3.json",this::end,report->report.put("checks",checks).put("passed",checks.length()).put("captures",captures).put("sessions",sessions).put("localPhotosUsed",false).put("realUploadListenerStarted",false).put("keyReceipts",ProbeKeys.receipts()).put("inputLayer","app-internal-window-dispatch").put("formalArmRuntimeAccepted",false).put("actualXiaomi",false));}}
}
