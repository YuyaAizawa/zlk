package zlk.ir.clcalc;

import java.util.function.Consumer;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.idcalc.IcExp;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface CcPattern
extends PrettyPrintable, LocationHolder {

	Type type();

	record Wildcard(Location loc) implements CcPattern {
		@Override
		public Type type() {
			throw new IllegalStateException();
		}
	}

	record Var(
			Id id,
			Type type,
			Location loc) implements CcPattern {}

	record Dector(
			IcExp.IcVarCtor ctor,
			Seq<CcPattern> args,
			Type type,
			Location loc) implements CcPattern {}

	record Record(
			Seq<Var> fields,
			Type type,
			Location loc) implements CcPattern {}

	public default void walkVars(Consumer<Id> action) {
		switch(this) {
		case Wildcard(Location _) -> {}
		case Var(Id id, Type _, Location _) -> {
			action.accept(id);
		}
		case Dector(IcExp.IcVarCtor _, Seq<CcPattern> args, Type _, Location _) -> {
			args.forEach(arg -> arg.walkVars(action));
		}
		case Record(Seq<Var> fields, Type _, Location _) ->
			fields.forEach(field -> action.accept(field.id()));
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Wildcard(Location _) -> {
			pp.append("_");
		}
		case Var(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case Dector(IcExp.IcVarCtor ctor, Seq<CcPattern> args, Type _, Location _) -> {
			pp.append(ctor);
			for(CcPattern arg: args) {
				pp.append(" ").append(arg);
			}
		}
		case Record(Seq<Var> fields, Type _, Location _) -> {
			pp.append("{");
			fields.forEachIndexed((i, field) -> pp
					.append(i == 0 ? " " : ", ")
					.append(field.id()));
			pp.append(" }");
		}
		}
	}
}
