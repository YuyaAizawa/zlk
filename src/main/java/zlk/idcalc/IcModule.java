package zlk.idcalc;

import java.util.List;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcModule(
		String name,
		List<IcType> types,
		List<IcDecl> decls,
		String origin)
implements PrettyPrintable{

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module ").append(name()).endl();

		types.forEach(ty -> {
			pp.endl();
			pp.append(ty).endl();
		});

		decls.forEach(decl -> {
			pp.endl();
			pp.append(decl).endl();
		});
	}
}
