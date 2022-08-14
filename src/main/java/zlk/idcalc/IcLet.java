package zlk.idcalc;

import zlk.util.PrettyPrinter;

public record IcLet(
		IcDecl decl,
		IcExp body)
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
