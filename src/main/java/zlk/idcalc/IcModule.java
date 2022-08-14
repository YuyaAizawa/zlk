package zlk.idcalc;

import java.util.List;

import zlk.util.PrettyPrintable;
import zlk.util.PrettyPrinter;

public record IcModule(
		String name,
		List<IcDecl> decls,
		String origin)
implements PrettyPrintable{

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module ").append(name()).endl();
		decls.forEach(decl -> {
			pp.endl();
			pp.append(decl).endl();
		});
	}
}
