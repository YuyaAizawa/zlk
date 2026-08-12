package zlk.ir.reuse.own;

import java.util.function.Consumer;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

public sealed interface OwnPattern extends LocationHolder {
	Type type();

	record Wildcard(Type type, Location loc) implements OwnPattern {}

	/**
	 * patternが導入するbinder．ownershipはこのbinderが所有参照を持つかを表す．
	 */
	record Var(
			LocalVar var,
			Ownership ownership,
			Location loc
	) implements OwnPattern {
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
			Seq<OwnPattern> args,
			Type type,
			Location loc
	) implements OwnPattern {}

	record Record(
			Seq<Var> fields,
			Type type,
			Location loc
	) implements OwnPattern {}

	public default void walkVars(Consumer<LocalVar> action) {
		switch(this) {
		case OwnPattern.Wildcard _ -> {}
		case OwnPattern.Var(LocalVar var, _, _) -> {
			action.accept(var);
		}
		case OwnPattern.Ctor(_, Seq<OwnPattern> args, _, _) -> {
			args.forEach(arg -> arg.walkVars(action));
		}
		case OwnPattern.Record(Seq<Var> fields, _, _) -> {
			fields.forEach(field -> action.accept(field.var));
		}
		}
	}
}
