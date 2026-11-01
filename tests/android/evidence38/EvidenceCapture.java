package org.zhixiaoce.aperture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Test-only: an identified native target must draw and finish a window frame before PixelCopy. */
final class EvidenceCapture {
    static JSONObject capture(Instrumentation instrument,Activity activity,Window window,String caseId,String targetName,
                              Supplier<View> actualTarget,Supplier<String> observedHomeMode,long timeoutMs)throws Exception {
        if(!caseId.matches("[a-z0-9-]{1,60}")||timeoutMs<100||timeoutMs>15000)throw new IllegalArgumentException("Bounded named case required");
        Request request=new Request(activity,window,caseId,targetName,actualTarget,observedHomeMode,timeoutMs);
        request.ui.post(request::begin);
        if(!request.done.await(timeoutMs+1000,TimeUnit.MILLISECONDS))request.finish(false,"main-thread-deadline-not-delivered");
        JSONObject result=request.result();String filename="s95-"+caseId+"-"+request.started+"-"+System.nanoTime();
        if(request.image!=null){String name=filename+(result.optBoolean("valid")?".png":"-invalid.png");try(FileOutputStream file=activity.openFileOutput(name,0)){request.image.compress(Bitmap.CompressFormat.PNG,100,file);}request.image.recycle();result.put("image",name);}
        else result.put("image",JSONObject.NULL);
        String receipt=filename+".json";result.put("receipt",receipt);
        try(FileOutputStream file=activity.openFileOutput(receipt,0)){file.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}
        return result;
    }
    private static final class Request {
        final Activity activity;final Window window;final String caseId,targetName;final Supplier<View> target;final Supplier<String> homeMode;
        final Handler ui=new Handler(Looper.getMainLooper());final CountDownLatch done=new CountDownLatch(1);final AtomicBoolean finished=new AtomicBoolean();
        final long started=SystemClock.uptimeMillis(),timeout;final String startedUtc=Instant.now().toString();final JSONObject data=new JSONObject();FrameEvidenceGate gate;
        View observed,root;Bitmap image;boolean listening,copying;long frameClockNanos=-1;Choreographer.FrameCallback frameClock;ViewTreeObserver.OnDrawListener draw;ViewTreeObserver.OnGlobalLayoutListener layout;ViewTreeObserver.OnPreDrawListener preDraw;Window.OnFrameMetricsAvailableListener metrics;
        Request(Activity a,Window w,String id,String name,Supplier<View> t,Supplier<String> mode,long ms){activity=a;window=w;caseId=id;targetName=name;target=t;homeMode=mode;timeout=ms;}
        synchronized void put(String key,Object value){try{data.put(key,value==null?JSONObject.NULL:value);}catch(Exception impossible){throw new IllegalStateException(impossible);}}
        void begin(){
            if(finished.get())return;if(SystemClock.uptimeMillis()>=started+timeout){finish(false,"timeout-before-ui-setup");return;}
            try{
                PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
                put("actualAppPackage",info.packageName);put("actualAppVersionCode",info.versionCode);put("actualAppVersionName",info.versionName);
                put("actualHomeMode",homeMode.get());put("activityClass",activity.getClass().getName());
                if((window.getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0){finish(false,"secure-window-not-captured");return;}
                // The probe is isolated and empty. Refuse any album data rather than recording photos.
                if(new GalleryStore(activity).snapshot().getJSONArray("photos").length()!=0){finish(false,"album-not-empty-privacy-stop");return;}
                root=window.getDecorView();layout=this::tryTarget;root.getViewTreeObserver().addOnGlobalLayoutListener(layout);preDraw=()->{tryTarget();return true;};root.getViewTreeObserver().addOnPreDrawListener(preDraw);
                ui.postAtTime(()->finish(false,"timeout"),started+timeout);tryTarget();
            }catch(Exception error){finish(false,"capture-setup-failed");}
        }
        boolean visible(View view){
            if(view==null||activity.isFinishing()||activity.isDestroyed()||!view.isAttachedToWindow()||!view.isShown()||view.getWidth()<=0||view.getHeight()<=0||!root.hasWindowFocus()||view.getRootView()!=root)return false;
            for(View node=view;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null)if(node.getAlpha()<=0)return false;
            Rect rect=new Rect();return view.getGlobalVisibleRect(rect)&&rect.width()>0&&rect.height()>0;
        }
        boolean current(){
            if(finished.get()||gate==null)return false;
            View actual=target.get();boolean ok=gate.valid(activity,window,actual,visible(actual),activity.isDestroyed()||activity.isFinishing(),SystemClock.uptimeMillis());
            if(!ok)finish(false,gate.failure());return ok;
        }
        void tryTarget(){
            if(finished.get()||listening)return;if(SystemClock.uptimeMillis()>=started+timeout){finish(false,"timeout");return;}
            View actual=target.get();if(!visible(actual))return;
            observed=actual;gate=new FrameEvidenceGate(activity,window,observed,SystemClock.uptimeMillis(),Math.max(1,started+timeout-SystemClock.uptimeMillis()));listening=true;
            put("observedTargetClass",actual.getClass().getName());put("observedTargetTag",actual.getTag());put("targetVisible",true);put("waitingPredicate","same activity/window/target + visible + OnDraw + completed window VSYNC >= drawn VSYNC + PixelCopy SUCCESS");
            // Use the public callback API; getFrameTimeNanos() is hidden on Android SDK stubs.
            frameClock=frame->{if(!current()||gate.drawFrame()>=0)return;frameClockNanos=frame;root.invalidate();Choreographer.getInstance().postFrameCallback(frameClock);};
            draw=()->{if(!current()||gate.drawFrame()>=0||frameClockNanos<0)return;gate.drawn(frameClockNanos);put("drawFrameNanos",frameClockNanos);put("drawTimestampSource","Choreographer.FrameCallback before OnDraw");};
            metrics=(w,m,dropped)->{
                if(!current())return;long frame=m.getMetric(FrameMetrics.VSYNC_TIMESTAMP);
                if(gate.committed(frame)){put("completedFrameNanos",frame);put("droppedFrameMetricCallbacks",dropped);copy();}
            };
            root.getViewTreeObserver().addOnDrawListener(draw);window.addOnFrameMetricsAvailableListener(metrics,ui);Choreographer.getInstance().postFrameCallback(frameClock);actual.invalidate();root.invalidate();
        }
        void copy(){
            if(!current()||copying)return;copying=true;
            Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            try{PixelCopy.request(window,bitmap,status->{
                if(finished.get()){bitmap.recycle();return;}
                put("pixelCopyStatus",status);
                if(!current()||!gate.copied(status)){bitmap.recycle();if(!finished.get())finish(false,gate.failure());return;}
                image=bitmap;put("capturedAtUtc",Instant.now().toString());put("capturedUptimeMillis",SystemClock.uptimeMillis());put("actualHomeMode",homeMode.get());put("frameWidth",bitmap.getWidth());put("frameHeight",bitmap.getHeight());finish(true,"");
            },ui);}catch(RuntimeException error){bitmap.recycle();finish(false,"pixel-copy-request-failed");}
        }
        void finish(boolean valid,String reason){
            if(!finished.compareAndSet(false,true))return;
            put("valid",valid);put("invalidReason",valid?JSONObject.NULL:reason);put("waitCompleted",valid);put("waitElapsedMillis",SystemClock.uptimeMillis()-started);put("finishedAtUtc",Instant.now().toString());
            ui.post(()->{if(frameClock!=null)Choreographer.getInstance().removeFrameCallback(frameClock);if(root!=null&&root.getViewTreeObserver().isAlive()){if(layout!=null)root.getViewTreeObserver().removeOnGlobalLayoutListener(layout);if(preDraw!=null)root.getViewTreeObserver().removeOnPreDrawListener(preDraw);if(draw!=null)root.getViewTreeObserver().removeOnDrawListener(draw);}if(metrics!=null)window.removeOnFrameMetricsAvailableListener(metrics);});done.countDown();
        }
        synchronized JSONObject result()throws Exception {
            JSONObject result=new JSONObject(data.toString());result.put("caseId",caseId).put("requestedTarget",targetName).put("startedAtUptimeMillis",started).put("startedAtUtc",startedUtc).put("timeoutMillis",timeout).put("evidenceType","native-window-buffer-after-frame-fence").put("realRendererCrashProven",false);
            if(!result.has("actualAppVersionCode"))result.put("actualAppVersionCode",JSONObject.NULL).put("actualAppVersionName",JSONObject.NULL).put("actualAppPackage",JSONObject.NULL);
            if(!result.has("actualHomeMode"))result.put("actualHomeMode",JSONObject.NULL);
            result.put("homeModeObserved",!result.isNull("actualHomeMode"));if(result.isNull("actualHomeMode"))result.put("homeModeUnobservedReason","not observed from the active capture Activity");
            if(!result.has("waitingPredicate"))result.put("waitingPredicate","target visibility not established");
            return result;
        }
    }
}
