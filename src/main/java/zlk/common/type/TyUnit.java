package zlk.common.type;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrinter;

public enum TyUnit implements Type {
	SINGLETON;

	@Override
	public 	<R> R fold(
			Function<TyUnit, R> forUnit,
			Function<TyBool, R> forBool,
			Function<TyI32, R> forI32,
			Function<TyArrow, R> forArrow) {
		return forUnit.apply(this);
	}

	@Override
	public void match(
			Consumer<TyUnit> forUnit,
			Consumer<TyBool> forBool,
			Consumer<TyI32> forI32,
			Consumer<TyArrow> forArrow) {
		forUnit.accept(this);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("()");
	}

	@Override
	public String toString() {
		return "()";
	}
}
