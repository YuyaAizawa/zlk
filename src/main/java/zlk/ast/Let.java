package zlk.ast;

public record Let(
		Decl decl,
		Exp body)
implements Exp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("let ");
		decl.mkString(sb);
		sb.append(" in ");
		body.mkString(sb);
	}
}
