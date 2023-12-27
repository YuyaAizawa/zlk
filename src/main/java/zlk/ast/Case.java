package zlk.ast;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record Case(
		Exp exp,
		List<CaseBranch> branches,
		Location loc)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("case ").append(exp).append(" of").inc();
		for(CaseBranch branch : branches) {
			pp.endl();
			pp.append(branch);
		}
		pp.dec();
	}
}
