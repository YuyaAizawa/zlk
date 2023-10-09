package zlk.clcalc;

import java.util.List;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcModule(
		String name,
		List<CcType> types,
		List<CcDecl> toplevels,
		String origin)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module:").endl().inc();
		pp.append("name: ").append(name).endl();
		pp.append("origin: ").append(origin).endl();

		pp.append("types:").endl().inc();
		types.forEach(type -> pp.append(type).endl());
		pp.dec();

		pp.append("decls:").endl().inc();
		toplevels.forEach(pp::append);
		pp.dec().dec();
	}
}
