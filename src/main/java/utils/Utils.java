package utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Utils {

    public static ISerializer<String> stringSerializer = new ISerializer<>() {
        @Override
        public void serialize(String s, ByteBuf byteBuf) {
            var len = s.getBytes().length;
            byteBuf.writeInt(len);
            ByteBufUtil.reserveAndWriteUtf8(byteBuf, s, len);
        }

        @Override
        public String deserialize(ByteBuf byteBuf) {
            var len = byteBuf.readInt();
            return byteBuf.readCharSequence(len, StandardCharsets.UTF_8).toString();
        }
    };

    public static ISerializer<byte[]> byteArraySerializer = new ISerializer<>() {
        @Override
        public void serialize(byte[] bytes, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(bytes.length);
            byteBuf.writeBytes(bytes);
        }

        @Override
        public byte[] deserialize(ByteBuf byteBuf) throws IOException {
            var len = byteBuf.readInt();
            var bytes = new byte[len];
            byteBuf.readBytes(bytes);
            return bytes;
        }
    };

}
