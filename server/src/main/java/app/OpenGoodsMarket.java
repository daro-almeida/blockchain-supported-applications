package app;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import blockchain.notifications.ExecutedOperation;
import blockchain.requests.ClientRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.messages.client.replies.GenericClientReply;
import app.messages.client.replies.OperationStatusReply;
import app.messages.client.replies.OperationStatusReply.Status;
import app.messages.client.requests.Cancel;
import app.messages.client.requests.CheckOperationStatus;
import app.messages.client.requests.IssueOffer;
import app.messages.client.requests.IssueWant;
import app.messages.exchange.requests.Deposit;
import app.messages.exchange.requests.Withdrawal;
import blockchain.BlockChainProtocol;
import consensus.PBFTProtocol;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Offer;
import utils.Utils;
import utils.Wallets;
import utils.Want;

public class OpenGoodsMarket extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(OpenGoodsMarket.class);

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    public final static String PROTO_NAME = "OpenGoodsMarket";
    public final static short PROTO_ID = 500;

    private int clientChannel;
    private PublicKey exchangePk;

    public enum OperationType {
        ISSUEWANT, ISSUEOFFER, CANCEL, DEPOSIT, WITHDRAWAL
    }

    private final HashMap<UUID, OperationStatusReply.Status> opStatus = new HashMap<>();
    private final HashMap<UUID, SignedProtoMessage> ops = new HashMap<>();
    private final Map<UUID, OperationType> opsType = new HashMap<>();

    private final Map<ClientRequest, Host> reqDestinations = new HashMap<>();

    private final Wallets wallets = new Wallets();

    // o sortedSet deve estar ordenada por pricePerUnit
    private final Map<String, SortedSet<Want>> wants = new HashMap<>();

    // o sortedSet deve estar ordenada por pricePerUnit
    private final Map<String, SortedSet<Offer>> offers = new HashMap<>();

    public static void main(String[] args) throws InvalidParameterException, IOException,
            HandlerRegistrationException, ProtocolAlreadyExistsException, GeneralSecurityException {
        Properties props = Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);
        if (props.containsKey("interface")) {
            String address = getAddress(props.getProperty("interface"));
            if (address == null)
                return;
            props.put(ADDRESS_KEY, address);
        }

        Babel babel = Babel.getInstance();

        OpenGoodsMarket opm = new OpenGoodsMarket(props);
        BlockChainProtocol bc = new BlockChainProtocol(props);
        PBFTProtocol pbft = new PBFTProtocol(props);

        babel.registerProtocol(opm);
        babel.registerProtocol(bc);
        babel.registerProtocol(pbft);

        opm.init(props);
        bc.init(props);
        pbft.init(props);

        babel.start();

        logger.info("System is running...");
    }

    public OpenGoodsMarket(Properties props) throws IOException, ProtocolAlreadyExistsException,
            HandlerRegistrationException, GeneralSecurityException {

        super(OpenGoodsMarket.PROTO_NAME, OpenGoodsMarket.PROTO_ID);
        this.exchangePk = Crypto.getTruststore().getCertificate("exchange").getPublicKey();
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

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, props.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);

        registerMessageSerializer(clientChannel, IssueOffer.MESSAGE_ID, IssueOffer.serializer);
        registerMessageSerializer(clientChannel, IssueWant.MESSAGE_ID, IssueWant.serializer);
        registerMessageSerializer(clientChannel, Cancel.MESSAGE_ID, Cancel.serializer);
        registerMessageSerializer(clientChannel, CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);

        registerMessageSerializer(clientChannel, Deposit.MESSAGE_ID, Deposit.serializer);
        registerMessageSerializer(clientChannel, Withdrawal.MESSAGE_ID, Withdrawal.serializer);

        registerMessageSerializer(clientChannel, OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
        registerMessageSerializer(clientChannel, GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);

        registerMessageHandler(clientChannel, IssueOffer.MESSAGE_ID, this::handleIssueOfferMessage);
        registerMessageHandler(clientChannel, IssueWant.MESSAGE_ID, this::handleIssueWantMessage);
        registerMessageHandler(clientChannel, Cancel.MESSAGE_ID, this::handleCancelMessage);
        registerMessageHandler(clientChannel, CheckOperationStatus.MESSAGE_ID, this::handleCheckOperationStatusMessage);

        registerMessageHandler(clientChannel, Deposit.MESSAGE_ID, this::handleDepositMessage);
        registerMessageHandler(clientChannel, Withdrawal.MESSAGE_ID, this::handleWithdrawalMessage);

        registerChannelEventHandler(clientChannel, ClientUpEvent.EVENT_ID, this::uponClientConnectionUp);
        registerChannelEventHandler(clientChannel, ClientDownEvent.EVENT_ID, this::uponClientConnectionDown);

        subscribeNotification(ExecutedOperation.NOTIFICATION_ID, this::handleExecutedOperation);
    }

    public void handleIssueOfferMessage(IssueOffer io, Host from, short sourceProto, int channelID) {
        logger.info("Received IssueOffer (" + io.getRid() + " from " + from + "(client" + io.getcID() + ")");
        ops.put(io.getRid(), io);

        if (io.getQuantity() <= 0 || io.getPricePerUnit() <= 0) {
            opStatus.put(io.getRid(), OperationStatusReply.Status.FAILED);
        }

        var signature = io.getSignature();
        var req = new ClientRequest(io.getRid(), io.getOpBytes(), signature, io.getcID());
        opsType.put(io.getRid(), OperationType.ISSUEOFFER);
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        GenericClientReply ack = new GenericClientReply(req.getRequestId());
        reqDestinations.put(req, from);
        opStatus.put(io.getRid(), OperationStatusReply.Status.PENDING);
        sendMessage(clientChannel, ack, sourceProto, from, 0);

        if (offers.containsKey(io.getResourceType())) {
            SortedSet<Offer> off = offers.get(io.getResourceType());
            off.add(new Offer(io.getcID(), io.getResourceType(), io.getQuantity(), io.getPricePerUnit()));
            offers.put(io.getResourceType(), off);
        } else {
            offers.put(io.getResourceType(), new TreeSet<Offer>());
        }

        if (opStatus.get(io.getRid()) == OperationStatusReply.Status.FAILED) {
            sendMessage(clientChannel, new OperationStatusReply(io.getRid(), OperationStatusReply.Status.FAILED),
                    sourceProto, from, 0);
        }

    }

    public void handleIssueWantMessage(IssueWant iw, Host from, short sourceProto, int channelID) {
        logger.info("Received IssueWant (" + iw.getRid() + " from " + from + "(client" + iw.getcID() + ")");
        ops.put(iw.getRid(), iw);
        opsType.put(iw.getRid(), OperationType.ISSUEWANT);

        if (wallets.getClientAmount(iw.getcID()) < iw.getcCSDs()) {
            opStatus.put(iw.getRid(), OperationStatusReply.Status.FAILED);
        }

        var signature = iw.getSignature();
        ClientRequest req = new ClientRequest(iw.getRid(), iw.getOpBytes(), signature, iw.getcID());
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        GenericClientReply ack = new GenericClientReply(req.getRequestId());
        reqDestinations.put(req, from);
        opStatus.put(iw.getRid(), OperationStatusReply.Status.PENDING);
        wallets.decreaseClientPendingAmount(iw.getcID(), iw.getcCSDs());
        sendMessage(clientChannel, ack, sourceProto, from, 0);

        if (wants.containsKey(iw.getResourceType())) {
            SortedSet<Want> want = wants.get(iw.getResourceType());
            want.add(new Want(iw.getcID(), iw.getResourceType(), iw.getQuantity(), iw.getPricePerUnit()));
            wants.put(iw.getResourceType(), want);
        } else {
            wants.put(iw.getResourceType(), new TreeSet<Want>());
        }

        if (opStatus.get(iw.getRid()) == OperationStatusReply.Status.FAILED) {
            sendMessage(clientChannel, new OperationStatusReply(iw.getRid(), OperationStatusReply.Status.FAILED),
                    sourceProto, from, 0);
        }

    }

    public void handleCancelMessage(Cancel c, Host from, short sourceProto, int channelID) {
        var rID = c.getrID();
        logger.info("Received Cancel for operation " + rID + " from " + from);
        ops.put(c.getrID(), c);

        // rejected if the guy who wants to cancel is not the same guy who did the
        // operation being cancelled
        if (opsType.get(rID) == OperationType.ISSUEOFFER) {
            IssueOffer io = (IssueOffer) ops.get(rID);
            if (c.getcID() != io.getcID()) {
                opStatus.put(rID, OperationStatusReply.Status.REJECTED);
            }
        } else if (opsType.get(rID) == OperationType.ISSUEWANT) {
            IssueWant iw = (IssueWant) ops.get(rID);
            if (c.getcID() != iw.getcID()) {
                opStatus.put(rID, OperationStatusReply.Status.REJECTED);
            }
        }

        var signature = c.getSignature();
        var req = new ClientRequest(rID, c.getOpBytes(), signature, c.getcID());
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        opsType.put(c.getrID(), OperationType.CANCEL);
        GenericClientReply ack = new GenericClientReply(req.getRequestId());
        reqDestinations.put(req, from);
        opStatus.put(rID, OperationStatusReply.Status.PENDING);
        sendMessage(clientChannel, ack, sourceProto, from, 0);

        if (opStatus.get(rID) == OperationStatusReply.Status.REJECTED) {
            sendMessage(clientChannel, new OperationStatusReply(rID, OperationStatusReply.Status.REJECTED),
                    sourceProto, from, 0);
        }

    }

    public void handleCheckOperationStatusMessage(CheckOperationStatus cos, Host from, short sourceProto,
            int channelID) {
        logger.info("Received CheckOperation for operation " + cos.getrID() + " from " + from);

        OperationStatusReply osr = null;

        Status s = opStatus.get(cos.getrID());

        if (s != null) {
            switch (s) {
                case CANCELLED:
                    osr = new OperationStatusReply(cos.getrID(), Status.CANCELLED);
                    break;
                case EXECUTED:
                    osr = new OperationStatusReply(cos.getrID(), Status.EXECUTED);
                    break;
                case FAILED:
                    osr = new OperationStatusReply(cos.getrID(), Status.FAILED);
                    break;
                case PENDING:
                    osr = new OperationStatusReply(cos.getrID(), Status.PENDING);
                    int r = new Random(System.currentTimeMillis()).nextInt(100);
                    if (r >= 0 && r < 25) {
                        opStatus.put(cos.getrID(), Status.EXECUTED);
                    } else if (r >= 25 && r < 50) {
                        opStatus.put(cos.getrID(), Status.FAILED);
                    } else if (r >= 50 && r < 75) {
                        opStatus.put(cos.getrID(), Status.REJECTED);
                    } else {
                        opStatus.put(cos.getrID(), Status.EXECUTED);
                    }
                    break;
                case REJECTED:
                    osr = new OperationStatusReply(cos.getrID(), Status.REJECTED);
                    break;
                default:
                    osr = new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
                    break;

            }
        } else {
            osr = new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
        }

        if (osr != null) {
            sendMessage(clientChannel, osr, sourceProto, from, 0);
        }
    }

    public void handleDepositMessage(Deposit d, Host from, short sourceProto, int channelID) {
        var clientId = d.getClientID();
        var amount = d.getAmount();

        logger.info("Received deposit of " + amount + " to client" + clientId + " from the Exchange ("
                + from + ")");
        ops.put(d.getRid(), d);
        opsType.put(d.getRid(), OperationType.DEPOSIT);

        if (!clientId.equals(exchangePk)) {
            opStatus.put(d.getRid(), OperationStatusReply.Status.REJECTED);
        }

        if (amount <= 0) {
            opStatus.put(d.getRid(), OperationStatusReply.Status.FAILED);
        }

        var signature = d.getSignature();
        var req = new ClientRequest(d.getRid(), d.getOpBytes(), signature, clientId);
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        GenericClientReply ack = new GenericClientReply(req.getRequestId());
        reqDestinations.put(req, from);

        if (wallets.hasWallet(clientId)) {
            wallets.increaseClientPendingAmount(clientId, amount);
        } else {
            wallets.createPendingWallet(clientId, amount);
        }

        opStatus.put(d.getRid(), OperationStatusReply.Status.PENDING);
        sendMessage(clientChannel, ack, sourceProto, from, 0);

        if (opStatus.get(d.getRid()) == OperationStatusReply.Status.FAILED) {
            sendMessage(clientChannel, new OperationStatusReply(d.getRid(), OperationStatusReply.Status.FAILED),
                    sourceProto, from, 0);
        }

        if (opStatus.get(d.getRid()) == OperationStatusReply.Status.REJECTED) {
            sendMessage(clientChannel, new OperationStatusReply(d.getRid(), OperationStatusReply.Status.REJECTED),
                    sourceProto, from, 0);
        }

    }

    public void handleWithdrawalMessage(Withdrawal w, Host from, short sourceProto, int channelID) {
        var clientId = w.getClientID();
        var amount = w.getAmount();

        logger.info("Received withdrawal of " + amount + " to client" + clientId + " from the Exchange ("
                + from + ")");
        ops.put(w.getRid(), w);
        opsType.put(w.getRid(), OperationType.WITHDRAWAL);

        if (!clientId.equals(exchangePk)) {
            opStatus.put(w.getRid(), OperationStatusReply.Status.REJECTED);
        }

        if (!wallets.hasWallet(clientId)) {
            opStatus.put(w.getRid(), OperationStatusReply.Status.FAILED);
        } else {
            if (amount <= 0 || wallets.notEnoughMoney(clientId, amount)) {
                opStatus.put(w.getRid(), OperationStatusReply.Status.FAILED);
            } else {
                var signature = w.getSignature();
                var req = new ClientRequest(w.getRid(), w.getOpBytes(), signature, clientId);
                sendRequest(req, BlockChainProtocol.PROTO_ID);
                GenericClientReply ack = new GenericClientReply(req.getRequestId());
                reqDestinations.put(req, from);
                opStatus.put(w.getRid(), OperationStatusReply.Status.PENDING);
                wallets.decreaseClientPendingAmount(clientId, amount);
                sendMessage(clientChannel, ack, sourceProto, from, 0);

            }
        }

        if (opStatus.get(w.getRid()) == OperationStatusReply.Status.FAILED) {
            sendMessage(clientChannel, new OperationStatusReply(w.getRid(), OperationStatusReply.Status.FAILED),
                    sourceProto, from, 0);
        }

        if (opStatus.get(w.getRid()) == OperationStatusReply.Status.REJECTED) {
            sendMessage(clientChannel, new OperationStatusReply(w.getRid(), OperationStatusReply.Status.REJECTED),
                    sourceProto, from, 0);
        }

    }

    private void handleExecutedOperation(ExecutedOperation notif, short sourceProto) {

        ClientRequest client = notif.getRequest();
        UUID requestId = client.getRequestId();

        sendMessage(clientChannel, new OperationStatusReply(requestId, OperationStatusReply.Status.EXECUTED),
                sourceProto, reqDestinations.get(client), 0);

        if (opsType.get(requestId) == OperationType.ISSUEOFFER) {
            handleExecutedIssueOffer(requestId);
        } else if (opsType.get(requestId) == OperationType.ISSUEWANT) {
            handleExecutedIssueWant(requestId);
        } else if (opsType.get(requestId) == OperationType.CANCEL) {
            handleExecutedCancel(requestId);
        } else if (opsType.get(requestId) == OperationType.DEPOSIT) {
            handleExecutedDeposit(requestId);
        } else if (opsType.get(requestId) == OperationType.WITHDRAWAL) {
            handleExecutedWithdrawal(requestId);
        }

    }

    private void handleExecutedIssueOffer(UUID requestId) {
        IssueOffer io = (IssueOffer) ops.get(requestId);
        SortedSet<Offer> setOffers = offers.get(io.getResourceType());
        // FIXME isto tá horrível mas não tava a conseguir arranjar maneira de ir buscar
        // o offer com o a mesma publicKey
        Offer offer = null;
        for (Offer off : setOffers) {
            if (off.getClientId().equals(io.getcID())) {
                offer = off;
            }
        }
        int stock = offer.getStock();
        float offerPrice = offer.getPricePerUnit();
        if (wants.containsKey(offer.getResourceType())) {
            // TODO needs to make sure the wants set is sorted
            for (Want w : wants.get(offer.getResourceType())) {
                int quantity = w.getQuantity();
                float wantPrice = w.getPricePerUnit();
                if (wantPrice >= offerPrice) {
                    if (stock > quantity) {
                        offer.decreaseStock(quantity);
                        wallets.increaseClientPendingAmount(offer.getClientId(), quantity * wantPrice);
                        wallets.increaseClientAmount(offer.getClientId(), quantity * wantPrice);
                        wallets.decreaseClientAmount(w.getClientId(), quantity * wantPrice);
                        wants.get(offer.getResourceType()).remove(w);
                    } else if (stock == quantity) {
                        wallets.increaseClientPendingAmount(offer.getClientId(), quantity * wantPrice);
                        wallets.increaseClientAmount(offer.getClientId(), quantity * wantPrice);
                        wallets.decreaseClientAmount(w.getClientId(), quantity * wantPrice);
                        offers.get(offer.getResourceType()).remove(offer);
                        wants.get(offer.getResourceType()).remove(w);
                        return;
                    } else {
                        w.decreaseQuantity(stock);
                        wallets.increaseClientPendingAmount(offer.getClientId(), stock * wantPrice);
                        wallets.increaseClientAmount(offer.getClientId(), stock * wantPrice);
                        wallets.decreaseClientAmount(w.getClientId(), stock * wantPrice);
                        offers.get(offer.getResourceType()).remove(offer);
                        return;
                    }
                } else {
                    return;
                }
            }

        }
    }

    private void handleExecutedIssueWant(UUID requestId) {
        IssueWant iw = (IssueWant) ops.get(requestId);
        SortedSet<Want> setWants = wants.get(iw.getResourceType());
        // FIXME isto tá horrível mas não tava a conseguir arranjar maneira de ir buscar
        // o offer com o a mesma publicKey
        Want want = null;
        for (Want w : setWants) {
            if (w.getClientId().equals(iw.getcID())) {
                want = w;
            }
        }
        int quantity = want.getQuantity();
        float wantPrice = want.getPricePerUnit();
        for (Offer o : offers.get(iw.getResourceType())) {
            int offerStock = o.getStock();
            float price = o.getPricePerUnit();
            if (wantPrice >= price) {
                if (offerStock > quantity) {
                    o.decreaseStock(quantity);
                    wallets.increaseClientPendingAmount(o.getClientId(), quantity * wantPrice);
                    wallets.increaseClientAmount(o.getClientId(), quantity * wantPrice);
                    wallets.decreaseClientAmount(want.getClientId(), quantity * wantPrice);
                    wants.get(iw.getResourceType()).remove(want);
                    return;
                } else if (offerStock == quantity) {
                    wallets.increaseClientPendingAmount(o.getClientId(), quantity * wantPrice);
                    wallets.increaseClientAmount(o.getClientId(), quantity * wantPrice);
                    wallets.decreaseClientAmount(want.getClientId(), quantity * wantPrice);
                    offers.get(iw.getResourceType()).remove(o);
                    wants.get(iw.getResourceType()).remove(want);
                    return;
                } else {
                    want.decreaseQuantity(offerStock);
                    wallets.increaseClientPendingAmount(o.getClientId(), offerStock * wantPrice);
                    wallets.increaseClientAmount(o.getClientId(), offerStock * wantPrice);
                    wallets.decreaseClientAmount(want.getClientId(), offerStock * wantPrice);
                    offers.get(iw.getResourceType()).remove(o);
                }
            } else {
                return;
            }
        }
    }

    private void handleExecutedCancel(UUID requestId) {
        // TODO depois de cancelled retirar a offer ou want
        Cancel c = (Cancel) ops.get(requestId);
        if (opStatus.containsKey(c.getrID())) {
            opStatus.put(c.getrID(), OperationStatusReply.Status.CANCELLED);
            ops.remove(c.getrID());
        }
    }

    private void handleExecutedDeposit(UUID requestId) {
        Deposit d = (Deposit) ops.get(requestId);
        if (wallets.hasWallet(d.getClientID())) {
            wallets.increaseClientAmount(d.getClientID(), d.getAmount());
        } else {
            wallets.createWallet(d.getClientID(), d.getAmount());
        }
    }

    private void handleExecutedWithdrawal(UUID requestId) {
        Withdrawal w = (Withdrawal) ops.get(requestId);
        wallets.decreaseClientAmount(w.getClientID(), w.getAmount());
    }

    private void uponClientConnectionUp(ClientUpEvent event, int channel) {
        logger.debug(event);
    }

    private void uponClientConnectionDown(ClientDownEvent event, int channel) {
        logger.warn(event);
    }
}