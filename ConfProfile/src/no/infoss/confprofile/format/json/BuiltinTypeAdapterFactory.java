package no.infoss.confprofile.format.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class BuiltinTypeAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unchecked")
	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> token) {
		Class<? super T> clazz = token.getRawType();
		if(Map.class.equals(clazz)) {
			return (TypeAdapter<T>) new MapTypeAdapter(gson);
		} else if(List.class.equals(clazz)) {
			return (TypeAdapter<T>) new ListTypeAdapter(gson);
		} else if(Dictionary.class.equals(clazz)) {
			return (TypeAdapter<T>) new DictionaryTypeAdapter(gson);
		} else if(Array.class.equals(clazz)) {
			return (TypeAdapter<T>) new ArrayTypeAdapter(gson);
		}
		
		return null;
	}
	
	
	public static class MapTypeAdapter extends TypeAdapter<Map<String, Object>> {
		public static final String TAG = MapTypeAdapter.class.getSimpleName();
		
		private Gson mGson;
		
		public MapTypeAdapter(Gson gson) {
			mGson = gson;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Map<String, Object> read(JsonReader reader) throws IOException {
			Map<String, Object> result = new HashMap<String, Object>();
			reader.beginObject();
			while(reader.peek() == JsonToken.NAME) {
				String name = reader.nextName();
				
				Object value;
				if(reader.peek() == JsonToken.BEGIN_ARRAY) {
					TypeAdapter<List> adapter = mGson.getAdapter(List.class);
					value = adapter.read(reader);
				} else if(reader.peek() == JsonToken.BEGIN_OBJECT) {
					TypeAdapter<Map> adapter = mGson.getAdapter(Map.class);
					value = adapter.read(reader);
				} else if(reader.peek() == JsonToken.STRING) {
					value = reader.nextString();
				} else if(reader.peek() == JsonToken.NUMBER) {
					value = reader.nextInt();
				} else if(reader.peek() == JsonToken.BOOLEAN) {
					value = reader.nextBoolean();
				} else {
					value = null;
				}
				result.put(name, value);
			}
			reader.endObject();
			return result;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public void write(JsonWriter writer, Map<String, Object> map)
				throws IOException {
			writer.beginObject();
			for(Entry<String, Object> entry : map.entrySet()) {
				writer.name(entry.getKey());
				Object value = entry.getValue();
				if(value instanceof Map) {
					TypeAdapter<Map> adapter = mGson.getAdapter(Map.class);
					adapter.write(writer, (Map) value);
				} else if(value instanceof List) {
					TypeAdapter<List> adapter = mGson.getAdapter(List.class);
					adapter.write(writer, (List) value);
				} else if(value instanceof String){
					writer.value((String) value);
				} else if(value instanceof Number){
					writer.value((Number) value);
				} else if(value instanceof Boolean){
					writer.value((Boolean) value);
				} else if(value == null) {
					writer.nullValue();
				} else {
					TypeAdapter adapter = mGson.getAdapter(value.getClass());
					adapter.write(writer, value);
				}
			}
			writer.endObject();
		}
		
	}
	
	public static class ListTypeAdapter extends TypeAdapter<List<Object>> {
		public static final String TAG = MapTypeAdapter.class.getSimpleName();
		
		private Gson mGson;
		
		public ListTypeAdapter(Gson gson) {
			mGson = gson;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public List<Object> read(JsonReader reader) throws IOException {
			List<Object> result = new ArrayList<Object>();
			reader.beginArray();
			while(reader.peek() != JsonToken.END_ARRAY) {	
				Object value;
				if(reader.peek() == JsonToken.BEGIN_ARRAY) {
					TypeAdapter<List> adapter = mGson.getAdapter(List.class);
					value = adapter.read(reader);
				} else if(reader.peek() == JsonToken.BEGIN_OBJECT) {
					TypeAdapter<Map> adapter = mGson.getAdapter(Map.class);
					value = adapter.read(reader);
				} else if(reader.peek() == JsonToken.STRING) {
					value = reader.nextString();
				} else if(reader.peek() == JsonToken.NUMBER) {
					value = reader.nextInt();
				} else if(reader.peek() == JsonToken.BOOLEAN) {
					value = reader.nextBoolean();
				} else {
					value = null;
				}
				result.add(value);
			}
			reader.endArray();
			return result;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public void write(JsonWriter writer, List<Object> list)
				throws IOException {
			writer.beginArray();
			for(Object value : list) {
				if(value instanceof Map) {
					TypeAdapter<Map> adapter = mGson.getAdapter(Map.class);
					adapter.write(writer, (Map) value);
				} else if(value instanceof List) {
					TypeAdapter<List> adapter = mGson.getAdapter(List.class);
					adapter.write(writer, (List) value);
				} else if(value instanceof String){
					writer.value((String) value);
				} else if(value instanceof Number){
					writer.value((Number) value);
				} else if(value instanceof Boolean){
					writer.value((Boolean) value);
				} else if(value == null) {
					writer.nullValue();
				} else {
					TypeAdapter adapter = mGson.getAdapter(value.getClass());
					adapter.write(writer, value);
				}
			}
			writer.endArray();
		}
		
	}
	
	public static class DictionaryTypeAdapter extends TypeAdapter<Dictionary> {
		public static final String TAG = MapTypeAdapter.class.getSimpleName();
		
		private Gson mGson;
		
		public DictionaryTypeAdapter(Gson gson) {
			mGson = gson;
		}

		@Override
		public Dictionary read(JsonReader reader) throws IOException {
			try {
				throw new UnsupportedOperationException("Can't read Dictionary with BuiltinTypeAdapterFactory.DictionaryTypeAdapter");
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void write(JsonWriter writer, Dictionary dictionary)
				throws IOException {
			Map<String, Object> map = dictionary.asMap();
			TypeAdapter<Map> adapter = mGson.getAdapter(Map.class);
			adapter.write(writer, map);
		}
		
	}
	
	public static class ArrayTypeAdapter extends TypeAdapter<Array> {
		public static final String TAG = MapTypeAdapter.class.getSimpleName();
		
		private Gson mGson;
		
		public ArrayTypeAdapter(Gson gson) {
			mGson = gson;
		}

		@Override
		public Array read(JsonReader reader) throws IOException {
			try {
				throw new UnsupportedOperationException("Can't read Array with BuiltinTypeAdapterFactory.ArrayTypeAdapter");
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void write(JsonWriter writer, Array array)
				throws IOException {
			List<Object> list = array.asList();
			TypeAdapter<List> adapter = mGson.getAdapter(List.class);
			adapter.write(writer, list);
		}
		
	}
}
