package zlk.idcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcLetrec(
		List<IcDecl> decls,
		IcExp body,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("letrec").endl().inc();
		for(IcDecl decl : decls) {
			pp.append(decl).endl();
		}
		pp.dec().append("in");
		if(IcExp.isLet(body)) {
			pp.append(" ").append(body);
		} else {
			pp.endl();
			pp.inc().append(body).dec();
		}
	}
}