package blockchain.requests;

import blockchain.messages.RedirectClientRequestMessage;

public record PendingRequest(ClientRequest request, long timestamp) {

}
