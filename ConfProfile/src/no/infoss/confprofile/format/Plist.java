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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.util.XmlUtils;

import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.util.Base64;
import android.util.Xml;

/**
 * Plist representation class.
 * Plist does not perform just structure integrity check. Data integrity isn't checked.
 * This class is useful on the first and second phases of device registration process.
 * @author Dmitry Vorobiev
 *
 */
public class Plist implements XmlSerializable {	
	public static final String TAG = Plist.class.getSimpleName();
	
	public static final String DOCDECL = " plist PUBLIC \"-//Apple Inc//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"";
	
	public static final String TYPE_BOOLEAN = "boolean";
	public static final String TYPE_INTEGER = "integer";
	public static final String TYPE_STRING = "string";
	public static final String TYPE_DATA = "data";
	public static final String TYPE_ARRAY = "array";
	public static final String TYPE_DICT = "dict";
	
	public static final String TYPE_BOOLEAN_TRUE = "true"; //handle <true />
	public static final String TYPE_BOOLEAN_FALSE = "false"; //handle <false />
	
	private final Object mWrapped;
	
	private boolean mIsSigned = false;
	private boolean mIsTrusted = false;
	
	public Plist(CMSSignedData cmsSignedData) throws XmlPullParserException, IOException {
		this(new ByteArrayInputStream((byte[]) cmsSignedData.getSignedContent().getContent()));
		SignerInformationStore signerStore = cmsSignedData.getSignerInfos();
		@SuppressWarnings("unchecked")
		Collection<SignerInformation> signers = signerStore.getSigners();
		for(SignerInformation signer : signers) {
			//TODO: verify signed data	
		}
		
		mIsSigned = true;
	}
	
	public Plist(File file) throws XmlPullParserException, IOException {
		this(new FileInputStream(file));
	}
	
	public Plist(byte[] buff) throws XmlPullParserException, IOException {
		this(new ByteArrayInputStream(buff));
	}
	
	public Plist(InputStream stream) throws XmlPullParserException, IOException {
		this(prepareParser(stream));
	}
	
	public Plist(XmlPullParser parser) throws XmlPullParserException, IOException {
		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "plist");
		
