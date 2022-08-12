package zlk.ast;

import java.util.List;

public record Let(
		List<Decl> decls,
		Exp body)
implements Exp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("let ");
		decls.forEach(decl -> decl.mkString(sb));
		sb.append(" in ");
		body.mkString(sb);
	}
}
