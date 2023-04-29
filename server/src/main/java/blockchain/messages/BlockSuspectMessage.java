package blockchain.messages;

import blockchain.Block;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

public class BlockSuspectMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 204;

    private final Block block;
    private final int nodeId;

    public BlockSuspectMessage(Block block, int nodeId) {
        super(BlockSuspectMessage.MESSAGE_ID);

        this.block = block;
        this.nodeId = nodeId;
    }

    public Block getBlock() {
        return block;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<BlockSuspectMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(BlockSuspectMessage protoMessage, ByteBuf out) throws IOException {
            Block.serializer.serialize(protoMessage.block, out);
            out.writeInt(protoMessage.nodeId);

        }

        @Override
        public BlockSuspectMessage deserializeBody(ByteBuf in) throws IOException {
            Block block = Block.serializer.deserialize(in);
            int nodeId = in.readInt();
            return new BlockSuspectMessage(block, nodeId);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }
}
