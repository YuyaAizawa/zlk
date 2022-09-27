package zlk.idcalc;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcDecl(
		Id id,
		IdList args,
		Type type,
		IcExp body,
		Location loc)
implements PrettyPrintable {

	public String name() {
		return id.canonicalName();
	}

	public Type returnTy() {
		return type.apply(args.size());
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id).append(" ").append(args);
		pp.append(" : ").append(type).append(" =").endl();
		pp.inc().append(body).dec();
	}
}
