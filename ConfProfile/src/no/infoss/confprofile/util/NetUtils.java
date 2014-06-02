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
	
	public static int mask4StrToInt(String mask4) {
		int maskIp = ip4StrToInt(mask4);
		int maskNum = 0;
		for(int i = 0; i < 32; i++) {
			if(((maskIp >>> (31 - i)) & 0x01) == 1) {
				maskNum++;
			} else {
				break;
			}
		}
		
		return maskNum;
	}
	
	public static String mask4IntToStr(int mask4) {
		if(mask4 < 0 || mask4 > 32) {
			throw new IllegalArgumentException("Invalid IPv4 subnet /".concat(String.valueOf(mask4)));
		}
		int maskIp = 0;
		for(int i = 0; i < mask4; i++) {
			maskIp |= 0x01;
			maskIp <<= 1;
		}
		for(int i = mask4; i < 32; i++) {
			maskIp <<= 1;
		}
		
		return ip4IntToStr(maskIp);
	}
}
