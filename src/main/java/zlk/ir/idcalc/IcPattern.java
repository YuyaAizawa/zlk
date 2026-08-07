package zlk.ir.idcalc;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.id.Id;
import zlk.ir.idcalc.IcPattern.Dector;
import zlk.ir.idcalc.IcPattern.Record;
import zlk.ir.idcalc.IcPattern.Var;
import zlk.ir.idcalc.IcPattern.Wildcard;
import zlk.util.collection.Accumulator;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcPattern
extends ExpOrPattern, PrettyPrintable, LocationHolder
permits Wildcard, Var, Dector, Record {

	record Wildcard(Location loc) implements IcPattern {}

	record Var(
			Id id,
			Location loc) implements IcPattern {}

	record Dector(
			IcExp.IcVarCtor ctor,
			Seq<IcPattern> args,
			Location loc) implements IcPattern {}

	record RecordField(String name, IcPattern pattern, Location loc) implements LocationHolder {}
	record Record(Seq<RecordField> fields, Location loc) implements IcPattern {}

	public default void accumulateVars(Accumulator<Id> known) {
		switch(this) {
		case Wildcard(Location _) -> {}
		case Var(Id id, Location _) -> {
			known.add(id);
		}
		case Dector(IcExp.IcVarCtor _, Seq<IcPattern> args, Location _) -> {
			args.forEach(arg -> arg.accumulateVars(known));
		}
		case Record(Seq<RecordField> fields, Location _) ->
			fields.forEach(field -> field.pattern().accumulateVars(known));
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Wildcard(Location _) -> {
			pp.append("_");
		}
		case Var(Id id, Location _) -> {
			pp.append(id);
		}
		case Dector(IcExp.IcVarCtor ctor, Seq<IcPattern> args, Location _) -> {
			pp.append(ctor);
			for(IcPattern arg: args) {
				pp.append(" ").append(arg);
			}
		}
		case Record(Seq<RecordField> fields, Location _) -> {
			pp.append("{");
			fields.forEachIndexed((i, field) -> pp
					.append(i == 0 ? " " : ", ")
					.append(field.name()).append(" = ").append(field.pattern()));
			if(!fields.isEmpty()) pp.append(" ");
			pp.append("}");
		}
		}
	}
}
