package org.zhixiaoce.aperture;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.*;
import javax.net.ssl.*;
import okhttp3.OkHttpClient;

/** Synthetic localhost JVM protocol harness. Test credentials arrive through stdin. */
public final class NasProtocolHarness {
    private static String decode(String value){return value.equals("-")?"":new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static String encode(byte[] value){return Base64.getEncoder().encodeToString(value);}
    public static void main(String[] args)throws Exception{
        OkHttpClient.Builder tls=new OkHttpClient.Builder();
        if(args.length==1){KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null);try(InputStream cert=new FileInputStream(args[0])){store.setCertificateEntry("fixture",CertificateFactory.getInstance("X.509").generateCertificate(cert));}TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);X509TrustManager manager=(X509TrustManager)managers.getTrustManagers()[0];SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{manager},null);tls.sslSocketFactory(context.getSocketFactory(),manager);}
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));String line;NasReader reader=null;NasReader.Resource retained=null;
        try{while((line=input.readLine())!=null){String[] p=line.split(" ");String id=p[0],command=p[1];try{
            String result="";
            if(command.equals("OPEN")){if(retained!=null){retained.close();retained=null;}if(reader!=null){reader.close();reader=null;}NasLocation location=new NasLocation(decode(p[2]));char[] password=decode(p[4]).toCharArray();try{reader=location.smb?new SmbNasReader(location,decode(p[3]),password,decode(p[5])):new DavNasReader(location,decode(p[3]),password,tls);}finally{Arrays.fill(password,'\0');}}
            else if(command.equals("LIST")){StringBuilder rows=new StringBuilder();for(NasReader.Entry e:reader.list(decode(p[2])))rows.append(e.directory?"D":"F").append('\t').append(e.size).append('\t').append(e.path).append('\n');result=rows.toString();}
            else if(command.equals("RETAIN")){if(retained!=null)retained.close();retained=reader.open(decode(p[2]));result=Long.toString(retained.size());}
            else if(command.equals("AT")){byte[] data=new byte[Integer.parseInt(p[3])];int n=retained.readAt(Long.parseLong(p[2]),data,0,data.length);result=n<0?"EOF":encode(Arrays.copyOf(data,n));}
            else if(command.equals("CLOSE")){if(retained!=null){retained.close();retained=null;}if(reader!=null)reader.close();}
            else if(command.equals("LOCATION")){NasLocation location=new NasLocation(decode(p[2]));result=location.smb?location.smbPath(decode(p[3])):location.davPath(decode(p[3]),true).toString();}
            else if(command.equals("PARSE")){result=Integer.toString(DavNasReader.parse(new NasLocation(decode(p[2])),"",Base64.getDecoder().decode(p[3])).size());}
            else if(command.equals("BOUND")){NasLibrary library=new NasLibrary("[]");org.json.JSONObject item=library.add(new org.json.JSONObject(decode(p[2])));library.assign(p[3],item.getString("id"),decode(p[4]));NasLibrary.Route route=library.route(p[3]);org.json.JSONObject config=route.connection;char[] password=config.getString("password").toCharArray();try(NasReader selected=NasReader.connect(new NasLocation(config.getString("root")),config.getString("user"),password,config.getString("domain"))){StringBuilder rows=new StringBuilder();for(NasReader.Entry e:selected.list(route.path))rows.append(e.path).append('\n');result=rows.toString();}finally{Arrays.fill(password,'\0');}}
            else throw new IllegalArgumentException();
            System.out.println(id+" OK "+encode(result.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception error){if("1".equals(System.getenv("APERTURE_NAS_TEST_TRACE")))error.printStackTrace(System.err);System.out.println(id+" ERROR");}System.out.flush();}}
        finally{if(retained!=null)retained.close();if(reader!=null)reader.close();}
    }
}
