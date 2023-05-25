package app.toolbox;

import blockchain.BlockChainProtocol;
import consensus.PBFTProtocol;
import metrics.Metrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import utils.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class Toolbox extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Toolbox.class);

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    public final static String PROTO_NAME = "Toolbox";
    public final static short PROTO_ID = 600;

    private int clientChannel;

    public static void main(String[] args) throws Exception {
        Properties props = Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);
        if (props.containsKey("interface")) {
            String address = Utils.getAddress(props.getProperty("interface"));
            if (address == null)
                return;
            props.put(ADDRESS_KEY, address);
        }

        Metrics.initMetrics(props);

        Babel babel = Babel.getInstance();

        Toolbox toolbox = new Toolbox(props);
        BlockChainProtocol bc = new BlockChainProtocol(props);
        PBFTProtocol pbft = new PBFTProtocol(props);

        babel.registerProtocol(toolbox);
        babel.registerProtocol(bc);
        babel.registerProtocol(pbft);

        toolbox.init(props);
        bc.init(props);
        pbft.init(props);

        babel.start();

        logger.info("Running OpenGoodsMarket");
    }

    public Toolbox(Properties props) {
        super(PROTO_NAME, PROTO_ID);
    }

    @Override
    public void init(Properties  props) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, props.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);

        //register stuff here
    }
}
