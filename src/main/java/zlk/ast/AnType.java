package zlk.ast;

import zlk.ast.AnType.Arrow;
import zlk.ast.AnType.Record;
import zlk.ast.AnType.Type;
import zlk.ast.AnType.Unit;
import zlk.ast.AnType.Var;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface AnType extends PrettyPrintable, LocationHolder
permits Unit, Var, Type, Arrow, Record {
	record Unit(Location loc) implements AnType {};
	record Var(String name, Location loc) implements AnType {};
	record Type(String ctor, Seq<AnType> args, Location loc) implements AnType {};
	record Arrow(AnType arg, AnType ret, Location loc) implements AnType {};
	record RecordField(String name, AnType type, Location loc) implements LocationHolder, PrettyPrintable {
		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append(name).append(" : ").append(type);
		}
	};
	record Record(Seq<RecordField> fields, Location loc) implements AnType {};

	default AnType updateLoc(Location loc) {
		return switch(this) {
		case Unit(Location _) -> new Unit(loc);
		case Var(String name, Location _) -> new Var(name, loc);
		case Type(String ctor, Seq<AnType> args, Location _) -> new Type(ctor, args, loc);
		case Arrow(AnType arg, AnType ret, Location _) -> new Arrow(arg, ret, loc);
		case Record(Seq<RecordField> fields, Location _) -> new Record(fields, loc);
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Unit _ -> {
			pp.append("()");
		}
		case Var var -> {
			pp.append(var.name);
		}
		case Type(String ctor, Seq<AnType> args, _) -> {
			pp.append(ctor);
			args.forEach(arg -> {
				pp.append(" ");
				if(arg instanceof Arrow || arg instanceof Type type && !type.args.isEmpty()) {
					pp.append("(").append(arg).append(")");
				} else {
					pp.append(arg);
				}
			});
		}
		case Arrow(AnType arg, AnType ret, _) -> {
			if(arg instanceof Arrow) {
				pp.append("(").append(arg).append(")");
			} else {
				pp.append(arg);
			}
			pp.append(" -> ").append(ret);
		}
		case Record(Seq<RecordField> fields, _) -> {
			if(fields.isEmpty()) {
				pp.append("{}");
			} else {
				pp.append("{ ").append(PrettyPrintable.join(fields, ", ")).append(" }");
			}
		}
		}
	}
}
