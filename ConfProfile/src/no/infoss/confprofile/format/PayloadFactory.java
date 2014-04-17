package no.infoss.confprofile.format;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;

public class PayloadFactory {
	public static final String TAG = PayloadFactory.class.getSimpleName();
	private static final Map<String, Class<? extends Payload>> REGISTERED_PAYLOADS;
	static {
		REGISTERED_PAYLOADS = new HashMap<String, Class<? extends Payload>>();
		register(ScepPayload.class);
	}
	
	public static Payload createPayload(Dictionary payloadDict) throws ConfigurationProfileException {
		String type = payloadDict.getString(ConfigurationProfile.KEY_PAYLOAD_TYPE);
		if(type == null) {
			throw new ConfigurationProfileException("Missing payload type");
		}
		
		Class<? extends Payload> clazz = REGISTERED_PAYLOADS.get(type);
		if(clazz == null) {
			throw new ConfigurationProfileException("Unknown payload type ".concat(String.valueOf(type)));
		}
		
		Payload result = null;
		try {
			Constructor<? extends Payload> constructor = clazz.getConstructor(Dictionary.class);
			result = constructor.newInstance(payloadDict);
		} catch (Exception e) {
			throw new ConfigurationProfileException("Error while instantiating payload object", e);
		}
		
		return result;
	}

	public static Payload createPayload(Map<String, Object> map) throws ConfigurationProfileException {
		return createPayload(Dictionary.wrap(map));
	}
	
	public static Payload createPayload(XmlPullParser parser) throws XmlPullParserException, IOException {
		return createPayload(Dictionary.parse(parser));
	}
	
	private static void register(Class<? extends Payload> clazz) {
		try {
			String key = (String) clazz.getField("VALUE_PAYLOAD_TYPE").get(null);
			REGISTERED_PAYLOADS.put(key, clazz);
		} catch(Exception e) {
			Log.d(TAG, "Can't register payload class ".concat(String.valueOf(clazz)), e);
		}
	}
}
