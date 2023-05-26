package zlk.recon;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrintable;

public sealed interface TypeSchema extends PrettyPrintable
permits TsVar, TsBase, TsArrow {
	default <R> R fold(
			Function<? super TsVar, ? extends R> forVar,
			Function<? super TsBase, ? extends R> forBase,
			Function<? super TsArrow, ? extends R> forArrow) {
		if(this instanceof TsVar var) {
			return forVar.apply(var);
		} else if(this instanceof TsBase base) {
			return forBase.apply(base);
		} else if(this instanceof TsArrow arrow) {
			return forArrow.apply(arrow);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super TsVar> forVar,
			Consumer<? super TsBase> forBase,
			Consumer<? super TsArrow> forArrow) {
		if(this instanceof TsVar var) {
			forVar.accept(var);
		} else if(this instanceof TsBase base) {
			forBase.accept(base);
		} else if(this instanceof TsArrow arrow) {
			forArrow.accept(arrow);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default boolean contains(TsVar alpha) {
		return fold(
				var   -> var.equals(alpha),
				base  -> false,
				arrow -> arrow.arg().contains(alpha) || arrow.ret().contains(alpha));
	}

	default TypeSchema apply(int size) {
		if(size == 0) {
			return this;
		} else {
			return fold(
					var   -> {throw new IllegalArgumentException();},
					base  -> {throw new IllegalArgumentException();},
					arrow -> arrow.ret().apply(size - 1));
		}
	}
}
