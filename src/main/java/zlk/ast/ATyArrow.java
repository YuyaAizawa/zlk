package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record ATyArrow(
	AType arg,
	AType ret,
	Location loc
) implements AType {

	@Override
	public void mkString(PrettyPrinter pp) {
		arg.fold(
			base  -> pp.append(base),
			arrow -> pp.append("(").append(arrow).append(")"))
				.append(" -> ").append(ret);
	}

	@Override
	public String toString() {
		return arg + " -> " + ret;
	}

}
