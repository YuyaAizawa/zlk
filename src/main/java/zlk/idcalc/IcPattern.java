package zlk.idcalc;

import java.util.Set;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.idcalc.IcPattern.Dector;
import zlk.idcalc.IcPattern.Var;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcPattern extends PrettyPrintable, LocationHolder, ExpOrPattern
permits Var, Dector {

	record Var(
			Id id,
			Location loc) implements IcPattern {}

	record Dector(
			IcExp.IcVarCtor ctor,
			Seq<Arg> args,
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
		case Dector(IcExp.IcVarCtor ctor, Seq<Arg> _, Location _) -> {
			yield ctor.id();
		}
		};
	}

	public default void accumulateVars(Set<Id> known) {
		switch(this) {
		case Var(Id id, Location _) -> {
			known.add(id);
		}
		case Dector(IcExp.IcVarCtor _, Seq<Arg> args, Location _) -> {
			args.forEach(arg -> arg.pattern().accumulateVars(known));
		}
		}
	}
	public default void accumulateVars(SeqBuffer<Id> known) {
		switch(this) {
		case Var(Id id, Location _) -> {
			known.add(id);
		}
		case Dector(IcExp.IcVarCtor _, Seq<Arg> args, Location _) -> {
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
		case Dector(IcExp.IcVarCtor ctor, Seq<Arg> args, Location _) -> {
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
