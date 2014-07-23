package no.infoss.confprofile.util;

import java.io.IOException;

import android.net.LocalServerSocket;

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
		return String.format("%d.%d.%d.%d", b0, b1, b2, b3);
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
	
	public static byte[] ip6StrToBytes(String ip6, byte[] buff) {
		byte[] result = null;
		if(buff != null && buff.length >= 16) {
			result = buff;
		} else {
			result = new byte[16];
		}
		
		String parts[] = ip6.split("\\:");
		if(parts == null || parts.length < 1 || parts.length > 8) {
			throw new IllegalArgumentException("Invalid IPv6 address: ".concat(ip6));
		}
		
		int pos = 0;
		boolean applyingZeroGroups = false;
		boolean hybridIp6Ip4 = false;
		if(parts[parts.length - 1].contains(".")) {
			hybridIp6Ip4 = true;
		}
		
		for(int i = 0; i < parts.length; i++) {
			String part = parts[i];
			
			//handle double colon here
			if(part == null) {
				if(applyingZeroGroups) {
					throw new IllegalArgumentException("Invalid IPv6 address: ".concat(ip6));
				}
				applyingZeroGroups = true;

				int restGroups = parts.length - i - 1;
				if(hybridIp6Ip4 && restGroups > 0) {
					restGroups++;
				}
				
				int zeroBytesCount = (16 - pos - (restGroups * 2));
				if(zeroBytesCount < 0) {
					throw new IllegalArgumentException("Invalid IPv6 address: ".concat(ip6));
				}
				
				for(int j = 0; j < zeroBytesCount; j++) {
					result[pos] = 0;
					pos++;
				}
				
				continue;
			}
			
			//handle hybrid address here
			if(hybridIp6Ip4 && i == parts.length - 1) {
				int ip4 = ip4StrToInt(part);
				
				if(pos != 12) {
					throw new IllegalArgumentException("Invalid IPv6 address: ".concat(ip6));
				}
				
				result[pos] = (byte) (ip4 >>> 24); 
				pos++;
				
				result[pos] = (byte) ((ip4 >>> 16) & 0x000000ff);
				pos++;
				
				result[pos] = (byte) ((ip4 >>> 8) & 0x000000ff);
				pos++;
				
				result[pos] = (byte) (ip4 & 0x000000ff);
				pos++;
				
				continue;
			}
			
			//adding standard group
			int groupLen = part.length();
			if(pos > 14 || groupLen < 1 || groupLen > 4) {
				throw new IllegalArgumentException("Invalid IPv6 address: ".concat(ip6));
			}
			
			for(int j = 0; j < 4 - groupLen; j++) {
				part = "0".concat(part);
			}
			
			part = part.toLowerCase();
			
			byte data;
			
			data = 0;
			data |= (MiscUtils.hexToIntDigit(part.charAt(0)) << 4) & 0xf0;
			data |= (MiscUtils.hexToIntDigit(part.charAt(1))) & 0x0f;
			result[pos] = data;
			pos++;
			
			data = 0;
			data |= (MiscUtils.hexToIntDigit(part.charAt(2)) << 4) & 0xf0;
			data |= (MiscUtils.hexToIntDigit(part.charAt(3))) & 0x0f;
			result[pos] = data;
			pos++;
		}
		
		return result;
	}
	
	public static String ip6BytesToStr(byte[] ip6) {
		if(ip6 == null || ip6.length != 16) {
			throw new IllegalArgumentException("Invalid IPv6 address");
		}
		
		String[] parts = new String[8];
		for(int i = 0; i < 8; i++) {
			parts[i] = String.format("%02x%02x", ip6[i * 2], ip6[i * 2 + 1]);
		}
		
		return StringUtils.join(parts, ":", false);
	}
	
	public static LocalServerSocket bindLocalServerSocket(String path) throws IOException {
		int fd = bindUnixSocket(path);
		if(fd == -1) {
			throw new IOException("Can't create or bind unix socket: ".concat(path));
		}
		
		return new LocalServerSocket(MiscUtils.intToFileDescriptor(fd));
	}
	
	private static native int bindUnixSocket(String path);
}
