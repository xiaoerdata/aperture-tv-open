package org.zhixiaoce.aperture;
import java.net.*;import java.nio.file.*;
/** Loopback only. No Android, household data or production listener. */
public final class PhoneEditorFixture {
    public static void main(String[] args)throws Exception{
        MessagePhoneSession session=new MessagePhoneSession(InetAddress.getLoopbackAddress(),new PhoneEditingTest.Memory(),Files.readAllBytes(Path.of(args[0])),()->true,()->{});
        Runtime.getRuntime().addShutdownHook(new Thread(session::close));System.out.println(session.start());System.out.flush();System.in.read();session.close();
    }
}
