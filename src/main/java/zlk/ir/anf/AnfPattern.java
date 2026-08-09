package zlk.ir.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

public sealed interface AnfPattern extends LocationHolder {

	Type type();

	record Wildcard(Type type, Location loc) implements AnfPattern {}

	record Var(AnfVar var, Location loc) implements AnfPattern {
		@Override
		public Type type() {
			return var.type();
		}
	}

	record Ctor(
			Id ctor,
			Seq<AnfPattern> args,
			Type type,
			Location loc
	) implements AnfPattern {}

	record Record(
			Seq<Var> fields,
			Type type,
			Location loc
	) implements AnfPattern {}
}
