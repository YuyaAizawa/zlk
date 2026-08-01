package zlk.ir.idcalc;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * ADT宣言．
 * @param vars 型変数は{@link Type.Var}，レコードの列変数は空のopen {@link Type.Record}として保持する
 */
public record IcTypeDecl(
	Id id,
	Seq<Type> vars,
	Seq<IcCtor> ctors,
	Location loc
) implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("type ").append(id);
		vars.forEach(var -> pp.append(" ").append(parameterName(var)));
		pp.append(" =").inc();
		if(ctors.size() == 1) {
			pp.endl().append(ctors.head());
		} else {
			ctors.forEach(ctor -> pp.endl().append("| ").append(ctor));
		}
		pp.dec();
	}

	private static String parameterName(Type parameter) {
		if (parameter instanceof Type.Var(String name)) {
			return name;
		}
		if (parameter instanceof Type.Record(Type.Row row)
				&& row.fields().size() == 0
				&& row.isOpen()) {
			return row.extension().orElseThrow().name();
		}
		throw new IllegalStateException("invalid ADT parameter: " + parameter);
	}
}
