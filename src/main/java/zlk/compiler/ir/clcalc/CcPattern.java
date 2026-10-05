package zlk.compiler.ir.clcalc;

import java.util.function.Consumer;

import zlk.compiler.id.Id;
import zlk.compiler.ir.idcalc.IcExp;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface CcPattern
extends PrettyPrintable, LocationHolder {

	Type type();

	record Wildcard(Type type, Location loc) implements CcPattern {}

	record Var(
			Id id,
			Type type,
			Location loc) implements CcPattern {}

	record Ctor(
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
		case Wildcard(Type _, Location _) -> {}
		case Var(Id id, Type _, Location _) -> {
			action.accept(id);
		}
		case Ctor(IcExp.IcVarCtor _, Seq<CcPattern> args, Type _, Location _) -> {
			args.forEach(arg -> arg.walkVars(action));
		}
		case Record(Seq<Var> fields, Type _, Location _) ->
			fields.forEach(field -> action.accept(field.id()));
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Wildcard(Type _, Location _) -> {
			pp.append("_");
		}
		case Var(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case Ctor(IcExp.IcVarCtor ctor, Seq<CcPattern> args, Type _, Location _) -> {
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
