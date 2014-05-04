/*
 * This file is part of Profile provisioning for Android
 * Copyright (C) 2014  Infoss AS, https://infoss.no, info@infoss.no
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

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

/**
 * Factory for creating payload instances.
 * @author Dmitry Vorobiev
 *
 */
public class PayloadFactory {
	public static final String TAG = PayloadFactory.class.getSimpleName();
	private static final Map<String, Class<? extends Payload>> REGISTERED_PAYLOADS;
	static {
		REGISTERED_PAYLOADS = new HashMap<String, Class<? extends Payload>>();
		register(ScepPayload.class);
		register(VpnPayload.class);
	}
	
	public static Payload createPayload(Dictionary payloadDict) throws ConfigurationProfileException {
		String type = payloadDict.getString(ConfigurationProfile.KEY_PAYLOAD_TYPE);
		if(type == null) {
			throw new ConfigurationProfileException("Missing payload type");
		}
		
		Class<? extends Payload> clazz = REGISTERED_PAYLOADS.get(type);
		if(clazz == null) {
			clazz = UnknownPayload.class;
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
	
	public static final class UnknownPayload extends Payload {

		public UnknownPayload(Dictionary dict) throws ConfigurationProfileException {
			super(dict);
		}

	}
}
