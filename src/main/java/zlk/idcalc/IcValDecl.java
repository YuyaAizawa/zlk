package zlk.idcalc;

import java.util.Optional;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcValDecl(
		Id id,
		Optional<Type> anno,
		Seq<IcPattern> args,
		IcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public String name() {
		return id.canonicalName();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		anno.ifPresent(ty -> {
			pp.append(" : ").append(ty).endl();
			pp.append(id);
		});
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" =").endl();
		pp.inc().append(body).dec();
	}
}
