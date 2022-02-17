package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.MkString;

/**
 * 式を表すインターフェース．イミュータブル．
 * @author YuyaAizawa
 *
 */
public sealed interface Exp extends MkString
permits Const, Id, App, If {

	default <R> R fold(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf) {
		if(this instanceof Const cnst) {
			return forConst.apply(cnst);
		} else if(this instanceof Id id) {
			return forId.apply(id);
		} else if(this instanceof App app) {
			return forApp.apply(app);
		} else if(this instanceof If ifExp) {
			return forIf.apply(ifExp);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp,
			Consumer<If> forIf) {
		if(this instanceof Const cnst) {
			forConst.accept(cnst);
		} else if(this instanceof Id id) {
			forId.accept(id);
		} else if(this instanceof App app) {
			forApp.accept(app);
		} else if(this instanceof If ifExp) {
			forIf.accept(ifExp);
		} else {
			throw new Error(this.getClass().toString());
		}
	}
}