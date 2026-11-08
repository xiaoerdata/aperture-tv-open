package org.zhixiaoce.aperture;

/** Reproduce specific v41 control-flow gaps with the same framework doubles. */
public final class AmbientReturnBeforeTest {
    private static AmbientWeb page(AmbientActivity a)throws Exception{java.lang.reflect.Field f=AmbientActivity.class.getDeclaredField("page");f.setAccessible(true);return (AmbientWeb)f.get(a);}
    private static AmbientActivity screen(boolean root){AmbientActivity a=new AmbientActivity();a.root=root;a.intent.putExtra("panel","weather");a.onCreate(null);a.onResume();return a;}
    public static void main(String[] args)throws Exception{
        AmbientActivity dead=screen(false);AmbientWeb p=page(dead);p.web.rendererGone();boolean unsafe=false;
        try{dead.onPause();}catch(IllegalStateException expected){unsafe=true;}
        boolean rendererUndisposed=p.web.destroys==0;dead.onDestroy();
        AmbientActivity root=screen(true);root.onBackPressed();boolean rootLeavesWithoutHome=root.finishes==1&&root.starts.isEmpty();root.onDestroy();
        AmbientActivity keys=screen(false);keys.dispatchKeyEvent(new android.view.KeyEvent(0,4));keys.dispatchKeyEvent(new android.view.KeyEvent(1,4));boolean reachesFocusedView=keys.superKeys==2&&keys.finishes==0;keys.onDestroy();
        if(!unsafe||!rendererUndisposed||!rootLeavesWithoutHome||!reachesFocusedView)throw new AssertionError("v41 baseline did not reproduce expected control-flow gaps");
        System.out.println(new org.json.JSONObject().put("deadWebViewUsedOnPause",unsafe).put("rendererNotDisposedInCallback",rendererUndisposed).put("taskRootFinishesWithoutHome",rootLeavesWithoutHome).put("backDispatchedToFocusedWebView",reachesFocusedView).toString());
    }
}
