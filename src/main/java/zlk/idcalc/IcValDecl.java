package zlk.idcalc;

import java.util.List;
import java.util.Optional;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcValDecl(
		Id id,
		Optional<Type> anno,
		List<IcPattern> args,
		Optional<List<IcValDecl>> recs,  // TODO 相互再帰するために後で追加する方式にする
		IcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public String name() {
		return id.canonicalName();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id).append(" ");
		recs.ifPresent(recList ->
				pp.withoutLineBreak(pp_ ->
						pp_.append(PrettyPrintable.from(recList))));
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

	public IcValDecl norec() {
		return new IcValDecl(id, anno, args, Optional.empty(), body, loc);
	}
}
