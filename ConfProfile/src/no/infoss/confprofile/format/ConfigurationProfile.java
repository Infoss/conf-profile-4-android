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
import java.io.InputStream;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;

import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;

import android.util.Log;

/**
 * 
 * @author Dmitry Vorobiev
 *
 */
public class ConfigurationProfile {
	public static final String TAG = ConfigurationProfile.class.getSimpleName(); 
	
	public static final int INVALID_PAYLOAD_VERSION = 0;
	
	public static final String KEY_ENCRYPTED_PAYLOAD_CONTENT = "EncryptedPayloadContent";
	
	public static final String KEY_HAS_REMOVAL_PASSCODE = "HasRemovalPasscode";
	public static final String KEY_IS_ENCRYPTED = "IsEncrypted";
	public static final String KEY_PAYLOAD_CONTENT = "PayloadContent";
	public static final String KEY_PAYLOAD_DESCRIPTION = "PayloadDescription";
	public static final String KEY_PAYLOAD_DISPLAY_NAME = "PayloadDisplayName";
	public static final String KEY_PAYLOAD_IDENTIFIER = "PayloadIdentifier";
	public static final String KEY_PAYLOAD_ORGANIZATION = "PayloadOrganization";
	public static final String KEY_PAYLOAD_UUID = "PayloadUUID";
	public static final String KEY_PAYLOAD_REMOVAL_DISALLOWED = "PayloadRemovalDisallowed";
	public static final String KEY_PAYLOAD_TYPE = "PayloadType";
	public static final String KEY_PAYLOAD_VERSION = "PayloadVersion";
	public static final String KEY_PAYLOAD_SCOPE = "PayloadScope";
	public static final String KEY_REMOVAL_DATE = "RemovalDate";
	public static final String KEY_DURATION_UNTIL_REMOVAL = "DurationUntilRemoval";
	public static final String KEY_CONSENT_TEXT = "ConsentText";
	
	private Plist mPlist;
	private final List<Payload> mPayloads;
	private boolean mIsPayloadContentEncrypted;
	
	private ConfigurationProfile(Plist plist) throws ConfigurationProfileException {
		mPlist = plist;
		
		mPayloads = new LinkedList<Payload>();
		parsePayloadContent();
		
		if(mPlist.getData(KEY_ENCRYPTED_PAYLOAD_CONTENT) != null) {
			mIsPayloadContentEncrypted = true;
		} else {
			mIsPayloadContentEncrypted = false;
		}
	}
	
	private void parsePayloadContent() throws ConfigurationProfileException {
		Array payloadArr = mPlist.getArray(KEY_PAYLOAD_CONTENT);
		if(payloadArr != null) {
			for(Object dict : payloadArr) {
				if(dict instanceof Dictionary) {
					mPayloads.add(PayloadFactory.createPayload((Dictionary) dict));
				}
			}
		}
	}
	
	public Plist getPlist() {
		return mPlist;
	}
	
	public boolean isPayloadContentEncrypted() {
		return mIsPayloadContentEncrypted;
	}
	
	public synchronized boolean decryptPayloadContent(PrivateKey key) {
		if(!mIsPayloadContentEncrypted) {
			return true;
		}
		
		InputStream is = null;
		try {
			byte encryptedBuff[] = mPlist.getData(KEY_ENCRYPTED_PAYLOAD_CONTENT);
			CMSEnvelopedData envelopedData = new CMSEnvelopedData(encryptedBuff);
			
			@SuppressWarnings("unchecked")
			Collection<RecipientInformation> rec = envelopedData.getRecipientInfos().getRecipients();
			Iterator<RecipientInformation> it = rec.iterator();
			while(it.hasNext()) {
				RecipientInformation info = it.next();
				CMSTypedStream recData = info.getContentStream(new JceKeyTransEnvelopedRecipient(key).setProvider("BC"));
				is = recData.getContentStream();
				Plist encryptedPlist = new Plist(is);
				mPlist.put(KEY_PAYLOAD_CONTENT, encryptedPlist.get());
				parsePayloadContent();
			}
		} catch(Exception e) {
			Log.w(TAG, "Can't decrypt payload content", e);
			return false;
		} finally {
			if(is != null) {
				try {
					is.close();
				} catch (IOException e) {
					Log.d(TAG, "Error while closing ASN1InputStream", e);
				}
			}
		}
		
		mIsPayloadContentEncrypted = false;
		return true;
	}
	
	public List<Payload> getPayloads() {
		return Collections.unmodifiableList(mPayloads);
	}
	
	public boolean hasRemovalPasscode() {
		return mPlist.getBoolean(KEY_HAS_REMOVAL_PASSCODE, false);
	}
	
	public boolean isEncrypted() {
		return mPlist.getBoolean(KEY_IS_ENCRYPTED, false);
	}
	
	public Array getPayloadContent() {
		return mPlist.getArray(KEY_PAYLOAD_CONTENT);
	}
	
	public String getPayloadDescription() {
		return mPlist.getString(KEY_PAYLOAD_DESCRIPTION, null);
	}
	
