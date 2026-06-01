package zlk.patterncheck;

import zlk.common.Location;
import zlk.util.collection.Seq;

public sealed interface PcError {
	record Incomplete(
			Location location,
			Seq<PcPattern> examples
	) implements PcError {}

	record Redundant(
			Location overallLocation,
			Location patternLocation,
			int caseIndex
	) implements PcError {}
}
