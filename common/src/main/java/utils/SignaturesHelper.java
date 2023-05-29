package utils;

import java.security.*;

public class SignaturesHelper {

	private static final String SignatureAlgorithm = "SHA256withRSA";
	
	public static byte[] generateSignature(byte[] value, PrivateKey key) {
		try {
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initSign(key);
			sig.update(value);
			return sig.sign();
		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static boolean checkSignature(byte[] value, byte[] signature, PublicKey key) {
		try {
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initVerify(key);
			sig.update(value);
			return sig.verify(signature);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		} catch (SignatureException e) {
			return false;
		}
	}
	
}
