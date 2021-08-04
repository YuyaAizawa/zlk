package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

public record InfoFun(
		String module,
		String name,
		Type type)
implements Info {

	@Override
	public <R> R map(
			Function<InfoFun, R> forFun,
			Function<InfoArg, R> forArg,
			Function<InfoBuiltin, R> forBuiltin) {
		return forFun.apply(this);
	}

	@Override
	public void match(
			Consumer<InfoFun> forFun,
			Consumer<InfoArg> forArg,
			Consumer<InfoBuiltin> forBuiltin) {
		forFun.accept(this);
	}
}
