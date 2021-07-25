package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

public record IdArg(
		int id,
		String name,
		Type type,
		IdFun fun,
		int idx)
implements IdInfo {

	@Override
	public <R> R map(
			Function<IdFun, R> forFun,
			Function<IdArg, R> forArg,
			Function<IdBuiltin, R> forBuiltin) {
		return forArg.apply(this);
	}

	@Override
	public void match(
			Consumer<IdFun> forFun,
			Consumer<IdArg> forArg,
			Consumer<IdBuiltin> forBuiltin) {
		forArg.accept(this);
	}
}
