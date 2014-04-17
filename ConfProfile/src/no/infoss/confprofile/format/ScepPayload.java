package no.infoss.confprofile.format;

import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;

public class ScepPayload extends Payload {
	public static final String VALUE_PAYLOAD_TYPE = "com.apple.security.scep";
	
	public static final String KEY_URL = "URL";
	public static final String KEY_NAME = "Name";
	public static final String KEY_SUBJECT = "Subject";
	public static final String KEY_CHALLENGE = "Challenge";
	public static final String KEY_KEYSIZE = "Keysize";
	public static final String KEY_KEY_TYPE = "Key Type";
	public static final String KEY_KEY_USAGE = "Key Usage";
	
	public static final int KEY_USAGE_SIGN = 1;
	public static final int KEY_USAGE_CRYPT = 4;
	public static final int KEY_USAGE_BOTH = 5;
	
	public static final int DEFAULT_KEY_SIZE = 2048;

	public ScepPayload(Dictionary dict) throws ConfigurationProfileException {
		super(dict);
		
		if(mDict.getString(KEY_URL, null) == null) {
			throw new ConfigurationProfileException("Can't create payload", KEY_URL);
		}
	}
	
	public String getURL() {
		return mDict.getString(KEY_URL);
	}
	
	public String getName() {
		return mDict.getString(KEY_NAME);
	}
	
	public String getSubject() {
		Array arr = mDict.getArray(KEY_SUBJECT);
		if(arr == null) {
			return null;
		}
		
		StringBuilder builder = new StringBuilder();
		int size = arr.size();
		for(int i = 0; i < size; i++) {
			Array subArr = arr.getArray(i);
			if(subArr == null) {
				//this should never happen
				break;
			}
			
			Array pieces = subArr.getArray(0);
			if(pieces == null) {
				//this should never happen
				break;
			}
			
			builder.append("/");
			builder.append(pieces.getString(0));
			builder.append("=");
			builder.append(pieces.getString(1));
		}
		return builder.toString();
	}
	
	public String getChallenge() {
		return mDict.getString(KEY_CHALLENGE);
	}

	public int getKeysize() {
		return mDict.getInteger(KEY_KEYSIZE, DEFAULT_KEY_SIZE);
	}
	
}
