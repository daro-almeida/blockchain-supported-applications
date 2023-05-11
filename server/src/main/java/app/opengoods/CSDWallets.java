package app.opengoods;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class CSDWallets {

    //CSD's
    private final Map<PublicKey, Float> wallets = new HashMap<>();

    private void createWallet(PublicKey key) {
        wallets.put(key, 0f);
    }

    public float getBalance(PublicKey key) {
        if (!wallets.containsKey(key))
            createWallet(key);

        return wallets.get(key);
    }

    public void increaseBalance(PublicKey key, Float amount) {
        if (!wallets.containsKey(key))
            createWallet(key);

        var clientAmount = wallets.get(key);
        wallets.put(key, clientAmount + amount);
    }

    public void decreaseBalance(PublicKey key, Float amount) {
        if (!wallets.containsKey(key))
            createWallet(key);

        var clientAmount = wallets.get(key);
        wallets.put(key, clientAmount - amount);
    }

    public boolean canAfford(PublicKey key, Float amount) {
        return amount > getBalance(key);
    }

}
