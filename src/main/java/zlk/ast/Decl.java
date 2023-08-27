package zlk.ast;

import java.util.List;
import java.util.Optional;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Decl(
		String name,
		Optional<AType> anno,
		List<Var> args,
		Exp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		anno.ifPresent(ty -> pp.append(name).append(" : ").append(ty).endl());
		pp.append(name);
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" =").endl();
		pp.inc().append(body).dec();
	}
}
