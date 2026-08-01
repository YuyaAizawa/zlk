package zlk.diagnostic;

import zlk.common.Location;
import zlk.phase.patterncheck.PcPattern;
import zlk.util.collection.Seq;

public sealed interface Diagnostic {

	// ================== patterncheck ==================

	record IncompletePattern(
			Location location,
			Seq<PcPattern> examples
	) implements Diagnostic {}

	record RedundantPattern(
			Location overallLocation,
			Location patternLocation,
			int caseIndex
	) implements Diagnostic {}
}
