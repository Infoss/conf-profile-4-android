package no.infoss.jni.jca;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import android.util.Log;

public class NoneWithRsaSignatureSpi extends SignatureSpi implements JcaConfigurator {
	private static final String RSA_TRANSFORM = "RSA";
	
	private static final int MODE_UNINITIALIZED = 0;
	private static final int MODE_SIGN = 1;
	private static final int MODE_VERIFY = 2;
	
	private int mMode;
	private ByteArrayOutputStream mOutputStream;
	private PublicKey mPublicKey;
	private PrivateKey mPrivateKey;
	
	public NoneWithRsaSignatureSpi() {
		mMode = MODE_UNINITIALIZED;
		mOutputStream = null;
	}
	
	private void reset() {
		mMode = MODE_UNINITIALIZED;
		mOutputStream = new ByteArrayOutputStream();
	}
	
	@Override
	protected void engineInitVerify(PublicKey publicKey)
			throws InvalidKeyException {
		if(!(publicKey instanceof RSAPublicKey)) {
			throw new InvalidKeyException("RSA public key is required");
		}
		
		reset();
		mMode = MODE_VERIFY;
		mPublicKey = publicKey;
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey)
			throws InvalidKeyException {
		if(!(privateKey instanceof RSAPrivateKey)) {
			throw new InvalidKeyException("RSA private key is required");
		}
		
		reset();
		mMode = MODE_SIGN;
		mPrivateKey = privateKey;
	}

	@Override
	protected void engineUpdate(byte b) throws SignatureException {
		try {
			mOutputStream.write(b);
		} catch(Exception e) {
			throw new SignatureException(e);
		}
	}

	@Override
	protected void engineUpdate(byte[] b, int off, int len)
			throws SignatureException {
		try {
			mOutputStream.write(b, off, len);
		} catch(Exception e) {
			throw new SignatureException(e);
		}
	}

	@Override
	protected byte[] engineSign() throws SignatureException {
		if(mMode != MODE_SIGN) {
			throw new SignatureException("Invalid mode");
		}
		
		byte[] result = null;
		try {
			//TODO: this native implementation should be probably removed
			/*
			Log.d(this.getClass().getSimpleName(), mPrivateKey.getFormat());
			byte[] encodedKey = mPrivateKey.getEncoded();
			result = nativeSignNoneWithRsa(encodedKey, mOutputStream.toByteArray());
			*/
			Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
			rsaCipher.init(Cipher.DECRYPT_MODE, mPrivateKey);
			int blockSize = rsaCipher.getBlockSize();
			Pkcs1Padding padding = new Pkcs1Padding(null);
			result = rsaCipher.doFinal(padding.wrap(mOutputStream.toByteArray(), blockSize));
		} catch(Exception e) {
			throw new SignatureException(e);
		}
		
		return result;
	}

	@Override
	protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
		if(mMode != MODE_VERIFY) {
			throw new SignatureException("Invalid mode");
		}
		
		boolean result = false;
		byte[] sigCalcBytes = null;
		try {
			Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
			rsaCipher.init(Cipher.ENCRYPT_MODE, mPublicKey);
			int blockSize = rsaCipher.getBlockSize();
			Pkcs1Padding padding = new Pkcs1Padding(null);
			sigCalcBytes = rsaCipher.doFinal(padding.wrap(mOutputStream.toByteArray(), blockSize));
			
			if(sigBytes.length == sigCalcBytes.length) {
				result = true;
				for(int i = 0; i < sigBytes.length; i++) {
					if(sigBytes[i] != sigCalcBytes[i]) {
						result = false;
						break;
					}
				}
			}
		} catch(Exception e) {
			throw new SignatureException(e);
		}
		
		return result;
	}

	@Override
	protected void engineSetParameter(String param, Object value)
			throws InvalidParameterException {
		throw new InvalidParameterException("No parameters are allowed");
	}

	@Override
	protected Object engineGetParameter(String param)
			throws InvalidParameterException {
		throw new InvalidParameterException("No parameters are allowed");
	}

	@Override
	public Map<Object, Object> getJcaConfiguration() {
		Map<Object, Object> result = new HashMap<Object, Object>(); 
		result.put("Signature.NONEwithRSA", getClass().getCanonicalName());
		return result;
	}
	
	private native byte[] nativeSignNoneWithRsa(byte[] derKey, byte[] data);

}
