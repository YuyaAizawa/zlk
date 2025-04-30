package zlk.ast;

import java.util.List;

import zlk.ast.AnType.Arrow;
import zlk.ast.AnType.Type;
import zlk.ast.AnType.Unit;
import zlk.ast.AnType.Var;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface AnType extends PrettyPrintable, LocationHolder
permits Unit, Var, Type, Arrow {
	record Unit(Location loc) implements AnType {};
	record Var(String name, Location loc) implements AnType {};
	record Type(String ctor, List<AnType> args, Location loc) implements AnType {};
	record Arrow(AnType arg, AnType ret, Location loc) implements AnType {};

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Unit _ -> {
			pp.append("()");
		}
		case Var var -> {
			pp.append(var.name);
		}
		case Type(String ctor, List<AnType> args, _) -> {
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
		}
	}
}
