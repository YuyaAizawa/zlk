package zlk.compiler.diagnostic;

/**
 * 診断を報告するための送信先．
 */
@FunctionalInterface
public interface DiagnosticReporter {
	void report(Diagnostic diag);
}
