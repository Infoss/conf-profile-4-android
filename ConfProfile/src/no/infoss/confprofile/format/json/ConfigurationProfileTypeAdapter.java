package no.infoss.confprofile.format.json;

import java.io.IOException;

import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfileException;
import no.infoss.confprofile.format.Plist;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class ConfigurationProfileTypeAdapter extends
		TypeAdapter<ConfigurationProfile> {
	private Gson mGson;
	
	public ConfigurationProfileTypeAdapter(Gson gson) {
		mGson = gson;
	}
	
	@Override
	public ConfigurationProfile read(JsonReader reader) throws IOException {
		ConfigurationProfile result = null;
		JsonToken nextToken = reader.peek();
		
		if(nextToken == JsonToken.NULL) {
			return null;
		} else {
			Object objToWrap = PlistTypesAdapterHelper.readSpecial(mGson, reader);
			try {
				result = ConfigurationProfile.wrap(new Plist(objToWrap));
			} catch (ConfigurationProfileException e) {
				throw new IOException("Invalid configuration profile", e);
			} 
		}
		
		return result;
	}

	@Override
	public void write(JsonWriter writer, ConfigurationProfile profile)
			throws IOException {
		
		if(profile == null || profile.getPlist() == null || profile.getPlist().get() == null) {
			writer.beginObject();
			writer.nullValue();
			writer.endObject();
		} else {
			Object obj = profile.getPlist().get();
			PlistTypesAdapterHelper.writeSpecial(mGson, writer, obj);
		}
		
	}

}
