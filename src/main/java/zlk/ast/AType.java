package zlk.ast;

import zlk.ast.AType.Arrow;
import zlk.ast.AType.Atom;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface AType extends PrettyPrintable, LocationHolder
permits Atom, Arrow {
	record Atom(String name, Location loc) implements AType {};
	record Arrow(AType arg, AType ret, Location loc) implements AType {};

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Atom atom -> {
			pp.append(atom.name);
		}
		case Arrow arrow -> {
			switch(arrow.arg) {
			case Atom _ -> pp.append(arrow.arg);
			case Arrow _ -> pp.append("(").append(arrow.arg).append(")");
			}
			pp.append(" -> ").append(arrow.ret);
		}
		}
	}
}
