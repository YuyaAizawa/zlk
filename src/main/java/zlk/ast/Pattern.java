package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;

public sealed interface Pattern extends PrettyPrintable, LocationHolder
permits PVar, PCtor {
	default <R> R fold(
			Function<? super PVar, ? extends R> forVar,
			Function<? super PCtor, ? extends R> forCtor) {
		if(this instanceof PVar var) {
			return forVar.apply(var);
		} else if(this instanceof PCtor ctor) {
			return forCtor.apply(ctor);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super PVar> forVar,
			Consumer<? super PCtor> forCtor) {
		if(this instanceof PVar var) {
			forVar.accept(var);
			return;
		} else if(this instanceof PCtor ctor) {
			forCtor.accept(ctor);
			return;
		} else {
			throw new Error(this.getClass().toString());
		}
	}
}
