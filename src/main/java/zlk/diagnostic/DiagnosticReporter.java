package zlk.diagnostic;

/**
 * 診断を回収するための追記専用の回収器
 */
public interface DiagnosticReporter {
	void report(Diagnostic diag);
}