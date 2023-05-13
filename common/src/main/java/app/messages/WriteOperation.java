package app.messages;

import app.messages.client.requests.Cancel;
import app.messages.client.requests.IssueOffer;
import app.messages.client.requests.IssueWant;
import app.messages.exchange.requests.Deposit;
import app.messages.exchange.requests.Withdrawal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

public abstract class WriteOperation extends SignedProtoMessage {

    public enum OperationType {
        ISSUE_WANT, ISSUE_OFFER, CANCEL, DEPOSIT, WITHDRAWAL;
        private static final OperationType[] values = values();
        public static OperationType get(int ordinal) { return values[ordinal]; }
    }

    private final OperationType type;

    public WriteOperation(short id, OperationType type) {
        super(id);
        this.type = type;
    }

    public OperationType getType() {
        return type;
    }

    public final byte[] getBytes() {
        return this.serializedMessage;
    }

    public static WriteOperation fromBytes(byte[] opBytes) {
        var byteBuf = Unpooled.wrappedBuffer(opBytes);
        try {
            byteBuf.skipBytes(2); // skip message id
            return serializer.deserializeBody(byteBuf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final byte[] getSignature() {
        return signature;
    }

    @Override
    public SignedMessageSerializer<WriteOperation> getSerializer() {
        return serializer;
    }

    public final static SignedMessageSerializer<WriteOperation> serializer = new SignedMessageSerializer<>() {
        @Override
        public void serializeBody(WriteOperation op, ByteBuf out) throws IOException {
            out.writeByte(op.getType().ordinal());
            switch (op.getType()) {
                case ISSUE_WANT -> IssueWant.serializer.serialize((IssueWant) op, out);
                case ISSUE_OFFER -> IssueOffer.serializer.serialize((IssueOffer) op, out);
                case CANCEL -> Cancel.serializer.serialize((Cancel) op, out);
                case DEPOSIT -> Deposit.serializer.serialize((Deposit) op, out);
                case WITHDRAWAL -> Withdrawal.serializer.serialize((Withdrawal) op, out);
            }
        }

        @Override
        public WriteOperation deserializeBody(ByteBuf in) throws IOException {
            OperationType type = OperationType.get(in.readByte());
            return switch (type) {
                case ISSUE_WANT -> IssueWant.serializer.deserialize(in);
                case ISSUE_OFFER -> IssueOffer.serializer.deserialize(in);
                case CANCEL -> Cancel.serializer.deserialize(in);
                case DEPOSIT -> Deposit.serializer.deserialize(in);
                case WITHDRAWAL -> Withdrawal.serializer.deserialize(in);
            };
        }
    };
}
