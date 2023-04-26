package app;

import app.messages.client.replies.CheckOperationStatus;
import app.messages.client.replies.GenericClientReply;
import app.messages.client.requests.Cancel;
import app.messages.client.requests.IssueOffer;
import app.messages.client.requests.IssueWant;
import app.messages.exchange.requests.Deposit;
import app.messages.exchange.requests.Withdrawal;
import app.notifications.ExecutedOperation;
import consensus.notifications.InitializedNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Properties;

//TODO
public class OpenGoodsMarket extends GenericProtocol {

    public static final String PROTO_NAME = "app";
    public static final short PROTO_ID = 300;

    public OpenGoodsMarket(Properties props) {
        super(PROTO_NAME, PROTO_ID);

    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        subscribeNotification(InitializedNotification.NOTIFICATION_ID, this::uponInitialized);
        subscribeNotification(ExecutedOperation.NOTIFICATION_ID, this::uponExecutedOperation);
    }

    private void uponInitialized(InitializedNotification notif , short proto) {
        var channel = notif.getPeerChannel();
        registerSharedChannel(channel);

        try {
            registerMessageHandler(channel, CheckOperationStatus.MESSAGE_ID, this::uponCheckOperationStatusMessage);
            registerMessageHandler(channel, Cancel.MESSAGE_ID, this::uponCancelMessage);
            registerMessageHandler(channel, IssueOffer.MESSAGE_ID, this::uponIssueOfferMessage);
            registerMessageHandler(channel, IssueWant.MESSAGE_ID, this::uponIssueWantMessage);
            registerMessageHandler(channel, Deposit.MESSAGE_ID, this::uponDepositMessage);
            registerMessageHandler(channel, Withdrawal.MESSAGE_ID, this::uponWithdrawalMessage);
        } catch (HandlerRegistrationException e) {
            throw new RuntimeException(e);
        }
        registerMessageSerializer(channel, CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);
        registerMessageSerializer(channel, GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);
        registerMessageSerializer(channel, Cancel.MESSAGE_ID, Cancel.serializer);
        registerMessageSerializer(channel, IssueOffer.MESSAGE_ID, IssueOffer.serializer);
        registerMessageSerializer(channel, Deposit.MESSAGE_ID, Deposit.serializer);
        registerMessageSerializer(channel, Withdrawal.MESSAGE_ID, Withdrawal.serializer);
    }

    // ---------------------------- Notifications ----------------------------

    private void uponExecutedOperation(ExecutedOperation notif, short proto) {

    }

    // ---------------------------- Client Message Handlers ----------------------------

    private void uponCheckOperationStatusMessage(CheckOperationStatus msg, Host host, short proto, int channel) {
    }

    private void uponCancelMessage(Cancel msg, Host host, short proto, int channel) {
    }

    private void uponIssueOfferMessage(IssueOffer msg, Host host, short proto, int channel) {
    }

    private void uponIssueWantMessage(IssueWant msg, Host host, short proto, int channel) {
    }


    private void uponDepositMessage(Deposit msg, Host host, short proto, int channel) {
    }

    private void uponWithdrawalMessage(Withdrawal msg, Host host, short proto, int channel) {
    }
}
