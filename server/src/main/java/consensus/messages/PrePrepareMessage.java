package consensus.messages;

import java.io.*;
import java.security.*;
import java.util.Arrays;
import java.util.Objects;

import consensus.requests.ProposeRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
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
		var bb = Unpooled.buffer(8 + (digest != null ? digest.length : 0));
		bb.writeInt(viewNumber);
		bb.writeInt(seq);
		if (digest != null)
			bb.writeBytes(digest);
		return ByteBufUtil.getBytes(bb);
	}

	public static final ISerializer<PrePrepareMessage> serializer = new ISerializer<>() {

		@Override
		public void serialize(PrePrepareMessage prePrepareMessage, ByteBuf byteBuf) throws IOException {
			// if signature is null throw, digest and request can be null, if digest is null request has to be null
			if (prePrepareMessage.signature == null)
				throw new IOException("PrePrepareMessage signature is null");

			byteBuf.writeInt(prePrepareMessage.viewNumber);
			byteBuf.writeInt(prePrepareMessage.seq);
			Utils.byteArraySerializer.serialize(prePrepareMessage.signature, byteBuf);

			Utils.byteArraySerializer.serialize(prePrepareMessage.digest, byteBuf);
			if (prePrepareMessage.request != null) {
				byteBuf.writeByte(1);
				ProposeRequest.serializer.serialize(prePrepareMessage.request, byteBuf);
			}
			else
				byteBuf.writeByte(0);
		}

		@Override
		public PrePrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
			int viewNumber = byteBuf.readInt();
			int seq = byteBuf.readInt();
			byte[] signature = Utils.byteArraySerializer.deserialize(byteBuf);

			byte[] digest = Utils.byteArraySerializer.deserialize(byteBuf);
			byte hasRequest = byteBuf.readByte();
			if (hasRequest == 1) {
				ProposeRequest request = ProposeRequest.serializer.deserialize(byteBuf);
				return new PrePrepareMessage(viewNumber, seq, digest, request, signature);
			}
			else
				return new PrePrepareMessage(viewNumber, seq, digest, null, signature);
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
