package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

public record IdFun(
		int id,
		String module,
		String name,
		Type type)
implements IdInfo {

	@Override
	public <R> R map(
			Function<IdFun, R> forFun,
			Function<IdArg, R> forArg,
			Function<IdBuiltin, R> forBuiltin) {
		return forFun.apply(this);
	}

	@Override
	public void match(
			Consumer<IdFun> forFun,
			Consumer<IdArg> forArg,
			Consumer<IdBuiltin> forBuiltin) {
		forFun.accept(this);
	}
}
