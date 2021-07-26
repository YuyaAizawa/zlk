package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("preview")
public sealed interface Const extends Exp
permits Bool, I32 {

	static Bool bool(boolean value) {
		return value ? Bool.TRUE : Bool.FALSE;
	}

	static I32 i32(int value) {
		return new I32(value);
	}

	<R> R map(
			Function<Bool, R> forBool,
			Function<I32, R> forI32);

	void match(
			Consumer<Bool> forBool,
			Consumer<I32> forI32);

	@Override
	default <R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf) {
		return forConst.apply(this);
	}

	@Override
	default void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp,
			Consumer<If> forIf) {
		forConst.accept(this);
	}

	@Override
	default void mkString(StringBuilder sb) {
		match(
				bool -> sb.append(bool.value() ? "True" : "False"),
				i32  -> sb.append(i32.value()));
	}
}
