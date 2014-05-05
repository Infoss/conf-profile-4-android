package no.infoss.confprofile.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;

import no.infoss.confprofile.format.ScepPayload;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.Selector;
import org.bouncycastle.util.Store;

public class CryptoUtils {
	public static final String TAG = CryptoUtils.class.getSimpleName();
	
	public static final Selector SELECTOR_ALL = new Selector() {
		
		@Override
		public boolean match(Object arg0) {
			return true;
		}
		
		@Override
		public Selector clone() {
			Selector result = null;
			try {
				result = (Selector) super.clone();
			} catch(Exception e) {
				//nothing to do here
			}
			return result;
		}
	};
	
	public static final String formatFingerprint(byte[] rawFingerprint) {
		if(rawFingerprint == null || rawFingerprint.length == 0) {
			return "";
		}
		
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < rawFingerprint.length - 1; i++) {
			builder.append(String.format("%02x", rawFingerprint[i]));
			builder.append(":");
		}
		builder.append(String.format("%02x", rawFingerprint[rawFingerprint.length - 1]));
		return builder.toString();
	}

	public static String getRandomAlphanumericString(int len) {
		final String abc = "abcdefghijklmnopqrstuvwxyz0123456789";
		Random rnd = new Random(System.currentTimeMillis());
		StringBuilder builder = new StringBuilder(len);
		for(int i = 0; i < len; i++) {
			int pos = rnd.nextInt(len);
			builder.append(abc.substring(pos, pos + 1));
		}
		return builder.toString();
	}
	
	public static AsymmetricCipherKeyPair genBCRSAKeypair(int bits) throws NoSuchAlgorithmException {
		RSAKeyGenerationParameters params = new RSAKeyGenerationParameters(
				BigInteger.valueOf(65537), //e=65537 is commonly used exponent 
				SecureRandom.getInstance("SHA1PRNG"), 
				bits, 
				80);
		
		RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
		gen.init(params);
		return gen.generateKeyPair();
	}
	
	public static PublicKey getRSAPublicKey(AsymmetricCipherKeyPair keypair) 
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		RSAKeyParameters bcPublicKey = (RSAKeyParameters) keypair.getPublic();	
		RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
				bcPublicKey.getModulus(), 
				bcPublicKey.getExponent());
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
	}
	
	public static PrivateKey getRSAPrivateKey(AsymmetricCipherKeyPair keypair) 
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		RSAKeyParameters bcPublicKey = (RSAKeyParameters) keypair.getPublic();
		RSAPrivateCrtKeyParameters bcPrivateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();
		RSAPrivateCrtKeySpec keyspec = new RSAPrivateCrtKeySpec(
				bcPublicKey.getModulus(), 
				bcPublicKey.getExponent(),
				bcPrivateKey.getExponent(), 
				bcPrivateKey.getP(), 
				bcPrivateKey.getQ(), 
				bcPrivateKey.getDP(), 
				bcPrivateKey.getDQ(), 
				bcPrivateKey.getQInv());
        return KeyFactory.getInstance("RSA").generatePrivate(keyspec);
	}
	
	public static X509Certificate createCert(X509Certificate caCert, String subject, AsymmetricCipherKeyPair keyPair, String sigAlg) 
			throws IOException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   NoSuchAlgorithmException {
		Calendar calendar = Calendar.getInstance();
		X500Name subjectName = new X500Name(subject);
		X500Name issuerName = (caCert == null) ? subjectName : new X500Name(caCert.getSubjectX500Principal().getName());  
		
		SubjectPublicKeyInfo pubkeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(keyPair.getPublic());
		
		sigAlg = (sigAlg == null) ? "SHA1WithRSAEncryption" : sigAlg;
		
		long serial = calendar.getTimeInMillis();
		calendar.add(Calendar.DAY_OF_MONTH, -1); //calendar matches yesterday for now
		Date notBefore = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, +2); //calendar matches tomorrow for now (yesterday + 2 days)
		Date notAfter = calendar.getTime();
		
		X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
				issuerName, 
				BigInteger.valueOf(serial), 
				notBefore, 
				notAfter, 
				subjectName, 
				pubkeyInfo);
		
		int bcKeyUsage = 0;
		bcKeyUsage = KeyUsage.encipherOnly | KeyUsage.keyCertSign;
		
		builder.addExtension(Extension.keyUsage, false, new KeyUsage(bcKeyUsage));
		
	    JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(sigAlg);
		ContentSigner signer = csBuilder.build(getRSAPrivateKey(keyPair));
		
		X509CertificateHolder certHolder = builder.build(signer);
		return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
	}
	
	public static String makeKeyAlias(String uuid) {
		return "key:".concat(uuid);
	}
	
	public static String makeCertAlias(String uuid) {
		return "cert:".concat(uuid);
	}
	
	public static int appleSCEPKeyUsageToBC(int scepKeyUsage) {
		int result = 0;
		
		if((scepKeyUsage & ScepPayload.KEY_USAGE_CRYPT) != 0) {
			result |= KeyUsage.dataEncipherment;
		}
		
		if((scepKeyUsage & ScepPayload.KEY_USAGE_SIGN) != 0) {
			result |= KeyUsage.digitalSignature;
		}
		
		return result;
	}
	
	public static String debugCMSSignedData(CMSSignedData signedData) {
		StringBuilder builder = new StringBuilder();
		
		Store certStore = signedData.getCertificates();
		@SuppressWarnings("unchecked")
		Collection<X509CertificateHolder> certs = certStore.getMatches(SELECTOR_ALL);
		Iterator<X509CertificateHolder> itCert = certs.iterator();
		int idx = 0;
		while(itCert.hasNext()) {
			X509CertificateHolder cert = itCert.next();
			builder.append("cert #");
			builder.append(idx);
			builder.append("\n");
			builder.append("issuer: ");
			builder.append(cert.getIssuer().toString());
			builder.append("\n");
			builder.append("subject: ");
			builder.append(cert.getSubject().toString());
			builder.append("\n");
			builder.append("serial: ");
			builder.append(cert.getSerialNumber());
			builder.append("\n");
			builder.append("not before: ");
			builder.append(cert.getNotBefore());
			builder.append("\n");
			builder.append("not after: ");
			builder.append(cert.getNotAfter());
			builder.append("\n");
			builder.append("\n");
		}
		
		SignerInformationStore signerStore = signedData.getSignerInfos();
		@SuppressWarnings("unchecked")
		Collection<SignerInformation> infos = signerStore.getSigners();
		Iterator<SignerInformation> itSign = infos.iterator();
		idx = 0;
		while(itSign.hasNext()) {
			SignerInformation info = itSign.next();
			builder.append("signer #");
			builder.append(idx);
			builder.append("\n");
			builder.append("issuer: ");
			builder.append(info.getSID().getIssuer().toString());
			builder.append("\n");
			builder.append("serial=");
			builder.append(info.getSID().getSerialNumber());
			builder.append("\n");
			
			idx++;
		}
		
		builder.append(signedData.getSignedContent().getContent());
		return builder.toString();
	}
}
