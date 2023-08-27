package zlk.common.type;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record TyAtom(Id id) implements Type, PrettyPrintable {

	public TyAtom(String name) {
		this(Id.fromCanonicalName(name));
	}

	@Override
	public <R> R fold(
			Function<TyAtom, R> forBase,
			BiFunction<Type, Type, R> forArrow,
			Function<String, R> forVar) {
		return forBase.apply(this);
	}

	@Override
	public void match(
			Consumer<TyAtom> forBase,
			BiConsumer<Type, Type> forArrow,
			Consumer<String> forVar) {
		forBase.accept(this);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
	}

	@Override
	public String toString() {
		return id.toString();
	}
}
