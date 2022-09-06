package zlk.ast;

import java.util.List;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Module(
		String name,
		List<Decl> decls,
		String origin)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module ").append(name()).endl();

		decls.forEach(decl -> {
			pp.endl();
			pp.append(decl).endl();
		});
	}
}