		parser.nextTag();
		mWrapped = parseValue(parser);
	}
	
	/**
	 * 
	 * @param objectToWrap
	 */
	public Plist(Object objectToWrap) {
		if(getType(objectToWrap) == null) {
			throw new IllegalArgumentException("Can't wrap ".concat(String.valueOf(objectToWrap)));
		}
		mWrapped = objectToWrap;
	}
	
	/////////////////////////////////////////////
	// Common methods
	/////////////////////////////////////////////
	
	/**
	 * Get type of a wrapped object
	 * @return
	 */
	public String getType() {
		return getType(mWrapped);
	}
	
	/**
	 * Get a wrapped object
	 * @return
	 */
	public Object get() {
		return mWrapped;
	}
	
	/////////////////////////////////////////////
	// Array methods
	/////////////////////////////////////////////
	public int size() {
		return asArray().size();
	}
	
	public Object get(int index) {
		return asArray().get(index);
	}
	
	public Boolean getBoolean(int index) {
		return asArray().getBoolean(index);
	}
	
	public boolean getBoolean(int index, boolean defValue) {
		return asArray().getBoolean(index, defValue);
	}
	
	public Integer getInteger(int index) {
		return asArray().getInteger(index);
	}
	
	public int getInteger(int index, int defValue) {
		return asArray().getInteger(index, defValue);
	}
	
	public String getString(int index) {
		return asArray().getString(index);
	}
	
	public String getString(int index, String defValue) {
		return asArray().getString(index, defValue);
	}
	
	public byte[] getData(int index) {
		return asArray().getData(index);
	}
	
	public Array getArray(int index) {
		return asArray().getArray(index);
	}
	
	public Dictionary getDictionary(int index) {
		return asArray().getDictionary(index);
	}
	
	public String getType(int index) {
		return Plist.getType(get(index));
	}
	
	/////////////////////////////////////////////
	// Dictionary methods
	/////////////////////////////////////////////
	public boolean containsKey(String key) {
		return asDictionary().containsKey(key);
	}
	
	public Object get(String key) {
		return asDictionary().get(key);
	}
	
	public boolean getBoolean(String key, boolean defValue) {
		return asDictionary().getBoolean(key, defValue);
	}
	
	public int getInteger(String key, int defValue) {
		return asDictionary().getInteger(key, defValue);
	}
	
	public String getString(String key, String defValue) {
		return asDictionary().getString(key, defValue);
	}
	
	public byte[] getData(String key) {
		return asDictionary().getData(key);
	}
	
	public Array getArray(String key) {
		return asDictionary().getArray(key);
	}
	
	public Dictionary getDictionary(String key) {
		return asDictionary().getDictionary(key);
	}
	
	/*package*/ void put(String key, Object object) {
		asDictionary().put(key, object);
	}
	
	public String getType(String key) {
		return asDictionary().getType(key);
	}
	
	public void writeXml(OutputStream stream) 
			throws IllegalArgumentException, 
				   IllegalStateException, 
				   IOException {
		XmlSerializer serializer = Xml.newSerializer();
		serializer.setOutput(stream, "UTF-8");
		serializer.startDocument("UTF-8", null);
		serializer.docdecl(Plist.DOCDECL);
		serializer.startTag(null, "plist");
		serializer.attribute(null, "version", "1.0");
		
		writeXml(serializer);
		
		serializer.endTag(null, "plist");
		serializer.endDocument();
		serializer.flush();
	}
	
	@Override
	public void writeXml(XmlSerializer serializer) 
			throws IllegalArgumentException, 
				   IllegalStateException, 
				   IOException {
		XmlSerializable serializable = null;
		if(mWrapped instanceof XmlSerializable) {
			serializable = (XmlSerializable) mWrapped;
		} else {
			serializable = new XmlSerializableWrapper(mWrapped);
		}
		serializable.writeXml(serializer);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if(!mIsSigned) {
			builder.append("not signed, ");
		} else {
			builder.append("signed, ");
			if(mIsTrusted) {
				builder.append("trusted, ");
			} else {
				builder.append("not trusted, ");
			}
		}
		builder.append(mWrapped.toString());
		return builder.toString();
	}
	
	private Array asArray() {
		return (Array) mWrapped;
	}
	
	private Dictionary asDictionary() {
		return (Dictionary) mWrapped;
	}
	
	public static String getType(Object object) {
		if(object == null) {
			return null;
		}
		
		if(object instanceof Boolean) {
			return TYPE_BOOLEAN;
		} else if(object instanceof Integer) {
			return TYPE_INTEGER;
		} else if(object instanceof String) {
			return TYPE_STRING;
		} else if(object instanceof byte[]) { 
			return TYPE_DATA;
		} else if(object instanceof Array) {
			return TYPE_ARRAY;
		} else if(object instanceof Dictionary) {
			return TYPE_DICT;
		}
		
		return null;
	}
	
	/*package*/ static XmlPullParser prepareParser(InputStream stream) throws XmlPullParserException {
		XmlPullParser parser = Xml.newPullParser();
		parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
		parser.setInput(stream, null);
		return parser;
	}
	
	private static Object parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
		Object object = null;
		
		String objectType = parser.getName();
		if(parser.getEventType() != XmlPullParser.START_TAG) {
			throw new PlistFormatException("Unexpected tag. Should be START_TAG for ".concat(objectType));
		}
		
		try {
			if(TYPE_BOOLEAN.equalsIgnoreCase(objectType)) {
				String value = XmlUtils.readText(parser);
				if(!"true".equalsIgnoreCase(value) && 
						!"false".equalsIgnoreCase(value)) {
					//invalid boolean data
					throw new PlistFormatException("Boolean value is ".concat(value));
				}
				object = Boolean.valueOf(value);
			} else if(TYPE_BOOLEAN_TRUE.equalsIgnoreCase(objectType)) {
				object = Boolean.TRUE;
				parser.next();
			} else if(TYPE_BOOLEAN_FALSE.equalsIgnoreCase(objectType)) {
				object = Boolean.FALSE;
				parser.next();
			} else if(TYPE_INTEGER.equalsIgnoreCase(objectType)) {
				object = Integer.valueOf(XmlUtils.readText(parser));
			} else if(TYPE_STRING.equalsIgnoreCase(objectType)) {
				object = XmlUtils.readText(parser);
			} else if(TYPE_DATA.equalsIgnoreCase(objectType)) {
				object = Base64.decode(XmlUtils.readText(parser).getBytes(), Base64.DEFAULT);
			} else if(TYPE_ARRAY.equalsIgnoreCase(objectType)) {
				object = Array.parse(parser);
			} else if(TYPE_DICT.equalsIgnoreCase(objectType)) {
				object = Dictionary.parse(parser);
			} else {
				throw new PlistFormatException("Unknown type ".concat(objectType));
			}
		} catch(Exception e) {
			throw new PlistFormatException("Invalid data format", parser, e);
		}
		
		parser.require(XmlPullParser.END_TAG, null, objectType);
		
		return object;
	}
	
	private static void writeXml(XmlSerializer serializer, Object object) 
			throws IllegalArgumentException, IllegalStateException, IOException {
		String objectType = getType(object);
		
		if(TYPE_BOOLEAN.equalsIgnoreCase(objectType)) {
			serializer.startTag(null, TYPE_BOOLEAN);
			serializer.text(object.toString());
			serializer.endTag(null, TYPE_BOOLEAN);
		} else if(TYPE_INTEGER.equalsIgnoreCase(objectType)) {
			serializer.startTag(null, TYPE_INTEGER);
			serializer.text(object.toString());
			serializer.endTag(null, TYPE_INTEGER);
		} else if(TYPE_STRING.equalsIgnoreCase(objectType)) {
			serializer.startTag(null, TYPE_STRING);
			serializer.text(object.toString());
			serializer.endTag(null, TYPE_STRING);
		} else if(TYPE_DATA.equalsIgnoreCase(objectType)) {
			serializer.startTag(null, TYPE_DATA);
			serializer.text(Base64.encodeToString((byte[]) object, Base64.DEFAULT));
			serializer.endTag(null, TYPE_DATA);
		} else if(TYPE_ARRAY.equalsIgnoreCase(objectType)) {
			((Array) object).writeXml(serializer);
		} else if(TYPE_DICT.equalsIgnoreCase(objectType)) {
			((Dictionary) object).writeXml(serializer);
		}
	}
	
	public static final class Array implements Iterable<Object>{
		private final List<Object> mList;
		
		private Array(List<Object> list) {
			mList = list; //TODO: wrap nested values
		}
		
		public int size() {
			return mList.size();
		}
		
		public Object get(int index) {
			return mList.get(index);
		}
		
		public Boolean getBoolean(int index) {
			Object object = get(index);
			if(!TYPE_BOOLEAN.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Boolean) object;
		}
		
		public boolean getBoolean(int index, boolean defValue) {
			Boolean object = getBoolean(index);
			return object == null ? defValue : object;
		}
		
		public Integer getInteger(int index) {
			Object object = get(index);
			if(!TYPE_INTEGER.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Integer) object;
		}
		
		public int getInteger(int index, int defValue) {
			Integer object = getInteger(index);
			return object == null ? defValue : object;
		}
		
		public String getString(int index) {
			return getString(index, null);
		}
		
		public String getString(int index, String defValue) {
			Object object = get(index);
			if(!TYPE_STRING.equalsIgnoreCase(Plist.getType(object))) {
				return defValue;
			}
			return (String) object;
		}
		
		public byte[] getData(int index) {
			Object object = get(index);
			if(!TYPE_DATA.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (byte[]) object;
		}
		
		public Array getArray(int index) {
			Object object = get(index);
			if(!TYPE_ARRAY.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Array) object;
		}
		
		public Dictionary getDictionary(int index) {
			Object object = get(index);
			if(!TYPE_DICT.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Dictionary) object;
		}
		
		public String getType(int index) {
			return Plist.getType(get(index));
		}
		
		/*package*/ void add(Object object) {
			mList.add(object);
		}
		
		/*package*/ void add(int location, Object object) {
			mList.add(object);
		}
		
		public void writeXml(XmlSerializer serializer) 
				throws IllegalArgumentException, IllegalStateException, IOException {
			serializer.startTag(null, TYPE_ARRAY);
			for(Object item : mList) {
				Plist.writeXml(serializer, item);
			}
			serializer.endTag(null, TYPE_ARRAY);
		}
		
		@Override
		public String toString() {
			return mList.toString();
		}
		
		public static Array wrap(List<Object> list) {
			return new Array(list);
		}
		
		public static Array parse(InputStream stream) throws XmlPullParserException, IOException {
			return parse(Plist.prepareParser(stream));
		}
		
		public static Array parse(XmlPullParser parser) throws XmlPullParserException, IOException {
			List<Object> list = new LinkedList<Object>();
			
			parser.require(XmlPullParser.START_TAG, null, "array");
			while(parser.nextTag() != XmlPullParser.END_TAG) {
				list.add(Plist.parseValue(parser));
			}
			parser.require(XmlPullParser.END_TAG, null, "array");
			return Array.wrap(list);
		}

		@Override
		public Iterator<Object> iterator() {
			return mList.iterator();
		}
	}
	
	public static final class Dictionary {
		private final Map<String, Object> mMap;
		
		private Dictionary(Map<String, Object> map) {
			mMap = map; //TODO: wrap nested values
		}
		
		public boolean containsKey(String key) {
			return mMap.containsKey(key);
		}
		
		public Object get(String key) {
			return mMap.get(key);
		}
		
		public Boolean getBoolean(String key) {
			Object object = get(key);
			if(!TYPE_BOOLEAN.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Boolean) object;
		}
		
		public boolean getBoolean(String key, boolean defValue) {
			Boolean object = getBoolean(key);
			return object == null ? defValue : object;
		}
		
		public Integer getInteger(String key) {
			Object object = get(key);
			if(!TYPE_INTEGER.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Integer) object;
		}
		
		public int getInteger(String key, int defValue) {
			Integer object = getInteger(key);
			return object == null ? defValue : object;
		}
		
		public String getString(String key) {
			return getString(key, null);
		}
		
		public String getString(String key, String defValue) {
			Object object = get(key);
			if(!TYPE_STRING.equalsIgnoreCase(Plist.getType(object))) {
				return defValue;
			}
			return (String) object;
		}
		
		public byte[] getData(String key) {
			Object object = get(key);
			if(!TYPE_DATA.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (byte[]) object;
		}
		
		public Array getArray(String key) {
			Object object = get(key);
			if(!TYPE_ARRAY.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Array) object;
		}
		
		public Dictionary getDictionary(String key) {
			Object object = get(key);
			if(!TYPE_DICT.equalsIgnoreCase(Plist.getType(object))) {
				return null;
			}
			return (Dictionary) object;
		}
		
		public String getType(String key) {
			return Plist.getType(get(key));
		}
		
		/*package*/ void put(String key, Object object) {
			mMap.put(key, object);
		}
		
		public void writeXml(XmlSerializer serializer) 
				throws IllegalArgumentException, IllegalStateException, IOException {
			serializer.startTag(null, TYPE_DICT);
			for(Entry<String, Object> item : mMap.entrySet()) {
				serializer.startTag(null, "key");
				serializer.text(item.getKey());
				serializer.endTag(null, "key");
				Plist.writeXml(serializer, item.getValue());
			}
			serializer.endTag(null, TYPE_DICT);
		}
		
		@Override
		public String toString() {
			return mMap.toString();
		}
		
		public static Dictionary wrap(Map<String, Object> map) {
			return new Dictionary(map);
		}
		
		public static Dictionary parse(InputStream stream) throws XmlPullParserException, IOException {
			return parse(Plist.prepareParser(stream));
		}
		
		public static Dictionary parse(XmlPullParser parser) throws XmlPullParserException, IOException {
			Map<String, Object> map = new HashMap<String, Object>();
			String key = null;
			Object object = null;
			
			parser.require(XmlPullParser.START_TAG, null, "dict");
			while(parser.nextTag() != XmlPullParser.END_TAG) {
				parser.require(XmlPullParser.START_TAG, null, "key");
				key = XmlUtils.readText(parser);
				parser.require(XmlPullParser.END_TAG, null, "key");
				if(parser.nextTag() != XmlPullParser.START_TAG) {
					throw new PlistFormatException("Unbalanced key ".concat(key));
				}
				
				object = Plist.parseValue(parser);
				
				if(map.containsKey(key)) {
					throw new PlistFormatException("Key interception ".concat(key));
				} else {
					map.put(key, object);
				}
			}
			parser.require(XmlPullParser.END_TAG, null, "dict");
			return Dictionary.wrap(map);
		}
	}

	private static class XmlSerializableWrapper implements XmlSerializable {
		private Object mWrappedObject;
		private String mType;
		
		private XmlSerializableWrapper(Object wrappedObject) {
			mType = Plist.getType(wrappedObject);
			if(mType == null) {
				throw new IllegalArgumentException("Can't serialize ".concat(String.valueOf(wrappedObject)));
			}
			
			mWrappedObject = wrappedObject;
		}
		
		@Override
		public void writeXml(XmlSerializer serializer)
				throws IllegalArgumentException, 
				IllegalStateException,
				IOException {
			if(mWrappedObject instanceof XmlSerializable) {
				((XmlSerializable) mWrappedObject).writeXml(serializer);
			} else {
				Plist.writeXml(serializer, mWrappedObject);
			}
		}
		
	}
}
