package utils;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class Wallets {

    private final Map<PublicKey, Float> wallet = new HashMap<>();
    private final Map<PublicKey, Float> pendingWallet = new HashMap<>();

    public Wallets() {
    }

    public void createWallet(PublicKey key, Float amount) {
        wallet.put(key, amount);
    }

    public void createPendingWallet(PublicKey key, Float amount) {
        pendingWallet.put(key, amount);
    }



    public Map<PublicKey, Float> getWallet() {
        return wallet;
    }

    public Map<PublicKey, Float> getPendingWallet() {
        return pendingWallet;
    }

    public boolean hasWallet(PublicKey key) {
        return wallet.containsKey(key);
    }

    public boolean hasPendingWallet(PublicKey key) {
        return pendingWallet.containsKey(key);
    }

    public float getClientAmount(PublicKey key) {
        return wallet.get(key);
    }

    public float getClientPendingAmount(PublicKey key) {
        return pendingWallet.get(key);
    }

    public void increaseClientAmount(PublicKey key, Float amount) {
        if (wallet.containsKey(key)) {
            var clientAmount = wallet.get(key);
            wallet.put(key, clientAmount + amount);
        } else {
            wallet.put(key, amount);
        }
    }

    public void increaseClientPendingAmount(PublicKey key, Float amount) {
        if (pendingWallet.containsKey(key)) {
            var clientAmount = pendingWallet.get(key);
            pendingWallet.put(key, clientAmount + amount);
        } else {
            pendingWallet.put(key, amount);
        }
    }

    public void decreaseClientAmount(PublicKey key, Float amount) {
        var clientAmount = wallet.get(key);
        wallet.put(key, clientAmount - amount);
    }

    public void decreaseClientPendingAmount(PublicKey key, Float amount) {
        var clientAmount = pendingWallet.get(key);
        pendingWallet.put(key, clientAmount - amount);
    }

    public boolean notEnoughMoney(PublicKey key, Float amount) {
        return amount > wallet.get(key);
    }

}
