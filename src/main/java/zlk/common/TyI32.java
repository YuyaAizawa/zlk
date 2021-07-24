package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

public record TyI32 ()
implements Type {

	@Override
	public 	<R> R map(
			Function<TyUnit, R> forUnit,
			Function<TyBool, R> forBool,
			Function<TyI32, R> forI32,
			Function<TyArrow, R> forArrow) {
		return forI32.apply(this);
	}

	@Override
	public void match(
			Consumer<TyUnit> forUnit,
			Consumer<TyBool> forBool,
			Consumer<TyI32> forI32,
			Consumer<TyArrow> forArrow) {
		forI32.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("I32");
	}
}
