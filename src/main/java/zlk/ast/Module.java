package zlk.ast;

import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Module(
		String name,
		Seq<Decl> decls
) implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module ").append(name()).endl();

		decls.forEach(decl -> {
			pp.endl();
			pp.append(decl).endl();
		});
	}
}
