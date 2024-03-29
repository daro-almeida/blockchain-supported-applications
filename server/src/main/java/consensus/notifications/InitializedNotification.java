package consensus.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.Node;
import utils.View;

import java.security.PrivateKey;

public class InitializedNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 103;

    private final int peerChannel;
    private final Node self;
    private final PrivateKey key;
    private final View view;

    public InitializedNotification(int peerChannel, Node self, PrivateKey key, View view) {
        super(NOTIFICATION_ID);
        this.peerChannel = peerChannel;
        this.self = self;
        this.key = key;
        this.view = new View(view);
    }

    public Node getSelf() {
        return self;
    }

    public PrivateKey getKey() {
        return key;
    }

    public View getView() {
        return view;
    }

    public int getPeerChannel() {
    	return peerChannel;
    }
}
