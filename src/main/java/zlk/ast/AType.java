package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;

public sealed interface AType extends PrettyPrintable, LocationHolder
permits ATyAtom, ATyArrow {
	default <R> R fold(
			Function<? super ATyAtom, ? extends R> forBase,
			Function<? super ATyArrow, ? extends R> forArrow) {
		if(this instanceof ATyAtom base) {
			return forBase.apply(base);
		} else if(this instanceof ATyArrow arrow) {
			return forArrow.apply(arrow);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super ATyAtom> forBase,
			Consumer<? super ATyArrow> forArrow) {
		if(this instanceof ATyAtom base) {
			forBase.accept(base);
		} else if(this instanceof ATyArrow arrow) {
			forArrow.accept(arrow);
		} else {
			throw new Error(this.getClass().toString());
		}
	}
}
