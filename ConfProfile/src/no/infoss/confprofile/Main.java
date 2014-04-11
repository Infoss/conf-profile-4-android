package no.infoss.confprofile;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import no.infoss.confprofile.format.Plist;

import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.os.Bundle;

public class Main extends Activity {
	public static final String TAG = Main.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		//TODO: get intent and call parseMobileconfig if needed
	}
	
	private Plist parseMobileconfig(String filePath) throws CMSException, XmlPullParserException, IOException {
		CMSSignedData data = new CMSSignedData(new FileInputStream("1.mobileconfig"));
		//TODO: check signature when importing first mobileconfig
		return new Plist(new ByteArrayInputStream((byte[]) data.getSignedContent().getContent()));
	}
}
