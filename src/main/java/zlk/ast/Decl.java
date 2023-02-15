package zlk.ast;

import java.util.List;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Decl(
		String name,
		AType anno,
		List<String> args,
		Exp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name).append(" : ").append(anno).endl();
		pp.append(name);
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" =").endl();
		pp.inc().append(body).dec();
	}
}
