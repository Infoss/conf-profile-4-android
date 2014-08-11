package no.infoss.confprofile.util;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertSelector;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import no.infoss.confprofile.format.ScepPayload;
import no.infoss.jscep.transport.HttpClientTransportFactory;

import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.jscep.client.Client;
import org.jscep.client.ClientException;
import org.jscep.client.EnrollmentResponse;
import org.jscep.client.verification.OptimisticCertificateVerifier;
import org.jscep.transaction.FailInfo;
import org.jscep.transaction.TransactionException;
import org.jscep.transport.response.Capabilities;

import android.content.Context;

public class ScepUtils {
	
	public static final CertSelector ALL_SELECTOR = new CertSelector() {
		
		@Override
		public boolean match(Certificate cert) {
			return true;
		}
		
		@Override
		public CertSelector clone() {
			try {
				return (CertSelector) super.clone();
			} catch(Exception e) {
				return null;
			}
		}
	};
	
	public static ScepStruct doScep(ScepPayload scepPayload, Context ctx, String userAgent) 
			throws NoSuchAlgorithmException, 
				   IOException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   ClientException, 
				   TransactionException, 
				   CertStoreException, 
				   KeyStoreException {
		ScepStruct result = new ScepStruct();
		//prepare SCEP
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("User-Agent", userAgent);
		headers.put("Connection", "close");
		HttpClientTransportFactory factory = new HttpClientTransportFactory(ctx, headers);
		headers.clear();
		
		//TODO: be less optimistic when verify a certificate
		Client scepClient = new Client(new URL(scepPayload.getURL()), new OptimisticCertificateVerifier());
		scepClient.setTransportFactory(factory);
		Capabilities caps = scepClient.getCaCapabilities();
		String sigAlg = caps.getStrongestSignatureAlgorithm();
		
		//create base certificate for SCEP
		AsymmetricCipherKeyPair requesterKeypair = CryptoUtils.genBCRSAKeypair(scepPayload.getKeysize());
		X509Certificate requesterCert = CryptoUtils.createCert(
				null, 
				scepPayload.getSubject(), 
				requesterKeypair, 
				sigAlg);
		
		//create CSR
		AsymmetricCipherKeyPair entityKeypair = CryptoUtils.genBCRSAKeypair(scepPayload.getKeysize());
		PublicKey entityPubKey = CryptoUtils.getRSAPublicKey(entityKeypair);
		PrivateKey entityPrivKey = CryptoUtils.getRSAPrivateKey(entityKeypair);
		
		result.keyPair = entityKeypair;
		
		X500Principal entitySubject = requesterCert.getSubjectX500Principal(); // use the same subject as the self-signed certificate
		PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(entitySubject, entityPubKey); 
		
		DERPrintableString password = new DERPrintableString(scepPayload.getChallenge());
		csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, password);
		
		ExtensionsGenerator extGen = new ExtensionsGenerator();
		int keyUsage = CryptoUtils.appleSCEPKeyUsageToBC(scepPayload.getKeyUsage());
		extGen.addExtension(Extension.keyUsage, false, new KeyUsage(keyUsage));
		csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
		
		JcaContentSignerBuilder csrSignerBuilder = new JcaContentSignerBuilder("SHA1withRSA");
		ContentSigner csrSigner = csrSignerBuilder.build(entityPrivKey);
		PKCS10CertificationRequest csr = csrBuilder.build(csrSigner);

		//Enroll certificate via SCEP 
		EnrollmentResponse enrollment = scepClient.enrol(
				(X509Certificate)requesterCert, 
				CryptoUtils.getRSAPrivateKey(requesterKeypair), 
				csr, 
				scepPayload.getName());
		
		int retryCycles = 6;
		while(enrollment.isPending() && retryCycles > 0) {
			retryCycles--;
			
			try {
				Thread.sleep(5000);
			} catch(InterruptedException e) {
				//nothing to do here
			}
			
			enrollment = scepClient.poll(
					(X509Certificate)requesterCert, 
					CryptoUtils.getRSAPrivateKey(requesterKeypair), 
					entitySubject, 
					enrollment.getTransactionId(), 
					scepPayload.getName());
		}
		
		result.isFailed = enrollment.isFailure();
		result.isPending = enrollment.isPending();
		
		if(result.isFailed) {
			result.scepFailInfo = enrollment.getFailInfo();
		}
		
		if(result.isFailed || result.isPending) {
			return result;
		}
		
		Collection<? extends Certificate> certs = enrollment.getCertStore().getCertificates(ALL_SELECTOR);
		result.certs = certs.toArray(new Certificate[certs.size()]);
		
		return result;
	}
	
	public static class ScepStruct {
		public boolean isPending;
		public boolean isFailed;
		public FailInfo scepFailInfo;
		
		public AsymmetricCipherKeyPair keyPair;
		public Certificate[] certs;
	}
}

