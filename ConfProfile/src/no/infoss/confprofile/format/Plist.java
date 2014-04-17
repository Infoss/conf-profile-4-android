package no.infoss.confprofile.format;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.util.XmlUtils;

import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.util.Log;
import android.util.Xml;

public class Plist {	
	public static final String TAG = Plist.class.getSimpleName();
	
	public static final String DOCDECL = " plist PUBLIC \"-//Apple Inc//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"";
	
	public static final String TYPE_BOOLEAN = "boolean";
	public static final String TYPE_INTEGER = "integer";
	public static final String TYPE_STRING = "string";
	public static final String TYPE_ARRAY = "array";
	public static final String TYPE_DICT = "dict";
	
	private final Dictionary mDict;
	
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
	
	public Plist(InputStream stream) throws XmlPullParserException, IOException {
		this(prepareParser(stream));
	}
	
	public Plist(XmlPullParser parser) throws XmlPullParserException, IOException {
		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "plist");
		
		parser.nextTag();
		mDict = Dictionary.parse(parser);
	}
	
	public boolean containsKey(String key) {
		return mDict.containsKey(key);
	}
	
	public Object get(String key) {
		return mDict.get(key);
	}
	
	public boolean getBoolean(String key, boolean defValue) {
		return mDict.getBoolean(key, defValue);
	}
	
	public int getInteger(String key, int defValue) {
		return mDict.getInteger(key, defValue);
	}
	
	public String getString(String key, String defValue) {
		return mDict.getString(key, defValue);
	}
	
	public Array getArray(String key) {
		return mDict.getArray(key);
	}
	
	public Dictionary getDictionary(String key) {
		return mDict.getDictionary(key);
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
		builder.append(mDict.toString());
		return builder.toString();
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
		} else if(Array.class.equals(objClass)) {
			return TYPE_ARRAY;
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
		} else if(TYPE_ARRAY.equalsIgnoreCase(objectType)) {
			((Array) object).writeXml(serializer);
		} else if(TYPE_DICT.equalsIgnoreCase(objectType)) {
			((Dictionary) object).writeXml(serializer);
		}
	}
	
	public static final class Array {
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

}
