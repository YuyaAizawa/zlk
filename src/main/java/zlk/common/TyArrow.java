package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

public record TyArrow(
		Type arg,
		Type ret)
implements Type {

	@Override
	public 	<R> R map(
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
	public void mkString(StringBuilder sb) {
		arg.match(
				unit -> unit.mkString(sb),
				bool -> bool.mkString(sb),
				i32 -> i32.mkString(sb),
				arrow -> arrow.mkStringEnclosed(sb));
		sb.append(" -> ");
		ret.mkString(sb);
	}
}