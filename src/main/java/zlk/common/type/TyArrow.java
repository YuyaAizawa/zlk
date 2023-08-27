package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public record TyArrow(
		Type arg,
		Type ret)
implements Type {

	@Override
	public <R> R fold(
			Function<TyAtom, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			Function<String, R> forVar) {
		return forArrow.apply(arg, ret);
	}

	@Override
	public void match(
			Consumer<TyAtom> forBase,
			BiConsumer<Type, Type> forArrow,
			Consumer<String> forVar) {
		forArrow.accept(arg, ret);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}