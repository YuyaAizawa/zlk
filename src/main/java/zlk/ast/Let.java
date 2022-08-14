package zlk.ast;

import java.util.List;

import zlk.util.PrettyPrinter;

public record Let(
		List<Decl> decls,
		Exp body)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("let").endl();

		pp.inc();
		for(Decl decl : decls) {
			pp.append(decl).endl();
		}
		pp.dec();

		pp.append("in");
		if(Exp.isLet(body)) {
			pp.append(" ").append(body);
		} else {
			pp.endl();
			pp.inc().append(body).dec();
		}
	}
}
