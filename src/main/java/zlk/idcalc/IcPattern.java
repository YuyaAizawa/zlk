package zlk.idcalc;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;

public sealed interface IcPattern extends PrettyPrintable, LocationHolder
permits IcPVar, IcPCtor {

	public default <R> R fold(
			Function<? super IcPVar, ? extends R> forVar,
			Function<? super IcPCtor, ? extends R> forCtor) {
		if(this instanceof IcPVar var) {
			return forVar.apply(var);
		} else if(this instanceof IcPCtor ctor) {
			return forCtor.apply(ctor);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	public default <R> void match(
			Consumer<? super IcPVar> forVar,
			Consumer<? super IcPCtor> forCtor) {
		if(this instanceof IcPVar var) {
			forVar.accept(var);
		} else if(this instanceof IcPCtor ctor) {
			forCtor.accept(ctor);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	public default void addVars(Set<Id> known) {
		match(
				var  -> known.add(var.id()),
				ctor -> ctor.args().forEach(pat -> pat.addVars(known)));
	}
}
