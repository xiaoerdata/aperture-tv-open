package org.zhixiaoce.aperture;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real coordination tests with blocked built-ins, never a user plugin or a simulated CPython claim. */
public final class PluginPreparationTest {
    private static int assertions;
    private static final CinemaPythonPreparation.Guard ALLOWED=()->{};
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
    private interface Work {void run()throws Exception;}
    private static IOException denied(Work action,String text)throws Exception {
        try{action.run();throw new AssertionError("Expected: "+text);}catch(IOException expected){check(expected.getMessage().contains(text),expected.toString());return expected;}
    }
    private static void signaled(CountDownLatch signal)throws Exception {
        if(!signal.await(3,TimeUnit.SECONDS))throw new AssertionError("Fixture coordination timed out");
    }
    private static void guard(AtomicBoolean allowed)throws IOException {
        if(!allowed.get()||Thread.currentThread().isInterrupted())throw new IOException("插件操作已取消");
    }
    public static void main(String[] args)throws Exception {
        check(CinemaPythonPreparation.TIMEOUT_MILLIS==60_000,"Production preparation has its own 60s budget");
        ExecutorService callers=Executors.newCachedThreadPool();
        try {
            // Two waiters share one initializer; cancelling a page cannot interrupt another page's preparation.
            CinemaPythonPreparation shared=new CinemaPythonPreparation(3000);
            CountDownLatch began=new CountDownLatch(1),release=new CountDownLatch(1),secondWaiting=new CountDownLatch(1);
            AtomicInteger starts=new AtomicInteger(),interrupts=new AtomicInteger();AtomicBoolean firstAllowed=new AtomicBoolean(true);
            Object module=new Object();
            Callable<Object> builtins=()->{starts.incrementAndGet();began.countDown();try{release.await();}catch(InterruptedException e){interrupts.incrementAndGet();throw e;}return module;};
            Future<Object> first=callers.submit(()->shared.await(builtins,()->guard(firstAllowed)));
            signaled(began);
            Future<Object> second=callers.submit(()->shared.await(()->{throw new AssertionError("Duplicate preparation");},()->secondWaiting.countDown()));
            signaled(secondWaiting);firstAllowed.set(false);
            try{first.get(2,TimeUnit.SECONDS);throw new AssertionError("Cancelled page continued");}
            catch(ExecutionException error){check(error.getCause() instanceof IOException&&error.getCause().getMessage().contains("已取消"),"Cancel ends only the first waiter");}
            check(!second.isDone(),"Other caller still waits for the shared future");
            check(interrupts.get()==0,"Page cancellation never interrupts built-in initialization");
            release.countDown();check(second.get(2,TimeUnit.SECONDS)==module,"Other caller obtains initialized module");
            check(starts.get()==1,"Only one initializer runs across callers");
            check(shared.await(()->{throw new AssertionError("Ready preparation repeated");},ALLOWED)==module,"Successful preparation is reused");

            // An interrupt on a waiting Activity worker is not forwarded to the process-wide initializer.
            CinemaPythonPreparation interruptible=new CinemaPythonPreparation(3000);
            CountDownLatch interruptedBegan=new CountDownLatch(1),interruptedRelease=new CountDownLatch(1);
            AtomicBoolean retainedInterrupt=new AtomicBoolean();AtomicReference<Throwable> cancelledResult=new AtomicReference<>();
            Thread waiting=new Thread(()->{try{interruptible.await(()->{interruptedBegan.countDown();interruptedRelease.await();return module;},ALLOWED);cancelledResult.set(new AssertionError("Interrupted caller continued"));}catch(Throwable e){cancelledResult.set(e);retainedInterrupt.set(Thread.currentThread().isInterrupted());}});
            waiting.start();signaled(interruptedBegan);waiting.interrupt();waiting.join(2000);
            check(!waiting.isAlive(),"Interrupted caller leaves promptly");
            check(cancelledResult.get() instanceof IOException&&cancelledResult.get().getMessage().contains("已取消"),"Interrupt reports cancellation");
            check(retainedInterrupt.get(),"Interrupt flag is preserved for the Activity worker");
            interruptedRelease.countDown();check(interruptible.await(()->{throw new AssertionError("Interrupted shared future restarted");},ALLOWED)==module,"Interrupted waiter does not cancel shared preparation");

            CinemaPythonPreparation unauthorized=new CinemaPythonPreparation(3000);AtomicInteger deniedStarts=new AtomicInteger();
            denied(()->unauthorized.await(()->{deniedStarts.incrementAndGet();return module;},()->{throw new IOException("授权已撤销");}),"授权已撤销");
            check(deniedStarts.get()==0,"Authorization is checked before preparing any runtime");

            // Authorization can change while the built-ins load, even when they finish successfully.
            CinemaPythonPreparation revoked=new CinemaPythonPreparation(3000);AtomicBoolean approved=new AtomicBoolean(true);AtomicInteger userCode=new AtomicInteger();
            denied(()->{revoked.await(()->{approved.set(false);return module;},()->{if(!approved.get())throw new IOException("授权已撤销");});userCode.incrementAndGet();},"授权已撤销");
            check(userCode.get()==0,"Revocation during preparation prevents later user-code execution");
            check(revoked.await(()->{throw new AssertionError("Revocation poisoned preparation");},ALLOWED)==module,"Per-caller revocation leaves built-ins usable");

            // Preparation failure is distinct from a user-plugin timeout and never repeatedly starts CPython.
            CinemaPythonPreparation broken=new CinemaPythonPreparation(3000);AtomicInteger failures=new AtomicInteger();
            IOException failure=denied(()->broken.await(()->{failures.incrementAndGet();throw new IllegalStateException("fixture");},ALLOWED),"内置运行环境准备失败");
            check(failure.getCause() instanceof IllegalStateException,"Original built-in failure remains diagnosable");
            denied(()->broken.await(()->{failures.incrementAndGet();return module;},ALLOWED),"内置运行环境准备失败");
            check(failures.get()==1,"Failed initialization cannot spawn another native initializer");
            check(shared.await(()->null,ALLOWED)==module,"Failure is local to its preparation state");

            // Deadline belongs to preparation, not each caller. A late completion cannot revive a timed-out runtime.
            CinemaPythonPreparation slow=new CinemaPythonPreparation(80);CountDownLatch slowRelease=new CountDownLatch(1),slowFinished=new CountDownLatch(1);AtomicInteger slowStarts=new AtomicInteger();
            long begin=System.nanoTime();
            try{denied(()->slow.await(()->{slowStarts.incrementAndGet();slowRelease.await();slowFinished.countDown();return module;},ALLOWED),"内置运行环境准备超时");}
            finally{slowRelease.countDown();}
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)<2000,"Preparation wait is bounded");
            signaled(slowFinished);
            denied(()->slow.await(()->{slowStarts.incrementAndGet();return module;},ALLOWED),"内置运行环境准备超时");
            check(slowStarts.get()==1,"Timeout is shared, not renewed by a later caller");

            // A timely successful future stays reusable after the original deadline has passed.
            CinemaPythonPreparation completed=new CinemaPythonPreparation(100);
            check(completed.await(()->module,ALLOWED)==module,"Ready initializer completes before its deadline");
            Thread.sleep(120);
            check(completed.await(()->{throw new AssertionError("Cached success expired");},ALLOWED)==module,"Successful cache does not expire at the initial deadline");
        } finally {callers.shutdownNow();}
        System.out.println(assertions+" Python preparation assertions passed");
    }
}
