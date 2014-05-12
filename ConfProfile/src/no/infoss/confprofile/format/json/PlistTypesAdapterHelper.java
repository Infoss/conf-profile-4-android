package no.infoss.confprofile.format.json;

import java.io.IOException;

import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class PlistTypesAdapterHelper {
	public static final String KEY_TYPE_PREFIX = "__type_";

	public static Object readSpecial(Gson gson, JsonReader reader) throws IOException {
		Object result = null;
		
		reader.beginObject();
		String type = reader.nextName();
		if(type != null && type.startsWith(KEY_TYPE_PREFIX)) {
			String plistType = type.substring(KEY_TYPE_PREFIX.length());
			TypeAdapter<?> adapter = null;
			if(Plist.TYPE_DICT.equals(plistType)) {
				adapter = gson.getAdapter(Dictionary.class);
			} else if(Plist.TYPE_ARRAY.equals(plistType)) {
				adapter = gson.getAdapter(Array.class);
			} else if(Plist.TYPE_DATA.equals(plistType)) {
				result = Base64.decode(reader.nextString(), Base64.DEFAULT);
			} else if(Plist.TYPE_STRING.equals(plistType)) {
				result = reader.nextString();
			} else if(Plist.TYPE_INTEGER.equals(plistType)) {
				result = Integer.valueOf(reader.nextInt());
			} else if(Plist.TYPE_BOOLEAN.equals(plistType)) {
				result = Boolean.parseBoolean(reader.nextString());
			}
			
			if(adapter != null) {
				result = adapter.read(reader);
			}
		}
		reader.endObject();

		return result;
	}

	public static void writeSpecial(Gson gson, JsonWriter writer, Object obj) throws IOException {
		String type = Plist.getType(obj);
		if(type == null) {
			throw new IOException("Unknown object type");
		}
		
		writer.beginObject();
		writer.name(KEY_TYPE_PREFIX.concat(type));
		
		if(Plist.TYPE_BOOLEAN.equals(type)) {
			writer.value(String.valueOf(obj));
		} else if(Plist.TYPE_INTEGER.equals(type)) {
			writer.value((Integer) obj);
		} else if(Plist.TYPE_STRING.equals(type)) {
			writer.value((String) obj);
		} else if(Plist.TYPE_DATA.equals(type)) {
			writer.value(Base64.encodeToString((byte[]) obj, Base64.DEFAULT));
		} else if(Plist.TYPE_ARRAY.equals(type)) {
			TypeAdapter<Array> adapter = gson.getAdapter(Array.class);
			adapter.write(writer, (Array) obj);
		} else if(Plist.TYPE_DICT.equals(type)) {
			TypeAdapter<Dictionary> adapter = gson.getAdapter(Dictionary.class);
			adapter.write(writer, (Dictionary) obj);
		}
		
		writer.endObject();
		
	}

}
