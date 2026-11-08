package org.zhixiaoce.aperture;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Ten-minute, foreground-only phone editor. Tokens stay in URL fragments and request headers. */
final class MessagePhoneSession implements AutoCloseable {
    interface Notes {JSONObject read() throws Exception;JSONObject edit(JSONObject command,BooleanSupplier allowed) throws Exception;}
    private final InetAddress address;private final Notes notes;private final byte[] page;private final BooleanSupplier allowed;private final Runnable changed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<>());
    private final Object writes=new Object();private volatile ServerSocket server;private volatile boolean closed;private long deadline;private String token,host;
    MessagePhoneSession(InetAddress address,Notes notes,byte[] page,BooleanSupplier allowed,Runnable changed){this.address=address;this.notes=notes;this.page=page;this.allowed=allowed;this.changed=changed;}
    synchronized String start() throws IOException {return start(600000);}
    synchronized String start(long lifetime) throws IOException {
        if(closed||server!=null||!allowed.getAsBoolean()||lifetime<=0||lifetime>600000)throw new IOException("扫码编辑已关闭");
        if(!privateAddress(address))throw new IOException("请连接家庭网络");
        byte[] secret=new byte[24];new SecureRandom().nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        ServerSocket listener=new ServerSocket(0,2,address);server=listener;listener.setSoTimeout(500);deadline=System.nanoTime()+lifetime*1000000;host=address.getHostAddress()+":"+listener.getLocalPort();
        Thread accept=new Thread(()->{try{while(valid()){
            try{Socket client=listener.accept();if(!privateAddress(client.getInetAddress())||clients.size()>=2){client.close();continue;}
                clients.add(client);client.setSoTimeout(5000);try{worker.execute(()->serve(client));}catch(RejectedExecutionException stopped){clients.remove(client);client.close();}
            }catch(SocketTimeoutException again){}
        }}catch(IOException stopped){}finally{close();}},"Kaishi-message-editor");accept.setDaemon(true);accept.start();
        return "http://"+host+"/#"+token;
    }
    static boolean privateAddress(InetAddress value){return value instanceof Inet4Address&&(value.isSiteLocalAddress()||value.isLoopbackAddress());}
    static InetAddress lanAddress() throws IOException {
        InetAddress fallback=null;
        for(NetworkInterface net:Collections.list(NetworkInterface.getNetworkInterfaces())){
            if(!net.isUp()||net.isLoopback()||net.getName().startsWith("tun"))continue;
            for(InetAddress ip:Collections.list(net.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){
                if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return ip;fallback=ip;
            }
        }if(fallback==null)throw new IOException("电视尚未连接家庭网络");return fallback;
    }
    boolean isClosed(){return closed;}
    private boolean valid(){return !closed&&allowed.getAsBoolean()&&server!=null&&!server.isClosed()&&System.nanoTime()<deadline;}
    private String line(InputStream in) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();for(int i=0;i<4096;i++){int c=in.read();if(c<0)throw new EOFException();if(c==10)return b.toString("US-ASCII").replaceFirst("\\r$","");b.write(c);}throw new IOException("Header too long");}
    private void serve(Socket socket){try(Socket client=socket){
        InputStream in=client.getInputStream();String[] request=line(in).split(" ");if(request.length!=3)throw new IOException();
        Map<String,String> headers=new HashMap<>();for(int i=0;i<32;i++){String s=line(in);if(s.isEmpty())break;int at=s.indexOf(':');if(at<1||headers.put(s.substring(0,at).toLowerCase(Locale.ROOT),s.substring(at+1).trim())!=null||i==31)throw new IOException();}
        if(!valid()||!host.equals(headers.get("host"))||headers.containsKey("transfer-encoding")){reply(client,403,"会话已关闭或地址无效");return;}
        if(request[0].equals("GET")&&request[1].equals("/")){reply(client,200,"text/html; charset=utf-8",page);return;}
        if(!request[1].equals("/api/notes")||!("Bearer "+token).equals(headers.get("authorization"))||headers.containsKey("origin")&&!("http://"+host).equals(headers.get("origin"))){reply(client,403,"请重新扫描电视上的二维码");return;}
        if(request[0].equals("GET")){synchronized(writes){if(!valid()){reply(client,403,"扫码编辑已关闭");return;}json(client,200,notes.read());}return;}
        if(!request[0].equals("POST")){reply(client,405,"不支持此操作");return;}
        if(!headers.getOrDefault("content-type","").split(";")[0].trim().equals("application/json")){reply(client,415,"请使用留言编辑页");return;}
        int size;try{size=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(NumberFormatException invalid){reply(client,400,"请求无效");return;}
        if(size<=0||size>8192){reply(client,413,"留言内容过长");return;}
        byte[] body=new byte[size];new DataInputStream(in).readFully(body);
        try{JSONObject next;synchronized(writes){if(!valid()){reply(client,403,"扫码编辑已关闭");return;}next=notes.edit(new JSONObject(new String(body,StandardCharsets.UTF_8)),this::valid);}changed.run();json(client,200,next);}
        catch(MessageNotes.Conflict conflict){reply(client,409,conflict.getMessage());}
        catch(Exception invalid){reply(client,400,invalid instanceof IOException?invalid.getMessage():"留言内容无效");}
    }catch(Exception ignored){}finally{clients.remove(socket);}}
    private void json(Socket s,int status,JSONObject body)throws IOException{reply(s,status,"application/json; charset=utf-8",body.toString().getBytes(StandardCharsets.UTF_8));}
    private void reply(Socket s,int status,String error)throws Exception{json(s,status,new JSONObject().put("error",error));}
    private void reply(Socket socket,int status,String type,byte[] body)throws IOException{
        OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Reply\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; form-action 'none'; base-uri 'none'\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    public void close(){synchronized(writes){closed=true;}ServerSocket listener=server;if(listener!=null)try{listener.close();}catch(IOException ignored){}synchronized(clients){for(Socket client:clients)try{client.close();}catch(IOException ignored){}}worker.shutdownNow();}
}
