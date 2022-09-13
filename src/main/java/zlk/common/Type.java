package zlk.common;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrintable;

public sealed interface Type extends PrettyPrintable
permits TyUnit, TyBool, TyI32, TyArrow {

	public static final TyUnit unit = new TyUnit();
	public static final TyBool bool = new TyBool();
	public static final TyI32 i32 = new TyI32();
	public static Type arrow(Type... rest) {
		if(rest.length < 2) {
			throw new IllegalArgumentException("length: "+rest.length);
		}

		Type tail = rest[rest.length - 1];
		for(int idx = rest.length - 2; idx > 0; idx--) {
			tail = new TyArrow(rest[idx], tail);
		}
		return new TyArrow(rest[0], tail);
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

	default TyArrow asArrow() {
		return fold(
				unit  -> null,
				bool  -> null,
				i32   -> null,
				arrow -> arrow);
	}

	default boolean isArrow() {
		return asArrow() != null;
	}

	default Type apply(int cnt) {
		Type type = this;
		for(int i = 0; i < cnt ; i++) {
			type = type.fold(
					unit  -> { throw new IndexOutOfBoundsException(cnt); },
					bool  -> { throw new IndexOutOfBoundsException(cnt); },
					i32   -> { throw new IndexOutOfBoundsException(cnt); },
					arrow -> arrow.ret());
		}
		return type;
	}

	default Type arg(int idx) {
		return apply(idx).fold(
				unit  -> unit,
				bool  -> bool,
				i32   -> i32,
				arrow -> arrow.arg());
	}
}
