package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

public record TyUnit()
implements Type {

	@Override
	public 	<R> R map(
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
	public void mkString(StringBuilder sb) {
		sb.append("()");
	}
}
