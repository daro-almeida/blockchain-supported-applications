package utils;

import consensus.messages.PrePrepareMessage;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidFormatException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.babel.generic.signed.NoSignaturePresentException;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

public class Crypto {

    public static final String CRYPTO_NAME_PREFIX = "node";
    public static final String CRYPTO_NAME_KEY = "crypto_name";
	public static final String KEY_STORE_LOCATION_KEY = "key_store_folder";
    public static final String KEY_STORE_PASSWORD_KEY = "key_store_password";
    public static final String TRUST_STORE_LOCATION_KEY = "trust_store";
    public static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";

    private static KeyStore trustStore;


    public static PrivateKey getPrivateKey(String me, Properties props) throws
            KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {

        String keyStoreLocation = props.getProperty(KEY_STORE_LOCATION_KEY)+"/"+me+".ks";
        char[] keyStorePassword = props.getProperty(KEY_STORE_PASSWORD_KEY).toCharArray();


        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        try (FileInputStream fis = new FileInputStream(keyStoreLocation)) {
            ks.load(fis, keyStorePassword);
        }

        return (PrivateKey) ks.getKey(me, keyStorePassword);
    }

    public static KeyStore getTruststore(Properties props) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (trustStore != null) return trustStore;

        String trustStoreLocation = props.getProperty(TRUST_STORE_LOCATION_KEY);
        char[] trustStorePassword = props.getProperty(TRUST_STORE_PASSWORD_KEY).toCharArray();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        try (FileInputStream fis = new FileInputStream(trustStoreLocation)) {
            ks.load(fis, trustStorePassword);
        }

        trustStore = ks;
        return ks;
    }

    public static KeyStore getTruststore() {
        return trustStore;
    }

    public static byte[] digest(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void signMessage(SignedProtoMessage msg, PrivateKey key) {
        try {
            msg.signMessage(key);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | InvalidSerializerException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean checkSignature(SignedProtoMessage msg, PublicKey key) {
        try {
            return msg.checkSignature(key);
        } catch (InvalidFormatException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (SignatureException | NoSignaturePresentException e) {
            return false;
        }
    }

    public static void signMessage(PrePrepareMessage msg, PrivateKey key) {
        try {
            msg.signMessage(key);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean checkSignature(PrePrepareMessage msg, PublicKey key) {
        try {
            return msg.checkSignature(key);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            return false;
        }
    }

}
