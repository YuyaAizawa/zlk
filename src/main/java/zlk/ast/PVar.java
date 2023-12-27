package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record PVar(
		String name,
		Location loc)
implements Pattern {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
	}
}
