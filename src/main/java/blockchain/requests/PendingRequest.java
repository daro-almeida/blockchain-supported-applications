package blockchain.requests;

import blockchain.messages.RedirectClientRequestMessage;

public record PendingRequest(RedirectClientRequestMessage message, long timestamp) {

}
