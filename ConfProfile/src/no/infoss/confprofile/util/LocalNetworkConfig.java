package no.infoss.confprofile.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;


public class LocalNetworkConfig {
	private int mSubnetIp;
	private int mSubnetMask;
	private int mLocalIp;
	private int mRemoteIp;
	
	private InetAddress[] mDns = new InetAddress[4];
	
	public LocalNetworkConfig(String subnet) {
		init(NetUtils.ip4StrToInt(subnet));
	}
	
	public LocalNetworkConfig(int subnet) {
		init(subnet);
	}
	
	private void init(int subnetIp) {
		int mask = 29;
		int bitmask = ((int)0xffffffff >>> (32 - mask)) << (32 - mask);
		
		mSubnetIp = subnetIp & bitmask;
		mSubnetMask = mask;
		
		int ip = mSubnetIp;
		byte[] buff = new byte[4];
		for(int i = 0; i < 4; i++) {
			ip++;
			
			try {
				mDns[i] = Inet4Address.getByAddress(NetUtils.ip4IntToBytes(ip, buff));
			} catch (UnknownHostException e) {
				mDns[i] = null;
			}
		}
		
		mRemoteIp = ip + 1;
		mLocalIp = ip + 2;
	}
	
	public int getSubnetIp() {
		return mSubnetIp;
	}

	public int getSubnetMask() {
		return mSubnetMask;
	}

	public int getLocalIp() {
		return mLocalIp;
	}

	public int getRemoteIp() {
		return mRemoteIp;
	}

	public String getSubnetAddr() {
		return NetUtils.ip4IntToStr(mSubnetIp);
	}

	public String getLocalAddr() {
		return NetUtils.ip4IntToStr(mLocalIp);
	}

	public String getRemoteAddr() {
		return NetUtils.ip4IntToStr(mRemoteIp);
	}

	public InetAddress[] getDnsAddresses(InetAddress[] dst) {
		if(dst == null || dst.length < mDns.length) {
			dst = new InetAddress[mDns.length];
		}
		
		System.arraycopy(mDns, 0, dst, 0, mDns.length);
		return dst;
	}
}