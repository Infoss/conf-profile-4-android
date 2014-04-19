package no.infoss.confprofile.util;

/**
 * Little helper class for dealing with strings
 * @author Dmitry Vorobiev
 *
 */
public class StringUtils {
	public static String join(String[] parts, String separator, boolean skipNulls) {
		int sepCount = parts.length;
		StringBuilder builder = new StringBuilder();
		
		for(String part : parts) {
			sepCount--;
			if(part == null) {
				if(skipNulls) {
					continue;
				} else {
					part = String.valueOf(null);
				}
			}
			
			builder.append(part);
			
			if(sepCount > 0) {
				builder.append(separator);
			}
		}
		
		return builder.toString();
	}
}
