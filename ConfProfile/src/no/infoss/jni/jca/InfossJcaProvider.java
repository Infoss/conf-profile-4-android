package no.infoss.jni.jca;

import java.io.File;
import java.lang.reflect.Constructor;
import java.security.GeneralSecurityException;
import java.security.NoSuchProviderException;
import java.security.Provider;

import android.content.Context;

public final class InfossJcaProvider extends Provider {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3342750616297453515L;
	public static final String NAME = "InfossJca";

	private Context mCtx;
	
	public InfossJcaProvider(Context ctx) {
		super(NAME, 0.1, "Android JCA provider for OCPA project");
		
		mCtx = ctx;
		
		init();
	}
	
	private void init() {
		try {
			if(!nativeInitProvider()) {
				throw new NoSuchProviderException("Can't initialize underlying library");
			}
			
			Constructor<?>[] constructors = new Constructor[] {
					NoneWithRsaSignatureSpi.class.getConstructor()
			};
			
			for(Constructor<?> constructor : constructors) {
				Object obj = constructor.newInstance();
				if(obj instanceof JcaConfigurator) {
					putAll(((JcaConfigurator) obj).getJcaConfiguration());
				}
			}
		} catch(Exception e) {
			new GeneralSecurityException("Error while instantiating provider", e);
		}
	}
	
	protected final String getLibcryptoPath() {
		return mCtx.getApplicationInfo().nativeLibraryDir.concat(File.separator).concat("libcrypto.so");
	}
	
	private native boolean nativeInitProvider();

	static {
		//TODO: this native implementation should be probably removed
		System.loadLibrary("infossjca");
	}
}
