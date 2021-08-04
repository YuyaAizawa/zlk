package zlk.ast;

public record If(
		Exp cond,
		Exp exp1,
		Exp exp2)
implements Exp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("if ");
		cond.mkString(sb);
		sb.append(" then ");
		exp1.mkString(sb);
		sb.append(" else ");
		exp2.mkString(sb);
	}
}
