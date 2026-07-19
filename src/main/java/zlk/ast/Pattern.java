package zlk.ast;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.parser.Token;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Pattern extends PrettyPrintable, LocationHolder {
	record Wildcard(Location loc) implements Pattern {}
	record Var(String name, Location loc) implements Pattern {}
	record Ctor(String name, Seq<Pattern> args, Location loc) implements Pattern {}
	record RecordField(String name, Pattern pattern, boolean shorthand, Location loc)
			implements LocationHolder {}
	record Record(Seq<RecordField> fields, Location loc) implements Pattern {}
	record Err(Token token, Location loc) implements Pattern {}

	default Pattern updateLoc(Location loc) {
		return switch(this) {
		case Wildcard(Location _) -> new Wildcard(loc);
		case Var(String name, Location _) -> new Var(name, loc);
		case Ctor(String name, Seq<Pattern> args, Location _) -> new Ctor(name, args, loc);
		case Record(Seq<RecordField> fields, Location _) -> new Record(fields, loc);
		case Err (Token token, Location _) -> new Err(token, loc);
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Wildcard(Location _) -> {
		    pp.append("_");
		}
		case Var(String name, _) -> {
			pp.append(name);
		}
		case Ctor(String name, Seq<Pattern> args, _) -> {
			pp.append(name);
			for(Pattern arg : args) {
				pp.append(" ").append(arg);
			}
		}
		case Record(Seq<RecordField> fields, _) -> {
			pp.append("{");
			fields.forEachIndexed((i, field) -> {
				pp.append(i == 0 ? " " : ", ").append(field.name());
				if(!field.shorthand()) pp.append(" = ").append(field.pattern());
			});
			if(!fields.isEmpty()) pp.append(" ");
			pp.append("}");
		}
		case Err(Token token, Location _) -> {
			pp.append(token);
		}
		}
	}
}
