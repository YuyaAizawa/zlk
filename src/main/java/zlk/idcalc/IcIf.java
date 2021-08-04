package zlk.idcalc;

public record IcIf(
		IcExp cond,
		IcExp exp1,
		IcExp exp2)
implements IcExp {

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
