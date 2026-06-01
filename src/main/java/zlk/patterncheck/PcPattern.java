package zlk.patterncheck;

import zlk.common.id.Id;
import zlk.util.collection.Seq;

public sealed interface PcPattern {
	enum Anything implements PcPattern {
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
			Seq<PcPattern> args
	) implements PcPattern {}
}
