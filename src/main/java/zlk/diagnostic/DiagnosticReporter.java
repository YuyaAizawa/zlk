package zlk.diagnostic;

/**
 * 診断を報告するための送信先．
 */
public interface DiagnosticReporter {
	void report(Diagnostic diag);

	/**
	 * 指定した種類の診断だけを拒否し，それ以外をこのreporterへ転送する．
	 *
	 * @param type 拒否する診断の種類
	 * @return 指定した種類を除外するreporter
	 */
	default DiagnosticReporter excluding(Class<? extends Diagnostic> type) {
		return diag -> {
			if(!type.isInstance(diag)) {
				report(diag);
			}
		};
	}
}