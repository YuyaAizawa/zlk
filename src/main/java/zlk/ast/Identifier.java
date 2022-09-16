package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record Identifier(
		String name,
		Location loc)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
	}
}
