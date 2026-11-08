package org.zhixiaoce.aperture;

import android.content.Intent;
import android.os.Handler;
import android.view.KeyEvent;
import android.webkit.WebView;

/** Actual production activity/wrapper with explicit JVM framework doubles. */
public final class AmbientReturnTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static AmbientWeb page(AmbientActivity activity)throws Exception{java.lang.reflect.Field field=AmbientActivity.class.getDeclaredField("page");field.setAccessible(true);return (AmbientWeb)field.get(activity);}
    private static AmbientActivity screen(String panel,boolean root){AmbientActivity a=new AmbientActivity();a.root=root;a.intent.putExtra("panel",panel);a.onCreate(null);a.onResume();a.onWindowFocusChanged(true);return a;}
    private static void back(AmbientActivity a){a.dispatchKeyEvent(new KeyEvent(0,4));a.dispatchKeyEvent(new KeyEvent(1,4));}
    public static void main(String[] args)throws Exception{
        AmbientActivity normal=screen("weather",false);WebView web=page(normal).web;web.hold=true;int calls=web.evaluations;
        back(normal);check(normal.finishes==1,"native weather Back exits only the child without a JS reply");check(normal.starts.isEmpty(),"healthy parent task reused without launching another home");check(normal.superKeys==0,"both Back edges intercepted before focused WebView history");check(web.evaluations==calls,"weather Back does not evaluate JavaScript");back(normal);normal.onKeyUp(23,new KeyEvent(1,23));check(normal.finishes==1&&web.evaluations==calls,"repeat and key-up after return cannot close another screen");normal.onPause();normal.onDestroy();check(web.destroys==1&&web.detachedAtDestroy,"child WebView detached and disposed once");

        AmbientActivity orphan=screen("weather",true);back(orphan);check(orphan.finishes==1&&orphan.starts.size()==1,"task-root child restores Kaishi home before finishing");Intent target=orphan.starts.get(0);check(target.target==MainActivity.class,"recovery destination is app home, never OEM home");check((target.flags&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0&&(target.flags&Intent.FLAG_ACTIVITY_SINGLE_TOP)!=0,"existing home can be reused");orphan.onDestroy();

        AmbientActivity button=screen("weather",false);AmbientWeb buttonPage=page(button);buttonPage.web.client.shouldOverrideUrlLoading(buttonPage.web,"aperture://ambient/close");buttonPage.web.client.shouldOverrideUrlLoading(buttonPage.web,"aperture://ambient/close");check(button.finishes==1,"on-screen return is idempotent");int before=buttonPage.web.evaluations;button.onPause();button.onWindowFocusChanged(false);check(buttonPage.web.evaluations==before,"leaving wrapper ignores lifecycle work");button.onDestroy();

        AmbientActivity settings=screen("settings",false);WebView pending=page(settings).web;pending.hold=true;settings.onBackPressed();Handler.advance(599);check(settings.finishes==0,"settings allows JS to handle Back");Handler.advance(1);check(settings.finishes==1,"600ms deadline returns when renderer never replies");pending.late();check(settings.finishes==1,"late callback cannot finish twice");settings.onDestroy();
        AmbientActivity twice=screen("settings",false);page(twice).web.hold=true;twice.onBackPressed();twice.onBackPressed();check(twice.finishes==1,"second pending Back returns immediately");twice.onDestroy();Handler.advance(1000);check(twice.finishes==1,"teardown removes pending Back timer");

        AmbientActivity paused=screen("settings",false);WebView late=page(paused).web;late.hold=true;paused.onBackPressed();paused.onPause();late.late();Handler.advance(1000);check(paused.finishes==0,"paused screen ignores old callback and cancels exit timer");paused.onDestroy();

        AmbientActivity failed=screen("settings",false);page(failed).web.throwEval=true;failed.onBackPressed();check(failed.finishes==1,"evaluation exception returns once");failed.onDestroy();
        AmbientActivity gone=screen("weather",false);AmbientWeb gonePage=page(gone);WebView dead=gonePage.web;dead.rendererGone();check(dead.destroys==1&&dead.detachedAtDestroy&&dead.stops==0,"renderer loss synchronously detaches/destroys without stopLoading");check(gone.finishes==1,"renderer failure returns child");gone.onPause();gone.onWindowFocusChanged(false);dead.client.onPageFinished(dead,dead.url);gonePage.active(true);gonePage.active(false);gone.onKeyUp(23,new KeyEvent(1,23));check(dead.illegal==0,"no pause/resume/evaluation after renderer death");gone.onDestroy();gone.onDestroy();check(dead.destroys==1,"destruction stays idempotent");check(!gonePage.evaluate("late",null),"closed wrapper rejects delayed work");

        AmbientActivity voice=screen("weather",false);voice.onKeyDown(231,new KeyEvent(0,231));voice.onKeyUp(231,new KeyEvent(1,231));check(voice.finishes==0,"system voice key does not close weather");voice.onPause();check(page(voice).web.globalPauses==0,"child does not globally pause other WebViews");voice.onDestroy();
        System.out.println(checks+" production weather-return lifecycle assertions passed with JVM framework doubles; Xiaomi task manager and renderer not executed");
    }
}
