package no.infoss.confprofile.util;

public class CryptoUtils {
	public static final String formatFingerprint(byte[] rawFingerprint) {
		if(rawFingerprint == null || rawFingerprint.length == 0) {
			return "";
		}
		
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < rawFingerprint.length - 1; i++) {
			builder.append(String.format("%02x", rawFingerprint[i]));
			builder.append(":");
		}
		builder.append(String.format("%02x", rawFingerprint[rawFingerprint.length - 1]));
		return builder.toString();
	}
}
