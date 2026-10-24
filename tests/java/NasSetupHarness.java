package org.zhixiaoce.aperture;
import java.io.*;
import org.json.JSONObject;

/** Test-only pipe. Public hello and ephemeral poll identity stay inside the synthetic test process. */
public final class NasSetupHarness {
    public static void main(String[] args)throws Exception{
        NasSetupCrypto receiver=new NasSetupCrypto();
        NasSetupClient client=null;JSONObject delivered=null;
        System.out.println(new JSONObject().put("hello",receiver.hello("开窗 · 标准版")).put("identity",receiver.identify()).put("fingerprint",receiver.fingerprint));
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));String line;
        while((line=input.readLine())!=null){
            try{
                JSONObject command=new JSONObject(line);
                if(command.has("start")){JSONObject start=command.getJSONObject("start");client=new NasSetupClient(start.getString("origin"),start.getString("cookie"),true);System.out.println(client.start(receiver,"开窗 · 标准版"));continue;}
                if(command.optBoolean("poll")){delivered=client.poll(receiver);System.out.println(delivered);continue;}
                if(command.has("finish")){System.out.println(client.finish(receiver,command.getString("finish"),delivered==null?0:delivered.getInt("revision")));continue;}
                if(command.optBoolean("verify")){
                    NasDraft draft=NasSetupImport.verify(receiver,client,delivered,()->!command.optBoolean("cancelled"));
                    NasLibrary library=new NasLibrary("[]");library.addDraft(draft);
                    javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance("AES");generator.init(256);javax.crypto.SecretKey key=generator.generateKey();
                    String encrypted=NasCipher.encrypt(library.json(),key);NasLibrary stored=new NasLibrary(NasCipher.decrypt(encrypted,key));
                    System.out.println(new JSONObject().put("valid",true).put("count",stored.connections().length()).put("bound",draft.role.isEmpty()||stored.route(draft.role)!=null).put("encrypted",!encrypted.contains(draft.connection.getString("password"))));continue;
                }
                if(command.optBoolean("close")){receiver.close();System.out.println("{\"closed\":true}");continue;}
                NasDraft draft=new NasDraft(receiver.open(command.getJSONObject("envelope")));
                System.out.println(new JSONObject().put("valid",true).put("role",draft.role).put("brand",draft.connection.getString("brand")));
            }catch(Exception invalid){System.out.println("{\"valid\":false}");}
        }
        receiver.close();
    }
}
