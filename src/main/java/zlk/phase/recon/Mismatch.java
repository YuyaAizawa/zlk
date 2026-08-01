package zlk.phase.recon;

/**
 * 単一化できない二つの推論型が与えられたことを表す．
 */
public final class Mismatch extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public enum Reason {
		INCOMPATIBLE,
		KIND,
		ROW_LACKS,
		RECURSIVE_ROW
	}

	private final Reason reason;

	public Mismatch() {
		this(Reason.INCOMPATIBLE, null);
	}

	public Mismatch(String message) {
		this(Reason.INCOMPATIBLE, message);
	}

	public Mismatch(Reason reason) {
		this(reason, null);
	}

	public Mismatch(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
