package blockchain.notifications;

import blockchain.requests.ClientRequest;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class ExecutedOperation extends ProtoNotification {

    public static final short NOTIFICATION_ID = 301;

    private final ClientRequest request;

    public ExecutedOperation(ClientRequest request) {
        super(NOTIFICATION_ID);

        this.request = request;
    }

    public ClientRequest getRequest() {
        return request;
    }
}
