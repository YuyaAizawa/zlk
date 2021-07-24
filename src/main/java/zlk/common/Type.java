package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("preview")
public sealed interface Type
permits TyUnit, TyBool, TyI32, TyArrow {

	static final TyUnit unit = new TyUnit();
	static final TyBool BOOL = new TyBool();
	static final TyI32 I32 = new TyI32();

	<R> R map(
			Function<TyUnit, R> forUnit,
			Function<TyBool, R> forBool,
			Function<TyI32, R> forI32,
			Function<TyArrow, R> forArrow);

	void match(
			Consumer<TyUnit> forUnit,
			Consumer<TyBool> forBool,
			Consumer<TyI32> forI32,
			Consumer<TyArrow> forArrow);

	void mkString(StringBuilder sb);

	default String mkString() {
		StringBuilder sb = new StringBuilder();
		mkString(sb);
		return sb.toString();
	}

	default void mkStringEnclosed(StringBuilder sb) {
		sb.append("(");
		mkString(sb);
		sb.append(")");
	}
}
