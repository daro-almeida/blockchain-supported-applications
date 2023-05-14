package app.open_goods;

import app.messages.WriteOperation;
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
import blockchain.notifications.ExecutedOperation;
import blockchain.requests.ClientRequest;
import consensus.PBFTProtocol;
import metrics.Metrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Utils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.*;

public class OpenGoodsMarket extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(OpenGoodsMarket.class);

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    public final static String PROTO_NAME = "OpenGoodsMarket";
    public final static short PROTO_ID = 500;

    private int clientChannel;
    private final PublicKey exchangeKey;

    private final Map<UUID, OperationStatusReply.Status> opStatus = new HashMap<>();

    // key = target request ID
    private final Map<UUID, Cancel> unmatchedCancels = new HashMap<>();
    // to send reply later
    private final Map<UUID, Destination> reqDestinations = new HashMap<>();

    private final CSDWallets csdWallets = new CSDWallets();

    // key = resourceType
    // ordered by pricePerUnit
    private final Map<String, SortedSet<Want>> wants = new HashMap<>();
    private final Map<String, SortedSet<Offer>> offers = new HashMap<>();

    // saved so can cancel later
    private final Map<UUID, IssueOffer> standingOffers = new HashMap<>();
    private final Map<UUID, IssueWant> standingWants = new HashMap<>();

    public static void main(String[] args) throws InvalidParameterException, IOException,
            HandlerRegistrationException, ProtocolAlreadyExistsException, GeneralSecurityException {
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

    public OpenGoodsMarket(Properties props) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        super(OpenGoodsMarket.PROTO_NAME, OpenGoodsMarket.PROTO_ID);
        this.exchangeKey = Crypto.getTruststore(props).getCertificate("exchange").getPublicKey();
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, props.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);

        registerMessageSerializer(clientChannel, IssueOffer.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, IssueWant.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, Cancel.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, Deposit.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, Withdrawal.MESSAGE_ID, WriteOperation.serializer);

        registerMessageSerializer(clientChannel, CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);
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

    /*
     * CLIENT MESSAGES
     */

    public void handleIssueOfferMessage(IssueOffer io, Host from, short sourceProto, int channelID) {
        logger.debug("Received IssueOffer ({} from client {})", io.getRid(), from);

        var dest = new Destination(from, sourceProto);
        if (!validateIssueOffer(io, dest) || !authenticatedOperation(io, io.getRid(), io.getcID(), dest))
            return;

        submitOperation(io.getRid(), io.getBytes(), io.getSignature(), io.getcID(), dest);
    }

    public void handleIssueWantMessage(IssueWant iw, Host from, short sourceProto, int channelID) {
        logger.debug("Received IssueWant ({} from client {})", iw.getRid(), from);

        var dest = new Destination(from, sourceProto);
        if (!validateIssueWant(iw, dest) || !authenticatedOperation(iw, iw.getRid(), iw.getcID(), dest))
            return;

        submitOperation(iw.getRid(), iw.getBytes(), iw.getSignature(), iw.getcID(), dest);
    }

    public void handleCancelMessage(Cancel c, Host from, short sourceProto, int channelID) {
        logger.debug("Received Cancel ({} from client {})", c.getrID(), from);

        var dest = new Destination(from, sourceProto);
        if (!validateCancel(c, dest) || !authenticatedOperation(c, c.getrID(), c.getcID(), dest))
            return;

        submitOperation(c.getrID(), c.getBytes(), c.getSignature(), c.getcID(), dest);
    }

    public void handleCheckOperationStatusMessage(CheckOperationStatus cos, Host from, short sourceProto,
            int channelID) {
        logger.debug("Received CheckOperationStatus ({} from client {})", cos.getrID(), from);

        OperationStatusReply osr;

        Status s = opStatus.get(cos.getrID());

        if (s != null) {
            osr = switch (s) {
                case CANCELLED -> new OperationStatusReply(cos.getrID(), Status.CANCELLED);
                case EXECUTED -> new OperationStatusReply(cos.getrID(), Status.EXECUTED);
                case FAILED -> new OperationStatusReply(cos.getrID(), Status.FAILED);
                case PENDING -> new OperationStatusReply(cos.getrID(), Status.PENDING);
                case REJECTED -> new OperationStatusReply(cos.getrID(), Status.REJECTED);
                default -> new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
            };
        } else {
            osr = new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
        }

        sendMessage(clientChannel, osr, sourceProto, from, 0);
    }

    public void handleDepositMessage(Deposit d, Host from, short sourceProto, int channelID) {
        logger.debug("Received deposit of " + d.getAmount() + " from exchange (" + from + ")");

        var dest = new Destination(from, sourceProto);
        if (!validateDeposit(d, dest) || !authenticatedOperation(d, d.getRid(), exchangeKey, dest))
            return;

        submitOperation(d.getRid(), d.getBytes(), d.getSignature(), exchangeKey, dest);
    }

    public void handleWithdrawalMessage(Withdrawal w, Host from, short sourceProto, int channelID) {
        logger.debug("Received withdrawal of " + w.getAmount() + " from exchange (" + from + ")");

        var dest = new Destination(from, sourceProto);
        if (!validateWithdrawal(w, dest) || !authenticatedOperation(w, w.getRid(), exchangeKey, dest))
            return;

        submitOperation(w.getRid(), w.getBytes(), w.getSignature(), exchangeKey, dest);
    }

    /*
     * NOTIFICATIONS
     */

    private void handleExecutedOperation(ExecutedOperation notif, short sourceProto) {
        assert (opStatus.get(notif.getRequest().getRequestId()) == Status.PENDING ||
                opStatus.get(notif.getRequest().getRequestId()) == null);

        var operation = WriteOperation.fromBytes(notif.getRequest().getOperation());
        switch(operation.getType()) {
            case ISSUE_OFFER -> handleExecutedIssueOffer((IssueOffer) operation);
            case ISSUE_WANT -> handleExecutedIssueWant((IssueWant) operation);
            case CANCEL -> handleExecutedCancel((Cancel) operation);
            case DEPOSIT -> handleExecutedDeposit((Deposit) operation);
            case WITHDRAWAL -> handleExecutedWithdrawal((Withdrawal) operation);
            default -> logger.error("Received unknown operation type");
        }
    }

    /*
     * AUXILIARY METHODS
     */

    private void handleExecutedIssueOffer(IssueOffer io) {
        if (preCancelled(io.getRid(), io.getcID()) || !validateIssueOffer(io, reqDestinations.get(io.getRid()))) {
            reqDestinations.remove(io.getRid());
            return;
        }

        logger.info("Executed IssueOffer for {} units of {}, {}CSD's each: {}",
                io.getResourceType(), io.getQuantity(), io.getPricePerUnit(), io.getRid());

        var offer = new Offer(io.getRid(), io.getcID(), io.getResourceType(), io.getQuantity(), io.getPricePerUnit());
        float offerPrice = offer.getPricePerUnit();

        offers.computeIfAbsent(offer.getResourceType(), k -> new TreeSet<>());
        var offersSet = offers.get(offer.getResourceType());
        wants.computeIfAbsent(offer.getResourceType(), k -> new TreeSet<>());
        var wantsSet = wants.get(offer.getResourceType());

        if (wantsSet.isEmpty()) {
            offersSet.add(offer);
            standingOffers.put(io.getRid(), io);
            changeAndNotifyStatus(io.getRid(), Status.EXECUTED);
            return;
        }

        Set<Want> toRemove = new HashSet<>();
        for (Want want : wantsSet) {
            float wantPrice = want.getPricePerUnit();
            if (wantPrice < offerPrice) {
                offersSet.add(offer);
                standingOffers.put(io.getRid(), io);
                break;
            }
            if(!match(offer, want))
                continue;

            if (want.getQuantity() == 0) toRemove.add(want);
            if (offer.getStock() == 0) break;
        }
        wantsSet.removeAll(toRemove);
        toRemove.forEach(want ->  standingWants.remove(want.getRequestId()));

        changeAndNotifyStatus(io.getRid(), Status.EXECUTED);
    }

    private void handleExecutedIssueWant(IssueWant iw) {
        if (preCancelled(iw.getRid(), iw.getcID()) || !validateIssueWant(iw, reqDestinations.get(iw.getRid()))) {
            reqDestinations.remove(iw.getRid());
            return;
        }

        logger.info("Executed IssueWant for {} units of {}, {}CSD's each: {}",
                iw.getQuantity(), iw.getResourceType(), iw.getPricePerUnit(), iw.getRid());

        var want = new Want(iw.getRid(), iw.getcID(), iw.getResourceType(), iw.getQuantity(), iw.getPricePerUnit());
        float wantPrice = want.getPricePerUnit();

        wants.computeIfAbsent(want.getResourceType(), k -> new TreeSet<>());
        var wantsSet = wants.get(want.getResourceType());
        offers.computeIfAbsent(want.getResourceType(), k -> new TreeSet<>());
        var offersSet = offers.get(want.getResourceType());

        if (offersSet.isEmpty()) {
            wantsSet.add(want);
            standingWants.put(iw.getRid(), iw);
            changeAndNotifyStatus(iw.getRid(), Status.EXECUTED);
            return;
        }

        Set<Offer> toRemove = new HashSet<>();
        for (Offer offer : offersSet) {
            float offerPrice = offer.getPricePerUnit();
            if (wantPrice < offerPrice) {
                wantsSet.add(want);
                standingWants.put(iw.getRid(), iw);
                break;
            }
            if(!match(offer, want)) continue;

            if (offer.getStock() == 0) toRemove.add(offer);
            if (want.getQuantity() == 0) break;
        }
        offersSet.removeAll(toRemove);
        toRemove.forEach(offer -> standingOffers.remove(offer.getRequestId()));

        changeAndNotifyStatus(iw.getRid(), Status.EXECUTED);
    }

    private void handleExecutedCancel(Cancel c) {
        if (!validateCancel(c, reqDestinations.get(c.getrID()))) {
            reqDestinations.remove(c.getrID());
            return;
        }

        logger.info("Executed Cancel on {}: {}", c.getTargetRequest(), c.getrID());

        var iw = standingWants.get(c.getTargetRequest());
        var io = standingOffers.get(c.getTargetRequest());
         if (iw == null && io == null) {
            unmatchedCancels.put(c.getTargetRequest(), c);
            return;
        }

        logger.info("Operation {} was cancelled", c.getTargetRequest());

        if (iw != null) {
            standingWants.remove(c.getTargetRequest());
            wants.get(iw.getResourceType()).removeIf(w -> w.getRequestId().equals(c.getTargetRequest()));
        } else {
            standingOffers.remove(c.getTargetRequest());
            offers.get(io.getResourceType()).removeIf(o -> o.getRequestId().equals(c.getTargetRequest()));
        }

        changeAndNotifyStatus(c.getTargetRequest(), Status.CANCELLED);
        changeAndNotifyStatus(c.getrID(), Status.EXECUTED);
    }

    private void handleExecutedDeposit(Deposit d) {
        if (!validateDeposit(d, reqDestinations.get(d.getRid()))) {
            reqDestinations.remove(d.getRid());
            return;
        }

        logger.info("Executed Deposit of {} CSD's: {}", d.getAmount(), d.getRid());

        csdWallets.increaseBalance(d.getClientID(), d.getAmount());

        changeAndNotifyStatus(d.getRid(), Status.EXECUTED);
    }

    private void handleExecutedWithdrawal(Withdrawal w) {
        if (!validateWithdrawal(w, reqDestinations.get(w.getRid()))) {
            reqDestinations.remove(w.getRid());
            return;
        }

        logger.info("Executed Withdrawal of {} CSD's: {}", w.getAmount(), w.getRid());

        csdWallets.decreaseBalance(w.getClientID(), w.getAmount());

        changeAndNotifyStatus(w.getRid(), Status.EXECUTED);
    }

    private boolean authenticatedOperation(WriteOperation op, UUID requestId, PublicKey key, Destination dest) {
        if (!Crypto.checkSignature(op, key)) {
            logger.warn("Invalid signature in operation from client {}", dest.host());
            opStatus.put(requestId, Status.REJECTED);
            sendStatus(requestId, Status.REJECTED, dest);
            return false;
        }
        return true;
    }

    private boolean repeatedOperation(UUID requestId, Destination dest) {
        if (opStatus.containsKey(requestId) && opStatus.get(requestId) != OperationStatusReply.Status.PENDING) {
            logger.warn("Repeated operation {}", requestId);
            opStatus.put(requestId, Status.FAILED);
            sendStatus(requestId, Status.FAILED, dest);
            return true;
        }
        return false;
    }

    private boolean validateIssueOffer(IssueOffer io, Destination dest) {
        if (repeatedOperation(io.getRid(), dest))
            return false;

        boolean failed = false;
        if (io.getQuantity() <= 0) {
            logger.warn("IssueOffer: Invalid quantity in {} from client {}", io.getRid(), io.getcID());
            failed = true;
        } else if (io.getPricePerUnit() <= 0) {
            logger.warn("IssueOffer: Invalid price in {} from client {}", io.getRid(), io.getcID());
            failed = true;
        }

        if (failed) {
            opStatus.put(io.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(io.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }

    private boolean validateIssueWant(IssueWant iw, Destination dest) {
        if (repeatedOperation(iw.getRid(), dest))
            return false;

        boolean failed = false;
        if (iw.getQuantity() <= 0) {
            logger.warn("IssueWant: Invalid quantity in {} from client {}", iw.getRid(), iw.getcID());
            failed = true;
        } else if (iw.getPricePerUnit() <= 0) {
            logger.warn("IssueWant: Invalid price in {} from client {}", iw.getRid(), iw.getcID());
            failed = true;
        }

        if (failed) {
            opStatus.put(iw.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(iw.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }

    private boolean validateCancel(Cancel c, Destination dest) {
        if (repeatedOperation(c.getrID(), dest))
            return false;

        boolean failed = false;
        if (opStatus.containsKey(c.getTargetRequest()) && opStatus.get(c.getTargetRequest()) == Status.CANCELLED) {
            logger.warn("Cancel: Target request {} is already cancelled", c.getTargetRequest());
            failed = true;
        } else if (unmatchedCancels.containsKey(c.getTargetRequest())) {
            logger.warn("Cancel: Cancel for Target request {} is already pending", c.getTargetRequest());
            failed = true;
        } else if (!standingOffers.containsKey(c.getTargetRequest()) && !standingWants.containsKey(c.getTargetRequest()) &&
                opStatus.containsKey(c.getTargetRequest()) && opStatus.get(c.getTargetRequest()) == Status.EXECUTED) {
            logger.warn("Cancel: request {} was already fulfilled", c.getTargetRequest());
            failed = true;
        }
        if (failed) {
            opStatus.put(c.getrID(), OperationStatusReply.Status.FAILED);
            sendStatus(c.getrID(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        boolean rejected = false;
        if ((standingOffers.containsKey(c.getTargetRequest()) && !standingOffers.get(c.getTargetRequest()).getcID().equals(c.getcID())) ||
                (standingWants.containsKey(c.getTargetRequest()) && !standingWants.get(c.getTargetRequest()).getcID().equals(c.getcID()))) {
            logger.warn("Cancel: Target request {} does not belong to client {}", c.getTargetRequest(), dest.host());
            rejected = true;
        }
        if (rejected) {
            opStatus.put(c.getrID(), OperationStatusReply.Status.REJECTED);
            sendStatus(c.getrID(), OperationStatusReply.Status.REJECTED, dest);
            return false;
        }

        return true;
    }

    private boolean validateDeposit(Deposit d, Destination dest) {
        if (repeatedOperation(d.getRid(), dest))
            return false;

        boolean failed = false;
        if (d.getAmount() <= 0) {
            logger.warn("Deposit: Invalid amount in {} from client {}", d.getRid(), dest.host());
            failed = true;
        }

        if (failed) {
            opStatus.put(d.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(d.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }

    private boolean validateWithdrawal(Withdrawal w, Destination dest) {
        if (repeatedOperation(w.getRid(), dest))
            return false;

        boolean failed = false;
        if (w.getAmount() <= 0) {
            logger.warn("Withdrawal: Invalid amount in {} from client {}", w.getRid(), dest.host());
            failed = true;
        } else if (!csdWallets.canAfford(w.getClientID(), w.getAmount())) {
            logger.warn("Withdrawal: Client {} has insufficient funds", w.getClientID());
            failed = true;
        }

        if (failed) {
            opStatus.put(w.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(w.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }

    private void submitOperation(UUID requestId, byte[] opBytes, byte[] signature, PublicKey clientId, Destination dest) {
        var req = new ClientRequest(requestId, opBytes, signature, clientId);
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        opStatus.put(requestId, Status.PENDING);
        reqDestinations.put(requestId, dest);

        sendAck(requestId, reqDestinations.get(requestId));
    }

    private boolean preCancelled(UUID requestId, PublicKey clientId) {
        boolean doCancel = false;
        var cancel = unmatchedCancels.get(requestId);
        if (cancel != null) {
            if (cancel.getcID().equals(clientId)) {
                logger.info("Operation {} was cancelled.", requestId);
                opStatus.put(requestId, Status.CANCELLED);
                if (reqDestinations.containsKey(requestId)) {
                    sendStatus(requestId, Status.CANCELLED, reqDestinations.get(requestId));
                    reqDestinations.remove(requestId);
                }
                doCancel = true;
            } else {
                logger.warn("Cancel: Target request {} does not belong to client {}", requestId, clientId);
                changeAndNotifyStatus(cancel.getrID(), Status.REJECTED);
            }
            unmatchedCancels.remove(requestId);
        }
        return doCancel;
    }

    private boolean match(Offer offer, Want want) {
        var stock = offer.getStock();
        var quantity = want.getQuantity();
        var offerPrice = offer.getPricePerUnit();
        var buyAmount = Math.min(stock, Math.min(quantity, (int) (csdWallets.getBalance(want.getClientId()) / offerPrice)));
        if (buyAmount == 0)
            return false;

        logger.info("Matched Offer {} with Want {} for {} units of {}",
                offer.getRequestId(), want.getRequestId(), buyAmount, offer.getResourceType());

        offer.decreaseStock(buyAmount);
        want.decreaseQuantity(buyAmount);
        csdWallets.increaseBalance(offer.getClientId(), buyAmount * offerPrice);
        csdWallets.decreaseBalance(want.getClientId(), buyAmount * offerPrice);
        return true;
    }

    private void sendAck(UUID requestId, Destination dest) {
        assert dest != null;
        GenericClientReply ack = new GenericClientReply(requestId);
        sendMessage(clientChannel, ack, dest.sourceProto(), dest.host(), 0);
    }

    private void changeAndNotifyStatus(UUID requestId, Status status) {
        opStatus.put(requestId, status);
        var hostToReply = reqDestinations.get(requestId);
        if (hostToReply != null) {
            sendStatus(requestId, status, hostToReply);
            reqDestinations.remove(requestId);
        }
    }
    private void sendStatus(UUID requestId, Status status, Destination dest) {
        if (dest == null)
            return;
        OperationStatusReply statusReply = new OperationStatusReply(requestId, status);
        sendMessage(clientChannel, statusReply, dest.sourceProto(), dest.host(), 0);
    }

    /*
     * CONNECTION EVENTS
     */

    private void uponClientConnectionUp(ClientUpEvent event, int channel) {
        logger.debug(event);
    }

    private void uponClientConnectionDown(ClientDownEvent event, int channel) {
        logger.warn(event);
    }
}