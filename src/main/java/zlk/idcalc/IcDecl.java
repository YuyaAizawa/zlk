package zlk.idcalc;

import zlk.common.Id;
import zlk.common.IdList;
import zlk.common.Type;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcDecl(
		Id id,
		IdList args,
		Type type,
		IcExp body)
implements PrettyPrintable {

	public String name() {
		return id.name();
	}

	public Type returnTy() {
		return type.arg(args.size());
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id).append(" ").append(args);
		pp.append(" : ").append(type).append(" =").endl();
		pp.inc().append(body).dec();
	}
}
