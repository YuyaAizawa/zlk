package zlk.clcalc;

import java.util.List;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcCtor(
		Id id,
		List<Type> args,
		Location loc
) implements PrettyPrintable, LocationHolder {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		args.forEach(arg -> pp.append(" ").append(arg));
	}
}
