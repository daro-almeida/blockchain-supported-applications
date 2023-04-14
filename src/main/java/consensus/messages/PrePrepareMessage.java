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

	private final int viewNumber;
	private final int seq;
	private final byte[] digest;
	private ProposeRequest request;
	private byte[] signature;

	public PrePrepareMessage(int viewNumber, int seq, byte[] digest, ProposeRequest request) {
		super(PrePrepareMessage.MESSAGE_ID);

		this.viewNumber = viewNumber;
		this.seq = seq;
		this.digest = digest;
		this.request = request;
	}

	public PrePrepareMessage(int viewNumber, int seq, byte[] digest) {
		this(viewNumber, seq, digest, null);
	}

	public PrePrepareMessage(int viewNumber, int seq) {
		this(viewNumber, seq, null, null);
	}

	private PrePrepareMessage(int viewNumber, int seq, byte[] digest, ProposeRequest request, byte[] signature) {
		this(viewNumber, seq, digest, request);

		this.signature = signature;
	}

	public int getViewNumber() {
		return viewNumber;
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

	public void setRequest(ProposeRequest request) {
		this.request = request;
	}

	public PrePrepareMessage nullRequestPrePrepare() {
		return new PrePrepareMessage(viewNumber, seq, digest, null, signature);
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
			outputStream.writeInt(viewNumber);
			outputStream.writeInt(seq);
			if (digest != null)
				byteArrayOutputStream.write(digest);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return byteArrayOutputStream.toByteArray();
	}

	public static final ISerializer<PrePrepareMessage> serializer = new ISerializer<>() {

		@Override
		public void serialize(PrePrepareMessage prePrepareMessage, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(prePrepareMessage.viewNumber);
			byteBuf.writeInt(prePrepareMessage.seq);
			if (prePrepareMessage.digest != null)
				Utils.byteArraySerializer.serialize(prePrepareMessage.digest, byteBuf);

			if (prePrepareMessage.signature != null) {
				Utils.byteArraySerializer.serialize(prePrepareMessage.signature, byteBuf);
			} else {
				throw new RuntimeException("PrePrepareMessage signature is null");
			}

			if (prePrepareMessage.request != null)
				ProposeRequest.serializer.serialize(prePrepareMessage.request, byteBuf);
		}

		@Override
		public PrePrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
			int viewN = byteBuf.readInt();
			int seq = byteBuf.readInt();
			byte[] digest = Utils.byteArraySerializer.deserialize(byteBuf);
			byte[] signature = Utils.byteArraySerializer.deserialize(byteBuf);

			if (byteBuf.readableBytes() == 0)
				return new PrePrepareMessage(viewN, seq, digest, null, signature);

			ProposeRequest request = ProposeRequest.serializer.deserialize(byteBuf);
			return new PrePrepareMessage(viewN, seq, digest, request, signature);
		}
	};

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PrePrepareMessage that = (PrePrepareMessage) o;
		return getViewNumber() == that.getViewNumber() && getSeq() == that.getSeq() && Arrays.equals(getDigest(), that.getDigest());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(getViewNumber(), getSeq());
		result = 31 * result + Arrays.hashCode(getDigest());
		return result;
	}
}
