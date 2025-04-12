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
		pp.append("type ").append(id).append(" =").inc();
		if(ctors.size() == 1) {
			pp.endl().append(ctors.get(0));
		} else {
			ctors.forEach(ctor -> pp.endl().append("| ").append(ctor));
		}
		pp.dec();
	}
}
