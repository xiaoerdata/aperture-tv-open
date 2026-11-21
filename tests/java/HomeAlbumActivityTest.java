package org.zhixiaoce.aperture;
import android.view.KeyEvent;
public class HomeAlbumActivityTest{
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static HomeAlbumActivity start(){HomeAlbumActivity a=new HomeAlbumActivity();a.onCreate(null);a.onResume();return a;}
 public static void main(String[] x){
 for(int key:new int[]{19,20,4}){HomeAlbumActivity a=start();HomeAlbumView v=(HomeAlbumView)a.content;check(v.active,"native view active without a WebView");a.dispatchKeyEvent(new KeyEvent(key,0,0));a.dispatchKeyEvent(new KeyEvent(key,1,0));a.dispatchKeyEvent(new KeyEvent(key,0,1));check(a.finishes==1&&a.result.page==(key==19?1:key==20?3:0),"up/apps down/messages back/home exactly once");a.onPause();a.onWindowFocusChanged(true);check(!v.active,"finishing frame cannot restart");a.onDestroy();}
 HomeAlbumActivity a=start();HomeAlbumView view=(HomeAlbumView)a.content;view.settings.run();check(a.started.target==GalleryActivity.class&&a.finishes==0,"confirm opens management and retains photo page");a.onPause();check(!view.active,"management pauses photos");a.onResume();check(view.active,"management return reuses viewer");a.dispatchKeyEvent(new KeyEvent(231,0,0));check(a.keys==1&&a.finishes==0,"voice belongs to system");a.dispatchKeyEvent(new KeyEvent(22,0,0));check(a.keys==2,"left/right delegated to native photo view");a.onDestroy();a.onResume();check(!view.active,"destroyed child stays inactive");
 HomeAlbumActivity orphan=start();orphan.root=true;orphan.onBackPressed();check(orphan.started.target==MainActivity.class&&orphan.finishes==1,"orphan child recovers app home");orphan.onDestroy();System.out.println(checks+" production photo-page lifecycle/navigation assertions passed with JVM doubles");}
}
