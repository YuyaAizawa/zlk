package zlk.clcalc;

import java.util.List;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcModule(
		String name,
		List<CcTypeDecl> types,
		List<CcFunDecl> funcs,
		String origin)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module:").endl();
		pp.indent(() -> {
			pp.append("name: ").append(name).endl();
			pp.append("origin: ").append(origin).endl();
			pp.append("decls:");
			pp.indent(() -> {
				types.forEach(type -> pp.endl().append(type));
				funcs.forEach(func -> pp.endl().append(func));

			});
		});
	}
}
