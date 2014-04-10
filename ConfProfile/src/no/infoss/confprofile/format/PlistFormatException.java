package no.infoss.confprofile.format;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class PlistFormatException extends XmlPullParserException {
	private static final long serialVersionUID = 4395496655646244554L;

	public PlistFormatException(String s) {
		super(s);
	}
	
	public PlistFormatException(String s, XmlPullParser parser, Throwable t) {
		super(s, parser, t);
	}

}
