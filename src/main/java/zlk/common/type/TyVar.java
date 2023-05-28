package zlk.common.type;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class TyVar implements Type {
	private static final AtomicInteger fresh = new AtomicInteger();
	private final int varId;

	public TyVar() {
		this.varId = fresh.getAndIncrement();
	}

	@Override
	public <R> R fold(
			Function<TyBase, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			IntFunction<R> forVar) {
		return forVar.apply(varId);
	}

	@Override
	public void match(
			Consumer<TyBase> forBase,
			BiConsumer<Type, Type> forArrow,
			IntConsumer forVar) {
		forVar.accept(varId);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
