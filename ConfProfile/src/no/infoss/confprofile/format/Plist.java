package no.infoss.confprofile.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Xml;

public class Plist {	
	public static final String TYPE_BOOLEAN = "boolean";
	public static final String TYPE_INTEGER = "integer";
	public static final String TYPE_STRING = "string";
	public static final String TYPE_ARRAY = "array";
	public static final String TYPE_DICT = "dict";
	
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
	
	private final Dictionary mDict;
	private final List<PlistPayload> mPayloads;
	
	public Plist(File file) throws XmlPullParserException, IOException {
		this(new FileInputStream(file));
	}
	
	public Plist(InputStream stream) throws XmlPullParserException, IOException {
		this(prepareParser(stream));
	}
	
	public Plist(XmlPullParser parser) throws XmlPullParserException, IOException {
		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "plist");
		
		parser.nextTag();
		mDict = Dictionary.parse(parser);
		
		List<PlistPayload> payloads = new LinkedList<PlistPayload>();
		Array payloadArr = mDict.getArray(KEY_PAYLOAD_CONTENT);
		if(payloadArr != null) {
			for(int i = 0; i < payloadArr.size(); i++) {
				payloads.add(PlistPayloadFactory.createPayload(payloadArr.getDictionary(i)));
			}
		}
		
		mPayloads = Collections.unmodifiableList(payloads);
	}
	
	public boolean containsKey(String key) {
		return mDict.containsKey(key);
	}
	
	public Object get(String key) {
		return mDict.get(key);
	}
	
	public boolean hasRemovalPasscode() {
		return mDict.getBoolean(KEY_HAS_REMOVAL_PASSCODE, false);
	}
	
	public boolean isEncrypted() {
		return mDict.getBoolean(KEY_IS_ENCRYPTED, false);
	}
	
	public Array getPayloadContent() {
		return mDict.getArray(KEY_PAYLOAD_CONTENT);
	}
	
	public List<PlistPayload> getPayloads() {
		return mPayloads;
	}
	
	@Override
	public String toString() {
		return mDict.toString();
	}
	
	public static String getType(Object object) {
		if(object == null) {
			return null;
		}
		
		Class<?> objClass = object.getClass();
		if(Boolean.class.equals(objClass)) {
			return TYPE_BOOLEAN;
		} else if(Integer.class.equals(objClass)) {
			return TYPE_INTEGER;
		} else if(String.class.equals(objClass)) {
			return TYPE_STRING;
		} else if(Dictionary.class.equals(objClass)) {
			return TYPE_DICT;
		}
		
		return null;
	}
	
	private static XmlPullParser prepareParser(InputStream stream) throws XmlPullParserException {
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
			} else if(TYPE_INTEGER.equalsIgnoreCase(objectType)) {
				object = Integer.valueOf(XmlUtils.readText(parser));
			} else if(TYPE_STRING.equalsIgnoreCase(objectType)) {
				object = XmlUtils.readText(parser);
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
	
	public static final class Array {
		private final List<Object> mList;
		
		private Array(List<Object> list) {
			mList = list;
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
		
		@Override
		public String toString() {
			return mList.toString();
		}
		
		public static Array wrap(List<Object> list) {
			return new Array(list);
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
	}
	
	public static final class Dictionary {
		private final Map<String, Object> mMap;
		
		private Dictionary(Map<String, Object> map) {
			mMap = map;
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
		
		@Override
		public String toString() {
			return mMap.toString();
		}
		
		public static Dictionary wrap(Map<String, Object> map) {
			return new Dictionary(map);
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
	
	public static abstract class PlistPayload {
		protected final Dictionary mDict;
		
		public PlistPayload(Dictionary dict) {
			mDict = dict;
		}
		
		public final Dictionary getDictionary() {
			return mDict;
		}
	}

}
