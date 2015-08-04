package no.infoss.confprofile;

import io.fabric.sdk.android.Fabric;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;

public class Application extends android.app.Application {
	@Override
	public void onCreate() {
		super.onCreate();
		
		Crashlytics crashlytics = new Crashlytics();
		CrashlyticsNdk crashlyticsNdk = new CrashlyticsNdk();
		Fabric.with(new Fabric.Builder(this).
				debuggable(!BuildConfig.DEBUG).
				kits(crashlytics, crashlyticsNdk).
				build());
	}
}
