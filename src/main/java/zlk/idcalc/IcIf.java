package zlk.idcalc;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcIf(
		IcExp cond,
		IcExp exp1,
		IcExp exp2,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("if ").append(cond).append(" then").endl();
		pp.inc().append(exp1).dec().endl();
		pp.append("else");
		if(IcExp.isIf(exp2)) {
			pp.append(" ").append(exp2);
		} else {
			pp.endl();
			pp.inc().append(exp2).dec();
		}
	}
}
