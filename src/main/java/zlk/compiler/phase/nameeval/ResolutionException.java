package zlk.compiler.phase.nameeval;

import zlk.compiler.diagnostic.Diagnostic;

/** Expected source-resolution failure; unexpected exceptions remain internal failures. */
final class ResolutionException extends RuntimeException {
	private final Diagnostic diagnostic;

	ResolutionException(Diagnostic diagnostic) {
		this.diagnostic = diagnostic;
	}

	Diagnostic diagnostic() {
		return diagnostic;
	}
}