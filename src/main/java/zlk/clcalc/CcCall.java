package zlk.clcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcCall(
		CcExp fun,
		List<CcExp> args,
		Location loc)
implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("call:").endl().inc();
		pp.field("funExp", fun);
		pp.append("argExp:").endl().inc();
		args.forEach(pp::append);
		pp.dec().dec();
	}
}
