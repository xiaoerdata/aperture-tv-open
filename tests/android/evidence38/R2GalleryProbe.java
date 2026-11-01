package org.zhixiaoce.aperture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** No socket creation: even an accidental confirmation can only start this in-memory fake. */
public final class R2GalleryProbe extends GalleryActivity {
    @Override protected boolean useWebGallery(){return true;}
    final RecoveryClock clock=new RecoveryClock();ControlledWebView view;
    volatile int created;volatile FakeSession last;boolean delay;
    @Override protected android.webkit.WebView newWebView(){return view=new ControlledWebView(this);}
    @Override protected PageDeadline.Clock deadlineClock(){return clock;}
    @Override protected PageDeadline.Scheduler deadlineScheduler(){return clock;}
    @Override protected GalleryUploadSession createUploadSession(Runnable changed){created++;return last=new FakeSession(delay);}
    static final class FakeSession implements GalleryUploadSession {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        volatile int starts,closes;volatile boolean closed;final boolean delayed;
        FakeSession(boolean delayed){this.delayed=delayed;}
        public String start()throws Exception {starts++;entered.countDown();try{if(delayed&&!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Fake deadline");return "https://synthetic.invalid/no-listener";}finally{returned.countDown();}}
        public void close(){closed=true;closes++;}
        public boolean isClosed(){return closed;}
    }
}
