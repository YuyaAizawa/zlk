package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrinter;

public record TyArrow(
		Type arg,
		Type ret)
implements Type {

	@Override
	public 	<R> R fold(
			Function<TyUnit, R> forUnit,
			Function<TyBool, R> forBool,
			Function<TyI32, R> forI32,
			Function<TyArrow, R> forArrow) {
		return forArrow.apply(this);
	}

	@Override
	public void match(
			Consumer<TyUnit> forUnit,
			Consumer<TyBool> forBool,
			Consumer<TyI32> forI32,
			Consumer<TyArrow> forArrow) {
		forArrow.accept(this);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		arg.match(
				unit  -> pp.append(unit),
				bool  -> pp.append(bool),
				i32   -> pp.append(i32),
				arrow -> pp.append("(").append(arrow).append(")"));
		pp.append(" -> ").append(ret);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}