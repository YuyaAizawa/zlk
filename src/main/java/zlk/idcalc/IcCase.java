package zlk.idcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcCase(
		IcExp target,
		List<IcCaseBranch> branches,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("case ").append(target).append(" of").inc();
		for(IcCaseBranch branch: branches) {
			pp.endl().append(branch);
		}
		pp.dec();
	}
}