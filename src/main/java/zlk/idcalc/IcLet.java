package zlk.idcalc;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcLet(
		IcDecl decl,
		IcExp body,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("let").endl();
		pp.inc().append(decl).dec().endl();
		pp.append("in");
		if(IcExp.isLet(body)) {
			pp.append(" ").append(body);
		} else {
			pp.endl();
			pp.inc().append(body).dec();
		}
	}
}
