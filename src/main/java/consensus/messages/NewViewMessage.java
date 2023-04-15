package consensus.messages;

import consensus.utils.ViewChangeManager;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Crypto;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NewViewMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 105;

    private final int newViewNumber;
    private final Set<ViewChangeMessage> viewChanges;
    private final Set<PrePrepareMessage> prePrepares;

    public NewViewMessage(int newViewNumber, Set<ViewChangeMessage> viewChanges, Set<PrePrepareMessage> prePrepares) {
        super(NewViewMessage.MESSAGE_ID);
        this.newViewNumber = newViewNumber;
        this.viewChanges = viewChanges;
        this.prePrepares = prePrepares;
    }

    public int getNewViewNumber() {
        return newViewNumber;
    }

    public Set<PrePrepareMessage> getPrePrepares() {
        return prePrepares;
    }



    public boolean viewChangesValid(int f, Map<Integer, PublicKey> publicKeys) {
        return viewChanges.size() >= 2 * f + 1 &&
                viewChanges.stream().allMatch(vc ->
                        Crypto.checkSignature(vc, publicKeys.get(vc.getNodeId())) && vc.preparedProofsValid(f, publicKeys));
    }

    public boolean prePreparesValid(PublicKey primaryPublicKey, int f) {
        if (!prePrepares.stream().allMatch(p -> Crypto.checkSignature(p, primaryPublicKey)))
            return false;

        //TODO this ((might)) be a scuffed way to do this, basically we are recreating the prePrepareMessages and see if
        // they are the same as the ones we received
        return prePrepares.equals(ViewChangeManager.newPrePrepareMessages(newViewNumber, viewChanges, f));
    }

    public Set<ViewChangeMessage> getViewChanges() {
        return viewChanges;
    }

    public static final SignedMessageSerializer<NewViewMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(NewViewMessage newViewMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(newViewMessage.newViewNumber);
            byteBuf.writeInt(newViewMessage.viewChanges.size());
            for (ViewChangeMessage viewChangeMessage : newViewMessage.viewChanges) {
                ViewChangeMessage.serializer.serialize(viewChangeMessage, byteBuf);
            }
            byteBuf.writeInt(newViewMessage.prePrepares.size());
            for (PrePrepareMessage prePrepareMessage : newViewMessage.prePrepares) {
                PrePrepareMessage.serializer.serialize(prePrepareMessage, byteBuf);
            }
        }

        @Override
        public NewViewMessage deserializeBody(ByteBuf byteBuf) throws IOException {
            int newViewNumber = byteBuf.readInt();
            int viewChangesSize = byteBuf.readInt();
            Set<ViewChangeMessage> viewChanges = new HashSet<>(viewChangesSize);
            for (int i = 0; i < viewChangesSize; i++) {
                viewChanges.add(ViewChangeMessage.serializer.deserialize(byteBuf));
            }
            int prePreparesSize = byteBuf.readInt();
            Set<PrePrepareMessage> prePrepares = new HashSet<>(prePreparesSize);
            for (int i = 0; i < prePreparesSize; i++) {
                prePrepares.add(PrePrepareMessage.serializer.deserialize(byteBuf));
            }
            return new NewViewMessage(newViewNumber, viewChanges, prePrepares);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }
}
