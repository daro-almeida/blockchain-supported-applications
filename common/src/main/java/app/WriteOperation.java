package app;

import app.open_goods.messages.client.requests.Cancel;
import app.open_goods.messages.client.requests.IssueOffer;
import app.open_goods.messages.client.requests.IssueWant;
import app.open_goods.messages.exchange.requests.Deposit;
import app.open_goods.messages.exchange.requests.Withdrawal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

public abstract class WriteOperation extends SignedProtoMessage {

    public WriteOperation(short id) {
        super(id);
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
            out.writeShort(op.getId());
            switch (op.getId()) {
                case Deposit.MESSAGE_ID -> Deposit.serializer.serialize((Deposit) op, out);
                case Withdrawal.MESSAGE_ID -> Withdrawal.serializer.serialize((Withdrawal) op, out);
                case IssueOffer.MESSAGE_ID -> IssueOffer.serializer.serialize((IssueOffer) op, out);
                case IssueWant.MESSAGE_ID -> IssueWant.serializer.serialize((IssueWant) op, out);
                case Cancel.MESSAGE_ID -> Cancel.serializer.serialize((Cancel) op, out);
                default -> throw new RuntimeException("Unknown message type");
            }
        }

        @Override
        public WriteOperation deserializeBody(ByteBuf in) throws IOException {
            return switch (in.readShort()) {
                case Deposit.MESSAGE_ID -> Deposit.serializer.deserialize(in);
                case Withdrawal.MESSAGE_ID -> Withdrawal.serializer.deserialize(in);
                case IssueOffer.MESSAGE_ID -> IssueOffer.serializer.deserialize(in);
                case IssueWant.MESSAGE_ID -> IssueWant.serializer.deserialize(in);
                case Cancel.MESSAGE_ID -> Cancel.serializer.deserialize(in);
                default -> throw new RuntimeException("Unknown message type");
            };
        }
    };
}
