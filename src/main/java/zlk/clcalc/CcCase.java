package zlk.clcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcCase(
		CcExp target,
		List<CcCaseBranch> branches,
		Location loc) implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("case ").append(target).append(" of").inc();
		for(CcCaseBranch branch: branches) {
			pp.endl().append(branch);
		}
		pp.dec();
	}
}
