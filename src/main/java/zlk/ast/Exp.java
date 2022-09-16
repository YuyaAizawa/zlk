package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 式を表すインターフェース．イミュータブル．
 * @author YuyaAizawa
 *
 */
public sealed interface Exp extends PrettyPrintable
permits Const, Identifier, App, If, Let {

	default <R> R fold(
			Function<Const, R> forConst,
			Function<Identifier, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf,
			Function<Let, R> forLet) {
		if(this instanceof Const cnst) {
			return forConst.apply(cnst);
		} else if(this instanceof Identifier id) {
			return forId.apply(id);
		} else if(this instanceof App app) {
			return forApp.apply(app);
		} else if(this instanceof If ifExp) {
			return forIf.apply(ifExp);
		} else if(this instanceof Let let) {
			return forLet.apply(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<Const> forConst,
			Consumer<Identifier> forId,
			Consumer<App> forApp,
			Consumer<If> forIf,
			Consumer<Let> forLet) {
		if(this instanceof Const cnst) {
			forConst.accept(cnst);
		} else if(this instanceof Identifier id) {
			forId.accept(id);
		} else if(this instanceof App app) {
			forApp.accept(app);
		} else if(this instanceof If ifExp) {
			forIf.accept(ifExp);
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

	public Location loc();

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	void mkString(PrettyPrinter pp);
}