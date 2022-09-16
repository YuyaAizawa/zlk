package zlk.ast;

import java.util.List;

import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Decl(
		String name,
		List<String> args,
		Type type,
		Exp body,
		Location loc)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" : ").append(type).append(" =").endl();
		pp.inc().append(body).dec();
	}
}
