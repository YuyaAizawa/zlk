package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.ast.Const;

public record IcConst(
		Const cnst)
implements IcExp {

	@Override
	public <R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp) {
		return forConst.apply(this);
	}

	@Override
	public void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp) {
		forConst.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		cnst().mkString(sb);
	}

}
