package no.infoss.jni.scepclient;

import javax.net.SocketFactory;

//TODO: modify scepclient from strongswan to use it instead of jscep
//After that we can remove jscep, apache-confprofile, slf4j, jsr305

public class ScepClient {
	private SocketFactory mSocketFactory;
	private byte[] mPkcs1;
	private int mPkcs1GenBits;
	private byte[] mPkcs10;
	
	static {
		//System.loadLibrary("scepclient");
	}

	private ScepClient(SocketFactory factory) {
		mSocketFactory = factory;
	}
	
	public void enroll() {
		//TODO: enrollment
	}
	
	public static class Builder {
		private SocketFactory mSocketFactory;
		private byte[] mPkcs1;
		private int mPkcs1GenBits = 2048;
		private byte[] mPkcs10;
		
		public Builder setSocketFactory(SocketFactory socketFactory) {
			mSocketFactory = socketFactory;
			return this;
		}
		
		public Builder setPkcs1Key(byte[] pkcs1der) {
			mPkcs1 = pkcs1der;
			return this;
		}
		
		public Builder setPkcs10Request(byte[] pkcs10der) {
			mPkcs10 = pkcs10der;
			return this;
		}
		
		/**
		 * Sets the length of key that is generated if no valid PKCS#1 key is supplied.
		 * Default value: 2048
		 * @param bits
		 * @return
		 */
		public Builder setKeyLength(int bits) {
			mPkcs1GenBits = bits;
			return this;
		}
		
		public ScepClient build() {
			ScepClient instance = new ScepClient(mSocketFactory);
			instance.mPkcs1 = mPkcs1;
			instance.mPkcs1GenBits = mPkcs1GenBits;
			instance.mPkcs10 = mPkcs10;
			return instance;
		}
	}
}
