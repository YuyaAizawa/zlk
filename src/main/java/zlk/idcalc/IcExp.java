package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("preview")
public sealed interface IcExp
permits IcConst, IcVar, IcApp {

	<R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp);

	void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp);

	void mkString(StringBuilder sb);

	default String mkString() {
		StringBuilder sb = new StringBuilder();
		mkString(sb);
		return sb.toString();
	}

	default void mkStringEnclosed(StringBuilder sb) {
		sb.append("(");
		mkString(sb);
		sb.append(")");
	}
}