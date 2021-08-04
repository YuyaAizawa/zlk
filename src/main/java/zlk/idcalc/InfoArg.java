package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

public record InfoArg(
		String name,
		Type type,
		InfoFun fun,
		int idx)
implements Info {

	@Override
	public <R> R map(
			Function<InfoFun, R> forFun,
			Function<InfoArg, R> forArg,
			Function<InfoBuiltin, R> forBuiltin) {
		return forArg.apply(this);
	}

	@Override
	public void match(
			Consumer<InfoFun> forFun,
			Consumer<InfoArg> forArg,
			Consumer<InfoBuiltin> forBuiltin) {
		forArg.accept(this);
	}
}
