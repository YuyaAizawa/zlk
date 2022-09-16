package zlk.clcalc;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcIf(
		CcExp cond,
		CcExp thenExp,
		CcExp elseExp,
		Location loc
) implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("if:").endl().inc();
		pp.field("cond", cond);
		pp.field("then", thenExp);
		pp.field("else", elseExp);
		pp.dec();
	}
}
