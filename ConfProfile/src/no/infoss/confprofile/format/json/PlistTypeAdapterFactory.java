package no.infoss.confprofile.format.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class PlistTypeAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unchecked")
	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> token) {
		Class<? super T> clazz = token.getRawType();
		if(ConfigurationProfile.class.equals(clazz)) {
			return (TypeAdapter<T>) new ConfigurationProfileTypeAdapter(gson);
		} else if(Dictionary.class.equals(clazz)) {
			return (TypeAdapter<T>) new DictionaryTypeAdapter(gson);
		} else if(Dictionary.class.equals(clazz)) {
			return (TypeAdapter<T>) new ArrayTypeAdapter(gson);
		}
		
		return null;
	}
	
	public static class DictionaryTypeAdapter extends TypeAdapter<Dictionary> {
		private Gson mGson;
		
		public DictionaryTypeAdapter(Gson gson) {
			mGson = gson;
		}
		
		@Override
		public Dictionary read(JsonReader reader) throws IOException {
			Map<String, Object> result = new HashMap<String, Object>();
			reader.beginObject();
			while(reader.peek() == JsonToken.NAME) {
				result.put(reader.nextName(), PlistTypesAdapterHelper.readSpecial(mGson, reader));
			}
			
			return Dictionary.wrap(result);
		}

		@Override
		public void write(JsonWriter writer, Dictionary dict) throws IOException {
			Map<String, Object> map = dict.asMap();
			writer.beginObject();
			for(Entry<String, Object> entry : map.entrySet()) {
				writer.name(entry.getKey());
				PlistTypesAdapterHelper.writeSpecial(mGson, writer, entry.getValue());
			}
			writer.endObject();
		}
		
	}
	
	public static class ArrayTypeAdapter extends TypeAdapter<Array> {
		private Gson mGson;
		
		public ArrayTypeAdapter(Gson gson) {
			mGson = gson;
		}

		@Override
		public Array read(JsonReader reader) throws IOException {
			List<Object> result = new LinkedList<Object>();
			reader.beginArray();
			while(reader.peek() == JsonToken.BEGIN_OBJECT) {
				result.add(PlistTypesAdapterHelper.readSpecial(mGson, reader));
			}
			
			return Array.wrap(result);
		}

		@Override
		public void write(JsonWriter writer, Array array) throws IOException {
			List<Object> list = array.asList();
			writer.beginArray();
			for(Object obj : list) {
				PlistTypesAdapterHelper.writeSpecial(mGson, writer, obj);
			}
			writer.endArray();
		}
		
	}

}
