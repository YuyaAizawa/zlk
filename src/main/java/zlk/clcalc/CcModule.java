package zlk.clcalc;

import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcModule(
		String name,
		Seq<CcTypeDecl> types,
		Seq<CcFunDecl> funcs)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module:").endl();
		pp.indent(() -> {
			pp.append("name: ").append(name).endl();
			pp.append("decls:");
			pp.indent(() -> {
				types.forEach(type -> pp.endl().append(type));
				funcs.forEach(func -> pp.endl().append(func));

			});
		});
	}
}
