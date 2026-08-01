package zlk.phase.patterncheck;

import zlk.common.id.Id;
import zlk.util.collection.Seq;

sealed interface Pattern {
	enum Anything implements Pattern {
		SINGLETON;

		@Override
		public String toString() {
			return "Anything";
		}
	}
//	record Literal(Object value) implements PcPattern {}
	record Ctor(
			Id unionId,
			Id ctorId,
			Seq<Pattern> args
	) implements Pattern {}
}
