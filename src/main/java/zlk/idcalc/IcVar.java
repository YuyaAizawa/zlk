package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

public record IcVar(
		String name,
		IdInfo idInfo)
implements IcExp {

	@Override
	public <R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp) {
		return forVar.apply(this);
	}

	@Override
	public void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp) {
		forVar.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(idInfo.name()).append(String.format("<%04d>", idInfo.id()));
	}
}
