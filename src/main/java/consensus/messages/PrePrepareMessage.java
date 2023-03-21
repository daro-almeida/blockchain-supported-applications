package consensus.messages;

import java.io.*;
import java.security.*;

import consensus.requests.ProposeRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.SignaturesHelper;

public class PrePrepareMessage extends ProtoMessage {

	public final static short MESSAGE_ID = 101;

	private final int viewN, seq;
	private final byte[] digest;
	private final ProposeRequest request;
	private byte[] signature;

	public PrePrepareMessage(int viewN, int seq, byte[] digest, ProposeRequest request) {
		super(PrePrepareMessage.MESSAGE_ID);

		this.viewN = viewN;
		this.seq = seq;
		this.digest = digest;
		this.request = request;
	}

	private PrePrepareMessage(int viewN, int seq, byte[] digest, ProposeRequest request, byte[] signature) {
		this(viewN, seq, digest, request);

		this.signature = signature;
	}

	public int getViewN() {
		return viewN;
	}

	public int getSeq() {
		return seq;
	}

	public byte[] getDigest() {
		return digest;
	}

	public ProposeRequest getRequest() {
		return request;
	}

	public void signMessage(PrivateKey key) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		var content = getSignatureContentBytes();

		signature = SignaturesHelper.generateSignature(content, key);
	}

	public boolean verifySignature(PublicKey key) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
		var content = getSignatureContentBytes();

		return SignaturesHelper.checkSignature(content, signature, key);
	}

	private byte[] getSignatureContentBytes() {
		var byteArrayOutputStream = new ByteArrayOutputStream();
		var outputStream = new DataOutputStream(byteArrayOutputStream);

		try {
			outputStream.writeInt(viewN);
			outputStream.writeInt(seq);
			byteArrayOutputStream.write(digest);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return byteArrayOutputStream.toByteArray();
	}

	public static final ISerializer<PrePrepareMessage> serializer = new ISerializer<>() {

		@Override
		public void serialize(PrePrepareMessage prePrepareMessage, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(prePrepareMessage.viewN);
			byteBuf.writeInt(prePrepareMessage.seq);
			byteBuf.writeInt(prePrepareMessage.digest.length);
			byteBuf.writeBytes(prePrepareMessage.digest);
			ProposeRequest.serializer.serialize(prePrepareMessage.request, byteBuf);

			if (prePrepareMessage.signature != null) {
				byteBuf.writeInt(prePrepareMessage.signature.length);
				byteBuf.writeBytes(prePrepareMessage.signature);
			} else {
				throw new RuntimeException("PrePrepareMessage signature is null");
			}
		}

		@Override
		public PrePrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
			int viewN = byteBuf.readInt();
			int seq = byteBuf.readInt();
			byte[] digest = new byte[byteBuf.readInt()];
			byteBuf.readBytes(digest);
			ProposeRequest request = ProposeRequest.serializer.deserialize(byteBuf);

			byte[] signature = new byte[byteBuf.readInt()];
			byteBuf.readBytes(signature);

			return new PrePrepareMessage(viewN, seq, digest, request, signature);
		}
	};
}
