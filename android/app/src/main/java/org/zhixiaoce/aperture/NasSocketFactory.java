package org.zhixiaoce.aperture;

import java.io.IOException;
import java.net.*;
import javax.net.SocketFactory;

/** SMBJ's default factory has an unbounded TCP connect; keep it off the UI with a TCP bound. DNS remains OS-managed. */
final class NasSocketFactory extends SocketFactory {
    private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = new Socket();
        try { if (local != null) socket.bind(local); socket.connect(remote, 5000); socket.setSoTimeout(8000); return socket; }
        catch (IOException error) { try { socket.close(); } catch (IOException ignored) {} throw error; }
    }
    public Socket createSocket() { return new Socket(); }
    public Socket createSocket(String host, int port) throws IOException { return connect(new InetSocketAddress(host,port),null); }
    public Socket createSocket(InetAddress host,int port) throws IOException { return connect(new InetSocketAddress(host,port),null); }
    public Socket createSocket(String host,int port,InetAddress local,int localPort) throws IOException { return connect(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort)); }
    public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort) throws IOException { return connect(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort)); }
}
