package zlk.ast;

import zlk.util.pp.PrettyPrinter;

public record Id(
		String name)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
	}
}
