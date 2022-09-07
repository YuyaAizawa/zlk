package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrinter;

public sealed interface Const extends Exp
permits Bool, I32 {

	static Bool bool(boolean value) {
		return value ? Bool.TRUE : Bool.FALSE;
	}

	static I32 i32(int value) {
		return new I32(value);
	}

	default <R> R fold(
			Function<Bool, R> forBool,
			Function<I32, R> forI32) {
		if(this instanceof Bool bool) {
			return forBool.apply(bool);
		} else if(this instanceof I32 i32) {
			return forI32.apply(i32);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<Bool> forBool,
			Consumer<I32> forI32) {
		if(this instanceof Bool bool) {
			forBool.accept(bool);
		} else if(this instanceof I32 i32) {
			forI32.accept(i32);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		match(
				bool -> pp.append(bool.value() ? "True" : "False"),
				i32  -> pp.append(i32.value()));
	}
}

