package org.zhixiaoce.aperture;

import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.LongSupplier;

/** An explicit, memory-only device session. No anonymous read access or account credentials. */
final class LocalControlSession {
    private final LongSupplier clock;private final SecureRandom random=new SecureRandom();
    private String pin="",ticket="",token="";private long pairUntil,ticketUntil,tokenUntil;private int failures;private boolean accepted;
    LocalControlSession(LongSupplier clock){this.clock=clock;}
    private String secret(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    synchronized String begin(){close();pin=String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1000000));pairUntil=clock.getAsLong()+120000;return pin;}
    synchronized String request(String offered){if(clock.getAsLong()>=pairUntil||pin.isEmpty()||failures>=5)throw new SecurityException("配对窗口已关闭");if(!equal(pin,offered)){failures++;throw new SecurityException("配对码无效");}if(!ticket.isEmpty()&&clock.getAsLong()<ticketUntil)throw new IllegalStateException("已有待确认连接");ticket=secret();ticketUntil=clock.getAsLong()+60000;accepted=false;return ticket;}
    synchronized void approve(String offered,boolean allow){if(!equal(ticket,offered)||clock.getAsLong()>=ticketUntil)return;if(!allow){ticket="";return;}accepted=true;token=secret();tokenUntil=clock.getAsLong()+1800000;pin="";pairUntil=0;}
    synchronized String claim(String offered){if(!equal(ticket,offered)||clock.getAsLong()>=ticketUntil)throw new SecurityException("配对请求已过期");return accepted?token:"";}
    synchronized boolean authorized(String offered){return !token.isEmpty()&&clock.getAsLong()<tokenUntil&&equal(token,offered);}
    synchronized long remaining(){return Math.max(0,tokenUntil-clock.getAsLong());}
    synchronized void close(){pin="";ticket="";token="";pairUntil=0;ticketUntil=0;tokenUntil=0;failures=0;accepted=false;}
    private static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
