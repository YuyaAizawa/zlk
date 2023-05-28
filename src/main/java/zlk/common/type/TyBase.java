package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public enum TyBase implements Type, PrettyPrintable {
	BOOL("Bool"),
	I32 ("I32");

	public final String name;

	private TyBase(String name) {
		this.name = name;
	}

	@Override
	public <R> R fold(
			Function<TyBase, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			IntFunction<R> forVar) {
		return forBase.apply(this);
	}

	@Override
	public void match(
			Consumer<TyBase> forBase,
			BiConsumer<Type, Type> forArrow,
			IntConsumer forVar) {
		forBase.accept(this);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
	}
}
