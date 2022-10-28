package zlk.ast;

import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record Lamb(
		String var,
		Type type,
		Exp body,
		Location loc
) implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("\\").append(var).append(" : ").append(type).append(".").append(body);
	}
}
