package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CaseBranch(
	Pattern pattern,
	Exp body,
	Location loc)
implements PrettyPrintable {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("| ").append(pattern).append(" ->").endl();
		pp.indent(() -> {
			pp.append(body);
		});
	}
}
