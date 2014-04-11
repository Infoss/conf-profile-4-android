package no.infoss.confprofile.format;

import java.io.IOException;
import java.util.Map;

import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.Plist.PlistPayload;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class PlistPayloadFactory {
	public static PlistPayload createPayload(Dictionary payloadArr) {
		//TODO: implement payload parsing
		return null;
	}
	
	public static PlistPayload createPayload(Map<String, Object> map) {
		return createPayload(Dictionary.wrap(map));
	}
	
	public static PlistPayload createPayload(XmlPullParser parser) throws XmlPullParserException, IOException {
		return createPayload(Dictionary.parse(parser));
	}
}
