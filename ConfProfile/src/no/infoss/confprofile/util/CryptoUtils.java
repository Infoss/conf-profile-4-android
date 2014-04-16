package no.infoss.confprofile.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Calendar;
import java.util.Random;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.SignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

import android.util.Log;

public class CryptoUtils {
	public static final String TAG = CryptoUtils.class.getSimpleName();
	
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
	
	public static TBSCertificate createBCTBSCert(X509Certificate caCert, String subject, AsymmetricKeyParameter pubKey, String sigAlg) 
			throws CertificateEncodingException, IOException {
		Calendar calendar = Calendar.getInstance();
		X500Name subjectName = new X500Name(subject);
		X500Name issuerName = (caCert == null) ? subjectName : new X500Name(caCert.getIssuerX500Principal().getName());  
		
		SubjectPublicKeyInfo pubkeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubKey);
		
		sigAlg = (sigAlg == null) ? "SHA1WithRSAEncryption" : sigAlg;
		SignatureAlgorithmIdentifierFinder sigAlgFinder = new DefaultSignatureAlgorithmIdentifierFinder();
		
		V3TBSCertificateGenerator gen = new V3TBSCertificateGenerator();
		gen.setSerialNumber(new ASN1Integer(calendar.getTimeInMillis()));
		gen.setIssuer(issuerName);
		gen.setSubject(subjectName);
		
		gen.setStartDate(new Time(calendar.getTime()));
		calendar.add(Calendar.YEAR, 30); //TODO: fix this or cert will be valid about 30 years
		gen.setEndDate(new Time(calendar.getTime()));
		
		gen.setSubjectPublicKeyInfo(pubkeyInfo);
		gen.setSignature(sigAlgFinder.find(sigAlg));
		
		return gen.generateTBSCertificate();
	}
	
	public static X509Certificate signCert(TBSCertificate tbsCert, AsymmetricKeyParameter privateKey) 
			throws OperatorCreationException, IOException, CertificateException {
		AlgorithmIdentifier algOID = tbsCert.getSignature();
		AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA");
	    AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
	    Log.d(TAG, sigAlgId.toString());
	    Log.d(TAG, digAlgId.toString());
		BcRSAContentSignerBuilder builder = new BcRSAContentSignerBuilder(sigAlgId, digAlgId);
		ContentSigner signer = builder.build(privateKey);
		byte[] encodedCert = tbsCert.getEncoded("DER");
		signer.getOutputStream().write(encodedCert);
		byte[] signature = signer.getSignature();
		
		ASN1EncodableVector asn1vec = new ASN1EncodableVector();
		asn1vec.add(tbsCert);
		asn1vec.add(algOID);
		asn1vec.add(new DERBitString(signature));
		
		Certificate cert = Certificate.getInstance(new DERSequence(asn1vec));
		
		return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert.getEncoded("DER")));
	}
	
}
