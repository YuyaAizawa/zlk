package zlk.common.cnst;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrintable;

public sealed interface ConstValue extends PrettyPrintable
permits Bool, I32 {
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
}
