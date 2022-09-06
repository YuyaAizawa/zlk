package zlk.idcalc;

import java.util.List;

import zlk.common.Type;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcDecl(
		IdInfo id,
		List<IdInfo> args,
		Type type,
		IcExp body)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id.name()).append("#");
		id.appendId(pp);

		args.forEach(arg -> {
			pp.append(" ");
			pp.append(arg.name()).append("#");
			arg.appendId(pp);
		});

		pp.append(" : ").append(type).append(" =").endl();
		pp.inc().append(body).dec();
	}
}
