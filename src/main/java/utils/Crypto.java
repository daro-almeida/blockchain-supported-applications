package utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

public class Crypto {

    public static final String CRYPTO_NAME_KEY = "crypto_name";
	public static final String KEY_STORE_LOCATION_KEY = "key_store_folder";
    public static final String KEY_STORE_PASSWORD_KEY = "key_store_password";
    public static final String TRUST_STORE_LOCATION_KEY = "trust_store";
    public static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";


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

        String trustStoreLocation = props.getProperty(TRUST_STORE_LOCATION_KEY);
        char[] trustStorePassword = props.getProperty(TRUST_STORE_PASSWORD_KEY).toCharArray();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        try (FileInputStream fis = new FileInputStream(trustStoreLocation)) {
            ks.load(fis, trustStorePassword);
        }
        return ks;
    }

}
