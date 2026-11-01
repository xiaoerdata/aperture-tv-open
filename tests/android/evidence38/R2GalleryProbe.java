package org.zhixiaoce.aperture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** No socket creation: even an accidental confirmation can only start this in-memory fake. */
public final class R2GalleryProbe extends GalleryActivity {
    @Override protected boolean useWebGallery(){return true;}
    final RecoveryClock clock=new RecoveryClock();ControlledWebView view;
    volatile int created;volatile FakeSession last;boolean delay,holdStart,holdUi;volatile long pauseAt,destroyAt;volatile int terminals,webCommits,nativeCommits;
    final java.util.concurrent.ConcurrentLinkedQueue<Runnable> starts=new java.util.concurrent.ConcurrentLinkedQueue<>(),uiTasks=new java.util.concurrent.ConcurrentLinkedQueue<>();
    final java.util.concurrent.ConcurrentLinkedQueue<org.json.JSONObject> boundaries=new java.util.concurrent.ConcurrentLinkedQueue<>();
    @Override protected android.webkit.WebView newWebView(){return view=new ControlledWebView(this);}
    @Override protected PageDeadline.Clock deadlineClock(){return clock;}
    @Override protected PageDeadline.Scheduler deadlineScheduler(){return clock;}
    @Override protected GalleryUploadSession createUploadSession(Runnable changed){created++;return last=new FakeSession(delay,changed);}
    @Override protected void dispatchUploadStart(Runnable task){if(holdStart)starts.add(task);else super.dispatchUploadStart(task);}
    @Override protected void dispatchUploadUi(Runnable task){if(holdUi)uiTasks.add(task);else super.dispatchUploadUi(task);}
    void releaseStarts(){for(Runnable task;(task=starts.poll())!=null;)new Thread(task,"Fake-start-release").start();}
    void releaseUi(){for(Runnable task;(task=uiTasks.poll())!=null;)task.run();}
    private Object state(String name){try{java.lang.reflect.Field f=GalleryActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(this);}catch(Exception e){throw new AssertionError(e);}}
    org.json.JSONObject snapshot(String event){try{return new org.json.JSONObject().put("event",event).put("uptime",android.os.SystemClock.uptimeMillis()).put("isFinishing",isFinishing()).put("isDestroyed",isDestroyed()).put("closed",state("closed")).put("resumed",state("resumed")).put("pauseAt",pauseAt).put("destroyAt",destroyAt).put("created",created).put("fakeClosed",last!=null&&last.closed).put("fakeCloses",last==null?0:last.closes).put("fakeStarts",last==null?0:last.starts).put("fakeStartedAt",last==null?0:last.startedAt).put("fakeReturnedAt",last==null?0:last.returnedAt).put("fakeClosedAt",last==null?0:last.closedAt).put("fakeTimedOut",last!=null&&last.timedOut).put("everSettingsUpload",hasScript("Settings.upload(")).put("everSettingsStatus",hasScript("Settings.status(")).put("webCommits",webCommits).put("nativeCommits",nativeCommits).put("terminals",terminals);}catch(org.json.JSONException e){throw new AssertionError(e);}}
    private boolean hasScript(String snippet){if(view!=null)synchronized(view.evaluated){for(String s:view.evaluated)if(s.contains(snippet))return true;}return false;}
    @Override protected synchronized void onUploadBoundary(GalleryTrace.Stage event){
        super.onUploadBoundary(event);if(event==GalleryTrace.Stage.UPLOAD_WEB_COMMITTED)webCommits++;if(event==GalleryTrace.Stage.UPLOAD_NATIVE_COMMITTED)nativeCommits++;
        boolean terminal=event==GalleryTrace.Stage.UPLOAD_START_REJECTED||event==GalleryTrace.Stage.UPLOAD_UI_REJECTED||event==GalleryTrace.Stage.UPLOAD_WEB_COMMITTED||event==GalleryTrace.Stage.UPLOAD_NATIVE_COMMITTED||event==GalleryTrace.Stage.UPLOAD_UI_FAILED;
        if(boundaries!=null)boundaries.add(snapshot(event.name()));
        // Publish the wait gate after the complete terminal receipt, never before its JSON is stored.
        if(terminal)terminals++;
    }
    @Override protected void onPause(){super.onPause();pauseAt=android.os.SystemClock.uptimeMillis();}
    @Override protected void onDestroy(){super.onDestroy();destroyAt=android.os.SystemClock.uptimeMillis();}
    static final class FakeSession implements GalleryUploadSession {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        volatile int starts,closes;volatile boolean closed,timedOut,failAfterGate;volatile long startedAt,returnedAt,closedAt;final boolean delayed;final Runnable changed;
        FakeSession(boolean delayed,Runnable changed){this.delayed=delayed;this.changed=changed;}
        public String start()throws Exception {starts++;startedAt=android.os.SystemClock.uptimeMillis();entered.countDown();try{if(delayed&&!release.await(20,TimeUnit.SECONDS)){timedOut=true;throw new IllegalStateException("Fake gate deadline");}if(failAfterGate)throw new java.io.IOException("Synthetic fake start failure");return "https://synthetic.invalid/no-listener";}finally{returnedAt=android.os.SystemClock.uptimeMillis();returned.countDown();}}
        public void close(){closed=true;closes++;closedAt=android.os.SystemClock.uptimeMillis();}
        public boolean isClosed(){return closed;}
    }
}
