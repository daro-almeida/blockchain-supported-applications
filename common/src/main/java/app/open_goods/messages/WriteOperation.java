package app.open_goods.messages;

import app.open_goods.messages.client.requests.Cancel;
import app.open_goods.messages.client.requests.IssueOffer;
import app.open_goods.messages.client.requests.IssueWant;
import app.open_goods.messages.exchange.requests.Deposit;
import app.open_goods.messages.exchange.requests.Withdrawal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

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

    public abstract ISerializer<? extends WriteOperation> getInnerSerializer();

    public final static SignedMessageSerializer<WriteOperation> serializer = new SignedMessageSerializer<>() {
        @Override
        public void serializeBody(WriteOperation op, ByteBuf out) throws IOException {
//            out.writeByte(op.getType().ordinal());
//            switch (op.getType()) {
//                case ISSUE_WANT -> IssueWant.serializer.serialize((IssueWant) op, out);
//                case ISSUE_OFFER -> IssueOffer.serializer.serialize((IssueOffer) op, out);
//                case CANCEL -> Cancel.serializer.serialize((Cancel) op, out);
//                case DEPOSIT -> Deposit.serializer.serialize((Deposit) op, out);
//                case WITHDRAWAL -> Withdrawal.serializer.serialize((Withdrawal) op, out);
//            }
        }

        @Override
        public WriteOperation deserializeBody(ByteBuf in) throws IOException {
//            OperationType type = OperationType.get(in.readByte());
//            return switch (type) {
//                case ISSUE_WANT -> IssueWant.serializer.deserialize(in);
//                case ISSUE_OFFER -> IssueOffer.serializer.deserialize(in);
//                case CANCEL -> Cancel.serializer.deserialize(in);
//                case DEPOSIT -> Deposit.serializer.deserialize(in);
//                case WITHDRAWAL -> Withdrawal.serializer.deserialize(in);
//            };
            return null;
        }
    };
}
