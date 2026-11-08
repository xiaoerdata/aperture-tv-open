package org.zhixiaoce.aperture;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.*;
import io.nayuki.qrcodegen.QrCode;

public final class PhoneEditingTest {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static final class Memory implements MessagePhoneSession.Notes {
        JSONObject current;
        Memory()throws Exception{current=MessageNotes.empty();}
        public synchronized JSONObject read()throws Exception{return new JSONObject(current.toString());}
        public synchronized JSONObject edit(JSONObject command,BooleanSupplier allowed)throws Exception{JSONObject next=MessageNotes.edit(current,command);if(!allowed.getAsBoolean())throw new IOException("closed");current=next;return read();}
    }
    static JSONObject save(long revision,String id,String text)throws Exception{return new JSONObject().put("revision",revision).put("action","save").put("id",id).put("text",text);}
    static int call(String address,String method,String bearer,String origin,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address.split("#")[0]+"api/notes").openConnection();c.setConnectTimeout(1000);c.setReadTimeout(2000);c.setRequestMethod(method);
        if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(origin!=null)c.setRequestProperty("Origin",origin);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
        try{return c.getResponseCode();}finally{c.disconnect();}
    }
    public static void main(String[] args)throws Exception{
        Memory notes=new Memory();JSONObject created=notes.edit(save(0,"","晚饭在冰箱"),()->true);String id=created.getJSONArray("messages").getJSONObject(0).getString("id");check(created.getLong("revision")==1,"create increments revision");
        try{notes.edit(save(0,"","重复提交"),()->true);throw new AssertionError();}catch(MessageNotes.Conflict expected){check(notes.read().getJSONArray("messages").length()==1,"retry does not duplicate note");}
        notes.edit(save(1,id,"改成今晚回家"),()->true);check(notes.read().getJSONArray("messages").getJSONObject(0).getString("text").equals("改成今晚回家"),"edit persists exact content");
        try{notes.edit(save(2,id,"x".repeat(501)),()->true);throw new AssertionError();}catch(IOException expected){check(notes.read().getLong("revision")==2,"reject long note without mutation");}
        try{notes.edit(save(2,id,"不得保存"),()->false);throw new AssertionError();}catch(IOException expected){check(notes.read().getLong("revision")==2,"revoked write does not mutate");}
        JSONObject deleted=new JSONObject().put("revision",2).put("action","delete").put("id",id);notes.edit(deleted,()->true);check(notes.read().getJSONArray("messages").length()==0,"delete persists");
        check(!MessagePhoneSession.privateAddress(InetAddress.getByName("8.8.8.8")),"public address rejected");
        AtomicBoolean allowed=new AtomicBoolean(true);MessagePhoneSession session=new MessagePhoneSession(InetAddress.getLoopbackAddress(),notes,"fixture".getBytes(StandardCharsets.UTF_8),allowed::get,()->{});
        String address=session.start(),secret=address.split("#")[1];
        try{
            check(call(address,"GET",null,null,null)==403,"API requires QR token");check(call(address,"GET","wrong",null,null)==403,"wrong token rejected");
            check(call(address,"GET",secret,null,null)==200,"paired reader accepted");
            check(call(address,"POST",secret,"https://example.invalid",save(3,"","blocked"))==403,"cross-origin write rejected");
            check(call(address,"POST",secret,null,save(3,"","<img src=x onerror=alert(1)>"))==200,"authenticated create works");
            check(call(address,"POST",secret,null,save(3,"","duplicate"))==409,"concurrent revision returns conflict");
            allowed.set(false);try{check(call(address,"GET",secret,null,null)==403,"revoked session rejected");}catch(IOException closed){check(true,"revocation closed listener");}
        }finally{session.close();}
        long revision=notes.read().getLong("revision");check(revision==4,"forbidden attempts did not change notes");
        MessagePhoneSession expiring=new MessagePhoneSession(InetAddress.getLoopbackAddress(),notes,new byte[0],()->true,()->{});String old=expiring.start(20);Thread.sleep(100);
        try{check(call(old,"POST",old.split("#")[1],null,save(4,"","expired"))==403,"expired writes rejected");}catch(IOException closed){check(true,"expiry closed connection");}finally{expiring.close();}
        check(notes.read().getLong("revision")==4,"expiry left storage unchanged");
        MessagePhoneSession slow=new MessagePhoneSession(InetAddress.getLoopbackAddress(),notes,new byte[0],()->true,()->{});String slowUrl=slow.start();URI uri=URI.create(slowUrl.split("#")[0]);
        try(Socket socket=new Socket(uri.getHost(),uri.getPort())){String request="POST /api/notes HTTP/1.1\r\nHost: "+uri.getAuthority()+"\r\nAuthorization: Bearer "+slowUrl.split("#")[1]+"\r\nContent-Type: application/json\r\nContent-Length: 4096\r\n\r\n{";socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));Thread.sleep(30);slow.close();}
        check(notes.read().getLong("revision")==4,"closing during body read prevents late write");
        String fixture="http://192.168.1.12:54321/#abcdefghijklmnopqrstuvwxyzABCDEF";QrCode qr=QrCode.encodeText(fixture,QrCode.Ecc.MEDIUM);int size=(qr.size+8)*8;StringBuilder pbm=new StringBuilder("P1\n"+size+" "+size+"\n");for(int y=0;y<size;y++){for(int x=0;x<size;x++)pbm.append(qr.getModule(x/8-4,y/8-4)?"1 ":"0 ");pbm.append('\n');}Files.writeString(Path.of(args[0]),pbm.toString());
        System.out.println(checks+" local-note CRUD, authorization, revision, expiry and cancellation checks passed; synthetic QR exported");
    }
}
