package zlk.idcalc;

import java.util.List;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcTypeDecl(
	Id id,
	List<IcCtor> ctors,
	Location loc
) implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("type ").append(id).endl().inc();
		pp.append("= ").append(ctors.get(0));
		ctors.subList(1, ctors.size())
				.forEach(ctor -> pp.endl().append("| ").append(ctor));
		pp.dec();
	}
}
