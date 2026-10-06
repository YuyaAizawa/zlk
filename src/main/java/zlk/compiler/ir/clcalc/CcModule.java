package zlk.compiler.ir.clcalc;

import zlk.compiler.ir.typing.TypeDecl;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcModule(
		String name,
		Seq<TypeDecl> types,
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
