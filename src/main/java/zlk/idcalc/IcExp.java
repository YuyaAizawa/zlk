package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.MkString;

public sealed interface IcExp extends MkString
permits IcConst, IcVar, IcApp, IcIf {

	default <R> R fold(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp,
			Function<IcIf, R> forIf) {
		if(this instanceof IcConst cnst) {
			return forConst.apply(cnst);
		} else if(this instanceof IcVar id) {
			return forVar.apply(id);
		} else if(this instanceof IcApp app) {
			return forApp.apply(app);
		} else if(this instanceof IcIf ifExp) {
			return forIf.apply(ifExp);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp,
			Consumer<IcIf> forIf) {
		if(this instanceof IcConst cnst) {
			forConst.accept(cnst);
		} else if(this instanceof IcVar id) {
			forVar.accept(id);
		} else if(this instanceof IcApp app) {
			forApp.accept(app);
		} else if(this instanceof IcIf ifExp) {
			forIf.accept(ifExp);
		} else {
			throw new Error(this.getClass().toString());
		}
	}
}