package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 式を表すインターフェース．イミュータブル．
 * @author YuyaAizawa
 *
 */
@SuppressWarnings("preview")
public sealed interface Exp
permits Const, Id, App {

	<R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp);

	void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp);

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