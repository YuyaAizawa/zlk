package zlk.common.type;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class TyVar implements Type {
	private static final AtomicInteger fresh = new AtomicInteger();
	private final int varId;

	public TyVar() {
		this.varId = fresh.getAndIncrement();
	}

	@Override
	public 	<R> R fold(
			IntFunction<R> forVar,
			Supplier<R> forBool,
			Supplier<R> forI32,
			BiFunction<Type, Type, R> forArrow) {
		return forVar.apply(varId);
	}

	@Override
	public void match(
			IntConsumer forVar,
			Runnable forBool,
			Runnable forI32,
			BiConsumer<Type, Type> forArrow) {
		forVar.accept(varId);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
