package no.infoss.jcajce;

import java.lang.reflect.Constructor;
import java.security.GeneralSecurityException;
import java.security.Provider;

/**
 * This provider is useful on Android prior to 4.2.1 due to known bug with absent NONEwithRSA 
 * signature scheme
 * 
 * @author Dmitry Vorobiev
 * @see <a href="https://code.google.com/p/android/issues/detail?id=36545">Related AOSP bug report #36545</a>
 * @see <a href="https://android-review.googlesource.com/#/c/40352/">Android reviewed change #40352</a>
 */
public final class InfossJcaProvider extends Provider {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3342750616297453515L;
	public static final String NAME = "InfossJca";
	
	public InfossJcaProvider() {
		super(NAME, 0.1, "Android JCA provider for OCPA project");
		
		init();
	}
	
	private void init() {
		try {			
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
	
}
