package app;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import consensus.PBFTProtocol;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import utils.Crypto;
import utils.SignaturesHelper;

public class RandomGenerationOperationApp {

    private static final Logger logger = LogManager.getLogger(RandomGenerationOperationApp.class);
  
    private static final short block_size = 4096;
    
    private Random r;
    
    private PrivateKey key;
    
    public static void main(String[] args) throws InvalidParameterException, IOException,
            HandlerRegistrationException, ProtocolAlreadyExistsException, GeneralSecurityException {
        Properties props =
                Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);
        if (props.containsKey("interface")) {
            String address = getAddress(props.getProperty("interface"));
            if (address == null) return;
         }
        new RandomGenerationOperationApp(props);
    }

    public RandomGenerationOperationApp(Properties props) throws IOException, ProtocolAlreadyExistsException,
            HandlerRegistrationException, GeneralSecurityException {

    	r = new Random(System.currentTimeMillis());
    	
        Babel babel = Babel.getInstance();

        PBFTProtocol pbft = new PBFTProtocol(props);

        babel.registerProtocol(pbft);
        
        pbft.init(props);

        babel.start();
        
        logger.info("Babel has started...");
        
        String cryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
        key = Crypto.getPrivateKey(cryptoName, props);
        
        logger.info("Waiting 10s to start issuing requests.");
        
        try { Thread.sleep(10 * 1000); } catch (InterruptedException e1) { }
        
        Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
		        while(true) {
		        	try {
			        	byte[] block = new byte[RandomGenerationOperationApp.block_size];
			        	r.nextBytes(block);
			        	byte[] signature = SignaturesHelper.generateSignature(block, key);
			        
			        	pbft.submitOperation(block, signature);
			        	      
						Thread.sleep(5 * 1000);
					} catch (InterruptedException e) {
						//nothing to be done here
					} catch (InvalidKeyException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SignatureException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} //Wait 5 seconds
		        }
			}
		});
        t.start();
        
        logger.info("Request generation thread is running.");
    }

    private static String getAddress(String inter) throws SocketException {
        NetworkInterface byName = NetworkInterface.getByName(inter);
        if (byName == null) {
            logger.error("No interface named " + inter);
            return null;
        }
        Enumeration<InetAddress> addresses = byName.getInetAddresses();
        InetAddress currentAddress;
        while (addresses.hasMoreElements()) {
            currentAddress = addresses.nextElement();
            if (currentAddress instanceof Inet4Address)
                return currentAddress.getHostAddress();
        }
        logger.error("No ipv4 found for interface " + inter);
        return null;
    }

}
