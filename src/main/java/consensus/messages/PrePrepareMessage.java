package consensus.messages;

import java.io.*;
import java.security.*;
import java.util.Arrays;
import java.util.Objects;

import consensus.requests.ProposeRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.SignaturesHelper;
import utils.Utils;

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

	public boolean checkSignature(PublicKey key) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
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
			Utils.byteArraySerializer.serialize(prePrepareMessage.digest, byteBuf);
			ProposeRequest.serializer.serialize(prePrepareMessage.request, byteBuf);

			if (prePrepareMessage.signature != null) {
				Utils.byteArraySerializer.serialize(prePrepareMessage.signature, byteBuf);
			} else {
				throw new RuntimeException("PrePrepareMessage signature is null");
			}
		}

		@Override
		public PrePrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
			int viewN = byteBuf.readInt();
			int seq = byteBuf.readInt();
			byte[] digest = Utils.byteArraySerializer.deserialize(byteBuf);
			ProposeRequest request = ProposeRequest.serializer.deserialize(byteBuf);
			byte[] signature = Utils.byteArraySerializer.deserialize(byteBuf);

			return new PrePrepareMessage(viewN, seq, digest, request, signature);
		}
	};

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PrePrepareMessage that = (PrePrepareMessage) o;
		return getViewN() == that.getViewN() && getSeq() == that.getSeq() && Arrays.equals(getDigest(), that.getDigest());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(getViewN(), getSeq());
		result = 31 * result + Arrays.hashCode(getDigest());
		return result;
	}
}
