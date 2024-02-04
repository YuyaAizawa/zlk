package zlk.idcalc;

import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcCaseBranch(
		IcPattern pattern,
		IcExp body,
		Location loc)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(pattern).append(" ->").inc().endl();
		pp.append(body).dec();
	}
}
