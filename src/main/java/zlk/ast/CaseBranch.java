package zlk.ast;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CaseBranch(
	Pattern pattern,
	Exp body,
	Location loc)
implements PrettyPrintable, LocationHolder {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(pattern).append(" ->");
		pp.indent(() -> {
			pp.endl().append(body);
		});
	}
}
