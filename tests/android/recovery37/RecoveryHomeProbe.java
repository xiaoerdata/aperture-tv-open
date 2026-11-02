package org.zhixiaoce.aperture;
public final class RecoveryHomeProbe extends MainActivity {
    static int engine=90;final RecoveryClock clock=new RecoveryClock();ControlledWebView view;
    @Override protected int webViewVersion(){return engine;}
    @Override protected android.webkit.WebView newWebView(){return view=new ControlledWebView(this);}
    @Override protected PageDeadline.Clock deadlineClock(){return clock;}
    @Override protected PageDeadline.Scheduler deadlineScheduler(){return clock;}
}
