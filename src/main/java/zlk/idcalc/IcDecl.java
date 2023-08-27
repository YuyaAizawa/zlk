package zlk.idcalc;

import java.util.List;
import java.util.Optional;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcDecl(
		Id id,
		Optional<Type> anno,
		List<IcPattern> args,
		Optional<List<IcDecl>> recs,
		IcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public String name() {
		return id.canonicalName();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		if(recs.isPresent()) {
			pp.append(" [");
			pp.oneline(recs.get().stream().map(decl -> decl.id).toList(), ", ");
			pp.append("]");
		}
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

	public IcDecl norec() {
		return new IcDecl(id, anno, args, Optional.empty(), body, loc);
	}
}
