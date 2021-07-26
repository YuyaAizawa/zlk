package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

public record If(
		Exp cond,
		Exp exp1,
		Exp exp2)
implements Exp {

	@Override
	public <R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf) {
		return forIf.apply(this);
	}

	@Override
	public void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp,
			Consumer<If> forIf) {
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
