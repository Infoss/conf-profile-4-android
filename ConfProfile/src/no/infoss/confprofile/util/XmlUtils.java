package no.infoss.confprofile.util;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class XmlUtils {
	
	public static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
		if(parser.getEventType() != XmlPullParser.START_TAG) {
			throw new IllegalStateException();
		}
		skipToClosest(parser);
	}
	
	public static void skipToClosest(XmlPullParser parser) throws XmlPullParserException, IOException {
		int depth = 1;
		while (depth != 0) {
			switch (parser.next()) {
			case XmlPullParser.END_TAG: {
				depth--;
				break;
			}
			case XmlPullParser.START_TAG: {
				depth++;
				break;
			}
			}
		}
	}
	
	public static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
		String result = "";
		if (parser.next() == XmlPullParser.TEXT) {
			result = parser.getText();
			parser.nextTag();
		}
		return result;
	}
}
