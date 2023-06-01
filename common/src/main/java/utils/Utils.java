package utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Enumeration;
import java.util.UUID;

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
        public void serialize(byte[] bytes, ByteBuf byteBuf) {
            if (bytes == null) {
                byteBuf.writeInt(0);
                return;
            }
            byteBuf.writeInt(bytes.length);
            byteBuf.writeBytes(bytes);
        }

        @Override
        public byte[] deserialize(ByteBuf byteBuf) {
            var len = byteBuf.readInt();
            if (len == 0) return null;
            var bytes = new byte[len];
            byteBuf.readBytes(bytes);
            return bytes;
        }
    };

    public static ISerializer<UUID> uuidSerializer = new ISerializer<UUID>() {
        @Override
        public void serialize(UUID uuid, ByteBuf byteBuf) {
            byteBuf.writeLong(uuid.getMostSignificantBits());
            byteBuf.writeLong(uuid.getLeastSignificantBits());
        }

        @Override
        public UUID deserialize(ByteBuf byteBuf) {
            var mostSigBits = byteBuf.readLong();
            var leastSigBits = byteBuf.readLong();
            return new UUID(mostSigBits, leastSigBits);
        }
    };

    public static ISerializer<PublicKey> rsaPublicKeySerializer = new ISerializer<>() {
        @Override
        public void serialize(PublicKey publicKey, ByteBuf byteBuf) throws IOException {
            byte[] encoded = publicKey.getEncoded();
            byteBuf.writeInt(encoded.length);
            byteBuf.writeBytes(encoded);
        }

        @Override
        public PublicKey deserialize(ByteBuf byteBuf) throws IOException {
            int length = byteBuf.readInt();
            byte[] publicKeyBytes = new byte[length];
            byteBuf.readBytes(publicKeyBytes);
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                // create an instance of the X509EncodedKeySpec class using the encoded bytes of the public key
                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                // create an instance of the PublicKey interface using the KeyFactory and the X509EncodedKeySpec
                return keyFactory.generatePublic(publicKeySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public static String bytesToHex(byte[] bytes) {
        var hexString = new StringBuilder();
        for (var b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static byte[] hexToBytes(String hex) {
        var bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    public static String getAddress(String inter) throws SocketException {
        NetworkInterface byName = NetworkInterface.getByName(inter);
        if (byName == null) {
            throw new RuntimeException("No interface with name " + inter);
        }
        Enumeration<InetAddress> addresses = byName.getInetAddresses();
        InetAddress currentAddress;
        while (addresses.hasMoreElements()) {
            currentAddress = addresses.nextElement();
            if (currentAddress instanceof Inet4Address)
                return currentAddress.getHostAddress();
        }
        throw new RuntimeException("No IPv4 address found for interface " + inter);
    }

}
