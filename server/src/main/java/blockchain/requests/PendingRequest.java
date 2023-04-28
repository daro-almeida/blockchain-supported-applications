package blockchain.requests;

import blockchain.messages.RedirectClientRequestMessage;

public class PendingRequest {

    public final ClientRequest request;
    public long timestamp;

    public PendingRequest(ClientRequest request, long timestamp) {
        this.request = request;
        this.timestamp = timestamp;
    }

    public ClientRequest request() {
        return request;
    }

    public long timestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
