package org.zhixiaoce.aperture;
import android.app.*;import android.os.SystemClock;import android.view.*;import java.lang.reflect.Field;import java.util.*;import org.json.*;
/** APP-INTERNAL input only. Captured target Window is never replaced between DOWN and UP. */
final class ProbeKeys {
    private static final Map<Activity,List<Dialog>> dialogs=new IdentityHashMap<>();private static final JSONArray receipts=new JSONArray();
    static void track(Activity a,Dialog d){dialogs.computeIfAbsent(a,k->new ArrayList<>()).add(d);}
    private static Object field(Activity a,String name){for(Class<?> c=a.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new AssertionError(e);}return null;}
    private static Dialog dialog(Activity a){
        for(String name:new String[]{"uploadConfirmation","notice","diagnosticsDialog","returnHomeDialog","settingsMenuDialog"}){Object v=field(a,name);if(v instanceof Dialog&&((Dialog)v).isShowing())return (Dialog)v;}
        List<Dialog> list=dialogs.get(a);if(list!=null)for(int i=list.size()-1;i>=0;i--)if(list.get(i).isShowing())return list.get(i);return null;
    }
    private static boolean live(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed();}
    static void requireDialog(Instrumentation instrument,Activity a,Dialog d){if(!live(a)||!a.getPackageName().equals(instrument.getTargetContext().getPackageName())||d==null||!d.isShowing()||d.getWindow()==null||!d.getWindow().getDecorView().isAttachedToWindow()||!d.getWindow().getDecorView().hasWindowFocus())throw new AssertionError("Manual gesture requires live focused probe dialog");}
    static void key(Instrumentation instrument,Activity a,int code){
        if(a==null||!a.getPackageName().equals(instrument.getTargetContext().getPackageName()))throw new AssertionError("Input requires exact probe package Activity");
        ProbeWait.until(()->{if(!live(a))throw new AssertionError("Input target already ended");Dialog d=dialog(a);Window w=d==null?a.getWindow():d.getWindow();return w!=null&&w.getDecorView().isAttachedToWindow()&&w.getDecorView().hasWindowFocus();},3000,"explicit target window focus");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();instrument.runOnMainSync(()->{try{
            if(!live(a))throw new AssertionError("Input target ended before DOWN");Dialog d=dialog(a);Window w=d==null?a.getWindow():d.getWindow();View root=w.getDecorView();if(!root.isAttachedToWindow()||!root.hasWindowFocus())throw new AssertionError("Target lost window before DOWN");
            long down=SystemClock.uptimeMillis();JSONObject receipt=new JSONObject();
            try{receipt.put("layer","app-internal-window-dispatch").put("activity",a.getClass().getName()).put("windowIdentity",System.identityHashCode(w)).put("dialog",d!=null).put("keyCode",code).put("downTime",down).put("downEventTime",down).put("focusedBeforeDown",true);receipts.put(receipt);
                if(d!=null)d.dispatchKeyEvent(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0));else a.dispatchKeyEvent(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0));
                receipt.put("isFinishingAfterDown",a.isFinishing()).put("isDestroyedAfterDown",a.isDestroyed());
                Dialog after=dialog(a);boolean changed=after!=d,ended=!live(a),gone=d!=null&&!d.isShowing();
                if(ended||gone||changed||!root.isAttachedToWindow()||!root.hasWindowFocus()){receipt.put("upDelivered",false).put("upDisposition",ended?"target-ended-not-delivered":gone?"target-window-closed-not-delivered":changed?"target-window-changed-not-delivered":"target-window-unavailable-not-delivered");return;}
                long up=SystemClock.uptimeMillis();receipt.put("upDownTime",down).put("upEventTime",up);
                if(d!=null)d.dispatchKeyEvent(new KeyEvent(down,up,KeyEvent.ACTION_UP,code,0));else a.dispatchKeyEvent(new KeyEvent(down,up,KeyEvent.ACTION_UP,code,0));receipt.put("upDelivered",true).put("upDisposition","same-target");
            }catch(JSONException e){throw new AssertionError(e);}
        }catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError("Explicit target input failed",failure.get());
        instrument.waitForIdleSync();
    }
    static JSONArray receipts(){return receipts;}
}
