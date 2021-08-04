package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

@SuppressWarnings("preview")
public sealed interface Info
permits InfoFun, InfoArg, InfoBuiltin {

	String name();
	Type type();

	<R> R map(
			Function<InfoFun, R> forFun,
			Function<InfoArg, R> forArg,
			Function<InfoBuiltin, R> forBuiltin);

	void match(
			Consumer<InfoFun> forFun,
			Consumer<InfoArg> forArg,
			Consumer<InfoBuiltin> forBuiltin);
}
