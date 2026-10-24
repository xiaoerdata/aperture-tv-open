package org.zhixiaoce.aperture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** JVM transport harness: credentials arrive only on stdin, never process args or reports. */
public final class HouseholdProxyHarness {
    private static HouseholdProxy current;
    private static final ExecutorService workers = Executors.newFixedThreadPool(4, task -> { Thread thread = new Thread(task); thread.setDaemon(true); return thread; });
    private static String decode(String text) { return "-".equals(text) ? "" : new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
    private static synchronized void send(String value) { System.out.println(value); System.out.flush(); }
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)); String line;
        while ((line = input.readLine()) != null) {
            String[] parts = line.split(" "); String id = parts[0], command = parts[1];
            if ("SET".equals(command)) { if (current != null) current.close(); current = new HouseholdProxy(decode(parts[2]), decode(parts[3]), Boolean.parseBoolean(parts[4])); send(id + " OK"); }
            else if ("ACTIVE".equals(command)) { current.active(Boolean.parseBoolean(parts[2])); send(id + " OK"); }
            else if ("CLOSE".equals(command)) { current.close(); send(id + " OK"); }
            else if ("GET".equals(command)) { HouseholdProxy target = current; workers.execute(() -> {
                HouseholdProxy.Reply reply = target.read(decode(parts[2]), parts[3], Boolean.parseBoolean(parts[4]));
                send(reply == null ? id + " 0" : id + " " + reply.status + " " + reply.mime + " " + Base64.getEncoder().encodeToString(reply.body));
            }); }
        }
        if (current != null) current.close(); workers.shutdownNow();
    }
}
