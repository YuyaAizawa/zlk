package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.PrettyPrintable;

public sealed interface Type extends PrettyPrintable
permits TyUnit, TyBool, TyI32, TyArrow {

	public static final TyUnit unit = new TyUnit();
	public static final TyBool bool = new TyBool();
	public static final TyI32 i32 = new TyI32();
	public static Type arrow(Type fun, Type arg) {
		return new TyArrow(fun, arg);
	}

	<R> R fold(
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
		return fold(
				unit  -> null,
				bool  -> null,
				i32   -> null,
				arrow -> arrow);
	}

	public default boolean isArrow() {
		return asArrow() != null;
	}

	default Type nth(int idx) {
		Type type = this;
		int rest = idx;
		while(rest > 0) {
			type = type.fold(
					unit  -> { throw new IndexOutOfBoundsException(idx); },
					bool  -> { throw new IndexOutOfBoundsException(idx); },
					i32   -> { throw new IndexOutOfBoundsException(idx); },
					arrow -> arrow.ret());
			rest--;
		}
		return type.fold(
				unit  -> unit,
				bool  -> bool,
				i32   -> i32,
				arrow -> arrow.arg());
	}
}
