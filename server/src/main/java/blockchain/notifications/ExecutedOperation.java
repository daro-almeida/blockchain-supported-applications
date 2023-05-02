package blockchain.notifications;

import blockchain.requests.ClientRequest;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

import java.util.UUID;

public class ExecutedOperation extends ProtoNotification {

    public static final short NOTIFICATION_ID = 301;

    private final UUID requestId;

    public ExecutedOperation(UUID requestId) {
        super(NOTIFICATION_ID);

        this.requestId = requestId;
    }

    public UUID getRequestId() {
        return requestId;
    }
}
