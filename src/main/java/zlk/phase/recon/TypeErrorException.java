package zlk.phase.recon;

/** Phase-local transport for a typed reconstruction failure. */
public final class TypeErrorException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final TypeError error;

	public TypeErrorException(TypeError error, Throwable cause) {
		super(error.toString(), cause);
		this.error = error;
	}

	public TypeError error() {
		return error;
	}
}