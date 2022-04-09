package zlk.idcalc;

public record IcLet(
		IcDecl decl,
		IcExp body
) implements IcExp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("let ");
		decl.mkString(sb);
		sb.append(" in ");
		body.mkString(sb);
	}
}
