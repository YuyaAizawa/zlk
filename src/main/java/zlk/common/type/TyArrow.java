package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public record TyArrow(
		Type arg,
		Type ret)
implements Type {

	@Override
	public 	<R> R fold(
			IntFunction<R> forVar,
			Supplier<R> forBool,
			Supplier<R> forI32,
			BiFunction<Type, Type, R> forArrow) {
		return forArrow.apply(arg, ret);
	}

	@Override
	public void match(
			IntConsumer forVar,
			Runnable forBool,
			Runnable forI32,
			BiConsumer<Type, Type> forArrow) {
		forArrow.accept(arg, ret);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}