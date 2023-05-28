package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public record TyArrow(
		Type arg,
		Type ret)
implements Type {



	@Override
	public <R> R fold(
			Function<TyBase, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			IntFunction<R> forVar) {
		return forArrow.apply(arg, ret);
	}

	@Override
	public void match(
			Consumer<TyBase> forBase,
			BiConsumer<Type, Type> forArrow,
			IntConsumer forVar) {
		forArrow.accept(arg, ret);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}