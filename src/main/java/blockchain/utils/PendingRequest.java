package blockchain.utils;

import blockchain.requests.ClientRequest;

public record PendingRequest(ClientRequest request, byte[] signature, long timestamp) {

}
