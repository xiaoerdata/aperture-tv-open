package org.zhixiaoce.aperture;
public final class RecoveryGalleryProbe extends GalleryActivity {
    @Override protected boolean useWebGallery(){return true;}
    final RecoveryClock clock=new RecoveryClock();ControlledWebView view;
    @Override protected android.webkit.WebView newWebView(){return view=new ControlledWebView(this);}
    @Override protected PageDeadline.Clock deadlineClock(){return clock;}
    @Override protected PageDeadline.Scheduler deadlineScheduler(){return clock;}
}
