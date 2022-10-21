package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 式を表すインターフェース．イミュータブル．
 * @author YuyaAizawa
 *
 */
public sealed interface Exp extends PrettyPrintable, LocationHolder
permits Cnst, Var, App, If, Let {

	default <R> R fold(
			Function<? super Cnst, ? extends R> forCnst,
			Function<? super Var, ? extends R> forVar,
			Function<? super App, ? extends R> forApp,
			Function<? super If, ? extends R> forIf,
			Function<? super Let, ? extends R> forLet) {
		if(this instanceof Cnst cnst) {
			return forCnst.apply(cnst);
		} else if(this instanceof Var var) {
			return forVar.apply(var);
		} else if(this instanceof App app) {
			return forApp.apply(app);
		} else if(this instanceof If if_) {
			return forIf.apply(if_);
		} else if(this instanceof Let let) {
			return forLet.apply(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super Cnst> forCnst,
			Consumer<? super Var> forVar,
			Consumer<? super App> forApp,
			Consumer<? super If> forIf,
			Consumer<? super Let> forLet) {
		if(this instanceof Cnst cnst) {
			forCnst.accept(cnst);
		} else if(this instanceof Var var) {
			forVar.accept(var);
		} else if(this instanceof App app) {
			forApp.accept(app);
		} else if(this instanceof If if_) {
			forIf.accept(if_);
		} else if(this instanceof Let let) {
			forLet.accept(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	static boolean isIf(Exp exp) {
		return exp instanceof If;
	}

	static boolean isLet(Exp exp) {
		return exp instanceof Let;
	}

	@Override
	public Location loc();

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	void mkString(PrettyPrinter pp);
}