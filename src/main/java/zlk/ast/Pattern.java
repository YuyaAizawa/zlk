package zlk.ast;

import zlk.ast.Pattern.Ctor;
import zlk.ast.Pattern.Var;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Pattern extends PrettyPrintable, LocationHolder
permits Var, Ctor {
	record Var(String name, Location loc) implements Pattern {}
	record Ctor(String name, Seq<Pattern> args, Location loc) implements Pattern {}

	default Pattern updateLoc(Location loc) {
		return switch(this) {
		case Var(String name, Location _) -> new Var(name, loc);
		case Ctor(String name, Seq<Pattern> args, Location _) -> new Ctor(name, args, loc);
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Var(String name, _) -> {
			pp.append(name);
		}
		case Ctor(String name, Seq<Pattern> args, _) -> {
			pp.append(name);
			for(Pattern arg : args) {
				pp.append(" ").append(arg);
			}
		}
		}
	}
}
