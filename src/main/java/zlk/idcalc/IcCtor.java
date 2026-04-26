package zlk.idcalc;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcCtor(
		Id id,
		Seq<Type> args,
		Location loc
) implements PrettyPrintable, LocationHolder {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		args.forEach(arg -> pp.append(" ").append(arg));
	}
}
