package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TyVar implements Type {
	private String name;

	public TyVar(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	@Override
	public <R> R fold(
			Function<TyAtom, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			Function<String, R> forVar) {
		return forVar.apply(name);
	}

	@Override
	public void match(
			Consumer<TyAtom> forBase,
			BiConsumer<Type, Type> forArrow,
			Consumer<String> forVar) {
		forVar.accept(name);
	}

	@Override
	public String toString() {
		return name;
	}
}
