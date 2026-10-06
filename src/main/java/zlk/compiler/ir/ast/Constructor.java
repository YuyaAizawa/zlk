package zlk.compiler.ir.ast;

import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Constructor(
		String name,
		Seq<AnType> args,
		Location loc
) implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
		args.forEach(ty -> pp.append(" ").append(ty));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
