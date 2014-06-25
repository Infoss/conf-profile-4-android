package no.infoss.jcajce;

import java.security.NoSuchAlgorithmException;

import javax.crypto.ShortBufferException;

public class Pkcs1Padding {
	private Hasher mHasher;
	
	/*package*/ public Pkcs1Padding(String hashAlgName) throws NoSuchAlgorithmException {
		if(hashAlgName == null) {
			mHasher = new NoneHasher();
		} else {
			throw new NoSuchAlgorithmException("Hash function isn't supported here");
		}
	}
	
	public byte[] wrap(byte[] data, int totalLen) throws ShortBufferException {
		return wrap(data, 0, data.length, totalLen);
	}
	
	public byte[] wrap(byte[] data, int offs, int len, int totalLen) throws ShortBufferException {
		if(totalLen < len + 3) {
			throw new ShortBufferException();
		}
		
		if(totalLen == -1) {
			totalLen = len + 3;
		}
		
		byte[] result = new byte[totalLen];
		
		int pos = 0;
		result[pos] = 0;
		pos++;
		
		result[pos] = 1;
		pos++;
		
		for(int i = 0; i < totalLen - len - 3; i++) {
			//write padding
			result[pos] = (byte) 0xff;
			pos++;
		}
		
		result[pos] = 0;
		pos++;
		
		byte[] hash = mHasher.hash(data, offs, len);
		for(int i = 0; i < hash.length; i++) {
			result[pos + i] = hash[i];
		}
		
		return result;
	}
	
	/*package*/ static abstract class Hasher {
		
		public byte[] hash(byte[] data) {
			return hash(data, 0, data.length);
		}
		
		public abstract byte[] hash(byte[] data, int offs, int len);
	}
	
	/*package*/ static class NoneHasher extends Hasher {

		@Override
		public byte[] hash(byte[] data, int offs, int len) {
			return data;
		}
		
	}
}
