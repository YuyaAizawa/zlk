package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("preview")
public sealed interface Type
permits TyUnit, TyBool, TyI32, TyArrow {

	public static final TyUnit unit = new TyUnit();
	public static final TyBool bool = new TyBool();
	public static final TyI32 i32 = new TyI32();
	public static Type arrow(Type fun, Type arg) {
		return new TyArrow(fun, arg);
	}

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

	public default TyArrow asArrow() {
		return map(
				unit  -> null,
				bool  -> null,
				i32   -> null,
				arrow -> arrow);
	}

	default Type nth(int idx) {
		Type type = this;
		int rest = idx;
		while(rest > 0) {
			type = type.map(
					unit  -> { throw new IndexOutOfBoundsException(idx); },
					bool  -> { throw new IndexOutOfBoundsException(idx); },
					i32   -> { throw new IndexOutOfBoundsException(idx); },
					arrow -> arrow.ret());
			rest--;
		}
		return type.map(
				unit  -> unit,
				bool  -> bool,
				i32   -> i32,
				arrow -> arrow.arg());
	}

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