	public String getPayloadDisplayName() {
		return mPlist.getString(KEY_PAYLOAD_DISPLAY_NAME, null);
	}
	
	public String getPayloadIdentifier() {
		return mPlist.getString(KEY_PAYLOAD_IDENTIFIER, null);
	}
	
	public String getPayloadOrganization() {
		return mPlist.getString(KEY_PAYLOAD_ORGANIZATION, null);
	}
	
	public String getPayloadUUID() {
		return mPlist.getString(KEY_PAYLOAD_UUID, null);
	}
	
	public boolean isPayloadRemovalDisallowed() {
		return mPlist.getBoolean(KEY_PAYLOAD_REMOVAL_DISALLOWED, false);
	}
	
	public String getPayloadType() {
		return mPlist.getString(KEY_PAYLOAD_TYPE, null);
	}
	
	public int getPayloadVersion() {
		return mPlist.getInteger(KEY_PAYLOAD_VERSION, INVALID_PAYLOAD_VERSION);
	}
	
	public String getPayloadScope() {
		return mPlist.getString(KEY_PAYLOAD_SCOPE, null);
	}
	
	//TODO: getRemovalDate()
	
	//TODO: getDurationUntilRemoval()
	
	@Override
	public String toString() {
		return mPlist.toString();
	}
	
	/**
	 * Check ConfigurationProfile mandatory fields and wrap plist
	 * @param plist
	 * @return
	 * @throws ConfigurationProfileException if at least one mandatory field 
	 *         is missing or contains invalid value
	 */
	public static ConfigurationProfile wrap(Plist plist) throws ConfigurationProfileException {
		//check mandatory fields
		if(plist.getString(KEY_PAYLOAD_IDENTIFIER, null) == null) {
			throw new ConfigurationProfileException("Can't wrap plist", KEY_PAYLOAD_IDENTIFIER);
		}
		
		if(plist.getString(KEY_PAYLOAD_UUID, null) == null) {
			throw new ConfigurationProfileException("Can't wrap plist", KEY_PAYLOAD_UUID);
		}
		
		//[Apple] Configuration Profile Reference, p. 7
		if(!"Configuration".equals(plist.getString(KEY_PAYLOAD_TYPE, null))) {
			throw new ConfigurationProfileException("Can't wrap plist", KEY_PAYLOAD_TYPE, "\"Configuration\"");
		}
		
		//[Apple] Configuration Profile Reference, p. 7
		if(plist.getInteger(KEY_PAYLOAD_VERSION, INVALID_PAYLOAD_VERSION) != 1) {
			throw new ConfigurationProfileException("Can't wrap plist", KEY_PAYLOAD_VERSION, "1");
		}
		
		return new ConfigurationProfile(plist);
	}
	
	public static abstract class Payload {
		protected final Dictionary mDict;
		
		public Payload(Dictionary dict) throws ConfigurationProfileException {
			mDict = dict;
			
			//check mandatory fields
			if(mDict.getString(KEY_PAYLOAD_IDENTIFIER, null) == null) {
				throw new ConfigurationProfileException("Can't create payload", KEY_PAYLOAD_IDENTIFIER);
			}
			
			if(mDict.getString(KEY_PAYLOAD_UUID, null) == null) {
				throw new ConfigurationProfileException("Can't create payload", KEY_PAYLOAD_UUID);
			}
			
			if(mDict.getString(KEY_PAYLOAD_TYPE, null) == null) {
				throw new ConfigurationProfileException("Can't create payload", KEY_PAYLOAD_TYPE);
			}
			
			if(mDict.getInteger(KEY_PAYLOAD_VERSION, INVALID_PAYLOAD_VERSION) == INVALID_PAYLOAD_VERSION) {
				throw new ConfigurationProfileException("Can't create payload", KEY_PAYLOAD_VERSION);
			}
		}
		
		public final Dictionary getDictionary() {
			return mDict;
		}
		
		public String getPayloadType() {
			return mDict.getString(KEY_PAYLOAD_TYPE, null);
		}
		
		public int getPayloadVersion() {
			return mDict.getInteger(KEY_PAYLOAD_VERSION, INVALID_PAYLOAD_VERSION);
		}
		
		public String getPayloadIdentifier() {
			return mDict.getString(KEY_PAYLOAD_IDENTIFIER, null);
		}
		
		public String getPayloadUUID() {
			return mDict.getString(KEY_PAYLOAD_UUID, null);
		}
		
		public String getPayloadDisplayName() {
			return mDict.getString(KEY_PAYLOAD_DISPLAY_NAME, null);
		}
		
		public String getPayloadDescription() {
			return mDict.getString(KEY_PAYLOAD_DESCRIPTION, null);
		}
		
		public String getPayloadOrganization() {
			return mDict.getString(KEY_PAYLOAD_ORGANIZATION, null);
		}
		
		public Object getPayloadContent() {
			return mDict.get(KEY_PAYLOAD_CONTENT);
		}
		
		public Dictionary getPayloadContentAsDictionary() {
			return mDict.getDictionary(KEY_PAYLOAD_CONTENT);
		}
	}
}
