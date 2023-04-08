package consensus.utils;

import java.security.PublicKey;
import java.util.Map;

public interface Proof {

    boolean isValid(int f, Map<Integer, PublicKey> publicKeys);
}
