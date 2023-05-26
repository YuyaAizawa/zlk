package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public record TyBool ()
implements Type {

	@Override
	public 	<R> R fold(
			IntFunction<R> forVar,
			Supplier<R> forBool,
			Supplier<R> forI32,
			BiFunction<Type, Type, R> forArrow) {
		return forBool.get();
	}

	@Override
	public void match(
			IntConsumer forVar,
			Runnable forBool,
			Runnable forI32,
			BiConsumer<Type, Type> forArrow) {
		forBool.run();
	}

	@Override
	public String toString() {
		return "Bool";
	}
}
