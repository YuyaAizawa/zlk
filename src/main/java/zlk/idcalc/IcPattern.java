package zlk.idcalc;

import java.util.List;
import java.util.Set;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.idcalc.IcPattern.Ctor;
import zlk.idcalc.IcPattern.Var;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcPattern extends PrettyPrintable, LocationHolder
permits Var, Ctor {

	record Var(
			Id id,
			Location loc) implements IcPattern {}

	record Ctor(
			IcExp.IcVarCtor ctor,
			List<Arg> args,
			Location loc) implements IcPattern {

		@Override
		public Id headId() {
			return ctor.id();
		}
	}

	public default Id headId() {
		return switch(this) {
		case Var(Id id, Location _) -> {
			yield id;
		}
		case Ctor(IcExp.IcVarCtor ctor, List<Arg> _, Location _) -> {
			yield ctor.id();
		}
		};
	}

	public default void accumulateVars(Set<Id> known) {
		switch(this) {
		case Var(Id id, Location _) -> {
			known.add(id);
		}
		case Ctor(IcExp.IcVarCtor _, List<Arg> args, Location _) -> {
			args.forEach(arg -> arg.pattern().accumulateVars(known));
		}
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Var(Id id, Location _) -> {
			pp.append(id);
		}
		case Ctor(IcExp.IcVarCtor ctor, List<Arg> args, Location _) -> {
			pp.append(ctor);
			for(Arg arg: args) {
				pp.append(" ").append(arg);
			}
		}
		}
	}

	record Arg(
			IcPattern pattern,
			Type type // for cache
	) implements PrettyPrintable {

		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append(pattern);
		}
	}
}

