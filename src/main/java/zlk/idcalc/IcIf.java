package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

public record IcIf(
		IcExp cond,
		IcExp exp1,
		IcExp exp2)
implements IcExp {

	@Override
	public <R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp,
			Function<IcIf, R> forIf) {
		return forIf.apply(this);
	}

	@Override
	public void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp,
			Consumer<IcIf> forIf) {
		forIf.accept(this);
	}

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
