package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.MkString;

@SuppressWarnings("preview")
public sealed interface IcExp extends MkString
permits IcConst, IcVar, IcApp, IcIf {

	<R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp,
			Function<IcIf, R> forIf);

	void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp,
			Consumer<IcIf> forIf);
}