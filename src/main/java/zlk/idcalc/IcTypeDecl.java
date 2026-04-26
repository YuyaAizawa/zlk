package zlk.idcalc;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcTypeDecl(
	Id id,
	Seq<Type> vars,
	Seq<IcCtor> ctors,
	Location loc
) implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("type ").append(id);
		vars.forEach(var -> pp.append(" ").append(var));
		pp.append(" =").inc();
		if(ctors.size() == 1) {
			pp.endl().append(ctors.head());
		} else {
			ctors.forEach(ctor -> pp.endl().append("| ").append(ctor));
		}
		pp.dec();
	}
}
