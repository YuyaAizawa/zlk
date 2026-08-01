package zlk.diagnostic;

import zlk.phase.patterncheck.PcError;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public sealed interface Diagnostic {
	record InvalidPattern(PcError e) implements Diagnostic {}  // TODO: もう少し分類しろ

	/**
	 * 診断を回収するための追記専用の回収器
	 */
	static final class Sink {
		private final SeqBuffer<Diagnostic> impl = new SeqBuffer<>();

		public void report(Diagnostic diag) {
			impl.add(diag);
		}

		public Seq<Diagnostic> toSeq() {
			return impl.toSeq();
		}
	}
}
