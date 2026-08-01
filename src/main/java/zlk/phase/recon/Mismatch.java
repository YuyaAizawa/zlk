package zlk.phase.recon;

/**
 * 単一化できない二つの推論型が与えられたことを表す．
 */
public final class Mismatch extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public Mismatch() {
		super();
	}

	public Mismatch(String message) {
		super(message);
	}
}
