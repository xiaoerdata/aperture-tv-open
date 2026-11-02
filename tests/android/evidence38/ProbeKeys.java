package org.zhixiaoce.aperture;
import android.app.*;import android.os.SystemClock;import android.os.IBinder;import android.graphics.Rect;import android.view.*;import java.lang.reflect.Field;import java.util.*;import org.json.*;
/** App-internal dispatch plus bounded default focus simulation, NOT the ViewRoot/IME/system chain.
 * Directional focus semantics adapted from AOSP Android 9 ViewRootImpl (Apache-2.0).
 * See AOSP-FOCUS-NOTICE.md and AOSP-FOCUS-LICENSE.txt. Never replaces the captured target. */
final class ProbeKeys {
    static final String INPUT_LAYER="app-internal-window-dispatch+default-focus-navigation-simulation";
    private static final Map<Activity,List<Dialog>> dialogs=new IdentityHashMap<>();private static final JSONArray receipts=new JSONArray();
    static void track(Activity a,Dialog d){dialogs.computeIfAbsent(a,k->new ArrayList<>()).add(d);}
    private static Object field(Activity a,String name){for(Class<?> c=a.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new AssertionError(e);}return null;}
    private static Dialog dialog(Activity a){
        for(String name:new String[]{"uploadConfirmation","notice","diagnosticsDialog","returnHomeDialog","settingsMenuDialog"}){Object v=field(a,name);if(v instanceof Dialog&&((Dialog)v).isShowing())return (Dialog)v;}
        List<Dialog> list=dialogs.get(a);if(list!=null)for(int i=list.size()-1;i>=0;i--)if(list.get(i).isShowing())return list.get(i);return null;
    }
    private static boolean live(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed();}
    private static final class Target {
        final Activity activity;final Dialog dialog;final Window window;final View root;final IBinder token;
        Target(Activity a){activity=a;dialog=ProbeKeys.dialog(a);window=dialog==null?a.getWindow():dialog.getWindow();root=window.getDecorView();token=root.getWindowToken();}
        boolean current(){return live(activity)&&ProbeKeys.dialog(activity)==dialog&&(dialog==null?activity.getWindow()==window:dialog.isShowing()&&dialog.getWindow()==window)&&window.getDecorView()==root&&root.getRootView()==root&&root.isAttachedToWindow()&&root.hasWindowFocus()&&token!=null&&root.getWindowToken()==token;}
    }
    private static int direction(int code){switch(code){case KeyEvent.KEYCODE_DPAD_LEFT:return View.FOCUS_LEFT;case KeyEvent.KEYCODE_DPAD_RIGHT:return View.FOCUS_RIGHT;case KeyEvent.KEYCODE_DPAD_UP:return View.FOCUS_UP;case KeyEvent.KEYCODE_DPAD_DOWN:return View.FOCUS_DOWN;default:return 0;}}
    private static boolean visible(View view){if(view==null||!view.isShown())return false;for(View n=view;n!=null;n=n.getParent() instanceof View?(View)n.getParent():null)if(n.getAlpha()<=0)return false;Rect r=new Rect();return view.getGlobalVisibleRect(r)&&r.width()>0&&r.height()>0;}
    private static boolean legal(Target t,View view){return view!=null&&view.getRootView()==t.root&&view.getWindowToken()==t.token&&view.isAttachedToWindow()&&visible(view)&&view.isEnabled()&&view.isFocusable();}
    private static JSONObject view(Target t,View v)throws JSONException{return new JSONObject().put("present",v!=null).put("identity",v==null?0:System.identityHashCode(v)).put("id",v==null?View.NO_ID:v.getId()).put("rootIdentity",v==null?0:System.identityHashCode(v.getRootView())).put("tokenIdentity",v==null?0:System.identityHashCode(v.getWindowToken())).put("sameRoot",v!=null&&v.getRootView()==t.root).put("sameToken",v!=null&&v.getWindowToken()==t.token).put("attached",v!=null&&v.isAttachedToWindow()).put("visible",visible(v)).put("enabled",v!=null&&v.isEnabled()).put("focusable",v!=null&&v.isFocusable()).put("legal",legal(t,v));}
    private static void postFocus(Target t,JSONObject receipt)throws JSONException{receipt.put("focusAfterNavigation",view(t,t.root.findFocus())).put("targetValidAfterNavigation",t.current());if(t.current()&&t.root.findFocus()!=null&&!legal(t,t.root.findFocus()))throw new AssertionError("Default navigation produced an illegal focus in captured target");}
    private static boolean navigate(Target t,KeyEvent down,boolean handled,JSONObject receipt)throws JSONException{
        int dir=direction(down.getKeyCode());receipt.put("direction",dir).put("navigationAttempted",false).put("focusBeforeNavigation",view(t,t.root.findFocus()));
        if(handled||down.getAction()!=KeyEvent.ACTION_DOWN||!down.hasNoModifiers()||dir==0||!t.current()){receipt.put("navigationDisposition",handled?"handler-consumed":!down.hasNoModifiers()?"modifiers-present":dir==0?"not-directional":"target-invalid");postFocus(t,receipt);return false;}
        receipt.put("navigationAttempted",true);View focused=t.root.findFocus();
        if(focused!=null){
            if(!legal(t,focused)){receipt.put("navigationDisposition","focused-view-invalid");postFocus(t,receipt);return false;}
            View candidate=focused.focusSearch(dir);boolean accepted=candidate!=focused&&legal(t,candidate);receipt.put("searchCandidate",view(t,candidate)).put("candidateAccepted",accepted);
            if(accepted&&t.current()){
                Rect rect=new Rect();focused.getFocusedRect(rect);if(t.root instanceof ViewGroup){((ViewGroup)t.root).offsetDescendantRectToMyCoords(focused,rect);((ViewGroup)t.root).offsetRectIntoDescendantCoords(candidate,rect);}
                receipt.put("requestRect",new JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom));
                if(t.current()&&legal(t,candidate)){boolean moved=candidate.requestFocus(dir,rect);receipt.put("requestFocusResult",moved);if(moved){receipt.put("navigationDisposition","natural-focus-search");postFocus(t,receipt);return true;}}
            }
            if(t.current()&&legal(t,focused)){boolean moved=t.root.dispatchUnhandledMove(focused,dir);receipt.put("unhandledMoveAttempted",true).put("unhandledMoveHandled",moved).put("navigationDisposition",moved?"same-root-unhandled-move":"no-focus-move");postFocus(t,receipt);return moved;}
        }else if(t.current()){
            // Public restoreDefaultFocus is safe here only if all possible focusables meet the
            // stricter probe visibility/token rules. Otherwise use a natural, validated search.
            ArrayList<View> choices=t.root.getFocusables(View.FOCUS_FORWARD);boolean allLegal=!choices.isEmpty();for(View choice:choices)allLegal&=legal(t,choice);
            receipt.put("defaultFocusCandidateCount",choices.size()).put("allDefaultCandidatesLegal",allLegal);
            if(allLegal&&t.current()){boolean restored=t.root.restoreDefaultFocus();receipt.put("defaultFocusAttempted",true).put("defaultFocusResult",restored).put("navigationDisposition","same-root-default-focus");postFocus(t,receipt);return restored;}
            View candidate=t.root instanceof ViewGroup?FocusFinder.getInstance().findNextFocus((ViewGroup)t.root,null,dir):null;receipt.put("searchCandidate",view(t,candidate)).put("candidateAccepted",legal(t,candidate));
            if(t.current()&&legal(t,candidate)){boolean moved=candidate.requestFocus(dir,null);receipt.put("requestFocusResult",moved).put("navigationDisposition","default-natural-focus-search");postFocus(t,receipt);return moved;}
        }
        receipt.put("navigationDisposition","no-legal-focus-target");postFocus(t,receipt);return false;
    }
    static void requireDialog(Instrumentation instrument,Activity a,Dialog d){if(!live(a)||!a.getPackageName().equals(instrument.getTargetContext().getPackageName())||d==null||!d.isShowing()||d.getWindow()==null||!d.getWindow().getDecorView().isAttachedToWindow()||!d.getWindow().getDecorView().hasWindowFocus())throw new AssertionError("Manual gesture requires live focused probe dialog");}
    static void key(Instrumentation instrument,Activity a,int code){key(instrument,a,code,0);}
    static void key(Instrumentation instrument,Activity a,int code,int modifiers){
        if(a==null||!a.getPackageName().equals(instrument.getTargetContext().getPackageName()))throw new AssertionError("Input requires exact probe package Activity");
        ProbeWait.until(()->{if(!live(a))throw new AssertionError("Input target already ended");Dialog d=dialog(a);Window w=d==null?a.getWindow():d.getWindow();return w!=null&&w.getDecorView().isAttachedToWindow()&&w.getDecorView().hasWindowFocus();},3000,"explicit target window focus");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();instrument.runOnMainSync(()->{try{
            Target target=new Target(a);if(!target.current())throw new AssertionError("Target invalid before DOWN");Dialog d=target.dialog;Window w=target.window;View root=target.root;
            long down=SystemClock.uptimeMillis();JSONObject receipt=new JSONObject();
            try{receipt.put("layer",INPUT_LAYER).put("activity",a.getClass().getName()).put("windowIdentity",System.identityHashCode(w)).put("rootIdentity",System.identityHashCode(root)).put("tokenIdentity",System.identityHashCode(target.token)).put("dialog",d!=null).put("keyCode",code).put("modifiers",modifiers).put("downTime",down).put("downEventTime",down).put("focusedBeforeDown",true).put("focusBeforeDown",view(target,root.findFocus()));receipts.put(receipt);
                KeyEvent downEvent=new KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0,modifiers);boolean downHandled=d!=null?d.dispatchKeyEvent(downEvent):a.dispatchKeyEvent(downEvent);receipt.put("downHandled",downHandled);
                receipt.put("isFinishingAfterDown",a.isFinishing()).put("isDestroyedAfterDown",a.isDestroyed());
                boolean navigationHandled=navigate(target,downEvent,downHandled,receipt);receipt.put("navigationHandled",navigationHandled).put("effectiveDownHandled",downHandled||navigationHandled);
                Dialog after=dialog(a);boolean changed=after!=d,ended=!live(a),gone=d!=null&&!d.isShowing();
                if(!target.current()){receipt.put("upDelivered",false).put("upHandled",JSONObject.NULL).put("upDisposition",ended?"target-ended-not-delivered":gone?"target-window-closed-not-delivered":changed?"target-window-changed-not-delivered":"target-window-unavailable-not-delivered");return;}
                long up=SystemClock.uptimeMillis();receipt.put("upDownTime",down).put("upEventTime",up);
                KeyEvent upEvent=new KeyEvent(down,up,KeyEvent.ACTION_UP,code,0,modifiers);boolean upHandled=d!=null?d.dispatchKeyEvent(upEvent):a.dispatchKeyEvent(upEvent);receipt.put("upDelivered",true).put("upHandled",upHandled).put("upDisposition","same-target").put("targetValidAfterUp",target.current()).put("focusAfterUp",view(target,root.findFocus()));
            }catch(JSONException e){throw new AssertionError(e);}
        }catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError("Explicit target input failed",failure.get());
        instrument.waitForIdleSync();
    }
    static JSONArray receipts(){return receipts;}
}
