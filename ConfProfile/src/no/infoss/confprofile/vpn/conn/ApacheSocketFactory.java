
package no.infoss.confprofile.vpn.conn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import no.infoss.confprofile.vpn.VpnManagerInterface;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;


public final class ApacheSocketFactory implements SocketFactory {
	private VpnManagerInterface mVpnMgr;
    
    public ApacheSocketFactory(VpnManagerInterface vpnMgr) {
        mVpnMgr = vpnMgr;        
    }

    @Override
    public Socket createSocket() {
        return new Socket();
    }

    public Socket connectSocket(Socket sock, 
    		String host, 
    		int port, 
    		InetAddress localAddress, 
    		int localPort, 
    		HttpParams params) throws IOException {

        if(host == null) {
            throw new IllegalArgumentException("Target host may not be null.");
        }
        
        if(params == null) {
            throw new IllegalArgumentException("Parameters may not be null.");
        }

        if(sock == null)
            sock = createSocket();
        
        if(localPort < 0) {
        	localPort = 0;
        }

        if(localAddress == null && localPort == 0) {
        	sock = sock.getChannel().socket(); //magic for creating underlying socket
        } else {
            sock.bind(new InetSocketAddress(localAddress, localPort));
        }
        
        //protect socket here
        mVpnMgr.protect(sock);

        int timeout = HttpConnectionParams.getConnectionTimeout(params);

        try {
            sock.connect(new InetSocketAddress(host, port), timeout);
        } catch (SocketTimeoutException e) {
        	String fmt = "Connection timeout: %s (%d ms)";
            throw new ConnectTimeoutException(
            		String.format(fmt, String.valueOf(sock.getRemoteSocketAddress()), timeout));
        }
        
        return sock;
    }


    @Override
    public boolean isSecure(Socket sock) throws IllegalArgumentException {
        return false;
    }


    @Override
    public boolean equals(Object obj) {
        return (obj == this);
    }

    @Override
    public int hashCode() {
        return ApacheSocketFactory.class.hashCode();
    }

}
