package zlk.ir.reuse.anf;

import java.util.function.Consumer;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

public sealed interface AnfPattern extends LocationHolder {

	Type type();

	record Wildcard(Type type, Location loc) implements AnfPattern {}

	record Var(LocalVar var, Location loc) implements AnfPattern {
		public Var {
			if(var.id().isEmpty()) {
				throw new RuntimeException("needs Id");
			}
		}

		public Id id() {
			return var.id().get();
		}
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

	public default void walkVars(Consumer<LocalVar> action) {
		switch(this) {
		case AnfPattern.Wildcard _ -> {}
		case AnfPattern.Var(LocalVar var, _) -> {
			action.accept(var);
		}
		case AnfPattern.Ctor(_, Seq<AnfPattern> args, _, _) -> {
			args.forEach(arg -> arg.walkVars(action));
		}
		case AnfPattern.Record(Seq<Var> fields, _, _) -> {
			fields.forEach(field -> action.accept(field.var));
		}
		}
	}
}
