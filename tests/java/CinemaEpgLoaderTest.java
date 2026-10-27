package org.zhixiaoce.aperture;

import android.os.Looper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Production loader/OkHttp with real HTTP and wall-clock TTL; callback queue is a test shim. */
public final class CinemaEpgLoaderTest {
    private static final JSONArray checks = new JSONArray();
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks.put(message);
    }
    private static void until(BooleanSupplier ready, long timeout) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() >= end) throw new AssertionError("Timed out waiting for HTTP/callback");
            Looper.dispatchOne(25);
        }
    }
    private static void quiet(long millis) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) Looper.dispatchOne(20);
    }
    private static final class Result implements CinemaEpgLoader.Done {
        CinemaEpg guide; String state; int count;
        public void accept(CinemaEpg guide, String state) { this.guide=guide; this.state=state; count++; }
    }
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService network = Executors.newCachedThreadPool();
        Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
        List<JSONObject> audit = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch handoffStarted = new CountDownLatch(1), cancelStarted = new CountDownLatch(1);
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String xml = "<tv><channel id='one'><display-name>合成频道</display-name></channel><programme channel='one' start='"+
            stamp.format(now.minusMinutes(5))+"' stop='"+stamp.format(now.plusMinutes(55))+"'><title>合成恢复节目</title></programme></tv>";
        server.setExecutor(network);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int number = requests.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            int status = path.equals("/missing") || path.equals("/cancel") || path.equals("/cache") && number <= 2 ? 503 : 200;
            JSONObject event = new JSONObject().put("path",path).put("request",number).put("status",status).put("atEpochMillis",System.currentTimeMillis());
            audit.add(event);
            if (path.equals("/handoff")) {
                handoffStarted.countDown();
                try { Thread.sleep(300); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            if (path.equals("/cancel")) cancelStarted.countDown();
            byte[] body = (status == 200 ? xml : "synthetic unavailable").getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        List<CinemaEpgLoader> loaders = new ArrayList<>();
        JSONObject report = new JSONObject().put("testScope","host JVM production EPG loader and real loopback HTTP; test-only callback queue, not Android runtime")
            .put("synthetic",true).put("realXiaomi",false).put("nativeUiExecuted",false).put("ttlChangedByTest",false);
        Throwable failure = null;
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            CinemaCatalog.Channel channel = new CinemaCatalog.Channel("合成频道","测试",Arrays.asList(base+"/media"));
            channel.epgId="one";
            CinemaEpgLoader cache = new CinemaEpgLoader(); loaders.add(cache);
            List<String> urls=Arrays.asList(base+"/cache"); Result failed=new Result();cache.load(urls,true,failed);
            until(()->failed.count==1,5000);
            check(requests.get("/cache").get()==2,"two consecutive failures perform exactly two HTTP attempts");
            check(!failed.state.isEmpty()&&failed.guide.programmes(channel).isEmpty(),"both failures return unavailable state without invented programmes");
            long first=audit.get(0).getLong("atEpochMillis"),second=audit.get(1).getLong("atEpochMillis");
            check(second-first>=450,"retry is backed off rather than a tight request loop");
            long failedAt=System.nanoTime();Result cachedFailure=new Result();cache.load(urls,true,cachedFailure);
            check(cachedFailure.count==1&&cachedFailure.guide==failed.guide,"immediate reload receives the same failed-cache result");
            check(requests.get("/cache").get()==2,"failed cache suppresses another HTTP request before expiry");
            // Keep the actual 30-second production TTL; never edit fields or substitute a test clock.
            quiet(30200);
            check(requests.get("/cache").get()==2,"cache expiry alone does not start background requests");
            Result recovered=new Result();cache.load(urls,true,recovered);until(()->recovered.count==1,5000);
            long waited=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-failedAt);
            check(waited>=30000&&requests.get("/cache").get()==3,"next load after the real 30-second TTL retries HTTP");
            check(recovered.state.isEmpty()&&recovered.guide.nowNext(channel,System.currentTimeMillis()).contains("合成恢复节目"),"fresh HTTP 200 restores actual parsed programme text");
            Result cachedSuccess=new Result();cache.load(urls,true,cachedSuccess);
            check(cachedSuccess.guide==recovered.guide&&requests.get("/cache").get()==3,"successful guide is reused without another request");
            report.put("failedCacheRecoveryWaitMillis",waited);

            CinemaEpgLoader handoff=new CinemaEpgLoader();loaders.add(handoff);Result oldOwner=new Result(),newOwner=new Result();
            handoff.load(Arrays.asList(base+"/handoff"),true,oldOwner);until(()->handoffStarted.getCount()==0,5000);
            handoff.load(Arrays.asList(base+"/handoff"),true,newOwner);until(()->newOwner.count==1,5000);
            check(oldOwner.count==0&&requests.get("/handoff").get()==1,"in-flight callback moves to the newest owner without duplicate network work");
            check(newOwner.guide.programmes(channel).size()==1,"new owner receives the real parsed guide");

            CinemaEpgLoader partial=new CinemaEpgLoader();loaders.add(partial);Result partlyAvailable=new Result();
            List<String> mixed=Arrays.asList(base+"/valid",base+"/missing");partial.load(mixed,true,partlyAvailable);until(()->partlyAvailable.count==1,5000);
            check(partlyAvailable.guide.programmes(channel).size()==1&&!partlyAvailable.state.isEmpty(),"partial failure preserves valid programme data and exposes incomplete state");
            check(requests.get("/valid").get()==1&&requests.get("/missing").get()==2,"only the failed source is retried");
            Result cachedPartial=new Result();partial.load(mixed,true,cachedPartial);
            check(cachedPartial.guide==partlyAvailable.guide&&requests.get("/missing").get()==2,"partial-result cache suppresses immediate retry storms");

            CinemaEpgLoader cancel=new CinemaEpgLoader();loaders.add(cancel);Result canceled=new Result();
            cancel.load(Arrays.asList(base+"/cancel"),true,canceled);until(()->cancelStarted.getCount()==0,5000);cancel.close();quiet(850);
            check(requests.get("/cancel").get()==1,"closing the loader prevents a retry after the first failed request");
            check(canceled.count==0,"closed loader never delivers a stale callback");
            report.put("passed",true);
        } catch (Throwable error) { failure=error;report.put("passed",false).put("error",error.toString()); }
        finally {
            for(CinemaEpgLoader loader:loaders)loader.close();server.stop(0);network.shutdownNow();
            report.put("checks",checks).put("httpAudit",new JSONArray(audit)).put("serverStopped",true);
            Files.writeString(Path.of(args[0]),report.toString(2)+"\n");
        }
        if(failure!=null)throw new AssertionError("EPG loader regression failed",failure);
        System.out.println(checks.length()+" production EPG loader assertions passed");
    }
}
