package zlk.ast;

import java.util.List;

import zlk.ast.Pattern.Ctor;
import zlk.ast.Pattern.Var;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Pattern extends PrettyPrintable, LocationHolder
permits Var, Ctor {
	record Var(String name, Location loc) implements Pattern {}
	record Ctor(String name, List<Pattern> args, Location loc) implements Pattern {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Var(String name, _) -> {
			pp.append(name);
		}
		case Ctor(String name, List<Pattern> args, _) -> {
			pp.append(name);
			for(Pattern arg : args) {
				pp.append(" ").append(arg);
			}
		}
		}
	}
}
