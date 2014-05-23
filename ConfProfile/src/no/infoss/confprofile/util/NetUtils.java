package no.infoss.confprofile.util;

public class NetUtils {
	public static int ip4StrToInt(String ip4) {
		int result = 0;
		String parts[] = ip4.split("\\.");
		if(parts == null || parts.length != 4) {
			throw new IllegalArgumentException("Invalid IPv4 address: ".concat(ip4));
		}
		
		int octet;
		for(int i = 0; i < 4; i++) {
			try {
				octet = Integer.parseInt(parts[i].trim());
				if(octet < 0 || octet > 255) {
					throw new IllegalArgumentException("Invalid octet:".concat(String.valueOf(octet)));
				}
				
				result |= octet << (24 - (i * 8)); 
			} catch(Exception e) {
				throw new IllegalArgumentException("Invalid IPv4 address: ".concat(ip4), e);
			}
		}
		
		return result;
	}
	
	public static String ip4IntToStr(int ip4) {
		int b0 = ip4 >>> 24;
		int b1 = (ip4 >>> 16) & 0x000000ff;
		int b2 = (ip4 >>> 8) & 0x000000ff;
		int b3 = ip4 & 0x000000ff;
		return String.format("%d\\.%d\\.%d\\.%d", b0, b1, b2, b3);
	}
}
