package no.infoss.confprofile;

import io.fabric.sdk.android.Fabric;

import com.crashlytics.android.Crashlytics;

public class Application extends android.app.Application {
	@Override
	public void onCreate() {
		super.onCreate();
		
		Crashlytics crashlytics = new Crashlytics();
		Fabric.Builder b = new Fabric.Builder(this);
		b.debuggable(!BuildConfig.DEBUG).kits(crashlytics).build();
	}
}
