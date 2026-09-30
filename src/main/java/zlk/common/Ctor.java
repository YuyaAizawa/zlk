package zlk.common;

import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Ctor(
		Id id,
		Seq<Type> args,
		Location loc
) implements PrettyPrintable, LocationHolder {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		args.forEach(arg -> {
			pp.append(" ");
			if(needsParen(arg)) {
				pp.append("(").append(arg).append(")");
			} else {
				pp.append(arg);
			}
		});
	}
	private static boolean needsParen(Type type) {
		return switch(type) {
		case Type.CtorApp(Id _, Seq<Type> args) -> !args.isEmpty();
		case Type.Arrow _ -> true;
		case Type.Var _ -> false;
		case Type.Record _ -> false;
		};
	}
}
