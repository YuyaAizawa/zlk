package zlk.compiler.phase.recon;

import zlk.compiler.id.Id;

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

	public sealed interface Detail {
		Detail NONE = new None();

		record None() implements Detail {}
		record ConstructorFamily(Id left, Id right) implements Detail {}
	}

	private final Reason reason;
	private final Detail detail;

	public Mismatch() {
		this(Reason.INCOMPATIBLE, Detail.NONE, null);
	}

	public Mismatch(String message) {
		this(Reason.INCOMPATIBLE, Detail.NONE, message);
	}

	public Mismatch(Detail detail) {
		this(Reason.INCOMPATIBLE, detail, null);
	}

	public Mismatch(Reason reason) {
		this(reason, Detail.NONE, null);
	}

	public Mismatch(Reason reason, String message) {
		this(reason, Detail.NONE, message);
	}

	private Mismatch(Reason reason, Detail detail, String message) {
		super(message);
		this.reason = reason;
		this.detail = detail;
	}

	public Reason reason() {
		return reason;
	}

	public Detail detail() {
		return detail;
	}
}
