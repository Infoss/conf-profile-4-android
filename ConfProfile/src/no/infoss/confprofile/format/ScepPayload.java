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

import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.util.StringUtils;

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
		
		if(getPayloadContent().getString(KEY_URL, null) == null) {
			throw new ConfigurationProfileException("Can't create payload", KEY_URL);
		}
	}
	
	public String getURL() {
		return getPayloadContent().getString(KEY_URL);
	}
	
	public String getName() {
		return getPayloadContent().getString(KEY_NAME);
	}
	
	public String getSubject() {
		Array arr = getPayloadContent().getArray(KEY_SUBJECT);
		if(arr == null) {
			return null;
		}
		
		int size = arr.size();
		String[] subjParts = new String[size];
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
			
			subjParts[i] = pieces.getString(0).concat("=").concat(pieces.getString(1));
		}
		
		return StringUtils.join(subjParts, ", ", true);
	}
	
	public String getChallenge() {
		return getPayloadContent().getString(KEY_CHALLENGE);
	}

	public int getKeysize() {
		return getPayloadContent().getInteger(KEY_KEYSIZE, DEFAULT_KEY_SIZE);
	}
	
	public String getKeyType() {
		return getPayloadContent().getString(KEY_KEY_TYPE);
	}
	
	public int getKeyUsage() {
		return getPayloadContent().getInteger(KEY_KEY_USAGE, KEY_USAGE_BOTH);
	}
	
}
