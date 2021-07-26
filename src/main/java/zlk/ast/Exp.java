package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.MkString;

/**
 * 式を表すインターフェース．イミュータブル．
 * @author YuyaAizawa
 *
 */
@SuppressWarnings("preview")
public sealed interface Exp extends MkString
permits Const, Id, App, If {

	<R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf);

	void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp,
			Consumer<If> forIf);
}