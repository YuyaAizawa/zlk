package zlk.idcalc;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcFunDecl(
		Id id,
		Optional<Type> anno,
		List<IcPattern> args,
		Optional<List<IcFunDecl>> recs,
		IcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public String name() {
		return id.canonicalName();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id).append(" ");
		recs.ifPresent(recList -> {
			Iterator<Id> idIter = recList.stream().map(decl -> decl.id).iterator();
			PrettyPrintable elmStyleIdList = PrettyPrintable.toElmListStyle(idIter);
			pp.withoutLineBreak(
					pp_ -> pp_.append(elmStyleIdList)
			);
		});
		anno.ifPresent(ty -> {
			pp.append(": ").append(ty).endl();
			pp.append(id);
		});
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" =").endl();
		pp.inc().append(body).dec();
	}

	public IcFunDecl norec() {
		return new IcFunDecl(id, anno, args, Optional.empty(), body, loc);
	}
}
