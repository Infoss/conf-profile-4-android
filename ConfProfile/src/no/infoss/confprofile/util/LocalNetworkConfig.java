package no.infoss.confprofile.util;


public class LocalNetworkConfig {
	public final int subnetIp;
	public final int subnetMask;
	public final int localIp;
	public final int remoteIp;
	
	public final String subnetAddr;
	public final String localAddr;
	public final String remoteAddr;
	
	public LocalNetworkConfig(String subnet, int mask, String local, String remote) {
		subnetAddr = subnet;
		subnetMask = mask;
		localAddr = local;
		remoteAddr = remote;
		
		subnetIp = NetUtils.ip4StrToInt(subnet);
		localIp = NetUtils.ip4StrToInt(local);
		remoteIp = NetUtils.ip4StrToInt(remote);
	}
	
	public LocalNetworkConfig(int subnet, int mask, int local, int remote) {
		subnetIp = subnet;
		subnetMask = mask;
		localIp = local;
		remoteIp = remote;
		
		subnetAddr = NetUtils.ip4IntToStr(subnet);
		localAddr = NetUtils.ip4IntToStr(local);
		remoteAddr = NetUtils.ip4IntToStr(remote);
	}
	
	public LocalNetworkConfig(String subnet) {
		this(applyMask(subnet, 30));
	}
	
	private LocalNetworkConfig(int subnet) {
		this(subnet, 30, subnet + 2, subnet + 1);
	}
	
	private static int applyMask(String addr, int mask) {
		int ip = NetUtils.ip4StrToInt(addr);
		int bitmask = ((int)0xffffffff >>> (32 - mask)) << (32 - mask);
		return ip & bitmask;
	}
}